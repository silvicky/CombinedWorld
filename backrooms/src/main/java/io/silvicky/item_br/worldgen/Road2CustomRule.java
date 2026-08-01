package io.silvicky.item_br.worldgen;

import io.silvicky.item.worldgen.CustomRule;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

public class Road2CustomRule implements CustomRule
{
    private final Map<Identifier, ChunkGenCache> caches = new HashMap<>();

    @Override
    public void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        ServerLevel level= (ServerLevel)(chunk.levelHeightAccessor);
        ChunkGenCache cache=caches.computeIfAbsent(level.dimension().identifier(),_->new ChunkGenCache(0,256,level,randomState));
        cache.apply(chunk);
    }

    @Override
    public String name()
    {
        return "road2";
    }
}
