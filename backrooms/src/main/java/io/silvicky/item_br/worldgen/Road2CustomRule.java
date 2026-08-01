package io.silvicky.item_br.worldgen;

import io.silvicky.item.worldgen.CustomRule;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class Road2CustomRule implements CustomRule
{
    private final ChunkGenCache chunkGenCache=new ChunkGenCache(0,256);

    @Override
    public void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        chunkGenCache.apply(chunk,randomState);
    }

    @Override
    public String name()
    {
        return "road2";
    }
}
