package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public class PlayerListMixin
{
    @Shadow
    @Final
    private MinecraftServer server;

    @Redirect(method = "setViewDistance(I)V",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;setViewDistance(I)V"))
    private void inject1(ServerChunkCache instance, int newDistance, @Local(name = "level") ServerLevel level)
    {
        if(StateSaver.getServerState(server).ext.view.containsKey(level.dimension.identifier()))return;
        instance.setViewDistance(newDistance);
    }
    @Redirect(method = "setSimulationDistance",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;setSimulationDistance(I)V"))
    private void inject2(ServerChunkCache instance, int newDistance, @Local(name = "level") ServerLevel level)
    {
        if(StateSaver.getServerState(server).ext.sim.containsKey(level.dimension.identifier()))return;
        instance.setSimulationDistance(newDistance);
    }
}
