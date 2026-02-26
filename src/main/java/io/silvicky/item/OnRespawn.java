package io.silvicky.item;

import net.minecraft.server.network.ServerPlayerEntity;

import static io.silvicky.item.backrooms.EntityVisibilityManager.updatePlayerVisibility;

public class OnRespawn {
    public static void respawn(ServerPlayerEntity oldPlayer,ServerPlayerEntity newPlayer,boolean alive)
    {
        if(alive)return;
        updatePlayerVisibility(newPlayer.getEntityWorld().getServer(),
                newPlayer.getEntityWorld().getRegistryKey().getValue().getNamespace(),
                newPlayer.getUuidAsString());
    }
}
