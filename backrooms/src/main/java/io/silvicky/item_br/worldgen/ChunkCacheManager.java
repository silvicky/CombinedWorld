package io.silvicky.item_br.worldgen;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.*;
import java.util.concurrent.*;

public abstract class ChunkCacheManager<B extends AbstractBlock<B>, C extends ChunkGenCache<B>, K extends SimpleChunk<B>>
{
    private static final int cacheSize=256;

    private final AsyncCache<RegionPos, C> cache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(cacheSize)
            .buildAsync();

    protected final RandomState randomState;

    protected ChunkCacheManager(RandomState randomState) {
        this.randomState = randomState;
    }

    protected abstract C newCache(RegionPos regionPos);

    protected abstract K newChunk();

    private CompletableFuture<C> requestAsync(RegionPos pos) {
        return cache.get(pos, p ->{
                    C result = newCache(p);
                    result.generate();
                    return result;
                });
    }

    protected abstract List<RegionPos> getSourceRegions(RegionPos regionPos);

    public CompletableFuture<ChunkAccess> generate(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        RegionPos regionPos = RegionPos.of(chunkPos);

        List<RegionPos> sources = getSourceRegions(regionPos);

        List<CompletableFuture<C>> futures = sources.stream()
                .map(this::requestAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(_ -> {
                    K rawChunk = newChunk();
                    for (int i = 0; i < sources.size(); i++) {
                        C cache = futures.get(i).join();
                        SimpleChunk<B> cachedChunk = cache.getChunk(chunkPos);
                        if (cachedChunk != null) {
                            rawChunk.combine(cachedChunk);
                        }
                    }
                    rawChunk.apply(chunk);
                    return chunk;
                });
    }
}
