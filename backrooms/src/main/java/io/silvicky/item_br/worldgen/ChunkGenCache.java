package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.*;

public abstract class ChunkGenCache
{
    final int baseY;

    final int height;

    final ServerLevel level;

    final RandomState randomState;

    private final Map<ChunkPos, SimpleChunk> chunks=new HashMap<>();

    public ChunkGenCache(int baseY, int height, ServerLevel level, RandomState randomState)
    {
        this.baseY = baseY;
        this.height = height;
        this.level = level;
        this.randomState = randomState;
    }

    public void setBlockState(BlockPos pos, BlockState state)
    {
        if(pos.getY()>=this.baseY&&pos.getY()<this.baseY+height)
        {
            ChunkPos chunkPos=ChunkPos.containing(pos);
            SimpleChunk simpleChunk=chunks.computeIfAbsent(chunkPos,_->new Road2Chunk(baseY,height,chunkPos));
            simpleChunk.setBlockState(pos, state);
        }
    }

    abstract void genChunk(ChunkPos chunkPos);

    public void apply(ChunkAccess chunk)
    {
        ChunkPos chunkPos=chunk.getPos();
        genChunk(chunkPos);
        //SimpleChunk simpleChunk=chunks.remove(chunkPos);
        SimpleChunk simpleChunk=chunks.get(chunkPos);//TODO somehow remove them elsewhere?
        if(simpleChunk!=null)
        {
            simpleChunk.apply(chunk);
        }
    }
}
