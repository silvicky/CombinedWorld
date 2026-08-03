package io.silvicky.item_br.worldgen;

import io.silvicky.item.worldgen.CustomRule;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class RoadCustomRule implements CustomRule
{
    private static final int rLarge=10000;
    private static final int rMedium=50;
    private static final int spaceShift=2;
    private static final int space=1<<spaceShift;
    private static final double threshold=0;
    public static boolean getNodeCoordination(RandomState random, int x, int z, boolean direction)
    {
        return true;
        /*double val;
        if(direction)val=random.getOrCreateNoise(Noises.CONTINENTALNESS).getValue(x*rLarge,0,z*rMedium);
        else val=random.getOrCreateNoise(Noises.CONTINENTALNESS).getValue(x*rMedium,rLarge,z*rLarge);
        return val>=threshold;*/
    }

    public static boolean[] getNodeCoordination(RandomState random, int x, int z)
    {
        boolean[] dir=new boolean[4];
        dir[0]=getNodeCoordination(random,x,z,false);
        dir[1]=getNodeCoordination(random,x,z,true);
        dir[2]=getNodeCoordination(random,x-1,z,false);
        dir[3]=getNodeCoordination(random,x,z-1,true);
        return dir;
    }
    @Override
    public void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        int cx=chunk.getPos().x();
        int cz=chunk.getPos().z();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlockState(chunk.getPos().getBlockAt(x,0,z),Blocks.CONCRETE.white().defaultBlockState());
        boolean bl=false;
        if(cx%space==0&&cz%space==0)
        {
            boolean[] dir = getNodeCoordination(randomState, cx>>spaceShift, cz>>spaceShift);
            if(!(dir[0]||dir[1]||dir[2]||dir[3]))bl=true;
            if (dir[0])
                chunk.setBlockState(chunk.getPos().getBlockAt(15, 1, 8), Blocks.CONCRETE.red().defaultBlockState());
            if (dir[1])
                chunk.setBlockState(chunk.getPos().getBlockAt(8, 1, 15), Blocks.CONCRETE.red().defaultBlockState());
            if (dir[2])
                chunk.setBlockState(chunk.getPos().getBlockAt(0, 1, 8), Blocks.CONCRETE.blue().defaultBlockState());
            if (dir[3])
                chunk.setBlockState(chunk.getPos().getBlockAt(8, 1, 0), Blocks.CONCRETE.blue().defaultBlockState());
        }
        else if(cx%space==0)
        {
            boolean dir=getNodeCoordination(randomState,cx>>spaceShift,cz>>spaceShift,true);
            if(dir)for(int z=0;z<16;z++)chunk.setBlockState(chunk.getPos().getBlockAt(8,1,z),Blocks.CONCRETE.yellow().defaultBlockState());
            else bl=true;
        }
        else if(cz%space==0)
        {
            boolean dir=getNodeCoordination(randomState,cx>>spaceShift,cz>>spaceShift,false);
            if(dir)for(int z=0;z<16;z++)chunk.setBlockState(chunk.getPos().getBlockAt(z,1,8),Blocks.CONCRETE.yellow().defaultBlockState());
            else bl=true;
        }
        else
        {
            bl=true;
        }
        if(bl)
        {
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlockState(chunk.getPos().getBlockAt(x,1,z),Blocks.CONCRETE.green().defaultBlockState());
        }
    }

    @Override
    public String name()
    {
        return "road";
    }
}
