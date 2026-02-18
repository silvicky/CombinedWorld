package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
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
import static io.silvicky.item.cfg.JSONConfig.useStorage;
import static io.silvicky.item.common.Util.getDimensionId;
import static io.silvicky.item.common.Util.toOverworld;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin
{
    @Shadow public abstract ServerWorld getEntityWorld();

    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getLevelProperties()Lnet/minecraft/world/WorldProperties;"))
    private void inject1(TeleportTarget teleportTarget, CallbackInfoReturnable<ServerPlayerEntity> cir)
    {
        //TODO Check if warp is allowed
        Identifier source=getEntityWorld().getRegistryKey().getValue();
        Identifier target=teleportTarget.world().getRegistryKey().getValue();
        ServerPlayerEntity entity=(ServerPlayerEntity) (Object)this;
        StateSaver stateSaver=StateSaver.getServerState(getEntityWorld().getServer());
        if(getDimensionId(source.toString()).equals(getDimensionId(target.toString())))return;
        savePos(entity, stateSaver);
        if(!source.getNamespace().equals(target.getNamespace()))
        {
            if(useStorage)saveInventory(entity, stateSaver);
        }
    }
    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;teleportSpectatingPlayers(Lnet/minecraft/world/TeleportTarget;Lnet/minecraft/server/world/ServerWorld;)V"))
    private void inject2(TeleportTarget teleportTarget, CallbackInfoReturnable<ServerPlayerEntity> cir, @Local(ordinal=1) ServerWorld serverWorld2)
    {
        Identifier source=serverWorld2.getRegistryKey().getValue();
        Identifier target=teleportTarget.world().getRegistryKey().getValue();
        ServerPlayerEntity entity=(ServerPlayerEntity) (Object)this;
        StateSaver stateSaver=StateSaver.getServerState(getEntityWorld().getServer());
        if(getDimensionId(source.toString()).equals(getDimensionId(target.toString())))return;
        if(!source.getNamespace().equals(target.getNamespace()))
        {
            if(useStorage)
            {
                try
                {
                    loadInventory(entity, teleportTarget.world(), stateSaver);
                }
                catch(Exception e)
                {
                    //TODO Any way to rollback?
                    throw new RuntimeException(e);
                }
            }
        }
    }
    @Inject(method = "setSpawnPoint",at= @At(value = "HEAD"),cancellable = true)
    private void inject3(ServerPlayerEntity.Respawn respawn, boolean sendMessage, CallbackInfo ci)
    {
        ServerPlayerEntity player=(ServerPlayerEntity) (Object)this;
        if(respawn==null)
        {
            //what's this??
            player.respawn=null;
            ci.cancel();
            return;
        }
        Identifier id=getDimensionId(respawn.respawnData().getDimension().getValue());
        if(id.equals(World.OVERWORLD.getValue()))
        {
            if(sendMessage&&!respawn.equals(player.respawn))player.sendMessage(ServerPlayerEntity.SET_SPAWN_TEXT);
            player.respawn = respawn;
        }
        else
        {
            HashMap<String, ServerPlayerEntity.Respawn> respawnMap=StateSaver.getServerState(getEntityWorld().getServer()).respawn.computeIfAbsent(id, i -> new HashMap<>());
            if(!respawn.equals(respawnMap.get(player.getUuidAsString())))player.sendMessage(ServerPlayerEntity.SET_SPAWN_TEXT);
            respawnMap.put(player.getUuidAsString(), respawn);
        }
        ci.cancel();
    }
    @Inject(method = "getRespawn",at= @At(value = "HEAD"),cancellable = true)
    private void inject4(CallbackInfoReturnable<ServerPlayerEntity.Respawn> cir)
    {
        ServerPlayerEntity player=(ServerPlayerEntity) (Object)this;
        Identifier id=getDimensionId(getEntityWorld().getRegistryKey().getValue());
        WorldProperties.SpawnPoint defaultSpawn=getEntityWorld().getServer().getSpawnPoint();
        WorldProperties.SpawnPoint defaultCurrentSpawn=WorldProperties.SpawnPoint.create(RegistryKey.of(RegistryKeys.WORLD,id),defaultSpawn.getPos(),defaultSpawn.yaw(),defaultSpawn.pitch());
        if(id.equals(World.OVERWORLD.getValue())) cir.setReturnValue(player.respawn);
        else cir.setReturnValue(StateSaver.getServerState(getEntityWorld().getServer()).respawn.computeIfAbsent(id,i->new HashMap<>()).getOrDefault(player.getUuidAsString(), new ServerPlayerEntity.Respawn(defaultCurrentSpawn,false)));
    }
    @Redirect(method="getRespawnTarget",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/TeleportTarget;missingSpawnBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/TeleportTarget$PostDimensionTransition;)Lnet/minecraft/world/TeleportTarget;"))
    private TeleportTarget inject5(ServerPlayerEntity player, TeleportTarget.PostDimensionTransition postDimensionTransition)
    {
        ServerWorld serverWorld = toOverworld(player.getEntityWorld().getServer(),player.getEntityWorld());
        WorldProperties.SpawnPoint spawnPoint = serverWorld.getSpawnPoint();
        return new TeleportTarget(
                serverWorld, TeleportTarget.getWorldSpawnPos(serverWorld, player), Vec3d.ZERO, spawnPoint.yaw(), spawnPoint.pitch(), true, false, Set.of(), postDimensionTransition
        );
    }
    @Redirect(method="getRespawnTarget",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/TeleportTarget;noRespawnPointSet(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/TeleportTarget$PostDimensionTransition;)Lnet/minecraft/world/TeleportTarget;"))
    private TeleportTarget inject6(ServerPlayerEntity player, TeleportTarget.PostDimensionTransition postDimensionTransition)
    {
        ServerWorld serverWorld = toOverworld(player.getEntityWorld().getServer(),player.getEntityWorld());
        WorldProperties.SpawnPoint spawnPoint = serverWorld.getSpawnPoint();
        return new TeleportTarget(
                serverWorld, TeleportTarget.getWorldSpawnPos(serverWorld, player), Vec3d.ZERO, spawnPoint.yaw(), spawnPoint.pitch(), false, false, Set.of(), postDimensionTransition
        );
    }
}
