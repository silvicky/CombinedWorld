package io.silvicky.item.worldgen;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public interface DecayRule
{
    boolean decay(@NonNull ChunkAccess chunk, @NonNull RandomState randomState);
    String name();
}
