package io.silvicky.item.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class CustomWorldGenAdv extends AbstractCustomWorldGen
{
    public static final MapCodec<CustomWorldGenAdv> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                    WorldGens.CUSTOM_RULE_ADV_CODEC.fieldOf("settings").forGetter(generator -> generator.worldGen)
            ).apply(instance, instance.stable(CustomWorldGenAdv::new)));

    private final BiomeSource biomeSource;

    private final CustomRuleAdv worldGen;

    public CustomWorldGenAdv(BiomeSource biomeSource, CustomRuleAdv worldGen)
    {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.worldGen = worldGen;
    }

    @Override
    protected void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        worldGen.gen(chunk,randomState);
    }

    @Override
    protected @NonNull MapCodec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }
}
