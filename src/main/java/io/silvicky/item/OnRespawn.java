package io.silvicky.item;

import net.minecraft.server.network.ServerPlayerEntity;

import static io.silvicky.item.InventoryManager.*;

public class OnRespawn {
    public static void respawn(ServerPlayerEntity oldPlayer,ServerPlayerEntity newPlayer,boolean alive)
    {
        if(getDimensionId(oldPlayer.getServerWorld()).equals(getDimensionId(newPlayer.getServerWorld())))return;
        if(alive) save(oldPlayer.server,oldPlayer);
        load(newPlayer.server,newPlayer,newPlayer.getServerWorld());
    }
}
