package io.silvicky.item_br.worldgen;

import io.silvicky.item.worldgen.CustomRule;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class RoadCustomRule implements CustomRule
{
    private static final Identifier key=Identifier.parse("silvicky:road");
    private boolean getNodeCoordination(RandomState random, int x, int z, int direction)
    {
        return random.getOrCreateRandomFactory(key).at(x,direction,z).nextBoolean();
    }

    private boolean[] getNodeCoordination(RandomState random, int x, int z)
    {
        boolean[] dir=new boolean[4];
        dir[0]=getNodeCoordination(random,x,z,0);
        dir[1]=getNodeCoordination(random,x,z,1);
        dir[2]=getNodeCoordination(random,x-1,z,0);
        dir[3]=getNodeCoordination(random,x,z-1,1);
        return dir;
    }
    @Override
    public void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        int cx=chunk.getPos().x();
        int cz=chunk.getPos().z();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlockState(chunk.getPos().getBlockAt(x,0,z),Blocks.WHITE_CONCRETE.defaultBlockState());
        boolean bl=false;
        if(cx%2==0&&cz%2==0)
        {
            boolean[] dir = getNodeCoordination(randomState, cx>>1, cz>>1);
            if(!(dir[0]||dir[1]||dir[2]||dir[3]))bl=true;
            if (dir[0])
                chunk.setBlockState(chunk.getPos().getBlockAt(15, 1, 8), Blocks.RED_CONCRETE.defaultBlockState());
            if (dir[1])
                chunk.setBlockState(chunk.getPos().getBlockAt(8, 1, 15), Blocks.RED_CONCRETE.defaultBlockState());
            if (dir[2])
                chunk.setBlockState(chunk.getPos().getBlockAt(0, 1, 8), Blocks.BLUE_CONCRETE.defaultBlockState());
            if (dir[3])
                chunk.setBlockState(chunk.getPos().getBlockAt(8, 1, 0), Blocks.BLUE_CONCRETE.defaultBlockState());
        }
        else if(cx%2==0)
        {
            boolean dir=getNodeCoordination(randomState,cx>>1,cz>>1,1);
            if(dir)for(int z=0;z<16;z++)chunk.setBlockState(chunk.getPos().getBlockAt(8,1,z),Blocks.YELLOW_CONCRETE.defaultBlockState());
            else bl=true;
        }
        else if(cz%2==0)
        {
            boolean dir=getNodeCoordination(randomState,cx>>1,cz>>1,0);
            if(dir)for(int z=0;z<16;z++)chunk.setBlockState(chunk.getPos().getBlockAt(z,1,8),Blocks.YELLOW_CONCRETE.defaultBlockState());
            else bl=true;
        }
        else
        {
            bl=true;
        }
        if(bl)
        {
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlockState(chunk.getPos().getBlockAt(x,1,z),Blocks.GREEN_CONCRETE.defaultBlockState());
        }
    }

    @Override
    public String name()
    {
        return "road";
    }
}
