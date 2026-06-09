package io.silvicky.item.worldgen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class WorldGens
{
    public static final Map<String, BasicWorldGen> worldGenMap=new HashMap<>();
    public static void registerWorldGen(BasicWorldGen worldGen)
    {
        worldGenMap.put(worldGen.name(), worldGen);
    }
    public static void register()
    {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.parse("silvicky:custom"), WorldGenHolder.CODEC);
        registerWorldGen(new ExampleWorldGen());
    }
}
