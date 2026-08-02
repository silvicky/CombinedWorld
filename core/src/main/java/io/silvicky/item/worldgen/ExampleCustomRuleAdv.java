package io.silvicky.item.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class ExampleCustomRuleAdv implements CustomRuleAdv
{
    public static final MapCodec<ExampleCustomRuleAdv> CODEC= MapCodec.unit(ExampleCustomRuleAdv::new);

    public static final Identifier ID=Identifier.parse("silvicky:example");

    @Override
    public void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        chunk.setBlockState(chunk.getPos().getBlockAt(8,0,8), Blocks.GLOWSTONE.defaultBlockState());
    }

    @Override
    public MapCodec<? extends CustomRuleAdv> codec()
    {
        return CODEC;
    }
}
