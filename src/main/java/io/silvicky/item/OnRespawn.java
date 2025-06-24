package io.silvicky.item;

import net.minecraft.server.network.ServerPlayerEntity;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.common.Util.*;

public class OnRespawn {
    public static void respawn(ServerPlayerEntity oldPlayer,ServerPlayerEntity newPlayer,boolean alive) {
        if(getDimensionId(oldPlayer.getWorld()).equals(getDimensionId(newPlayer.getWorld())))return;
        if(oldPlayer.getWorld().getRegistryKey().getValue().getNamespace().equals(newPlayer.getWorld().getRegistryKey().getValue().getNamespace()))return;
        StateSaver stateSaver=StateSaver.getServerState(oldPlayer.getServer());
        if(alive) saveInventory(oldPlayer,stateSaver);
        try{loadInventory(newPlayer,newPlayer.getWorld(),stateSaver);}
        catch(Exception e){LOGGER.error(e.getMessage());}
    }
}
