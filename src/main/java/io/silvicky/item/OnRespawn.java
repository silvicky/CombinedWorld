package io.silvicky.item;

import net.minecraft.server.network.ServerPlayerEntity;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.common.Util.*;

public class OnRespawn {
    public static void respawn(ServerPlayerEntity oldPlayer,ServerPlayerEntity newPlayer,boolean alive) {
        if(getDimensionId(oldPlayer.getEntityWorld()).equals(getDimensionId(newPlayer.getEntityWorld())))return;
        if(oldPlayer.getEntityWorld().getRegistryKey().getValue().getNamespace().equals(newPlayer.getEntityWorld().getRegistryKey().getValue().getNamespace()))return;
        StateSaver stateSaver=StateSaver.getServerState(oldPlayer.getEntityWorld().getServer());
        if(alive) saveInventory(oldPlayer,stateSaver);
        else saveInventoryDead(oldPlayer,stateSaver);
        try{loadInventory(newPlayer,newPlayer.getEntityWorld(),stateSaver);}
        catch(Exception e){LOGGER.error(e.getMessage());}
    }
}
