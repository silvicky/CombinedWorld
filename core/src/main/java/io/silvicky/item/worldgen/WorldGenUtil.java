package io.silvicky.item.worldgen;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;

public class WorldGenUtil
{
    public static RandomState getNoise(RegistryAccess registryAccess, long levelSeed, ChunkGenerator generator)
    {
        if(generator instanceof DecayWorldGen decayWorldGen)return decayWorldGen.noise(registryAccess, levelSeed);
        return RandomState.create(registryAccess.lookupOrThrow(Registries.NOISE), levelSeed, false, Blocks.STONE.defaultBlockState(), 63, NoiseRouterData.none());
    }
}
