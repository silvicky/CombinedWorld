package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LinearTransformer extends VecTransformer
{
    private static final int minX=-100;
    private static final int maxX=100;
    private static final int minZ=-100;
    private static final int maxZ=100;
    private final int xOffset;
    private final int zOffset;
    private static final Random random=new Random();
    public LinearTransformer(ServerPlayer player)
    {
        super(player);
        xOffset=minX+random.nextInt(maxX-minX+1);
        zOffset=minZ+random.nextInt(maxZ-minZ+1);
    }

    @Override
    public Map<ChunkPos, ChunkPos> getS2c()
    {
        //TODO ??
        ChunkPos center=player.chunkPosition();
        Map<ChunkPos,ChunkPos> s2c=new HashMap<>();
        for(int x=-viewDistance-2;x<=viewDistance+2;x++)
            for(int z=-viewDistance-2;z<=viewDistance+2;z++)
            {
                ChunkPos s=new ChunkPos(center.x+x,center.z+z);
                ChunkPos c=new ChunkPos(center.x+x+xOffset,center.z+z+zOffset);
                s2c.put(s,c);
            }
        return Map.copyOf(s2c);
    }

    @Override
    public void tick() {}

    @Override
    public ChunkPos s2cTransform(ChunkPos pos) {return new ChunkPos(pos.x+xOffset,pos.z+zOffset);}

    @Override
    public ChunkPos c2sTransform(ChunkPos pos) {return new ChunkPos(pos.x-xOffset,pos.z-zOffset);}
}
