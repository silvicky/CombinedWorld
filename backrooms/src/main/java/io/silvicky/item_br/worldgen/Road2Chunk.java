package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;

public class Road2Chunk extends SimpleChunk<Road2Blocks>
{
    @Override
    public void setBlockState(BlockPos pos, Road2Blocks state)
    {
        Road2Blocks cur=getBlockState(pos);
        if(cur==null||state.compareTo(cur)>0)super.setBlockState(pos, state);
    }
}
