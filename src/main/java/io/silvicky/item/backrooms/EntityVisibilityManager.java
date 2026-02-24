package io.silvicky.item.backrooms;

import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

public class EntityVisibilityManager
{
    public static boolean isVisible(ServerPlayerEntity player, EntitySpawnS2CPacket packet)
    {
        //TODO
        return true;
        //return !packet.getEntityType().equals(EntityType.PLAYER);
    }
}
