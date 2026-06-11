package io.silvicky.item.worldgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class ExampleCustomRule implements CustomRule
{
    @Override
    public void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        chunk.setBlockState(chunk.getPos().getBlockAt(8,0,8), Blocks.GLOWSTONE.defaultBlockState());
    }

    @Override
    public String name()
    {
        return "example";
    }
}
