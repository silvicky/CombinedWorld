package io.silvicky.item.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class WorldGens
{
    public static final Map<String, CustomRule> worldGenMap=new HashMap<>();
    public static final Map<String, DecayRule> decayRuleMap=new HashMap<>();
    public static final WritableRegistry<MapCodec<? extends CustomRuleAdv>> worldGenRegistry=new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.parse("silvicky:world_gen_rules")) , Lifecycle.stable());
    public static final Codec<CustomRuleAdv> CUSTOM_RULE_ADV_CODEC = worldGenRegistry.byNameCodec().dispatchStable(CustomRuleAdv::codec, Function.identity());
    public static void registerWorldGen(CustomRule worldGen)
    {
        worldGenMap.put(worldGen.name(), worldGen);
    }
    public static void registerDecayRule(DecayRule worldGen)
    {
        decayRuleMap.put(worldGen.name(), worldGen);
    }
    public static void registerWorldGenRegistry(Identifier id, MapCodec<? extends CustomRuleAdv> registry)
    {
        worldGenRegistry.register(ResourceKey.create(worldGenRegistry.key(), id) , registry, RegistrationInfo.BUILT_IN);
    }
    public static void register()
    {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.parse("silvicky:custom"), CustomWorldGen.CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.parse("silvicky:custom_adv"), CustomWorldGenAdv.CODEC);
        registerWorldGen(new ExampleCustomRule());
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.parse("silvicky:decay"), DecayWorldGen.CODEC);
        registerDecayRule(new ExampleDecayRule());
        registerWorldGenRegistry(ExampleCustomRuleAdv.ID,ExampleCustomRuleAdv.CODEC);
    }
}
