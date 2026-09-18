package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ChunkGenCache<T extends AbstractBlock<T>>
{
    final int baseY;

    final int height;

    final ServerLevel level;

    final RandomState randomState;

    private final Map<RegionPos, Map<ChunkPos, SimpleChunk<T>>> regionContent =new ConcurrentHashMap<>();

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

    public void setBlockState(BlockPos pos, T state)
    {
        if (pos.getY() < this.baseY || pos.getY() >= this.baseY + height) {
            return;
        }
        ChunkPos chunkPos=ChunkPos.containing(pos);
        RegionPos regionPos=RegionPos.of(chunkPos);
        Map<ChunkPos, SimpleChunk<T>> map= regionContent.computeIfAbsent(regionPos, _ -> new HashMap<>());
        SimpleChunk<T> simpleChunk=map.computeIfAbsent(chunkPos,_->getNewChunk(chunkPos));
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

    abstract List<RegionPos> getSourceRegions(RegionPos regionPos);

    private void genChunk(ChunkPos chunkPos)
    {
        RegionPos regionPos=RegionPos.of(chunkPos);
        boolean success=false;
        for(RegionPos sourceRegionPos:getSourceRegions(regionPos)){
            success=success||tryGenRegion(sourceRegionPos);
        }
        if(success)checkRecycle();
    }

    public void apply(ChunkAccess chunk)
    {
        ChunkPos chunkPos=chunk.getPos();
        genChunk(chunkPos);
        Map<ChunkPos, SimpleChunk<T>> map= regionContent.getOrDefault(RegionPos.of(chunkPos), new HashMap<>());
        SimpleChunk<T> simpleChunk=map.getOrDefault(chunkPos,getNewChunk(chunkPos));
        simpleChunk.apply(chunk);
    }

    abstract SimpleChunk<T> getNewChunk(ChunkPos chunkPos);
}
