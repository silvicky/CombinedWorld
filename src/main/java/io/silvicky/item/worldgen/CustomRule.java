package io.silvicky.item.worldgen;

import net.minecraft.server.level.WorldGenRegion;

public interface CustomRule
{
    void gen(WorldGenRegion level);
    String name();
}
