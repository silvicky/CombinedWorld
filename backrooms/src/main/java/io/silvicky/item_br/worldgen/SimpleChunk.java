package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;

public class SimpleChunk
{
    private final int baseY;

    private final int height;

    private final ChunkPos chunkPos;

    private final List<BlockState[][]> blockStates;

    public SimpleChunk(int baseY, int height, ChunkPos chunkPos)
    {
        this.baseY = baseY;
        this.height = height;
        this.chunkPos = chunkPos;
        this.blockStates=new ArrayList<>(height);
        for(int i=0;i<height;i++)this.blockStates.set(i,new BlockState[16][16]);
    }

    public void setBlockState(BlockPos pos, BlockState state)
    {
        if(chunkPos.contains(pos)&&pos.getY()>=this.baseY&&pos.getY()<this.baseY+height)
        {
            blockStates.get(pos.getY() - baseY)
                    [pos.getX() - chunkPos.getMinBlockX()]
                    [pos.getZ() - chunkPos.getMinBlockZ()]
                    = state;
        }
    }

    public BlockState getBlockState(BlockPos pos)
    {
        if(chunkPos.contains(pos)&&pos.getY()>=this.baseY&&pos.getY()<this.baseY+height)
        {
            return blockStates.get(pos.getY() - baseY)
                    [pos.getX() - chunkPos.getMinBlockX()]
                    [pos.getZ() - chunkPos.getMinBlockZ()];
        }
        else return null;
    }

    public void apply(ChunkAccess chunk)
    {
        for(int i=0;i<height;i++)for(int x = 0; x <16; x++)for(int z = 0; z <16; z++)
        {
            chunk.setBlockState(chunkPos.getBlockAt(x,i+baseY,z),blockStates.get(i)[x][z]);
        }
    }
}
