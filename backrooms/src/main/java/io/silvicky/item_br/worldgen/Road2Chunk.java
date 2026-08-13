package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Road2Chunk extends SimpleChunk
{
    public Road2Chunk(int baseY, int height, ChunkPos chunkPos)
    {
        super(baseY, height, chunkPos);
    }

    private static boolean compareBlockState(BlockState newState, BlockState oldState)
    {
        return !(newState.equals(Blocks.CONCRETE.white().defaultBlockState())
                && (oldState!=null&&oldState.equals(Blocks.CONCRETE.orange().defaultBlockState())));
    }

    @Override
    public void setBlockState(BlockPos pos, BlockState state)
    {
        if(compareBlockState(state,super.getBlockState(pos)))super.setBlockState(pos, state);
    }
}
