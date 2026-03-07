package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Set;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.backrooms.EntityVisibilityManager.updatePlayerVisibility;
import static io.silvicky.item.cfg.JSONConfig.useStorage;
import static io.silvicky.item.common.Util.getDimensionId;
import static io.silvicky.item.common.Util.toOverworld;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin
{
    @Shadow public abstract ServerLevel level();

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getLevelData()Lnet/minecraft/world/level/storage/LevelData;"))
    private void inject1(TeleportTransition teleportTarget, CallbackInfoReturnable<ServerPlayer> cir)
    {
        //TODO Check if warp is allowed
        Identifier source= level().dimension().identifier();
        Identifier target=teleportTarget.newLevel().dimension().identifier();
        ServerPlayer entity=(ServerPlayer) (Object)this;
        StateSaver stateSaver=StateSaver.getServerState(level().getServer());
        if(getDimensionId(source).equals(getDimensionId(target)))return;
        savePos(entity, stateSaver);
        if(!source.getNamespace().equals(target.getNamespace()))
        {
            if(useStorage)saveInventory(entity, stateSaver);
        }
    }
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleportSpectators(Lnet/minecraft/world/level/portal/TeleportTransition;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void inject2(TeleportTransition teleportTarget, CallbackInfoReturnable<ServerPlayer> cir, @Local(ordinal=1) ServerLevel serverWorld2)
    {
        Identifier source=serverWorld2.dimension().identifier();
        Identifier target=teleportTarget.newLevel().dimension().identifier();
        ServerPlayer entity=(ServerPlayer) (Object)this;
        StateSaver stateSaver=StateSaver.getServerState(level().getServer());
        if(getDimensionId(source).equals(getDimensionId(target)))return;
        if(!source.getNamespace().equals(target.getNamespace()))
        {
            if(useStorage)
            {
                try
                {
                    loadInventory(entity, teleportTarget.newLevel(), stateSaver);
                }
                catch(Exception e)
                {
                    //TODO Any way to rollback?
                    throw new RuntimeException(e);
                }
            }
        }
    }
    @Inject(method = "setRespawnPosition",at= @At(value = "HEAD"),cancellable = true)
    private void inject3(ServerPlayer.RespawnConfig respawn, boolean sendMessage, CallbackInfo ci)
    {
        ServerPlayer player=(ServerPlayer) (Object)this;
        if(respawn==null)
        {
            //what's this??
            player.respawnConfig =null;
            ci.cancel();
            return;
        }
        Identifier id=getDimensionId(respawn.respawnData().dimension().identifier());
        if(id.equals(Level.OVERWORLD.identifier()))
        {
            if(sendMessage&&!respawn.equals(player.respawnConfig))player.sendSystemMessage(ServerPlayer.SPAWN_SET_MESSAGE);
            player.respawnConfig = respawn;
        }
        else
        {
            HashMap<String, ServerPlayer.RespawnConfig> respawnMap=StateSaver.getServerState(level().getServer()).respawn.computeIfAbsent(id, i -> new HashMap<>());
            if(!respawn.equals(respawnMap.get(player.getStringUUID())))player.sendSystemMessage(ServerPlayer.SPAWN_SET_MESSAGE);
            respawnMap.put(player.getStringUUID(), respawn);
        }
        ci.cancel();
    }
    @Inject(method = "getRespawnConfig",at= @At(value = "HEAD"),cancellable = true)
    private void inject4(CallbackInfoReturnable<ServerPlayer.RespawnConfig> cir)
    {
        ServerPlayer player=(ServerPlayer) (Object)this;
        Identifier id=getDimensionId(level());
        LevelData.RespawnData defaultSpawn= level().getServer().getRespawnData();
        LevelData.RespawnData defaultCurrentSpawn= LevelData.RespawnData.of(ResourceKey.create(Registries.DIMENSION,id),defaultSpawn.pos(),defaultSpawn.yaw(),defaultSpawn.pitch());
        if(id.equals(Level.OVERWORLD.identifier())) cir.setReturnValue(player.respawnConfig);
        else cir.setReturnValue(StateSaver.getServerState(level().getServer()).respawn.computeIfAbsent(id, i->new HashMap<>()).getOrDefault(player.getStringUUID(), new ServerPlayer.RespawnConfig(defaultCurrentSpawn,false)));
    }
    @Redirect(method= "findRespawnPositionAndUseSpawnBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/TeleportTransition;missingRespawnBlock(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private TeleportTransition inject5(ServerPlayer player, TeleportTransition.PostTeleportTransition postDimensionTransition)
    {
        ServerLevel serverWorld = toOverworld(player.level().getServer(),player.level());
        LevelData.RespawnData spawnPoint = serverWorld.getRespawnData();
        return new TeleportTransition(
                serverWorld, TeleportTransition.findAdjustedSharedSpawnPos(serverWorld, player), Vec3.ZERO, spawnPoint.yaw(), spawnPoint.pitch(), true, false, Set.of(), postDimensionTransition
        );
    }
    @Redirect(method= "findRespawnPositionAndUseSpawnBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/TeleportTransition;createDefault(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private TeleportTransition inject6(ServerPlayer player, TeleportTransition.PostTeleportTransition postDimensionTransition)
    {
        ServerLevel serverWorld = toOverworld(player.level().getServer(),player.level());
        LevelData.RespawnData spawnPoint = serverWorld.getRespawnData();
        return new TeleportTransition(
                serverWorld, TeleportTransition.findAdjustedSharedSpawnPos(serverWorld, player), Vec3.ZERO, spawnPoint.yaw(), spawnPoint.pitch(), false, false, Set.of(), postDimensionTransition
        );
    }
    @Inject(method = "die",at = @At("HEAD"))
    private void inject7(DamageSource damageSource, CallbackInfo ci)
    {
        ServerPlayer player=(ServerPlayer) (Object)this;
        updatePlayerVisibility(player.level().getServer(),
                player.level().dimension().identifier().getNamespace(),
                player.getStringUUID());
    }
}
