package io.silvicky.item;

import net.minecraft.server.network.ServerPlayerEntity;

public class OnRespawn {
    public static void respawn(ServerPlayerEntity oldPlayer,ServerPlayerEntity newPlayer,boolean alive)
    {
        if(InventoryManager.getDimensionId(oldPlayer.getServerWorld()).equals(InventoryManager.getDimensionId(newPlayer.getServerWorld())))return;
        if(alive)InventoryManager.save(oldPlayer.server,oldPlayer);
        InventoryManager.load(newPlayer.server,newPlayer,newPlayer.getServerWorld());
    }
}
