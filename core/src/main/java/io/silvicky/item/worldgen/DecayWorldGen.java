package io.silvicky.item.worldgen;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DecayWorldGen extends ChunkGenerator
{
    public static final MapCodec<DecayWorldGen> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ChunkGenerator.CODEC.fieldOf("base").forGetter(generator -> generator.baseGen),
                    Codec.STRING.xmap(WorldGens.decayRuleMap::get, DecayRule::name).fieldOf("settings").forGetter(generator -> generator.rule)
            ).apply(instance, instance.stable(DecayWorldGen::new)));

    private final ChunkGenerator baseGen;

    private final DecayRule rule;

    public DecayWorldGen(ChunkGenerator baseGen, DecayRule rule)
    {
        super(baseGen.getBiomeSource());
        this.baseGen=baseGen;
        this.rule=rule;
    }

    public RandomState noise(RegistryAccess registryAccess, long levelSeed)
    {
        if(baseGen instanceof NoiseBasedChunkGenerator generator)
        {
            return RandomState.create(registryAccess.lookupOrThrow(Registries.NOISE), levelSeed, generator.generatorSettings().value());
        }
        if(baseGen instanceof DecayWorldGen decayWorldGen)
        {
            return decayWorldGen.noise(registryAccess, levelSeed);
        }
        return RandomState.create(registryAccess.lookupOrThrow(Registries.NOISE), levelSeed, false, Blocks.STONE.defaultBlockState(), 63, NoiseRouterData.none());
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
    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(@NonNull ServerLevel level, @NonNull HolderSet<Structure> wantedStructures, @NonNull BlockPos pos, int maxSearchRadius, boolean createReference)
    {
        return baseGen.findNearestMapStructure(level, wantedStructures, pos, maxSearchRadius, createReference);
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
    public @NonNull WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(@NonNull Level level, @NonNull StructureManager structureManager, @NonNull MobCategory mobCategory, @NonNull BlockPos pos)
    {
        return baseGen.getMobsAt(level, structureManager, mobCategory, pos);
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
    public @NonNull CompletableFuture<ChunkAccess> buildTerrain(@NonNull ChunkAccess centerChunk, @NonNull Blender blender, @NonNull RandomState randomState, @NonNull StructureManager structureManager, @NonNull BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion, @NonNull Set<Holder<Biome>> possibleBiomes)
    {
        if(rule.decay(centerChunk, randomState))return CompletableFuture.completedFuture(centerChunk);
        return baseGen.buildTerrain(centerChunk,blender,randomState,structureManager,biomeManager,carverBiomeRegion,possibleBiomes);
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
    public void addDebugScreenInfo(@NonNull List<String> result, @NonNull RandomState randomState, @NonNull BlockPos feetPos, @NonNull SamplerContext samplerContext)
    {
        baseGen.addDebugScreenInfo(result,randomState,feetPos,samplerContext);
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
