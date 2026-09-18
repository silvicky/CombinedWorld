package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.HashMap;
import java.util.Map;

public class SimpleChunk<T extends AbstractBlock<T>>
{
    private final int baseY;

    private final int height;

    private final ChunkPos chunkPos;

    private final Map<BlockPos, T> blockStates=new HashMap<>();

    public SimpleChunk(int baseY, int height, ChunkPos chunkPos)
    {
        this.baseY = baseY;
        this.height = height;
        this.chunkPos = chunkPos;
    }

    public void setBlockState(BlockPos pos, T block)
    {
        if(chunkPos.contains(pos)&&pos.getY()>=this.baseY&&pos.getY()<this.baseY+height)
        {
            blockStates.put(pos, block);
        }
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
}
