package io.silvicky.item;

import net.minecraft.server.network.ServerPlayerEntity;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.ItemStorage.LOGGER;

public class OnRespawn {
    public static void respawn(ServerPlayerEntity oldPlayer,ServerPlayerEntity newPlayer,boolean alive) {
        if(getDimensionId(oldPlayer.getServerWorld()).equals(getDimensionId(newPlayer.getServerWorld())))return;
        if(oldPlayer.getServerWorld().getRegistryKey().getValue().getNamespace().equals(newPlayer.getServerWorld().getRegistryKey().getValue().getNamespace()))return;
        StateSaver stateSaver=StateSaver.getServerState(oldPlayer.getServer());
        if(alive) saveInventory(oldPlayer,stateSaver);
        try{loadInventory(newPlayer,newPlayer.getServerWorld(),stateSaver);}
        catch(Exception e){LOGGER.error(e.getMessage());}
    }
}
