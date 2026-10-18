package io.silvicky.item_br.worldgen.abyss;

import io.silvicky.item.backrooms.AbyssWindow;
import io.silvicky.item.command.world.StagedDimensions;
import io.silvicky.item.worldgen.CustomRule;
import io.silvicky.item.worldgen.CustomWorldGen;
import io.silvicky.item.worldgen.WorldGens;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import java.nio.file.Path;
import java.util.Map;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * The five demo layers, contributed to a save as it is written.
 *
 * <p>This is the hardcoded half of the demo. A real world would define these as datapack dimensions, or go
 * through {@code /createworld} with a {@code world_gen_settings.dat}; this one builds the stems in code so
 * nothing has to be authored by hand before trying it.
 *
 * <h2>Why this is a {@link StagedDimensions} provider and not a startup hook</h2>
 *
 * <p>The first version resolved the {@code DimensionType} and biome holders at {@code SERVER_STARTED} and put
 * them in {@code ImportWorld.newDimensions}. That is one world too early: a {@code Holder} belongs to one
 * registry instance, and a save is encoded against whichever {@code RegistryAccess} the server has at that
 * moment. The result was {@code Element ... is not valid in current registry set} on every save after a world
 * switch - five repetitions of two holders, one per layer.
 *
 * <p>Resolving them here, from the access handed to {@link #contribute}, makes that class of failure
 * impossible: the holders are taken from the very registry set the settings are about to be encoded with.
 *
 * <p>The stems become real levels on the next start - the same lifecycle {@code /importworld} has.
 */
public final class AbyssWorlds implements StagedDimensions.Provider
{
    /** The 1024-tall type every layer uses. Registered by {@code data/silvicky/dimension_type/abyss_layer.json}. */
    private static final Identifier LAYER_TYPE = Identifier.parse("silvicky:abyss_layer");

    @Override
    public void contribute(RegistryAccess access, Path levelPath, Map<ResourceKey<LevelStem>, LevelStem> dimensions)
    {
        Holder<DimensionType> type;
        Holder<Biome> voidBiome;
        try
        {
            type = access.lookupOrThrow(Registries.DIMENSION_TYPE)
                         .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, LAYER_TYPE));
            voidBiome = access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        }
        catch (Exception e)
        {
            //A world being written before its datapacks are known, or a pack without this mod's data. Either
            //way there is nothing to add, and saying so once per save is better than a silent absence.
            LOGGER.error("[abyss] cannot resolve {} or minecraft:the_void; the demo layers are skipped", LAYER_TYPE, e);
            return;
        }

        int added = 0;
        for (int i = 0; i < AbyssWindow.LAYERS; i++)
        {
            ResourceKey<LevelStem> key = ResourceKey.create(Registries.LEVEL_STEM,
                                                           Identifier.fromNamespaceAndPath("abyss", "layer" + i));
            //The world's own dimensions win: after the first save these are already here, which is also what
            //makes this idempotent on every later save.
            if (dimensions.containsKey(key)) continue;

            CustomRule rule = WorldGens.worldGenMap.get("abyss_layer_" + i);
            if (rule == null)
            {
                LOGGER.error("[abyss] worldgen rule abyss_layer_{} is not registered", i);
                return;
            }
            dimensions.put(key, new LevelStem(type, new CustomWorldGen(new FixedBiomeSource(voidBiome), rule)));
            added++;
        }
        if (added > 0) LOGGER.info("[abyss] staged {} demo layers in {}; restart once to create them", added, levelPath);
    }
}
