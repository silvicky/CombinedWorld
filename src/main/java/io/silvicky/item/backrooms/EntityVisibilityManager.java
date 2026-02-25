package io.silvicky.item.backrooms;

import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

import static io.silvicky.item.StateSaver.getServerState;

public class EntityVisibilityManager
{
    public static boolean isVisible(ServerPlayerEntity player, EntitySpawnS2CPacket packet)
    {
        EntityVisibilityLevel level=getServerState(player.getEntityWorld().getServer()).entityVisibility.getOrDefault(player.getEntityWorld().getRegistryKey().getValue(),EntityVisibilityLevel.NORMAL);
        switch (level)
        {
            case NONE ->
            {
                return false;
            }
            case NO_PLAYER ->
            {
                return !packet.getEntityType().equals(EntityType.PLAYER);
            }
            case LIMITED ->
            {
                //TODO
                return true;
            }
            default ->
            {
                return true;
            }
        }
    }
}
