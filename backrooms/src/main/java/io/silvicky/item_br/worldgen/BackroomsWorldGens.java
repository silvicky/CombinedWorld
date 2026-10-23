package io.silvicky.item_br.worldgen;

import io.silvicky.item.backrooms.AbyssWindow;
import io.silvicky.item.worldgen.WorldGens;
import io.silvicky.item_br.worldgen.abyss.AbyssDecayRule;
import io.silvicky.item_br.worldgen.abyss.AbyssLayerRule;
import io.silvicky.item_br.worldgen.abyss.AbyssWorlds;
import io.silvicky.item_br.worldgen.abyss.SpiralRule;
import io.silvicky.item_br.worldgen.road2.Road2CustomRule;

import static io.silvicky.item.worldgen.WorldGens.registerDecayRule;

public class BackroomsWorldGens
{
    public static void register()
    {
        WorldGens.registerWorldGen(new HubCustomRule());
        WorldGens.registerWorldGen(new Level0CustomRule());
        WorldGens.registerWorldGen(new RoadCustomRule());
        WorldGens.registerWorldGenRegistry(Road2CustomRule.ID , Road2CustomRule.CODEC);
        registerDecayRule(new AbyssDecayRule());
        //The Abyss demo: five layers, each its own generator rule, sharing the decay rule above for the shaft.
        for (int i = 0; i < 5; i++)
            WorldGens.registerWorldGen(new AbyssLayerRule(i));
        //The cyclic stack: the same five layers, but the last one opens its ceiling into the first one's floor,
        //because in a cyclic pattern band 4 is followed by band 0. Separate levels rather than a flag on the
        //stack, so the acyclic demo keeps its closed floors and ceilings - which is what stops it from being
        //travelled past its ends.
        for (int i = 0; i < 5; i++)
            WorldGens.registerWorldGen(new AbyssLayerRule("abyss_cycle_layer_" + i, i, i == 4));
        WorldGens.registerWorldGen(new SpiralRule());

        //The 1024-tall derivatives: the same three shapes at the height a real abyss wants. Separate rules rather
        //than a parameter in the worldgen settings, because the height is fixed when the level is created.
        int real = AbyssWorlds.REAL_BAND_HEIGHT;
        for (int i = 0; i < 5; i++)
            WorldGens.registerWorldGen(new AbyssLayerRule("abyss_layer_1024_" + i, i, false, real));
        for (int i = 0; i < 5; i++)
            WorldGens.registerWorldGen(new AbyssLayerRule("abyss_cycle_layer_1024_" + i, i, i == 4, real));
        WorldGens.registerWorldGen(new SpiralRule("abyss_spiral_1024", real));
    }
}
