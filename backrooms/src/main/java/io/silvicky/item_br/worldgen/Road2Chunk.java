package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import static io.silvicky.item_br.worldgen.Road2Cache.EDGE;
import static io.silvicky.item_br.worldgen.Road2Cache.ROAD;

public class Road2Chunk extends SimpleChunk
{
    public Road2Chunk(int baseY, int height, ChunkPos chunkPos)
    {
        super(baseY, height, chunkPos);
    }

    private static boolean compareBlockState(BlockState newState, BlockState oldState)
    {
        return !(newState.equals(EDGE)
                && (oldState!=null&&oldState.equals(ROAD)));
    }

    @Override
    public void setBlockState(BlockPos pos, BlockState state)
    {
        if(compareBlockState(state,super.getBlockState(pos)))super.setBlockState(pos, state);
    }
}
