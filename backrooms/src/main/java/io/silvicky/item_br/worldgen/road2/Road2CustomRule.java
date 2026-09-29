package io.silvicky.item_br.worldgen.road2;

import com.mojang.serialization.MapCodec;
import io.silvicky.item.worldgen.CustomRuleAdv;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Road2CustomRule implements CustomRuleAdv
{
    public static final MapCodec<Road2CustomRule> CODEC= MapCodec.unit(Road2CustomRule::new);

    public static final Identifier ID=Identifier.parse("silvicky:road2");

    /**
     * One manager per world. Every {@code ServerLevel} owns exactly one {@link RandomState} (reached through
     * {@code ChunkMap.randomState()}), and {@code RandomState} is {@code final} and does not override
     * {@code equals}, so it is a reliable per-level key that {@link #gen} is already handed.
     *
     * <p>This used to be a single non-volatile field guarded by {@code if (cache == null)}, which let two
     * threads publish half-built managers to one another and permanently captured whichever {@code RandomState}
     * won the race - wrong for every world but the first, and a second cold cache when it raced.
     */
    private static final Map<RandomState, Road2CacheManager> MANAGERS=new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<ChunkAccess> gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        return MANAGERS.computeIfAbsent(randomState,Road2CacheManager::new).generate(chunk);
    }

    /**
     * Releases every per-world region cache. Called when the server stops - otherwise this static map would
     * keep each level's {@link RandomState}, and so its whole {@code ChunkMap}, alive for the life of the JVM.
     */
    public static void clearCaches()
    {
        MANAGERS.clear();
    }

    @Override
    public MapCodec<? extends CustomRuleAdv> codec()
    {
        return CODEC;
    }
}
