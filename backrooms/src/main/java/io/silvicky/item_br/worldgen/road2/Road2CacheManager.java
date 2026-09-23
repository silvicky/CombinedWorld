package io.silvicky.item_br.worldgen.road2;

import io.silvicky.item_br.worldgen.ChunkCacheManager;
import io.silvicky.item_br.worldgen.RegionPos;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.List;

public class Road2CacheManager extends ChunkCacheManager<Road2Blocks,Road2Cache,Road2Chunk> {
    public Road2CacheManager(RandomState randomState) {
        super(randomState);
    }

    @Override
    protected Road2Cache newCache(RegionPos regionPos) {
        return new Road2Cache(randomState, regionPos);
    }

    @Override
    protected Road2Chunk newChunk() {
        return new Road2Chunk();
    }

    @Override
    protected List<RegionPos> getSourceRegions(RegionPos regionPos) {
        return List.of(regionPos,
                regionPos.add(-1,0),
                regionPos.add(0,-1));
    }
}
