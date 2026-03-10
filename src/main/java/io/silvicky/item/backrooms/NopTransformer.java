package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public class NopTransformer extends VecTransformer
{
    public NopTransformer(ServerPlayer player)
    {
        super(player);
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
                ChunkPos c1=new ChunkPos(center.x+x,center.z+z);
                s2c.put(c1,c1);
            }
        return Map.copyOf(s2c);
    }

    @Override
    public void tick() {}

    @Override
    public ChunkPos s2cTransform(ChunkPos pos) {return pos;}

    @Override
    public ChunkPos c2sTransform(ChunkPos pos) {return pos;}
}
