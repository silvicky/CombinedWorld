package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;

public interface AbstractBlock<T> extends Comparable<T> {
    void apply(BlockPos blockPos, ChunkAccess chunkAccess);
}
