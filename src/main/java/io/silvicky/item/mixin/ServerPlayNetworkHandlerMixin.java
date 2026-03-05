package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.util.math.MathHelper.floor;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin
{

    @Redirect(method = "onPlayerInteractBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;subtract(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d inject1(Vec3d instance, Vec3d vec)
    {
        return VecTransformer.instance.s2cTransform(instance).subtract(VecTransformer.instance.s2cTransform(vec));
    }
    @Inject(method = "isEntityNotCollidingWithBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldView;getCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/math/Vec3d;)Ljava/lang/Iterable;"), cancellable = true)
    private void inject2(WorldView world, Entity entity, Box box, double newX, double newY, double newZ, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 1) Box box2)
    {
        if(floor(box.minX)/16!=floor(box2.minX)/16
        ||floor(box.minZ)/16!=floor(box2.minZ)/16
        ||floor(box.maxX)/16!=floor(box2.maxX)/16
        ||floor(box.maxZ)/16!=floor(box2.maxX)/16)
        {
            cir.setReturnValue(false);
        }
    }
}
