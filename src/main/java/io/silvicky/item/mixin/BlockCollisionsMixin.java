package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

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
        if((!(context instanceof EntityCollisionContext entityCollisionContext))
        ||!(entityCollisionContext.getEntity() instanceof ServerPlayer player))
        {
            return getChunk(i,j);
        }
        BlockPos pos = VecTransformer.getInstance(player).c2sTransform(new BlockPos(i, 0, j));
        return getChunk(pos.getX(),pos.getZ());
    }
    @ModifyArg(method = "computeNext",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockPos inject2(BlockPos blockPos)
    {
        if((!(context instanceof EntityCollisionContext entityCollisionContext))
                ||!(entityCollisionContext.getEntity() instanceof ServerPlayer player))
        {
            return blockPos;
        }
        return VecTransformer.getInstance(player).c2sTransform(blockPos);
    }
}
