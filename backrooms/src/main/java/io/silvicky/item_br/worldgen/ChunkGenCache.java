package io.silvicky.item_br.worldgen;

import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public class ChunkGenCache
{
    private final Map<ChunkPos, SimpleChunk> chunks=new HashMap<>();
}
