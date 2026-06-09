package io.silvicky.item.worldgen;

import net.minecraft.server.level.WorldGenRegion;

public interface BasicWorldGen
{
    void gen(WorldGenRegion level);
    String name();
}
