package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin
{
    @Redirect(method = "canInteractWithBlockAt",at= @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getEyePos()Lnet/minecraft/util/math/Vec3d;"))
    public Vec3d inject1(PlayerEntity instance)
    {
        return VecTransformer.instance.s2cTransform(instance.getEyePos());
    }
    @ModifyArg(method = "canInteractWithBlockAt",at= @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Box;<init>(Lnet/minecraft/util/math/BlockPos;)V"))
    public BlockPos inject2(BlockPos pos)
    {
        return VecTransformer.instance.s2cTransform(pos);
    }
}
