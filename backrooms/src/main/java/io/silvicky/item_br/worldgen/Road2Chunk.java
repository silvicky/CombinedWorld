package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public class Road2Chunk extends SimpleChunk<Road2Blocks>
{
    public Road2Chunk(int baseY, int height, ChunkPos chunkPos)
    {
        super(baseY, height, chunkPos);
    }

    @Override
    public void setBlockState(BlockPos pos, Road2Blocks state)
    {
        Road2Blocks cur=getBlockState(pos);
        if(cur==null||state.compareTo(cur)>0)super.setBlockState(pos, state);
    }
}
