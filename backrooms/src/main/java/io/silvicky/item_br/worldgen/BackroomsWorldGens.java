package io.silvicky.item_br.worldgen;

import io.silvicky.item.worldgen.WorldGens;
import io.silvicky.item_br.worldgen.road2.Road2CustomRule;

public class BackroomsWorldGens
{
    public static void register()
    {
        WorldGens.registerWorldGen(new HubCustomRule());
        WorldGens.registerWorldGen(new Level0CustomRule());
        WorldGens.registerWorldGen(new RoadCustomRule());
        WorldGens.registerWorldGenRegistry(Road2CustomRule.ID , Road2CustomRule.CODEC);
    }
}
