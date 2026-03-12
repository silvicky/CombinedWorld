package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

import static io.silvicky.item.cfg.JSONConfig.linearChunkRange;

public class LinearTransformer extends VecTransformer
{
    private final int xOffset;
    private final int zOffset;
    public LinearTransformer(ServerPlayer player)
    {
        super(player);
        xOffset=getRandom(linearChunkRange);
        zOffset=getRandom(linearChunkRange);
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
