package io.silvicky.item.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

/**
 * Advanced rule, new instance for each world and allows nearly everything in config
 */
public interface CustomRuleAdv
{
    void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState);

    MapCodec<? extends CustomRuleAdv> codec();
}
