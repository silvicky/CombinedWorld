package io.silvicky.item.worldgen;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public interface CustomRule
{
    void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState);
    String name();
}
