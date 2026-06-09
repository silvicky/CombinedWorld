package io.silvicky.item.worldgen;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Blocks;

public class ExampleWorldGen implements BasicWorldGen
{
    @Override
    public void gen(WorldGenRegion level)
    {
        level.setBlock(level.getCenter().getBlockAt(8,0,8), Blocks.GLOWSTONE.defaultBlockState(),16);
    }

    @Override
    public String name()
    {
        return "example";
    }
}
