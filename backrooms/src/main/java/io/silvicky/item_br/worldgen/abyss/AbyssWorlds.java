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
    /**
     * The height the demo levels are generated at, which is also the band height their patterns use.
     *
     * <p>It is a property of these levels - {@code silvicky:abyss_layer} is 64 tall - and no longer of the window,
     * which takes its height from whichever pattern is in force. A world with a different band height needs levels of
     * that height and a pattern that says so; this constant is only the demo's.
     */
    public static final int DEMO_BAND_HEIGHT = 64;

    /**
     * The height the 1024 derivatives are generated at.
     *
     * <p>Each demo pattern has a derivative at this height, reading from its own levels. The number is what a real
     * abyss wants - a layer deep enough to fall through - and, at three bands, it is also close to the largest that
     * fits: {@code AbyssGeometry.maxBandHeight()} is 1344, because a window may span -2032..2032.
     */
    public static final int REAL_BAND_HEIGHT = 1024;

    /** The 64-tall type every band uses. Registered by {@code data/silvicky/dimension_type/abyss_layer.json}. */
    private static final Identifier LAYER_TYPE = Identifier.parse("silvicky:abyss_layer");

    /** The 1024-tall type the derivatives use; registered by {@code abyss_layer_1024.json}. */
    private static final Identifier LAYER_TYPE_1024 = Identifier.parse("silvicky:abyss_layer_1024");

    /** The five-layer stack. */
    public static final String STACK = "abyss:stack";
    /** The same five layers, with layer 4 opening back into layer 0. */
    public static final String CYCLE = "abyss:cycle";
    /** The endless spiral. */
    public static final String SPIRAL = "backrooms:spiral";

    /** The stack again, at {@link #REAL_BAND_HEIGHT}. */
    public static final String STACK_1024 = "abyss:stack_1024";
    /** The cycle again, at {@link #REAL_BAND_HEIGHT}. */
    public static final String CYCLE_1024 = "abyss:cycle_1024";
    /** The spiral again, at {@link #REAL_BAND_HEIGHT}. */
    public static final String SPIRAL_1024 = "backrooms:spiral_1024";

    /** The key the 1024 derivatives are announced with, so F3 does not show the 64 one for them. */
    public static final Identifier WINDOW_DIMENSION_1024 = Identifier.parse("abyss:window_1024");
    /** The cyclic 1024 stack's key. */
    public static final Identifier WINDOW_DIMENSION_1024_CYCLE = Identifier.parse("abyss:cycling_1024");
    /** The 1024 spiral's key. */
    public static final Identifier WINDOW_DIMENSION_1024_SPIRAL = Identifier.parse("backrooms:level_922xxx_1024");

    /** The spiral's level. */
    public static final Identifier SPIRAL_LEVEL = Identifier.parse("backrooms:level922");
    /** The 1024 spiral's level. */
    public static final Identifier SPIRAL_LEVEL_1024 = Identifier.parse("backrooms:level922_1024");

    /** Namespace of the 1024 stack's levels; the cycle's is this plus {@code _cycle} on each level name. */
    public static final String STACK_1024_NAMESPACE = "abyss1024";
    public static final String CYCLE_1024_NAMESPACE = "abyss_cycle1024";

    public AbyssWorlds() {}

    /** Registers the patterns. Called from the mod initialiser, before any world exists. */
    public static void registerPatterns()
    {
        registerFamily(STACK, CYCLE, SPIRAL, DEMO_BAND_HEIGHT, "abyss", "abyss_cycle", SPIRAL_LEVEL,
                AbyssPatterns.DEFAULT_WINDOW_DIMENSION,
                Identifier.parse("abyss:cycling"),
                Identifier.parse("backrooms:level_922xxx"));
        //The same three shapes at 1024, reading from their own levels. A pattern's band height is part of its
        //geometry, so these are genuinely different worlds rather than the demo stretched: see AbyssGeometry.
        registerFamily(STACK_1024, CYCLE_1024, SPIRAL_1024, REAL_BAND_HEIGHT,
                STACK_1024_NAMESPACE, CYCLE_1024_NAMESPACE, SPIRAL_LEVEL_1024,
                WINDOW_DIMENSION_1024, WINDOW_DIMENSION_1024_CYCLE, WINDOW_DIMENSION_1024_SPIRAL);
    }

    /**
     * The three demo shapes at one band height.
     *
     * @param stackNamespace namespace of the five stack levels; levels are {@code layer0..4} in it
     * @param cycleNamespace the same for the cyclic five
     * @param keys           the window key each of the three is announced with; one per pattern, because
     *                       {@link AbyssPatterns#windowKeys()} emits one entry per pattern and two patterns sharing a
     *                       key would put it in the login level list twice
     */
    private static void registerFamily(String stackName, String cycleName, String spiralName, int height,
                                       String stackNamespace, String cycleNamespace, Identifier spiralLevel,
                                       Identifier... keys)
    {
        List<AbyssPattern.Band> stack = new ArrayList<>(AbyssWindow.WINDOW_LAYERS + 2);
        List<AbyssPattern.Band> cycle = new ArrayList<>(AbyssWindow.WINDOW_LAYERS + 2);
        for (int i = 0; i < 5; i++)
        {
            stack.add(new AbyssPattern.Band(levelKey(stackNamespace, "layer" + i), 0));
            cycle.add(new AbyssPattern.Band(levelKey(cycleNamespace, "layer" + i), 0));
        }
        AbyssPatterns.register(new AbyssPattern(stackName, height, false, stack, keys[0]));
        //Same shape, cyclic: band 4 is followed by band 0 again, forever.
        AbyssPatterns.register(new AbyssPattern(cycleName, height, true, cycle, keys[1]));
        AbyssPatterns.register(new AbyssPattern(spiralName, height, true,
                List.of(new AbyssPattern.Band(levelKey(spiralLevel.getNamespace(), spiralLevel.getPath()), 0)),
                keys[2]));
    }

    private static ResourceKey<net.minecraft.world.level.Level> levelKey(String namespace, String path)
    {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(namespace, path));
    }

    @Override
    public void contribute(RegistryAccess access, Path levelPath, Map<ResourceKey<LevelStem>, LevelStem> dimensions)
    {
        Holder<Biome> voidBiome;
        Holder<DimensionType> type;
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

        added += contributeReal(access, dimensions, voidBiome);

        if (added > 0) LOGGER.info("[abyss] staged {} demo levels in {}; restart once to create them", added, levelPath);
    }

    /**
     * The 1024-tall derivatives: the same three shapes, each reading from levels generated at that height.
     *
     * <p>Separate levels rather than a taller view of the 64 ones, because a band's geometry has to agree with the
     * level it reads from - a 1024-band cannot take its slice from a 64-tall level. Missing the 1024 dimension type is
     * not fatal: the 64 demos are staged anyway and the derivatives are simply absent, which is the same level of
     * service a missing rule gets.
     */
    private static int contributeReal(RegistryAccess access,
                                      Map<ResourceKey<LevelStem>, LevelStem> dimensions, Holder<Biome> biome)
    {
        Holder<DimensionType> type;
        try
        {
            type = access.lookupOrThrow(Registries.DIMENSION_TYPE)
                         .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, LAYER_TYPE_1024));
        }
        catch (Exception e)
        {
            LOGGER.error("[abyss] cannot resolve {}; the {} levels are skipped", LAYER_TYPE_1024, REAL_BAND_HEIGHT, e);
            return 0;
        }

        int added = 0;
        for (int i = 0; i < 5; i++)
            added += add(dimensions, type, biome, STACK_1024_NAMESPACE, "layer" + i, "abyss_layer_1024_" + i);
        for (int i = 0; i < 5; i++)
            added += add(dimensions, type, biome, CYCLE_1024_NAMESPACE, "layer" + i, "abyss_cycle_layer_1024_" + i);
        added += add(dimensions, type, biome, SPIRAL_LEVEL_1024.getNamespace(), SPIRAL_LEVEL_1024.getPath(),
                "abyss_spiral_1024");
        return added;
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
