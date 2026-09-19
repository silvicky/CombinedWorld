package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.HashMap;
import java.util.Map;

public class SimpleChunk<T extends AbstractBlock<T>>
{
    private final Map<BlockPos, T> blockStates=new HashMap<>();

    public void setBlockState(BlockPos pos, T block)
    {
        blockStates.put(pos, block);
    }

    public T getBlockState(BlockPos pos)
    {
        return blockStates.get(pos);
    }

    public void apply(ChunkAccess chunk)
    {
        for(Map.Entry<BlockPos, T> entry:blockStates.entrySet())
        {
            entry.getValue().apply(entry.getKey(), chunk);
        }
    }

    public void combine(SimpleChunk<T> simpleChunk)
    {
        for(Map.Entry<BlockPos, T> entry:simpleChunk.blockStates.entrySet()) {
            setBlockState(entry.getKey(), entry.getValue());
        }
    }
}
