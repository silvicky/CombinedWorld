package io.silvicky.item;

import net.minecraft.server.network.ServerPlayerEntity;

import static io.silvicky.item.InventoryManager.*;

public class OnRespawn {
    public static void respawn(ServerPlayerEntity oldPlayer,ServerPlayerEntity newPlayer,boolean alive)
    {
        if(getDimensionId(oldPlayer.getServerWorld()).equals(getDimensionId(newPlayer.getServerWorld())))return;
        if(oldPlayer.getServerWorld().getRegistryKey().getValue().getNamespace().equals(newPlayer.getServerWorld().getRegistryKey().getValue().getNamespace()))return;
        StateSaver stateSaver=StateSaver.getServerState(oldPlayer.getServer());
        if(alive) saveInventory(oldPlayer.server,oldPlayer,stateSaver);
        loadInventory(newPlayer.server,newPlayer,newPlayer.getServerWorld(),stateSaver);
    }
}
