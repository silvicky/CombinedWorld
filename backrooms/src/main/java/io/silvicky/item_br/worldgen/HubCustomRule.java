package io.silvicky.item_br.worldgen;

import io.silvicky.item.worldgen.CustomRule;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.NonNull;

public class HubCustomRule implements CustomRule
{
    private static final int[][] dome={{0,1},{0,2},{0,3},{1,4},{1,5},{2,6},{3,7},{4,7},{5,8},{6,8},{7,8}};
    @Override
    public void gen(@NonNull ChunkAccess chunk)
    {
        if(chunk.getPos().x()!=0)return;
        for(int z=0;z<16;z++)
        {
            for (int[] ints : dome)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(ints[0], ints[1], z), Blocks.STONE.defaultBlockState());
                chunk.setBlockState(chunk.getPos().getBlockAt(14 - ints[0], ints[1], z), Blocks.STONE.defaultBlockState());
                chunk.setBlockState(chunk.getPos().getBlockAt(ints[0], ints[1]+1, z), Blocks.STONE_SLAB.defaultBlockState());
                chunk.setBlockState(chunk.getPos().getBlockAt(14 - ints[0], ints[1]+1, z), Blocks.STONE_SLAB.defaultBlockState());
            }
            for(int i : new int[]{1,2,12,13})
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(i, 1, z), Blocks.STONE_SLAB.defaultBlockState());
            }
            chunk.setBlockState(chunk.getPos().getBlockAt(3, 0, z), Blocks.WHITE_CONCRETE.defaultBlockState());
            chunk.setBlockState(chunk.getPos().getBlockAt(11, 0, z), Blocks.WHITE_CONCRETE.defaultBlockState());
            for(int i=4;i<=10;i++)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(i, 0, z), Blocks.BLACK_CONCRETE.defaultBlockState());
            }
            if(z%4==0)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(4, 7, z), Blocks.GLOWSTONE.defaultBlockState());
                chunk.setBlockState(chunk.getPos().getBlockAt(10, 7, z), Blocks.GLOWSTONE.defaultBlockState());
            }
            if(z%8<4)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(7, 0, z), Blocks.WHITE_CONCRETE.defaultBlockState());
            }
        }
    }

    @Override
    public String name()
    {
        return "hub";
    }
}
