package io.silvicky.item.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin
{
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "changeGameModeForPlayer",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;setGameModeForPlayer(Lnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;)V",shift = At.Shift.AFTER),cancellable = true)
    private void injected(GameType gameMode, CallbackInfoReturnable<Boolean> cir)
    {
        if(player.connection ==null)cir.setReturnValue(true);
    }
}
