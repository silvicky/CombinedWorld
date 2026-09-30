package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;

public interface PositionedAccess
{
    void item_storage$setPlayer(ServerPlayer player);
    ServerPlayer item_storage$getPlayer();
    void item_storage$setRemap(ChunkRemap remap);
    ChunkRemap item_storage$getRemap();
}
