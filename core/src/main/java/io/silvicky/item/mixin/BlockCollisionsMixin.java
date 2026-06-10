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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;

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
    @Redirect(method = "computeNext",at= @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;"))
    private BlockPos.MutableBlockPos inject2(BlockPos.MutableBlockPos instance, int i, int j, int k)
    {
        if((!(context instanceof EntityCollisionContext entityCollisionContext))
                ||!(entityCollisionContext.getEntity() instanceof ServerPlayer player))
        {
            return instance.set(i,j,k);
        }
        return instance.set(VecTransformer.getInstance(player).c2sTransform(new BlockPos(i,j,k)));
    }
    @Inject(method="<init>(Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/world/phys/shapes/CollisionContext;Lnet/minecraft/world/phys/AABB;ZLjava/util/function/BiFunction;)V",at=@At("HEAD"))
    private static void inject3(CollisionGetter collisionGetter, CollisionContext collisionContext, AABB aABB, boolean bl, BiFunction<?,?,?> biFunction, CallbackInfo ci, @Local(argsOnly = true) LocalRef<AABB> localRef)
    {
        if((!(collisionContext instanceof EntityCollisionContext entityCollisionContext))
                ||!(entityCollisionContext.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        try
        {
            VecTransformer transformer=VecTransformer.getInstance(player);
            localRef.set(aABB.move(transformer.s2cTransform(aABB.getBottomCenter()).subtract(aABB.getBottomCenter())));
        }
        catch (Exception ignored) {}
    }
    @ModifyArg(method = "computeNext",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"),index = 0)
    private VoxelShape inject4(VoxelShape voxelShape)
    {
        if((!(context instanceof EntityCollisionContext entityCollisionContext))
                ||!(entityCollisionContext.getEntity() instanceof ServerPlayer player))
        {
            return voxelShape;
        }
        try
        {
            VecTransformer transformer=VecTransformer.getInstance(player);
            return voxelShape.move(transformer.s2cTransform(voxelShape.bounds().getBottomCenter()).subtract(voxelShape.bounds().getBottomCenter()));
        }
        catch (Exception ignored) {return voxelShape;}
    }
}
