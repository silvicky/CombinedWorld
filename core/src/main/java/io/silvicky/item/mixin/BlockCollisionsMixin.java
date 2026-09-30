package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;

/**
 * Collision resolution for players.
 *
 * <p>These sites sit in the server's own physics, so their inputs are {@code [S]}. The query is translated
 * into the client's view by <em>one</em> rigid offset so that collision resolves against the blocks the
 * client is rendering rather than the ones at the same numeric position on the server. See
 * {@link io.silvicky.item.backrooms.CoordinateSpace} for the marker legend.
 *
 * <p>One offset is the whole point. An earlier version mapped the box through {@code s2cTransform} and then
 * mapped each per-block lookup back through {@code c2sTransform}; under a per-chunk random map those are
 * different maps in different chunks, so a wall beside the player was looked up under an unrelated offset
 * and ended up somewhere the box never went. A single translation moves every operand by the same vector, so
 * relative geometry is preserved exactly and the answer matches vanilla's, just in a translated frame.
 */
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsMixin
{
    @Shadow
    @Final
    private CollisionContext context;

    @Shadow
    @Nullable
    protected abstract BlockGetter getChunk(int i, int j);

    @Redirect(method = "computeNext",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockCollisions;getChunk(II)Lnet/minecraft/world/level/BlockGetter;"))
    private BlockGetter inject1(BlockCollisions<?> instance, int i, int j)
    {
        //[C>S] A client-view chunk coordinate, converted to the server position that actually holds it by
        //the same single offset inject2 uses. See inject3 for why one offset, not one per chunk.
        if(item_storage$offset==null)return getChunk(i,j);
        return getChunk(i+item_storage$chunkOffsetX,j+item_storage$chunkOffsetZ);
    }
    @Redirect(method = "computeNext",at= @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;"))
    private BlockPos.MutableBlockPos inject2(BlockPos.MutableBlockPos instance, int i, int j, int k)
    {
        //[C>S] Same single offset as inject1, so the query is translated rigidly into the client's view and
        //every coordinate converts back with the same vector.
        if(item_storage$offset==null)return instance.set(i,j,k);
        double[] o=item_storage$offset;
        return instance.set(i+(int)o[0],j+(int)o[1],k+(int)o[2]);
    }
    /**
     * The single translation applied to this query, in blocks. Null means "not remapped", and is also the
     * safe fallback - the query then resolves in server coordinates exactly as vanilla would.
     *
     * <p>Held per instance rather than read from a thread-local at each step. A {@code BlockCollisions} is an
     * {@code AbstractIterator}, so {@code computeNext} is re-entered on every {@code hasNext()} and re-runs
     * the entire cursor loop - {@code getChunk} and {@code pos.set} are inside that repeated loop, verified
     * against the bytecode. An earlier version staged the offset in a thread-local and consumed it with
     * {@code remove()} on the first {@code computeNext} call, assuming the method ran once per query. It
     * does not, so the offset vanished after the first block: the head of the query resolved in client
     * coordinates and the rest in server coordinates. A query that straddles two coordinate systems is a
     * phantom wall at a random position, which is the symptom this was added to remove.
     */
    @Unique
    private double[] item_storage$offset;

    /** The same translation expressed in whole chunks, for the chunk lookup in inject1. */
    @Unique
    private int item_storage$chunkOffsetX;
    @Unique
    private int item_storage$chunkOffsetZ;

    /**
     * Carries the offset from the static constructor handler to this instance.
     *
     * <p>Mixin requires a constructor handler to be static, so {@code this} is unavailable there - the first
     * parameter is the {@code CollisionGetter} (the level), not the object being built. The value is only read
     * and never removed, because the consumer keeps its own copy and may iterate long after construction.
     */
    @Unique
    private static final ThreadLocal<double[]> item_storage$staged=new ThreadLocal<>();

    /**
     * Claims the staged offset, once, and keeps it for the life of the query.
     *
     * <p>Idempotent because {@code computeNext} runs many times per query. A query that never iterates never
     * claims anything, which is the correct outcome for the many {@code BlockCollisions} instances built for
     * non-player contexts.
     */
    @Inject(method="computeNext",at=@At("HEAD"))
    private void inject0(CallbackInfoReturnable<?> cir)
    {
        if(this.item_storage$offset!=null)return;
        double[] staged=item_storage$staged.get();
        if(staged==null)return;
        this.item_storage$offset=staged;
        this.item_storage$chunkOffsetX=Math.floorDiv((int)staged[0],16);
        this.item_storage$chunkOffsetZ=Math.floorDiv((int)staged[2],16);
    }

    @Inject(method="<init>(Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/world/phys/shapes/CollisionContext;Lnet/minecraft/world/phys/AABB;ZLjava/util/function/BiFunction;)V",at=@At("HEAD"))
    private static void inject3(CollisionGetter collisionGetter, CollisionContext collisionContext, AABB aABB, boolean bl, BiFunction<?,?,?> biFunction, CallbackInfo ci, @Local(argsOnly = true) LocalRef<AABB> localRef)
    {
        if((!(collisionContext instanceof EntityCollisionContext entityCollisionContext))
                ||!(entityCollisionContext.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        //[S>C] Move the query into the client's view by ONE translation, derived here and reused verbatim by
        //inject1 and inject2.
        //
        //The previous version mapped the box through s2cTransform and then had each per-block lookup map
        //back through c2sTransform. Under a per-chunk random map those are different maps at different
        //chunks, so a block adjacent to the player could be looked up under a different chunk's offset than
        //the box was moved by - the wall would be somewhere the box never went.
        //
        //Being a single rigid translation, this is also correct when the box straddles a chunk boundary,
        //which is why the earlier straddle guard is gone. A previous attempt gated the remap on straddling,
        //which was the wrong condition: a wall one block away is in a different chunk whether or not the box
        //itself straddles, so the offsets differed in the common case and the guard could only ever have
        //helped when nothing was nearby.
        try
        {
            Vec3 centre=aABB.getBottomCenter();
            Vec3 mapped=VecTransformer.getInstance(player).s2cTransform(centre);
            Vec3 offset=mapped.subtract(centre);
            localRef.set(aABB.move(offset));
            item_storage$staged.set(new double[]{offset.x,offset.y,offset.z});
        }
        catch (Exception ignored) {}
    }
    @ModifyArg(method = "computeNext",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"),index = 0)
    private VoxelShape inject4(VoxelShape voxelShape)
    {
        //[S] No transform. The shape is fetched in server coordinates by inject2, and the shapes handed back
        //to the caller are left exactly as vanilla produced them.
        //
        //Rewriting this argument used to move one operand into the client's view while the other stayed in
        //server coordinates, so the AND test answered a question about two unrelated positions. The shape
        //the caller receives is unaffected either way, so the change could only corrupt the emptiness test.
        //The caller does its own translation with the single shared offset - see
        //ServerGamePacketListenerImplMixin inject7.
        return voxelShape;
    }
}
