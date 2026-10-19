package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.AbyssWindow;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.VecTransformer;
import io.silvicky.item.common.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin
{

    @Shadow public ServerPlayer player;

    @Shadow private double firstGoodX;

    @Shadow private double firstGoodY;

    @Shadow private double firstGoodZ;


    @Shadow
    private double lastGoodX;

    @Shadow
    private double lastGoodY;

    @Shadow
    private double lastGoodZ;

    /*
     * NOTE, deliberately not attached to any member - this records a redirect that was REMOVED.
     *
     * Left in S on purpose: this is a consistency check, not a reach check.
     *
     * <p>{@code handleUseItemOn} asks two questions. The first is reach, and that is judged in C by
     * {@code PlayerMixin}'s hooks on {@code isWithinBlockInteractionRange} - the same rule that breaking a block
     * uses, so a bucket and a pickaxe are treated identically. This one is the second question:
     *
     * <pre>
     *   Vec3 distance = location.subtract(Vec3.atCenterOf(pos));
     *   if (|distance.x| &lt; 1 &amp;&amp; |distance.y| &lt; 1 &amp;&amp; |distance.z| &lt; 1) ...
     * </pre>
     *
     * <p>which asks whether the client claimed to hit a point that is actually on that block's face. Both
     * operands belong to the same block, so this is a question about the server's own consistency and has
     * nothing to do with how far away anything is. It belongs in S.
     *
     * <p><b>Converting it was a bug, and it made using an item strictly harsher than breaking a block.</b> A
     * block's centre and a point on its face can sit in different pages: the block at {@code x=15} is in page 0
     * and its centre at {@code 15.5} with it, but its east face is at {@code x=16.0}, which is page 1. Convert
     * both and the offsets no longer cancel - the difference becomes the distance between two unrelated pages,
     * tens to hundreds of blocks - and the interaction is refused. Roughly one block in eight has an east or
     * north face on a page boundary.
     *
     * <p>It also failed closed. On a pruned page {@code ConnectionMixin} leaves the position in C, so this then
     * converted a C coordinate as if it were an S one, and the {@code ChunkUnusedException} arm returned
     * {@link VecTransformer#INF} - about 1.7 billion blocks, which fails the test and silently drops the click.
     *
     * <p>There is deliberately no redirect here any more. Removing it makes the check byte-for-byte vanilla's,
     * which is the correct outcome: reach is decided in one place, in C, by the same code whether the player is
     * breaking a block, using a bucket, or hitting a mob.
     *
     * The redirect that used to sit here was ServerGamePacketListenerImplMixin#inject1.
     */


    @ModifyVariable(method = "handlePlayerPositionChange", at = @At(value = "STORE",ordinal = 0),name="movedDist")
    private double inject3(double value,
                           @Local(name = "targetX")double d,
                           @Local(name = "targetY")double e,
                           @Local(name = "targetZ")double h)
    {
        //Measured in the player's own map, except across a shuffle seam where no C-space measurement
        //exists. See VecTransformer.playerMove for why, and for why skipping the seam used to
        //teleport the player every tick.
        Vec3 target=new Vec3(d, e, h);
        reportDisagreement(target);
        if (AbyssWindow.handles(player))
            return AbyssWindow.movementDistanceSq(player, new Vec3(firstGoodX, firstGoodY, firstGoodZ), target);
        return VecTransformer.getInstance(player)
                             .movementDistanceSq(new Vec3(firstGoodX, firstGoodY, firstGoodZ), target);
    }
    /**
     * Records a move packet that the server does not agree with, which is the thing that actually makes
     * the two sides fight.
     *
     * <p>The server holds one position for the player and the client reports another. If the two disagree
     * by more than a block or two, vanilla will correct the player, the correction is a teleport, and the
     * client walks off again - which is what a playtest log showed happening dozens of times in a row. This
     * prints the whole disagreement: where the client said it was, converted into server coordinates, where
     * the server thinks it is, and what each map says that distance is. That is enough to tell which side
     * is wrong without having to guess.
     */
    private void reportDisagreement(Vec3 target)
    {
        Vec3 here=player.position();
        Vec3 diff=target.subtract(here);
        if(diff.lengthSqr()<=64.0)return;
        VecTransformer transformer=VecTransformer.getInstance(player);
        Vec3 targetC=transformer.safeClient(target);
        Vec3 hereC=transformer.safeClient(here);
        Util.LOGGER.debug(
                "[remap-move] {} | packet says S {} but server holds S {} | dS=({},{}) | C {} vs C {}"
                        + " | dC=({}) | verdict={}",
                player.getGameProfile().name(),
                fmt(target), fmt(here), round(diff.x), round(diff.z),
                targetC==null?"<unmapped>":fmt(targetC), hereC==null?"<unmapped>":fmt(hereC),
                targetC==null||hereC==null?"?":round(targetC.subtract(hereC).length()),
                verdict(diff, targetC, hereC));
    }
    /**
     * Plain-language reading of a disagreement.
     *
     * <p>Note the direction of the test, because the obvious reading of it is backwards. A SMALL
     * {@code dC} is the healthy case: the two positions are close together on the player's own map, which
     * is the frame the game is judged in. A LARGE {@code dS} alongside it is the shuffle doing its job -
     * two chunks that are far apart on the real map and close together on the player's, or the reverse.
     * That is not a fault and needs no action.
     *
     * <p>A LARGE {@code dC} is the fault. It means the two positions are far apart in the very frame the
     * game is judged in, so the client and the server genuinely do not agree about where the player is.
     * That is the round trip breaking, and it is what makes the two sides fight.
     */
    private String verdict(Vec3 sDiff, Vec3 targetC, Vec3 hereC)
    {
        if(targetC==null||hereC==null)return "UNMAPPED, cannot compare in C";
        double c=targetC.subtract(hereC).length();
        double s=Math.sqrt(sDiff.lengthSqr());
        if(c<=8.0)
            return "OK - they agree in C; the S gap of "+round(s)+" is the shuffle";
        return "BROKEN - they are "+round(c)+" blocks apart in C, the round trip is not closing";
    }
    private static String fmt(Vec3 v)
    {
        return "("+round(v.x)+", "+round(v.y)+", "+round(v.z)+")";
    }
    private static String round(double v)
    {
        return String.valueOf(Math.round(v*100.0)/100.0);
    }
    @ModifyVariable(method = "handlePlayerPositionChange", at = @At(value = "STORE",ordinal = 1),name = "movedDist")
    private double inject4(double value,
                           @Local(name = "targetX")double d,
                           @Local(name = "targetY")double e,
                           @Local(name = "targetZ")double h)
    {
        Vec3 target = new Vec3(d, e, h);
        //The abyss window has to measure vertically as well: a band crossing moves the server's Y by a whole
        //band while the client's does not, and a local client does not run this check the way a remote one
        //does, which is why the warning only appears remotely.
        if (AbyssWindow.handles(player)) return AbyssWindow.movementDistanceSq(player, player.position(), target);
        return VecTransformer.getInstance(player).movementDistanceSq(player.position(), target);
    }
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleportSetPosition(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",shift = At.Shift.AFTER))
    private void inject5(PositionMoveRotation positionMoveRotation, Set<Relative> set, CallbackInfo ci)
    {
        //An absolute teleport puts the player at a named place, so the world has to honour both maps: the
        //player moves in S to that place, and in C to whatever position that place has. If the place is
        //already on the player's map, that is the position - and the current shuffle is KEPT. Only a place
        //with no position yet needs one initialised.
        //
        //This hook sees the teleport the server issues to correct a player back to where it thinks they
        //should be, and the one void recovery issues. Both land on somewhere already mapped, so neither
        //is a fresh arrival and neither should redraw the world. Drawing one anyway is what made a single
        //bad frame escalate: the correction landed on a mapped chunk, the shuffle was redrawn, the player's
        //neighbourhood was replaced with ungenerated or empty space, they fell, and it repeated. A playtest
        //log showed six redraws in two seconds with the player never moving.
        VecTransformer transformer=VecTransformer.peekInstance(player);
        if(transformer==null)return;
        ChunkPos destination=ChunkPos.containing(BlockPos.containing(player.position()));
        if(transformer.isMapped(destination))
        {
            //Already on the map, so the shuffle is kept - but the player may still have moved a long way,
            // since the placement pool is several pages wide. A join is the obvious case: they spawn wherever
            //they spawn, which can be far from where this transformer was built, and nothing would rebuild
            //the view around them because they did not cross a border. That is how a player arrives inside a
            //hole. So recentre unconditionally, and it is a no-op when they have not actually moved.
            transformer.recentre();
            Util.LOGGER.debug("[remap] {} | teleport -> {} is already on the map, shuffle kept",
                    player.getGameProfile().name(),destination);
            return;
        }
        if(transformer.ensureMapped(destination))
        {
            transformer.recentre();
            Util.LOGGER.debug("[remap] {} | teleport -> {} had no position, initialised one, shuffle kept",
                    player.getGameProfile().name(),destination);
            return;
        }
        //The pool is full and a position could not be made. A new shuffle is the last resort, not the
        //default - see CONVENTION.md 2.4.1 on why quietly reusing an occupied slot is not an option.
        VecTransformer previous=transformer;
        VecTransformer.refreshInstance(player);
        Util.LOGGER.debug("[remap] {} | teleport -> {} could not be placed, fell back to a new shuffle (was {})",
                player.getGameProfile().name(),destination,previous.getClass().getSimpleName());
    }
    @ModifyArg(method = "handlePlayerPositionChange", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"),index = 1)
    private Vec3 inject6(Vec3 par2,
                         @Local(name = "targetX")double d,
                         @Local(name = "targetY")double e,
                         @Local(name = "targetZ")double h)
    {
        try
        {
            //This is the movement the player is actually given, so it has to come from the same place the
            //anti-cheat measures. Taking the raw C difference here instead would hand the player a movement
            //of up to a hundred-odd blocks every time they crossed a chunk border, because the shuffle jump
            //would be applied as if they had walked it.
            return VecTransformer.getInstance(player).playerMove(new Vec3(lastGoodX,lastGoodY,lastGoodZ),
                                                              new Vec3(d,e,h));
        }
        catch (Exception ex)
        {
            return par2;
        }
    }
    @ModifyArg(method = "isEntityCollidingWithAnythingNew", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private AABB inject7(AABB value, @Local(argsOnly = true) Entity entity)
    {
        if(!(entity instanceof ServerPlayer player1))return value;
        try
        {
            Vec3 v = VecTransformer.getInstance(player1).s2cTransform(value.getBottomCenter());
            return value.move(v.subtract(value.getBottomCenter()));
        }
        catch (Exception exception)
        {
            return value;
        }
    }
    @ModifyArg(method = "isEntityCollidingWithAnythingNew", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"),index = 0)
    private VoxelShape inject8(VoxelShape value, @Local(argsOnly = true) Entity entity)
    {
        if(!(entity instanceof ServerPlayer player1))return value;
        try
        {
            Vec3 v = VecTransformer.getInstance(player1).s2cTransform(value.bounds().getBottomCenter());
            return value.move(v.subtract(value.bounds().getBottomCenter()));
        }
        catch (Exception exception)
        {
            return value;
        }
    }
    @Redirect(method = "handlePlayerPositionChange", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;checkMovementStatistics(DDD)V"))
    private void inject9(ServerPlayer instance, double dx, double dy, double dz, @Local(name = "startX")double startX, @Local(name = "startY")double startY, @Local(name = "startZ")double startZ)
    {
        try
        {
            //Same seam-corrected movement as inject6, for the same reason: this feeds the fly-speed and
            //walk-speed counters, and a shuffle jump would otherwise be recorded as the player sprinting.
            Vec3 from=new Vec3(startX,startY,startZ);
            Vec3 to=instance.position();
            Vec3 move=AbyssWindow.handles(player)
                    ? AbyssWindow.playerMove(player,from,to)
                    : VecTransformer.getInstance(player).playerMove(from,to);
            instance.checkMovementStatistics(move.x,move.y,move.z);
        }
        catch (Exception ex)
        {
            instance.checkMovementStatistics(dx,dy,dz);
        }
    }
}
