package io.silvicky.item.backrooms;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

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
    public static boolean isVisible(ServerPlayerEntity player, EntitySpawnS2CPacket packet)
    {
        int level=getServerState(player.getEntityWorld().getServer()).entityVisibility.getOrDefault(player.getEntityWorld().getRegistryKey().getValue(),0);
        EntityType<?> entityType=packet.getEntityType();
        Entity entity=entityType.create(player.getEntityWorld(), SpawnReason.COMMAND);
        if(entityType.equals(EntityType.PLAYER))
        {
            switch (level&0b11)
            {
                case 1 ->
                {
                    String namespace=player.getEntityWorld().getRegistryKey().getValue().getNamespace();
                    MinecraftServer server=player.getEntityWorld().getServer();
                    return getPlayerVisibility(server,namespace,player.getUuidAsString())==getPlayerVisibility(server,namespace,packet.getUuid().toString());
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
        if(entity instanceof LivingEntity)
        {
            return (level & 0b100) >> 2 != 1;
        }
        return (level & 0b1000) >> 3 != 1;
    }
}
