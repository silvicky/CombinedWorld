package io.silvicky.item_br.worldgen;

import io.silvicky.item.worldgen.CustomRule;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class Level0CustomRule implements CustomRule
{
    private static final Identifier key=Identifier.parse("silvicky:level_0");
    @Override
    public void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        int cx=chunk.getPos().x();
        int cz=chunk.getPos().z();
        RandomSource random=randomState.getOrCreateRandomFactory(key).at(cx,0,cz);
        int type = (cx ^ cz ^ random.nextInt()) & 63;
        //Ceiling
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(x, 20, z), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                chunk.setBlockState(chunk.getPos().getBlockAt(x, 21, z), Blocks.WHITE_CARPET.defaultBlockState());
            }
        //Light
        for (int x = 0; x < 4; x++)
            for (int z = 0; z < 4; z++)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(x * 4 + 3, 20, z * 4 + 2), Blocks.OCHRE_FROGLIGHT.defaultBlockState());
                chunk.setBlockState(chunk.getPos().getBlockAt(x * 4 + 2, 20, z * 4 + 2), Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            }
        //Common walls
        int xw=random.nextInt(3)+2;
        int zw=random.nextInt(3)+2;
        int xr=random.nextInt(16-xw)+1;
        int zr=random.nextInt(16-zw)+1;
        for (int y = 0; y < 4; y++)
        {
            for (int x = 0; x < xr; x++)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(x, 16 + y, 0), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
            }
            for (int x = xr+xw; x < 16; x++)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(x, 16 + y, 0), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
            }
            for (int z = 0; z < zr; z++)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(0, 16 + y, z), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
            }
            for (int z = zr+zw; z < 16; z++)
            {
                chunk.setBlockState(chunk.getPos().getBlockAt(0, 16 + y, z), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
            }
        }
        //Random walls
        //TODO
        //Floor
        if (type == 0)
        {
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                {
                    if (x % 3 == 0 || z % 3 == 0) for (int y = 0; y <= 15; y++)
                        chunk.setBlockState(chunk.getPos().getBlockAt(x, y, z), Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState());
                }
        }
        else
        {
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                {
                    chunk.setBlockState(chunk.getPos().getBlockAt(x, 15, z), Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState());
                }
        }
    }

    @Override
    public String name()
    {
        return "level_0";
    }
}
