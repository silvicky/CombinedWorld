package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.HashMap;
import java.util.Map;

public class SimpleChunk
{
    private final int baseY;

    private final int height;

    private final ChunkPos chunkPos;

    private final Map<BlockPos, BlockState> blockStates=new HashMap<>();

    public SimpleChunk(int baseY, int height, ChunkPos chunkPos)
    {
        this.baseY = baseY;
        this.height = height;
        this.chunkPos = chunkPos;
    }

    public void setBlockState(BlockPos pos, BlockState state)
    {
        if(chunkPos.contains(pos)&&pos.getY()>=this.baseY&&pos.getY()<this.baseY+height)
        {
            blockStates.put(pos, state);
        }
    }

    public BlockState getBlockState(BlockPos pos)
    {
        return blockStates.get(pos);
    }

    public void apply(ChunkAccess chunk)
    {
        for(Map.Entry<BlockPos, BlockState> entry:blockStates.entrySet())
        {
            chunk.setBlockState(entry.getKey(), entry.getValue());
        }
    }
}
