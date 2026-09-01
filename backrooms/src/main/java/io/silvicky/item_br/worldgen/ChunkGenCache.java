package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ChunkGenCache
{
    final int baseY;

    final int height;

    final ServerLevel level;

    final RandomState randomState;

    private final Map<RegionPos, Map<ChunkPos, SimpleChunk>> regionContent =new ConcurrentHashMap<>();

    private final Map<RegionPos, Byte> refCount = new ConcurrentHashMap<>();

    private final Queue<RegionPos> cachedRegionsQueue =new LinkedList<>();

    private final Set<RegionPos> cachedRegionsSet=new HashSet<>();

    private boolean recycleMode=false;

    private static final int queueLowerBound = 256;

    private static final int queueUpperBound = 512;

    private static final int queueRecycleCount = 8;

    public ChunkGenCache(int baseY, int height, ServerLevel level, RandomState randomState)
    {
        this.baseY = baseY;
        this.height = height;
        this.level = level;
        this.randomState = randomState;
    }

    private void checkRecycle() {
        if (recycleMode) {
            for (int i = 0; i < queueRecycleCount; i++) {
                RegionPos removedPos = cachedRegionsQueue.remove();
                cachedRegionsSet.remove(removedPos);
                refCount.compute(removedPos,(p,v)->
                {
                    if(v==null||v<=1)
                    {
                        regionContent.remove(p);
                        return null;
                    }
                    else return (byte) (v-1);
                });
                regionContent.remove(removedPos);
            }
            if (cachedRegionsQueue.size() <= queueLowerBound) {
                recycleMode = false;
            }
        } else {
            if (cachedRegionsQueue.size() > queueUpperBound) {
                recycleMode = true;
            }
        }
    }

    public void setBlockState(BlockPos pos, BlockState state)
    {
        if (pos.getY() < this.baseY || pos.getY() >= this.baseY + height) {
            return;
        }
        ChunkPos chunkPos=ChunkPos.containing(pos);
        RegionPos regionPos=RegionPos.of(chunkPos);
        Map<ChunkPos, SimpleChunk> map= regionContent.computeIfAbsent(regionPos, _ -> new HashMap<>());
        SimpleChunk simpleChunk=map.computeIfAbsent(chunkPos,_->new Road2Chunk(baseY,height,chunkPos));
        simpleChunk.setBlockState(pos, state);
    }

    abstract void genRegion(RegionPos regionPos);

    private boolean tryGenRegion(RegionPos regionPos)
    {
        if(cachedRegionsSet.contains(regionPos))return false;
        cachedRegionsSet.add(regionPos);
        cachedRegionsQueue.add(regionPos);
        refCount.compute(regionPos,(_,v)-> v==null?1: (byte) (v + 1));
        genRegion(regionPos);
        return true;
    }

    private void genChunk(ChunkPos chunkPos)
    {
        RegionPos regionPos=RegionPos.of(chunkPos);
        boolean success=tryGenRegion(regionPos);
        success=success||tryGenRegion(regionPos.add(-1,0));
        success=success||tryGenRegion(regionPos.add(0,-1));
        if(success)checkRecycle();
    }

    public void apply(ChunkAccess chunk)
    {
        ChunkPos chunkPos=chunk.getPos();
        genChunk(chunkPos);
        Map<ChunkPos, SimpleChunk> map= regionContent.getOrDefault(RegionPos.of(chunkPos), new HashMap<>());
        SimpleChunk simpleChunk=map.getOrDefault(chunkPos,new Road2Chunk(baseY,height,chunkPos));
        simpleChunk.apply(chunk);
    }
}
