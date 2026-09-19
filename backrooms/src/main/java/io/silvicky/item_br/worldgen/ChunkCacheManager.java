package io.silvicky.item_br.worldgen;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.*;

public abstract class ChunkCacheManager<B extends AbstractBlock<B>, C extends ChunkGenCache<B>, K extends SimpleChunk<B>>
{
    private final Map<RegionPos, C> caches = Collections.synchronizedMap(new LinkedHashMap<>(cacheSize, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RegionPos, C> eldest) {
            return size() > cacheSize;
        }
    });

    final RandomState randomState;

    private static final int cacheSize=256;

    protected ChunkCacheManager(RandomState randomState) {
        this.randomState = randomState;
    }

    abstract C newCache(RegionPos regionPos);

    abstract K newChunk();

    private C request(RegionPos pos)
    {
        if(caches.containsKey(pos))
        {
            return caches.get(pos);
        }
        C result = newCache(pos);
        result.generate();
        caches.put(pos, result);
        return result;
    }

    abstract List<RegionPos> getSourceRegions(RegionPos regionPos);

    public void generate(ChunkAccess chunk)
    {
        ChunkPos chunkPos=chunk.getPos();
        K rawChunk=newChunk();
        RegionPos regionPos=RegionPos.of(chunkPos);
        for(RegionPos sourceRegionPos:getSourceRegions(regionPos)){
            SimpleChunk<B> cachedChunk=request(sourceRegionPos).getChunk(chunkPos);
            if(cachedChunk!=null)rawChunk.combine(cachedChunk);
        }
        rawChunk.apply(chunk);
    }
}
