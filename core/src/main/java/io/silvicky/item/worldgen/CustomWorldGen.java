package io.silvicky.item.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class CustomWorldGen extends ChunkGenerator
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

    private void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        worldGen.gen(chunk,randomState);
    }

    @Override
    protected @NonNull MapCodec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }

    @Override
    public void spawnOriginalMobs(@NonNull WorldGenRegion worldGenRegion)
    {

    }

    @Override
    public int getGenDepth()
    {
        return 64;
    }

    @Override
    public @NonNull CompletableFuture<ChunkAccess> buildTerrain(@NonNull ChunkAccess centerChunk, @NonNull Blender blender, @NonNull RandomState randomState, @NonNull StructureManager structureManager, @NonNull BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion, @NonNull Set<Holder<Biome>> possibleBiomes)
    {
        gen(centerChunk, randomState);
        return CompletableFuture.completedFuture(centerChunk);
    }

    @Override
    public int getSeaLevel()
    {
        return 0;
    }

    @Override
    public int getMinY()
    {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.@NonNull Types type, @NonNull LevelHeightAccessor heightAccessor, @NonNull RandomState randomState)
    {
        return heightAccessor.getHeight();
    }

    @Override
    public @NonNull NoiseColumn getBaseColumn(int x, int z, @NonNull LevelHeightAccessor heightAccessor, @NonNull RandomState randomState)
    {
        BlockState[] column = new BlockState[heightAccessor.getHeight()];

        for (int i = 0; i < column.length; i++) {
            column[i] = Blocks.AIR.defaultBlockState();
        }

        return new NoiseColumn(0, column);
    }

    @Override
    public void addDebugScreenInfo(@NonNull List<String> result, @NonNull RandomState randomState, @NonNull BlockPos feetPos, @NonNull SamplerContext samplerContext)
    {

    }

    @Override
    public void applyBiomeDecoration(final @NonNull WorldGenLevel level, final @NonNull ChunkAccess chunk, final @NonNull StructureManager structureManager)
    {

    }
}
