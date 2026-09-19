package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ChunkGenCache<T extends AbstractBlock<T>>
{
    final RandomState randomState;

    final RegionPos regionPos;

    private final Map<ChunkPos, SimpleChunk<T>> chunks =new ConcurrentHashMap<>();

    public ChunkGenCache(RandomState randomState, RegionPos regionPos)
    {
        this.randomState = randomState;
        this.regionPos = regionPos;
    }

    public SimpleChunk<T> getChunk(ChunkPos chunkPos){
        return chunks.get(chunkPos);
    }

    public void setBlockState(BlockPos pos, T state)
    {
        ChunkPos chunkPos=ChunkPos.containing(pos);
        SimpleChunk<T> simpleChunk=chunks.computeIfAbsent(chunkPos,_->getNewChunk(chunkPos));
        simpleChunk.setBlockState(pos, state);
    }

    abstract void generate();

    abstract SimpleChunk<T> getNewChunk(ChunkPos chunkPos);
}
