package io.silvicky.item.mixin;

import io.silvicky.item.StateSaver;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;

import static io.silvicky.item.common.Util.getDimensionId;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level
{
    @Shadow @Final private MinecraftServer server;

    protected ServerLevelMixin(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, boolean bl, boolean bl2, long l, int i)
    {
        super(writableLevelData, resourceKey, registryAccess, holder, bl, bl2, l, i);
    }

    @ModifyArg(method = "tick", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
    private List<ServerPlayer> inject1(List<ServerPlayer> x)
    {
        return server.getPlayerList().getPlayers();
    }
    @ModifyArg(method = "updateSleepingPlayerList", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;update(Ljava/util/List;)Z"))
    private List<ServerPlayer> inject2(List<ServerPlayer> x)
    {
        return server.getPlayerList().getPlayers();
    }
    @Redirect(method= "tick",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;resetWeatherCycle()V"))
    private void inject4(ServerLevel instance)
    {
        for(ServerLevel world:server.getAllLevels())world.resetWeatherCycle();
    }
    @Redirect(method= "<init>",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long inject7(WorldOptions instance)
    {
        ServerLevel target=(ServerLevel) (Object)this;
        HashMap<Identifier,Long> seedMap;
        seedMap=StateSaver.getServerState(server).seed;
        Identifier cur=target.dimension().identifier();
        if(seedMap.containsKey(cur))return seedMap.get(cur);
        return instance.seed();
    }
    @Redirect(method= "getSeed",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long inject8(WorldOptions instance)
    {
        ServerLevel target=(ServerLevel) (Object)this;
        HashMap<Identifier,Long> seedMap;
        seedMap=StateSaver.getServerState(server).seed;
        Identifier cur=target.dimension().identifier();
        if(seedMap.containsKey(cur))return seedMap.get(cur);
        return instance.seed();
    }
    @Redirect(method= "getRespawnData",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getRespawnData()Lnet/minecraft/world/level/storage/LevelData$RespawnData;"))
    private LevelData.RespawnData inject9(MinecraftServer instance)
    {
        Identifier target=dimension.identifier();
        if(getDimensionId(target).equals(Level.OVERWORLD.identifier()))
        {
            return instance.getRespawnData();
        }
        HashMap<Identifier, LevelData.RespawnData> spawn=StateSaver.getServerState(instance).worldSpawn;
        LevelData.RespawnData defaultSpawn=instance.getRespawnData();
        return spawn.getOrDefault(target, LevelData.RespawnData.of(ResourceKey.create(Registries.DIMENSION,target),defaultSpawn.pos(),defaultSpawn.yaw(),defaultSpawn.pitch()));
    }
    @Redirect(method= "setRespawnData",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setRespawnData(Lnet/minecraft/world/level/storage/LevelData$RespawnData;)V"))
    private void inject10(MinecraftServer instance, LevelData.RespawnData spawnPoint)
    {
        Identifier target=dimension.identifier();
        if(getDimensionId(target).equals(Level.OVERWORLD.identifier()))
        {
            instance.setRespawnData(spawnPoint);
            return;
        }
        StateSaver.getServerState(instance).worldSpawn.put(target,spawnPoint);
    }
    @Inject(method= "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",at=@At("HEAD"),cancellable = true)
    private void inject11(@Nullable Entity entity, double d, double e, double f, Holder<SoundEvent> holder, SoundSource soundSource, float g, float h, long l, CallbackInfo ci)
    {
        if((StateSaver.getServerState(server).silence.getOrDefault(dimension.identifier(),0)&1)==1)ci.cancel();
    }
    @Inject(method= "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",at=@At("HEAD"),cancellable = true)
    private void inject12(@Nullable Entity entity, Entity entity2, Holder<SoundEvent> holder, SoundSource soundSource, float f, float g, long l, CallbackInfo ci)
    {
        if((StateSaver.getServerState(server).silence.getOrDefault(dimension.identifier(),0)&2)==2)ci.cancel();
    }
}
