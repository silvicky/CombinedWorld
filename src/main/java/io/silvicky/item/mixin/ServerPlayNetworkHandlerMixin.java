package io.silvicky.item.mixin;

import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin
{
    @Inject(method = "requestTeleport(Lnet/minecraft/entity/EntityPosition;Ljava/util/Set;)V",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;getEntityPos()Lnet/minecraft/util/math/Vec3d;"))
    public void inject1(EntityPosition pos, Set<PositionFlag> flags, CallbackInfo ci)
    {
        if(flags.contains(PositionFlag.X))return;
        pos.position().x+=16;
        pos.position().z+=16;
    }
}
