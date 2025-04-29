package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.GeneratorOptions;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.List;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
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
    @Redirect(method="<init>",at= @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerWorld;enderDragonFight:Lnet/minecraft/entity/boss/dragon/EnderDragonFight;",opcode = Opcodes.PUTFIELD,ordinal = 1))
    private void inject5(ServerWorld instance, EnderDragonFight value, @Local(argsOnly = true) long l)
    {
        if(!instance.getDimensionEntry().matchesKey(DimensionTypes.THE_END)){instance.enderDragonFight=null;return;}
        HashMap<Identifier,EnderDragonFight.Data> dragonFightHashMap;
        if(instance.getRegistryKey()!=World.OVERWORLD)dragonFightHashMap=StateSaver.getServerState(server).dragonFight;
        //this should not happen but...
        else dragonFightHashMap=StateSaver.getServerState(instance).dragonFight;
        Identifier cur=instance.getRegistryKey().getValue();
        instance.enderDragonFight = new EnderDragonFight(instance, l, dragonFightHashMap.getOrDefault(cur, EnderDragonFight.Data.DEFAULT));
    }
    @Redirect(method="savePersistentState",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/SaveProperties;setDragonFight(Lnet/minecraft/entity/boss/dragon/EnderDragonFight$Data;)V"))
    private void inject6(SaveProperties instance, EnderDragonFight.Data data)
    {
        ServerWorld target=(ServerWorld) (Object)this;
        HashMap<Identifier,EnderDragonFight.Data> dragonFightHashMap;
        if(target.getRegistryKey()!=World.OVERWORLD)dragonFightHashMap=StateSaver.getServerState(server).dragonFight;
            //this should not happen but...
        else dragonFightHashMap=StateSaver.getServerState(target).dragonFight;Identifier cur=target.getRegistryKey().getValue();
        assert target.enderDragonFight != null;
        if(target.getRegistryKey()==World.END)
            server.getSaveProperties().setDragonFight(target.enderDragonFight.toData());
        else
            dragonFightHashMap.put(cur,target.enderDragonFight.toData());
        //server.getOverworld().getChunkManager().getPersistentStateManager().save();
    }
    @Redirect(method="<init>",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/GeneratorOptions;getSeed()J"))
    private long inject7(GeneratorOptions instance)
    {
        ServerWorld target=(ServerWorld) (Object)this;
        HashMap<Identifier,Long> seedMap;
        if(target.getRegistryKey()!=World.OVERWORLD)seedMap=StateSaver.getServerState(server).seed;
            //this should not happen but...
        else seedMap=StateSaver.getServerState(target).seed;
        Identifier cur=target.getRegistryKey().getValue();
        if(seedMap.containsKey(cur))return seedMap.get(cur);
        return instance.getSeed();
    }
    @Redirect(method="getSeed",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/GeneratorOptions;getSeed()J"))
    private long inject8(GeneratorOptions instance)
    {
        ServerWorld target=(ServerWorld) (Object)this;
        HashMap<Identifier,Long> seedMap;
        if(target.getRegistryKey()!=World.OVERWORLD)seedMap=StateSaver.getServerState(server).seed;
            //this should not happen but...
        else
            try{seedMap=StateSaver.getServerState(target).seed;}
            catch(NullPointerException e){return instance.getSeed();}
        Identifier cur=target.getRegistryKey().getValue();
        if(seedMap.containsKey(cur))return seedMap.get(cur);
        return instance.getSeed();
    }
}
