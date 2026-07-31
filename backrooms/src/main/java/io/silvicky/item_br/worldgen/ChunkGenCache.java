package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.HashMap;
import java.util.Map;

public class ChunkGenCache
{
    private final int baseY;

    private final int height;

    private final Map<ChunkPos, SimpleChunk> chunks=new HashMap<>();

    public ChunkGenCache(int baseY, int height)
    {
        this.baseY = baseY;
        this.height = height;
    }

    public void setBlockState(BlockPos pos, BlockState state)
    {
        if(pos.getY()>=this.baseY&&pos.getY()<this.baseY+height)
        {
            //TODO
            ChunkPos chunkPos=ChunkPos.containing(pos);
            SimpleChunk simpleChunk=chunks.computeIfAbsent(chunkPos,_->new SimpleChunk(baseY,height,chunkPos));
            simpleChunk.setBlockState(pos, state);
        }
    }

    private BlockPos getChosenPos(ChunkPos chunkPos)
    {
        //TODO use real random
        return chunkPos.getBlockAt((chunkPos.x()*7)&15,0,(chunkPos.z()*9)&15);
    }

    private void requestChunk(ChunkPos chunkPos)
    {
        //TODO
    }

    public void apply(ChunkAccess chunk)
    {
        //TODO Dismiss generated chunks
        ChunkPos chunkPos=chunk.getPos();
        if(!chunks.containsKey(chunkPos))
        {
            requestChunk(chunkPos);
        }
        SimpleChunk simpleChunk=chunks.get(chunkPos);
        if(simpleChunk!=null)
        {
            simpleChunk.apply(chunk);
        }
    }
}
