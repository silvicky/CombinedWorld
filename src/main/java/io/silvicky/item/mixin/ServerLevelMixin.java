package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.objectweb.asm.Opcodes;
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
import java.util.function.BooleanSupplier;

import static io.silvicky.item.common.Util.getDimensionId;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin
{
    @Shadow @Final private MinecraftServer server;

    @Shadow
    @Final
    List<ServerPlayer> players;

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
    @Redirect(method= "tick",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private void inject3(ServerLevel instance, long timeOfDay)
    {
        for(ServerLevel world:server.getAllLevels())world.setDayTime(timeOfDay);
    }
    @Redirect(method= "tick",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;resetWeatherCycle()V"))
    private void inject4(ServerLevel instance)
    {
        for(ServerLevel world:server.getAllLevels())world.resetWeatherCycle();
    }
    @Redirect(method= "<init>",at= @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;dragonFight:Lnet/minecraft/world/level/dimension/end/EndDragonFight;",opcode = Opcodes.PUTFIELD,ordinal = 1))
    private void inject5(ServerLevel instance, EndDragonFight value, @Local(argsOnly = true) long l)
    {
        if(!instance.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)){instance.dragonFight =null;return;}
        HashMap<Identifier, EndDragonFight.Data> dragonFightHashMap;
        if(instance.dimension()!= Level.OVERWORLD)dragonFightHashMap=StateSaver.getServerState(server).dragonFight;
        //this should not happen but...
        else dragonFightHashMap=StateSaver.getServerState(instance).dragonFight;
        Identifier cur=instance.dimension().identifier();
        instance.dragonFight = new EndDragonFight(instance, l, dragonFightHashMap.getOrDefault(cur, EndDragonFight.Data.DEFAULT));
    }
    @Redirect(method= "saveLevelData",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;setEndDragonFightData(Lnet/minecraft/world/level/dimension/end/EndDragonFight$Data;)V"))
    private void inject6(WorldData instance, EndDragonFight.Data data)
    {
        ServerLevel target=(ServerLevel) (Object)this;
        HashMap<Identifier, EndDragonFight.Data> dragonFightHashMap;
        if(target.dimension()!= Level.OVERWORLD)dragonFightHashMap=StateSaver.getServerState(server).dragonFight;
            //this should not happen but...
        else dragonFightHashMap=StateSaver.getServerState(target).dragonFight;
        Identifier cur=target.dimension().identifier();
        if(target.dragonFight ==null)return;
        if(target.dimension()== Level.END)
            server.getWorldData().setEndDragonFightData(target.dragonFight.saveData());
        else
            dragonFightHashMap.put(cur,target.dragonFight.saveData());
    }
    @Redirect(method= "<init>",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long inject7(WorldOptions instance)
    {
        ServerLevel target=(ServerLevel) (Object)this;
        HashMap<Identifier,Long> seedMap;
        if(target.dimension()!= Level.OVERWORLD)seedMap=StateSaver.getServerState(server).seed;
            //this should not happen but...
        else seedMap=StateSaver.getServerState(target).seed;
        Identifier cur=target.dimension().identifier();
        if(seedMap.containsKey(cur))return seedMap.get(cur);
        return instance.seed();
    }
    @Redirect(method= "getSeed",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long inject8(WorldOptions instance)
    {
        ServerLevel target=(ServerLevel) (Object)this;
        HashMap<Identifier,Long> seedMap;
        if(target.dimension()!= Level.OVERWORLD)seedMap=StateSaver.getServerState(server).seed;
            //this should not happen but...
        else
            try{seedMap=StateSaver.getServerState(target).seed;}
            catch(NullPointerException e){return instance.seed();}
        Identifier cur=target.dimension().identifier();
        if(seedMap.containsKey(cur))return seedMap.get(cur);
        return instance.seed();
    }
    @Redirect(method= "getRespawnData",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getRespawnData()Lnet/minecraft/world/level/storage/LevelData$RespawnData;"))
    private LevelData.RespawnData inject9(MinecraftServer instance)
    {
        Identifier target=((ServerLevel) (Object)this).dimension().identifier();
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
        Identifier target=((ServerLevel) (Object)this).dimension().identifier();
        if(getDimensionId(target).equals(Level.OVERWORLD.identifier()))
        {
            instance.setRespawnData(spawnPoint);
            return;
        }
        StateSaver.getServerState(instance).worldSpawn.put(target,spawnPoint);
    }
    @ModifyArg(method = "explode",at= @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;<init>(Lnet/minecraft/world/phys/Vec3;FILjava/util/Optional;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/Holder;Lnet/minecraft/util/random/WeightedList;)V",ordinal = 0))
    public Vec3 inject11(Vec3 center, @Local ServerPlayer player)
    {
        //TODO move this
        try
        {
            return VecTransformer.getInstance(player).s2cTransform(center);
        }catch (Exception e){return center;}
    }
    @Inject(method = "tick",at=@At("TAIL"))
    private void inject12(BooleanSupplier booleanSupplier, CallbackInfo ci)
    {
        for(ServerPlayer player:players)
        {
            VecTransformer.getInstance(player).tick();
        }
    }
}
