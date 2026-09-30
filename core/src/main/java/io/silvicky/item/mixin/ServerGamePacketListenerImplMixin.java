package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.TransformAudit;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Movement and interaction handling for players.
 *
 * <p>Every position reaching a handler in this class came off a packet that {@code ConnectionMixin} already
 * converted to server coordinates, so the positions here are {@code [S]}. See
 * {@link io.silvicky.item.backrooms.CoordinateSpace} for the marker legend.
 *
 * <p>That fact is now measured rather than assumed. {@link TransformAudit} compared the inputs of every site
 * below against the value {@code ConnectionMixin} had already converted, plus the player's own server-side
 * position, and found all of them S-field. Four sites that were transforming those values a second time
 * ({@code handleUseItemOn/reach}, both {@code movedDist} stores, and the movement-statistics redirect) have
 * been reduced to vanilla's own computation.
 *
 * <p>Two sites are deliberately left transforming, because there the inputs are in <em>different</em> fields
 * and the transform is what makes them comparable - see {@code isEntityCollidingWithAnythingNew}. A site
 * being handed S-field input is only a bug when its counterpart is S-field too.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin
{

    @Shadow public ServerPlayer player;

    @Shadow
    private double lastGoodX;

    @Shadow
    private double lastGoodY;

    @Shadow
    private double lastGoodZ;

    /**
     * The single translation that every operand of the current collision comparison is moved by, set from
     * the player box in inject7 and reused verbatim by inject8.
     *
     * <p>A field on the connection rather than a local, because the two hooks are separate calls with no
     * shared scope. It must be exactly one offset: if the box and the shapes used different ones, the AND
     * test would compare two positions displaced relative to each other by the difference, which is what
     * produced invisible walls.
     *
     * <p>Null means "no offset available", which is also the safe fallback - the whole comparison then runs
     * untransformed, exactly as vanilla would. It is distinct from a genuine zero offset, which is a real
     * (identity) mapping and would be applied. Every path out of inject7 assigns this, including the
     * non-player and unmapped-chunk ones, so inject8 can never read a value left over from an earlier query.
     */
    @Unique
    private Vec3 item_storage$collisionOffset;

    @Redirect(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 inject1(Vec3 instance, Vec3 vec)
    {
        //[S] No transform. Both operands are S-field: ConnectionMixin already converted this packet's
        //hitResult to server coordinates, and the eye position is the server's own state. Subtracting two
        //points after mapping them is only equal to the server-space distance when the transform is a global
        //isometry - true for NOP and LINEAR, false for XOR and RANDOM, which are piecewise or arbitrary.
        //
        //This is also the only remaining user of VecTransformer.INF. That fallback is unreachable in
        //practice (the audit reports 0 unmapped lookups) but it is wrong if ever reached: INF is a position
        //vector being returned where a reach *distance* is expected, so the reach test would compare against
        //1e9 and pass for any block on the server.
        return instance.subtract(vec);
    }
    @ModifyVariable(method = "handlePlayerPositionChange", at = @At(value = "STORE",ordinal = 0),name="movedDist")
    private double inject3(double value,
                           @Local(name = "targetX")double d,
                           @Local(name = "targetY")double e,
                           @Local(name = "targetZ")double h)
    {
        //[S] No transform, and this is the confirmed cause of the movement symptoms.
        //
        //targetX/Y/Z and firstGoodX/Y/Z are both the server's own record of where the player is, so vanilla's
        //movedDist is already correct. The old code mapped both points into the client's view and subtracted
        //them there, which is only equal to the server-space distance when the transform is a global
        //isometry. For RANDOM it is an arbitrary per-chunk offset, so the difference between the two mapped
        //points could be orders of magnitude larger than the real movement - vanilla then logs "moved
        //wrongly" and snaps the player back to the server position. In the void that puts them below the
        //world again, re-triggering onBelowWorld and the noclip teleport, which is the infinite
        //teleportation loop.
        //
        //Measured: 375/375 samples of (targetX, targetZ) equalled the value ConnectionMixin had already
        //converted, confirming these are S-field and were being converted a second time.
        return value;
    }
    @ModifyVariable(method = "handlePlayerPositionChange", at = @At(value = "STORE",ordinal = 1),name = "movedDist")
    private double inject4(double value,
                           @Local(name = "targetX")double d,
                           @Local(name = "targetY")double e,
                           @Local(name = "targetZ")double h)
    {
        //[S] No transform, same reason as inject3. targetX/Y/Z is the converted packet target and
        //player.position() is the server's own state; both S-field, so vanilla's second movedDist is
        //already right. Note this variant zeroes the Y component when |dy| < 0.5, which is vanilla's own
        //rule for "ignore tiny vertical drift" - re-deriving that by hand is another way to drift out of
        //sync with vanilla. Measured: 375/375 samples S-field.
        return value;
    }
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleportSetPosition(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",shift = At.Shift.AFTER))
    private void inject5(PositionMoveRotation positionMoveRotation, Set<Relative> set, CallbackInfo ci)
    {
        //Not a transform: a dimension change has to rebuild the player's transformer, and the cache only
        //refreshes on teleport. A stale instance is not a harmless optimisation - a fresh LinearTransformer
        //draws new random offsets and a fresh RandomTransformer starts with an empty chunk map.
        VecTransformer.refreshInstance(player);
    }
    @ModifyArg(method = "handlePlayerPositionChange", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"),index = 1)
    private Vec3 inject6(Vec3 par2,
                         @Local(name = "targetX")double d,
                         @Local(name = "targetY")double e,
                         @Local(name = "targetZ")double h)
    {
        //[S] No transform. ConnectionMixin already rewrote ServerboundMovePlayerPacket's x/z into server
        //coordinates, so the delta vanilla computes here is already correct.
        return par2;
    }
    @ModifyArg(method = "isEntityCollidingWithAnythingNew", at= @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private AABB inject7(AABB value, @Local(argsOnly = true) Entity entity)
    {
        //[S>C] Required - and the offset it records must be the ONLY one used for this comparison.
        //
        //Both operands are S-field: the shapes in `collisions` come from getPreMoveCollisions, whose
        //BlockCollisions resolves each client-view coordinate to a server position, so the shapes come back
        //in server coordinates, and the entity's own AABB is server coordinates too. Moving both into the
        //client's view is right; moving each by ITS OWN offset is the bug.
        //
        //The previous version computed O_box = s2c(box) - box here, and inject8 independently computed
        //O_wall = s2c(wall) - wall for every shape. The AND test then compared (P + O_box) against
        //(wallPos + O_wall), so the wall was displaced relative to the player by (O_wall - O_box) - thousands
        //of blocks under a per-chunk random map. The wall simply landed somewhere else, which is an invisible
        //wall. The audit line "server_state=953" is the player's own position being fed through this site.
        //
        //So the offset is derived once, here, and inject8 reuses it verbatim. Every operand then moves by the
        //same vector, which is a rigid translation: relative geometry is preserved exactly, so the test
        //answers the same question vanilla does, just in a translated frame. Being a single translation, it
        //is also correct when the box straddles a chunk boundary - the earlier straddle guard was both
        //unnecessary and checking the wrong thing, since a wall one block away is in a different chunk
        //regardless of whether the box itself straddles.
        if(!(entity instanceof ServerPlayer player1)){ this.item_storage$collisionOffset=null; return value; }
        Vec3 centre=value.getBottomCenter();
        try
        {
            Vec3 mapped=VecTransformer.getInstance(player1).s2cTransform(centre);
            this.item_storage$collisionOffset=mapped.subtract(centre);
        }
        catch (Exception exception)
        {
            //No mapping for the player's own chunk. Leaving this null rather than zero is deliberate: inject8
            //must then also leave the shapes alone, so the whole comparison runs untransformed. A zero offset
            //would do that too, but null cannot be confused with a genuine "mapped to the same place".
            this.item_storage$collisionOffset=null;
        }
        TransformAudit.classify("isEntityCollidingWithAnythingNew/aabb",player1,centre.x,centre.z);
        if(this.item_storage$collisionOffset==null)return value;
        return value.move(this.item_storage$collisionOffset);
    }
    @ModifyArg(method = "isEntityCollidingWithAnythingNew", at= @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"),index = 0)
    private VoxelShape inject8(VoxelShape value, @Local(argsOnly = true) Entity entity)
    {
        //[S>C] Required, and this is the half that was wrong. The shape moves by the offset inject7 recorded
        //for the player's box - NOT by its own. See inject7 for why: two different offsets make the AND test
        //compare a player against a wall displaced by the difference between them.
        if(!(entity instanceof ServerPlayer player1))return value;
        Vec3 offset=this.item_storage$collisionOffset;
        if(offset==null)return value;
        return value.move(offset);
    }
    @Redirect(method = "handlePlayerPositionChange", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;checkMovementStatistics(DDD)V"))
    private void inject9(ServerPlayer instance, double dx, double dy, double dz, @Local(name = "startX")double startX, @Local(name = "startY")double startY, @Local(name = "startZ")double startZ)
    {
        //[S] No transform, same reason as inject3. instance.position() and startX/Y/Z are both the server's
        //own state. dx/dy/dz is already vanilla's own difference, so it is simply forwarded. Measured:
        //375/375 samples S-field.
        instance.checkMovementStatistics(dx,dy,dz);
    }
}
