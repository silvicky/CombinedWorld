package io.silvicky.item.worldgen;

import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public class WorldGenUtil
{
    public static NoiseGeneratorSettings getNoise(ChunkGenerator generator)
    {
        if(generator instanceof DecayWorldGen decayWorldGen)return decayWorldGen.noise();
        return NoiseGeneratorSettings.dummy();
    }
}
