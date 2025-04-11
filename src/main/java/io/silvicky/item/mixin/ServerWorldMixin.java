package io.silvicky.item.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

import static io.silvicky.item.InventoryManager.END;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {
    @Shadow @Final private MinecraftServer server;

    @ModifyArg(method = "tick", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/world/SleepManager;canResetTime(ILjava/util/List;)Z"))
    private List<ServerPlayerEntity> inject1(List<ServerPlayerEntity> x)
    {
        return server.getPlayerManager().getPlayerList();
    }
    @ModifyArg(method = "updateSleepingPlayers", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/world/SleepManager;update(Ljava/util/List;)Z"))
    private List<ServerPlayerEntity> inject2(List<ServerPlayerEntity> x)
    {
        return server.getPlayerManager().getPlayerList();
    }
    @Redirect(method="tick",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;setTimeOfDay(J)V"))
    private void inject3(ServerWorld instance, long timeOfDay)
    {
        for(ServerWorld world:server.getWorlds())world.setTimeOfDay(timeOfDay);
    }
    @Redirect(method="tick",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;resetWeather()V"))
    private void inject4(ServerWorld instance)
    {
        for(ServerWorld world:server.getWorlds())world.resetWeather();
    }
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerWorld;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"))
    private RegistryKey<World> inject5(ServerWorld instance)
    {
        RegistryKey<World> registryKey=instance.getRegistryKey();
        if(registryKey.getValue().getPath().endsWith(END))return World.END;
        else return registryKey;
    }
}
