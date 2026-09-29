package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;

public interface AbstractBlock<T> extends Comparable<T> {
    void apply(BlockPos blockPos, ChunkAccess chunkAccess);

    /**
     * Small stable index for this block, used as the on-disk slot in {@link SimpleChunk}. Implementations
     * must satisfy {@code values()[id()] == this} so the slot maps back unambiguously; enums can just
     * return {@link Enum#ordinal()}.
     */
    int id();
}
