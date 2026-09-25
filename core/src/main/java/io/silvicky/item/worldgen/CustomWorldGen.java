package io.silvicky.item.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class CustomWorldGen extends AbstractCustomWorldGen
{
    public static final MapCodec<CustomWorldGen> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                    Codec.STRING.xmap(WorldGens.worldGenMap::get, CustomRule::name).fieldOf("settings").forGetter(generator -> generator.worldGen)
            ).apply(instance, instance.stable(CustomWorldGen::new)));

    private final BiomeSource biomeSource;

    private final CustomRule worldGen;

    public CustomWorldGen(BiomeSource biomeSource, CustomRule worldGen)
    {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.worldGen = worldGen;
    }

    @Override
    protected @NonNull MapCodec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }

    @Override
    protected CompletableFuture<ChunkAccess> gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        worldGen.gen(chunk, randomState);
        return CompletableFuture.completedFuture(chunk);
    }
}
