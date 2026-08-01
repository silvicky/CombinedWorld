package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashMap;
import java.util.Map;

import static io.silvicky.item_br.worldgen.Graphic.drawLine;

public class ChunkGenCache
{
    private static final Identifier key=Identifier.parse("silvicky:road2");

    private final int baseY;

    private final int height;

    private final Map<ChunkPos, SimpleChunk> chunks=new HashMap<>();

    private static ChunkPos shift(ChunkPos pos,int x,int z)
    {
        return new ChunkPos(pos.x()+x,pos.z()+z);
    }

    public ChunkGenCache(int baseY, int height)
    {
        this.baseY = baseY;
        this.height = height;
    }

    public void setBlockState(BlockPos pos, BlockState state)
    {
        if(pos.getY()>=this.baseY&&pos.getY()<this.baseY+height)
        {
            //TODO dismiss gen...
            ChunkPos chunkPos=ChunkPos.containing(pos);
            SimpleChunk simpleChunk=chunks.computeIfAbsent(chunkPos,_->new SimpleChunk(baseY,height,chunkPos));
            simpleChunk.setBlockState(pos, state);
        }
    }

    private BlockPos getChosenPos(ChunkPos chunkPos, RandomState randomState)
    {
        RandomSource random=randomState.getOrCreateRandomFactory(key).at(chunkPos.x(),0,chunkPos.z());
        return chunkPos.getBlockAt(random.nextInt(16),0,random.nextInt(16));
    }

    private void requestChunk(ChunkPos chunkPos, RandomState randomState)
    {
        //TODO draw real things
        int[][] neighbors={{1,0},{-1,0},{0,1},{0,-1}};
        BlockPos core=getChosenPos(chunkPos, randomState);
        for (int[] neighbor : neighbors)
        {
            BlockPos cur = getChosenPos(shift(chunkPos, neighbor[0], neighbor[1]), randomState);
            drawLine(core.getX(), core.getZ(), cur.getX(), cur.getZ(), (x, z) -> setBlockState(new BlockPos(x, 0, z), Blocks.CONCRETE.white().defaultBlockState()));
        }
    }

    public void apply(ChunkAccess chunk, RandomState randomState)
    {
        //TODO Dismiss generated chunks
        ChunkPos chunkPos=chunk.getPos();
        requestChunk(chunkPos, randomState);
        SimpleChunk simpleChunk=chunks.get(chunkPos);
        if(simpleChunk!=null)
        {
            simpleChunk.apply(chunk);
        }
    }
}
