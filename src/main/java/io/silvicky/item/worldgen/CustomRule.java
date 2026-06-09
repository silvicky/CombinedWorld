package io.silvicky.item.worldgen;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.NonNull;

public interface CustomRule
{
    void gen(@NonNull ChunkAccess chunk);
    String name();
}
