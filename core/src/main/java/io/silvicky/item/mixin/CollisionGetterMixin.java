package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CollisionGetter.class)
public interface CollisionGetterMixin
{
    @Redirect(method = "findSupportingBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D"))
    private double inject3(BlockPos instance, Position position, @Local(argsOnly = true) Entity entity)
    {
        if(!(entity instanceof ServerPlayer player1))return instance.distToCenterSqr(position);
        try
        {
            VecTransformer transformer=VecTransformer.getInstance(player1);
            return transformer.s2cTransform(instance).distToCenterSqr(transformer.s2cTransform((Vec3) position));
        }
        catch (Exception e)
        {
            return instance.distToCenterSqr(position);
        }
    }
}
