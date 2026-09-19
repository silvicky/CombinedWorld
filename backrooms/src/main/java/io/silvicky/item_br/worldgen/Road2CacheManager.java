package io.silvicky.item_br.worldgen;

import net.minecraft.world.level.levelgen.RandomState;

import java.util.List;

public class Road2CacheManager extends ChunkCacheManager<Road2Blocks,Road2Cache,Road2Chunk>{
    public Road2CacheManager(RandomState randomState) {
        super(randomState);
    }

    @Override
    Road2Cache newCache(RegionPos regionPos) {
        return new Road2Cache(randomState, regionPos);
    }

    @Override
    Road2Chunk newChunk() {
        return new Road2Chunk();
    }

    @Override
    List<RegionPos> getSourceRegions(RegionPos regionPos) {
        return List.of(regionPos,
                regionPos.add(-1,0),
                regionPos.add(0,-1));
    }
}
