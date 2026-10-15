package io.silvicky.item_br.worldgen;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * Blends pre-computed region caches into a chunk.
 *
 * <p>Region caches are computed <em>synchronously and exactly once</em> per region, on the calling thread.
 *
 * <p>This used to be a Caffeine {@code AsyncCache} plus {@code thenApplyAsync}, which cost two dispatches onto
 * {@link java.util.concurrent.ForkJoinPool#commonPool()} per chunk and then blocked on the result. Because
 * {@code AsyncCache.get(key, fn)} dispatches the mapping function itself, the "async" cache bought no
 * parallelism while capping all work at {@code availableProcessors() - 1} shared threads - that was the
 * "lock waiting" in spark. Minecraft already generates chunks on its own larger pool
 * ({@code Util.backgroundExecutor()}), so the extra hop only added latency.
 *
 * <p>There is deliberately no {@code expireAfterWrite}: {@link #getSourceRegions} returns the <em>lagging</em>
 * neighbours {@code (x-1,z)} and {@code (x,z-1)}, so a write TTL shorter than the time to sweep a region
 * column guaranteed that every region was re-generated when the next column asked for it.
 *
 * <p>Note that {@link Cache#get(Object, java.util.function.Function)} runs the mapping function inside a
 * {@code ConcurrentHashMap.compute} bin lock. That is fine and desirable here: it only runs on a miss, so it
 * de-duplicates concurrent requests for the same region instead of generating it twice, and the cost is
 * amortised over the ~1024 chunks in a region.
 */
public abstract class ChunkCacheManager<B extends AbstractBlock<B>, C extends ChunkGenCache<B>, K extends SimpleChunk<B>>
{
    /**
     * Measured with a region-shaped workload: about 0.55 MB of block data per region, so this is roughly
     * 0.55 GB. The previous HashMap-per-block layout was about 6.3 MB per region, i.e. 6+ GB at this size.
     * A sweep only needs a few hundred regions live at a time, so treat this as headroom and lower it if
     * the heap is tight.
     */
    private static final int cacheSize=1024;

    private static final long reportIntervalNanos=30_000_000_000L;

    protected final RandomState randomState;

    private final LongAdder regionHits=new LongAdder();
    private final LongAdder regionMisses=new LongAdder();
    private final LongAdder generateNanos=new LongAdder();
    private final LongAdder regionWaitNanos=new LongAdder();
    private final LongAdder chunkCount=new LongAdder();
    private final LongAdder chunkNanos=new LongAdder();
    private final LongAdder evictions=new LongAdder();
    private final AtomicLong maxGenerateNanos=new AtomicLong();
    private final AtomicLong lastReportNanos=new AtomicLong(System.nanoTime());

    private final Cache<RegionPos, C> cache = Caffeine.newBuilder()
            .maximumSize(cacheSize)
            .removalListener((RegionPos key, C value, RemovalCause cause) -> {
                if(cause.wasEvicted())evictions.increment();
            })
            .build();

    protected ChunkCacheManager(RandomState randomState) {
        this.randomState = randomState;
    }

    protected abstract C newCache(RegionPos regionPos);

    protected abstract K newChunk();

    protected abstract List<RegionPos> getSourceRegions(RegionPos regionPos);

    private C request(RegionPos pos)
    {
        C cached=cache.getIfPresent(pos);
        if(cached!=null)
        {
            regionHits.increment();
            return cached;
        }
        regionMisses.increment();
        long requestStart=System.nanoTime();
        long[] generated={-1};
        C computed=cache.get(pos,p->{
            long start=System.nanoTime();
            C result=newCache(p);
            result.generate();
            generated[0]=System.nanoTime()-start;
            return result;
        });
        long total=System.nanoTime()-requestStart;
        if(generated[0]>=0)
        {
            //We actually built it, rather than losing the miss race to another thread.
            generateNanos.add(generated[0]);
            maxGenerateNanos.accumulateAndGet(generated[0],Math::max);
        }else
        {
            //Another thread built it while we were on the miss path; this is the de-dup cost.
            regionWaitNanos.add(total);
        }
        return computed;
    }

    public CompletableFuture<ChunkAccess> generate(ChunkAccess chunk) {
        long start=System.nanoTime();
        ChunkPos chunkPos = chunk.getPos();
        List<RegionPos> sources = getSourceRegions(RegionPos.of(chunkPos));
        K rawChunk = newChunk();
        for (RegionPos source : sources) {
            SimpleChunk<B> cachedChunk = request(source).getChunk(chunkPos);
            if (cachedChunk != null) {
                rawChunk.combine(cachedChunk);
            }
        }
        rawChunk.apply(chunk);
        chunkNanos.add(System.nanoTime()-start);
        chunkCount.increment();
        report();
        return CompletableFuture.completedFuture(chunk);
    }

    private void report()
    {
        long now=System.nanoTime();
        long last=lastReportNanos.get();
        if(now-last<reportIntervalNanos)return;
        if(!lastReportNanos.compareAndSet(last,now))return;
        //LongAdder sums are not a consistent snapshot across counters, so treat these as approximate.
        long hits=regionHits.sum();
        long misses=regionMisses.sum();
        long requests=hits+misses;
        long chunks=chunkCount.sum();
        double hitRate=requests==0?0.0:100.0*hits/requests;
        double perMiss=misses==0?0.0:generateNanos.sum()/1e6/misses;
        double perChunk=chunks==0?0.0:chunkNanos.sum()/1e3/chunks;
        double requestsPerChunk=chunks==0?0.0:(double)requests/chunks;
        long retained=0;
        for (C value : cache.asMap().values()) retained += value.retainedBytes();
        LOGGER.debug("[region-cache] hit rate {}% ({}/{}), avg generate {} ms, worst generate {} ms, "
                        +"de-dup wait {} ms, {} requests/chunk, evictions {}, {} us/chunk, "
                        +"cached {}/{} holding {} MB",
                String.format("%.2f",hitRate),hits,requests,
                String.format("%.2f",perMiss),
                String.format("%.2f",maxGenerateNanos.get()/1e6),
                String.format("%.1f",regionWaitNanos.sum()/1e6),
                String.format("%.2f",requestsPerChunk),
                evictions.sum(),
                String.format("%.1f",perChunk),
                cache.estimatedSize(),cacheSize,
                retained/1048576);
    }
}
