package io.silvicky.item_br.worldgen.abyss;

import io.silvicky.item.backrooms.AbyssPattern;
import io.silvicky.item.backrooms.AbyssPatterns;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * The two demos: a five-layer stack, and level 922's endless spiral.
 *
 * <p>Both are just {@link AbyssPattern}s - an ordered list of bands - plus the levels they name. The window
 * itself knows nothing about either of them.
 *
 * <h2>Stack</h2>
 *
 * <p>Five acyclic bands, one per layer. Travel is bounded at both ends, and every transit changes level, so
 * each slide re-sends the view.
 *
 * <h2>Spiral</h2>
 *
 * <p>One cyclic band naming {@code backrooms:level922}. What makes it endless is not a long list of bands but
 * the fact that the level's terrain repeats every 64 blocks: the composition for slot 0, 1 and 2 is the same
 * picture, so when the window slides nothing on the client needs to change - an unbroken descent costs one
 * position packet per period and no chunk traffic at all.
 *
 * <p>That also means the pattern is only correct if the geometry really is 64-periodic. {@link SpiralRule}
 * generates it that way; a level that does not repeat would show a break at every seam.
 *
 * @see io.silvicky.item.command.world.StagedDimensions
 */
public final class AbyssWorlds implements StagedDimensions.Provider
{
    /** The 64-tall type every band uses. Registered by {@code data/silvicky/dimension_type/abyss_layer.json}. */
    private static final Identifier LAYER_TYPE = Identifier.parse("silvicky:abyss_layer");

    /** The five-layer stack. */
    public static final String STACK = "abyss:stack";
    /** The same five layers, with layer 4 opening back into layer 0. */
    public static final String CYCLE = "abyss:cycle";
    /** The endless spiral. */
    public static final String SPIRAL = "backrooms:spiral";

    /** The spiral's level. */
    public static final Identifier SPIRAL_LEVEL = Identifier.parse("backrooms:level922");

    public AbyssWorlds() {}

    /** Registers the patterns. Called from the mod initialiser, before any world exists. */
    public static void registerPatterns()
    {
        List<AbyssPattern.Band> stack = new ArrayList<>(AbyssWindow.WINDOW_LAYERS + 2);
        List<AbyssPattern.Band> cycle = new ArrayList<>(AbyssWindow.WINDOW_LAYERS + 2);
        for (int i = 0; i < 5; i++)
        {
            stack.add(new AbyssPattern.Band(levelKey("abyss", "layer" + i), 0));
            cycle.add(new AbyssPattern.Band(levelKey("abyss_cycle", "layer" + i), 0));
        }
        AbyssPatterns.register(new AbyssPattern(STACK, AbyssWindow.BAND_HEIGHT, false, stack));
        //Same shape, cyclic: band 4 is followed by band 0 again, forever.
        AbyssPatterns.register(new AbyssPattern(CYCLE, AbyssWindow.BAND_HEIGHT, true, cycle));

        AbyssPatterns.register(new AbyssPattern(SPIRAL, AbyssWindow.BAND_HEIGHT, true,
                List.of(new AbyssPattern.Band(levelKey(SPIRAL_LEVEL.getNamespace(), SPIRAL_LEVEL.getPath()), 0))));
    }

    private static ResourceKey<net.minecraft.world.level.Level> levelKey(String namespace, String path)
    {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(namespace, path));
    }

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
            LOGGER.error("[abyss] cannot resolve {} or minecraft:the_void; the demo levels are skipped", LAYER_TYPE, e);
            return;
        }

        int added = 0;
        for (int i = 0; i < 5; i++)
            added += add(dimensions, type, voidBiome, "abyss", "layer" + i, "abyss_layer_" + i);
        //The cyclic stack needs its own levels: the wrap is built into the geometry (layer 4 has an open
        //ceiling, layer 0 a floor with a shaft), and the acyclic stack's layers deliberately do not.
        for (int i = 0; i < 5; i++)
            added += add(dimensions, type, voidBiome, "abyss_cycle", "layer" + i, "abyss_cycle_layer_" + i);
        added += add(dimensions, type, voidBiome, SPIRAL_LEVEL.getNamespace(), SPIRAL_LEVEL.getPath(), "abyss_spiral");

        if (added > 0) LOGGER.info("[abyss] staged {} demo levels in {}; restart once to create them", added, levelPath);
    }

    private static int add(Map<ResourceKey<LevelStem>, LevelStem> dimensions, Holder<DimensionType> type,
                           Holder<Biome> biome, String namespace, String path, String ruleName)
    {
        ResourceKey<LevelStem> key = ResourceKey.create(Registries.LEVEL_STEM,
                Identifier.fromNamespaceAndPath(namespace, path));
        //The world's own dimensions win, which is also what makes this idempotent on every later save.
        if (dimensions.containsKey(key)) return 0;
        CustomRule rule = WorldGens.worldGenMap.get(ruleName);
        if (rule == null)
        {
            LOGGER.error("[abyss] worldgen rule {} is not registered", ruleName);
            return 0;
        }
        dimensions.put(key, new LevelStem(type, new CustomWorldGen(new FixedBiomeSource(biome), rule)));
        return 1;
    }
}
