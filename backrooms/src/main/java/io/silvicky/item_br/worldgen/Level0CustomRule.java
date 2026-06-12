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
        int type = random.nextInt(256);
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
        if(type!=0)
        {
            int mode=random.nextInt(7);
            switch(mode)
            {
                case 0 ->//cross
                {
                    int crx= random.nextInt(11)+3;
                    int crz=random.nextInt(11)+3;
                    for(int y=0;y<4;y++)for(int x=3;x<=13;x++)
                    {
                        chunk.setBlockState(chunk.getPos().getBlockAt(crx, 16 + y, x), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
                        chunk.setBlockState(chunk.getPos().getBlockAt(x, 16 + y, crz), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
                    }
                }
                case 1 ->//x wall
                {
                    int xrb= random.nextInt(3)+11;
                    int xlb= random.nextInt(3)+3;
                    int crz=random.nextInt(11)+3;
                    for(int y=0;y<4;y++)for(int x=xlb;x<=xrb;x++)
                    {
                        chunk.setBlockState(chunk.getPos().getBlockAt(x, 16 + y, crz), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
                    }
                }
                case 2 ->//z wall
                {
                    int xrb= random.nextInt(3)+11;
                    int xlb= random.nextInt(3)+3;
                    int crz=random.nextInt(11)+3;
                    for(int y=0;y<4;y++)for(int x=xlb;x<=xrb;x++)
                    {
                        chunk.setBlockState(chunk.getPos().getBlockAt(crz, 16 + y, x), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
                    }
                }
                case 3 ->//x wall with a hole
                {
                    int xrw=random.nextInt(3)+2;
                    int xrr=random.nextInt(16-xrw)+1;
                    int crz=random.nextInt(11)+3;
                    for (int y = 0; y < 4; y++)
                    {
                        for (int x = 0; x < xrr; x++)
                        {
                            chunk.setBlockState(chunk.getPos().getBlockAt(x, 16 + y, crz), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
                        }
                        for (int x = xrr+xrw; x < 16; x++)
                        {
                            chunk.setBlockState(chunk.getPos().getBlockAt(x, 16 + y, crz), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
                        }
                    }
                }
                case 4 ->//z wall with a hole
                {
                    int xrw=random.nextInt(3)+2;
                    int xrr=random.nextInt(16-xrw)+1;
                    int crz=random.nextInt(11)+3;
                    for (int y = 0; y < 4; y++)
                    {
                        for (int x = 0; x < xrr; x++)
                        {
                            chunk.setBlockState(chunk.getPos().getBlockAt(crz, 16 + y, x), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
                        }
                        for (int x = xrr+xrw; x < 16; x++)
                        {
                            chunk.setBlockState(chunk.getPos().getBlockAt(crz, 16 + y, x), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
                        }
                    }
                }
                case 5 ->//matrix
                {
                    for(int y=0;y<4;y++)for(int x=0;x<5;x++)for(int z=0;z<5;z++)
                    {
                        chunk.setBlockState(chunk.getPos().getBlockAt(x*3+2, 16 + y, z*3+2), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
                    }
                }
                case 6 ->//none
                {}
            }
        }
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
