package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class PlayerMixin
{
    @Redirect(method = "isWithinBlockInteractionRange",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 inject1(Player instance)
    {
        return VecTransformer.instance.s2cTransform(instance.getEyePosition());
    }
    @ModifyArg(method = "isWithinBlockInteractionRange",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;<init>(Lnet/minecraft/core/BlockPos;)V"))
    public BlockPos inject2(BlockPos pos)
    {
        return VecTransformer.instance.s2cTransform(pos);
    }
}
