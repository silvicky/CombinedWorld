package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;

public interface PositionedAccess
{
    void item_storage$setPlayer(ServerPlayer player);
    ServerPlayer item_storage$getPlayer();
    void item_storage$setS2cMap(Map<ChunkPos,ChunkPos> s2cMap);
    Map<ChunkPos,ChunkPos>  item_storage$getS2cMap();
}
