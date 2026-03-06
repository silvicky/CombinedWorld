package io.silvicky.item.backrooms;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

import java.util.HashMap;
import java.util.Random;

import static io.silvicky.item.StateSaver.getServerState;
import static io.silvicky.item.cfg.JSONConfig.playerVisibilityRandomize;
import static io.silvicky.item.cfg.JSONConfig.playerVisibilityRange;

public class EntityVisibilityManager
{
    private static final Random random=new Random();
    public static long getPlayerVisibility(MinecraftServer server, String namespace, String player)
    {
        return getServerState(server).playerVisibility
                .computeIfAbsent(namespace,i->new HashMap<>())
                .compute(player, (k, v) -> (v == null) ? 0L : v % playerVisibilityRange);
    }
    public static void updatePlayerVisibility(MinecraftServer server, String namespace, String player)
    {
        getServerState(server).playerVisibility
                .computeIfAbsent(namespace,i->new HashMap<>())
                .compute(player, (k, v) ->
                        (
                                ((v == null) ? 0L : v )
                                        +(playerVisibilityRandomize?random.nextLong()%(playerVisibilityRange-1):0L)
                                        +1L
                        )%playerVisibilityRange);
    }
    public static void setPlayerVisibility(MinecraftServer server, String namespace, String player, long level)
    {
        getServerState(server).playerVisibility
                .computeIfAbsent(namespace,i->new HashMap<>())
                .put(player, level % playerVisibilityRange);
    }
    public static void initPlayerVisibility(MinecraftServer server, String namespace)
    {
        getServerState(server).playerVisibility.remove(namespace);
    }
    public static boolean isVisible(ServerPlayer player, ClientboundAddEntityPacket packet)
    {
        int level=getServerState(player.level().getServer()).entityVisibility.getOrDefault(player.level().dimension().identifier(),0);
        EntityType<?> entityType=packet.getType();
        Entity entity=entityType.create(player.level(), EntitySpawnReason.COMMAND);
        if(entityType.equals(EntityType.PLAYER))
        {
            switch (level&0b11)
            {
                case 1 ->
                {
                    String namespace=player.level().dimension().identifier().getNamespace();
                    MinecraftServer server=player.level().getServer();
                    return getPlayerVisibility(server,namespace,player.getStringUUID())==getPlayerVisibility(server,namespace,packet.getUUID().toString());
                }
                case 2 ->
                {
                    return false;
                }
                default ->
                {
                    return true;
                }
            }
        }
        if(!(entity instanceof LivingEntity))
        {
            return (level & 0b100) >> 2 != 1;
        }
        if(entityType.getCategory()== MobCategory.MONSTER)
        {
            return (level & 0b1000) >> 3 != 1;
        }
        if(entityType.equals(EntityType.VILLAGER))
        {
            return (level & 0b10000) >> 4 != 1;
        }
        if(entityType.getCategory()== MobCategory.MISC)
        {
            return (level & 0b100000) >> 5 != 1;
        }
        return (level & 0b1000000) >> 6 != 1;
    }
}
