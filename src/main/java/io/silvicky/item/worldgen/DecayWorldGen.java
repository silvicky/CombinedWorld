package io.silvicky.item.worldgen;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DecayWorldGen extends ChunkGenerator
{
    public static final MapCodec<DecayWorldGen> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ChunkGenerator.CODEC.fieldOf("base").forGetter(generator -> generator.baseGen)
            ).apply(instance, instance.stable(DecayWorldGen::new)));

    private final ChunkGenerator baseGen;

    public DecayWorldGen(ChunkGenerator baseGen)
    {
        super(baseGen.getBiomeSource());
        this.baseGen=baseGen;
    }

    @Override
    public void validate()
    {
        baseGen.validate();
    }

    @Override
    protected @NonNull MapCodec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }

    @Override
    public @NonNull ChunkGeneratorStructureState createState(@NonNull HolderLookup<StructureSet> structureSets, @NonNull RandomState randomState, long legacyLevelSeed)
    {
        return baseGen.createState(structureSets, randomState, legacyLevelSeed);
    }

    @Override
    public @NonNull Optional<Identifier> getTypeNameForDataFixer()
    {
        return baseGen.getTypeNameForDataFixer();
    }

    @Override
    public @NonNull CompletableFuture<ChunkAccess> createBiomes(@NonNull RandomState randomState, @NonNull Blender blender, @NonNull StructureManager structureManager, @NonNull ChunkAccess protoChunk)
    {
        return baseGen.createBiomes(randomState, blender, structureManager, protoChunk);
    }

    @Override
    public void applyCarvers(@NonNull WorldGenRegion region, long seed, @NonNull RandomState randomState, @NonNull BiomeManager biomeManager, @NonNull StructureManager structureManager, @NonNull ChunkAccess chunk)
    {
        baseGen.applyCarvers(region,seed,randomState,biomeManager,structureManager,chunk);
    }

    @Override
    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(@NonNull ServerLevel level, @NonNull HolderSet<Structure> wantedStructures, @NonNull BlockPos pos, int maxSearchRadius, boolean createReference)
    {
        return baseGen.findNearestMapStructure(level, wantedStructures, pos, maxSearchRadius, createReference);
    }

    @Override
    public void buildSurface(@NonNull WorldGenRegion level, @NonNull StructureManager structureManager, @NonNull RandomState randomState, @NonNull ChunkAccess protoChunk)
    {
        baseGen.buildSurface(level,structureManager,randomState,protoChunk);
    }

    @Override
    public void spawnOriginalMobs(@NonNull WorldGenRegion worldGenRegion)
    {
        baseGen.spawnOriginalMobs(worldGenRegion);
    }

    @Override
    public int getSpawnHeight(@NonNull LevelHeightAccessor heightAccessor)
    {
        return baseGen.getSpawnHeight(heightAccessor);
    }

    @Override
    public @NonNull BiomeSource getBiomeSource()
    {
        return baseGen.getBiomeSource();
    }

    @Override
    public int getGenDepth()
    {
        return baseGen.getGenDepth();
    }

    @Override
    public @NonNull WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(@NonNull Holder<Biome> biome, @NonNull StructureManager structureManager, @NonNull MobCategory mobCategory, @NonNull BlockPos pos)
    {
        return baseGen.getMobsAt(biome, structureManager, mobCategory, pos);
    }

    @Override
    public void createStructures(@NonNull RegistryAccess registryAccess, @NonNull ChunkGeneratorStructureState state, @NonNull StructureManager structureManager, @NonNull ChunkAccess centerChunk, @NonNull StructureTemplateManager structureTemplateManager, @NonNull ResourceKey<Level> level)
    {
        baseGen.createStructures(registryAccess, state, structureManager, centerChunk, structureTemplateManager, level);
    }

    @Override
    public void createReferences(@NonNull WorldGenLevel level, @NonNull StructureManager structureManager, @NonNull ChunkAccess centerChunk)
    {
        baseGen.createReferences(level, structureManager, centerChunk);
    }

    @Override
    public @NonNull CompletableFuture<ChunkAccess> fillFromNoise(@NonNull Blender blender, @NonNull RandomState randomState, @NonNull StructureManager structureManager, @NonNull ChunkAccess centerChunk)
    {
        return baseGen.fillFromNoise(blender,randomState,structureManager,centerChunk);
    }

    @Override
    public int getSeaLevel()
    {
        return baseGen.getSeaLevel();
    }

    @Override
    public int getMinY()
    {
        return baseGen.getMinY();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.@NonNull Types type, @NonNull LevelHeightAccessor heightAccessor, @NonNull RandomState randomState)
    {
        return baseGen.getBaseHeight(x, z, type, heightAccessor, randomState);
    }

    @Override
    public @NonNull NoiseColumn getBaseColumn(int x, int z, @NonNull LevelHeightAccessor heightAccessor, @NonNull RandomState randomState)
    {
        return baseGen.getBaseColumn(x, z, heightAccessor, randomState);
    }

    @Override
    public int getFirstFreeHeight(int x, int z, Heightmap.@NonNull Types type, @NonNull LevelHeightAccessor heightAccessor, @NonNull RandomState randomState)
    {
        return baseGen.getFirstFreeHeight(x, z, type, heightAccessor, randomState);
    }

    @Override
    public int getFirstOccupiedHeight(int x, int z, Heightmap.@NonNull Types type, @NonNull LevelHeightAccessor heightAccessor, @NonNull RandomState randomState)
    {
        return baseGen.getFirstOccupiedHeight(x, z, type, heightAccessor, randomState);
    }

    @Override
    public void addDebugScreenInfo(@NonNull List<String> result, @NonNull RandomState randomState, @NonNull BlockPos feetPos)
    {
        baseGen.addDebugScreenInfo(result,randomState,feetPos);
    }

    @Override
    public @NonNull BiomeGenerationSettings getBiomeGenerationSettings(@NonNull Holder<Biome> biome)
    {
        return baseGen.getBiomeGenerationSettings(biome);
    }

    @Override
    public void applyBiomeDecoration(final @NonNull WorldGenLevel level, final @NonNull ChunkAccess chunk, final @NonNull StructureManager structureManager)
    {
        baseGen.applyBiomeDecoration(level, chunk, structureManager);
    }
}
