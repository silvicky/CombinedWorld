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
import static io.silvicky.item_br.worldgen.RoadCustomRule.getNodeCoordination;

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
        return chunkPos.getBlockAt(random.nextInt(3,13),0,random.nextInt(3,13));
    }

    private void requestChunk(ChunkPos chunkPos, RandomState randomState)
    {
        //TODO draw real things
        int[][] neighbors={{1,0},{0,1}};
        BlockPos core=getChosenPos(chunkPos, randomState);
        boolean[] coordination=getNodeCoordination(randomState,chunkPos.x(),chunkPos.z());
        for (int i=0;i<2;i++)
        {
            BlockPos cur = getChosenPos(shift(chunkPos, neighbors[i][0], neighbors[i][1]), randomState);
            if(coordination[i])drawLine(core.getX(), core.getZ(), cur.getX(), cur.getZ(), (x, z) -> setBlockState(new BlockPos(x, 0, z), Blocks.CONCRETE.white().defaultBlockState()));
        }
    }

    public void apply(ChunkAccess chunk, RandomState randomState)
    {
        //TODO Dismiss generated chunks
        ChunkPos chunkPos=chunk.getPos();
        for(int x=-1;x<=0;x++)
            for(int z=-1;z<=0;z++)
                requestChunk(shift(chunkPos,x,z), randomState);
        SimpleChunk simpleChunk=chunks.get(chunkPos);
        if(simpleChunk!=null)
        {
            simpleChunk.apply(chunk);
        }
    }
}
