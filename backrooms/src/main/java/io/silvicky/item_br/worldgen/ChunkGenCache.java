package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashMap;
import java.util.Map;

import static io.silvicky.item_br.worldgen.Graphic.*;
import static io.silvicky.item_br.worldgen.RoadCustomRule.getNodeCoordination;

public class ChunkGenCache
{
    //TODO Separate this into two classes
    private static final Identifier key=Identifier.parse("silvicky:road2");

    private final int baseY;

    private final int height;

    private final ServerLevel level;

    private final RandomState randomState;

    private final Map<ChunkPos, SimpleChunk> chunks=new HashMap<>();

    private static ChunkPos shift(ChunkPos pos,int x,int z)
    {
        return new ChunkPos(pos.x()+x,pos.z()+z);
    }

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
            if(level.isLoaded(pos))return;//fixme this results in bug if chunk loaded by player
            ChunkPos chunkPos=ChunkPos.containing(pos);
            SimpleChunk simpleChunk=chunks.computeIfAbsent(chunkPos,_->new SimpleChunk(baseY,height,chunkPos));
            simpleChunk.setBlockState(pos, state);
        }
    }

    private BlockPos getChosenPos(RegionPos regionPos)
    {
        RandomSource random=randomState.getOrCreateRandomFactory(key).at(regionPos.x,0,regionPos.z);
        return regionPos.at(random.nextInt(16,48),0,random.nextInt(16,48));
    }

    private void genRegion(RegionPos regionPos)
    {
        //TODO draw real things
        //TODO Dismiss generated chunks
        int[][] neighbors={{1,0},{0,1}};
        BlockPos core=getChosenPos(regionPos);
        boolean[] coordination=getNodeCoordination(randomState,regionPos.x,regionPos.z);
        for (int i=0;i<2;i++)
        {
            BlockPos cur = getChosenPos(regionPos.add(neighbors[i][0], neighbors[i][1]));
            if(coordination[i])
            {
                Point2 p00=new Point2(core.getX(), core.getZ());
                Point2 p01=new Point2(cur.getX(), cur.getZ());
                drawSideRect(p00,p01,5,
                        (x,z)->setBlockState(new BlockPos(x,0,z),Blocks.CONCRETE.orange().defaultBlockState()),
                        (x,z)->setBlockState(new BlockPos(x,0,z),Blocks.CONCRETE.white().defaultBlockState()));
            }
        }
    }

    private void genChunk(ChunkPos chunkPos)
    {
        RegionPos regionPos=RegionPos.of(chunkPos);
        genRegion(regionPos);
        genRegion(regionPos.add(-1,0));
        genRegion(regionPos.add(0,-1));
    }

    public void apply(ChunkAccess chunk)
    {
        ChunkPos chunkPos=chunk.getPos();
        genChunk(chunkPos);
        SimpleChunk simpleChunk=chunks.remove(chunkPos);
        if(simpleChunk!=null)
        {
            simpleChunk.apply(chunk);
        }
    }
}
