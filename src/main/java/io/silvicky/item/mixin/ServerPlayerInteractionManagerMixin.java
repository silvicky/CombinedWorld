package io.silvicky.item.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin
{
    @Shadow @Final protected ServerPlayerEntity player;

    @Inject(method = "changeGameMode",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;setGameMode(Lnet/minecraft/world/GameMode;Lnet/minecraft/world/GameMode;)V",shift = At.Shift.AFTER),cancellable = true)
    private void injected(GameMode gameMode, CallbackInfoReturnable<Boolean> cir)
    {
        if(player.networkHandler==null)cir.setReturnValue(true);
    }
}
