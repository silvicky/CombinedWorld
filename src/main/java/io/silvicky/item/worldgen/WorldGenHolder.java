package io.silvicky.item.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WorldGenHolder extends ChunkGenerator
{
    public static final MapCodec<WorldGenHolder> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                    Codec.STRING.xmap(WorldGens.worldGenMap::get, BasicWorldGen::name).fieldOf("settings").forGetter(generator -> generator.worldGen)
            ).apply(instance, instance.stable(WorldGenHolder::new)));

    private final BiomeSource biomeSource;

    private final BasicWorldGen worldGen;

    public WorldGenHolder(BiomeSource biomeSource, BasicWorldGen worldGen)
    {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.worldGen = worldGen;
    }

    private void gen(WorldGenRegion level)
    {
        worldGen.gen(level);
    }

    @Override
    protected @NonNull MapCodec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }

    @Override
    public void applyCarvers(@NonNull WorldGenRegion region, long seed, @NonNull RandomState randomState, @NonNull BiomeManager biomeManager, @NonNull StructureManager structureManager, @NonNull ChunkAccess chunk)
    {

    }

    @Override
    public void buildSurface(@NonNull WorldGenRegion level, @NonNull StructureManager structureManager, @NonNull RandomState randomState, @NonNull ChunkAccess protoChunk)
    {
        gen(level);
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
    public @NonNull CompletableFuture<ChunkAccess> fillFromNoise(@NonNull Blender blender, @NonNull RandomState randomState, @NonNull StructureManager structureManager, @NonNull ChunkAccess centerChunk)
    {
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
    public void addDebugScreenInfo(@NonNull List<String> result, @NonNull RandomState randomState, @NonNull BlockPos feetPos)
    {

    }

    @Override
    public void applyBiomeDecoration(final @NonNull WorldGenLevel level, final @NonNull ChunkAccess chunk, final @NonNull StructureManager structureManager)
    {

    }
}
