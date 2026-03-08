package io.silvicky.item.helper;

import net.minecraft.server.level.ServerPlayer;

public interface PositionedAccess
{
    void item_storage$setPlayer(ServerPlayer player);
    ServerPlayer item_storage$getPlayer();
}
