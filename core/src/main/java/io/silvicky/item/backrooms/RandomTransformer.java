package io.silvicky.item.backrooms;

import io.silvicky.item.common.Util;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.silvicky.item.cfg.JSONConfig.randomChunkRange;

public class RandomTransformer extends VecTransformer
{
    private final Map<ChunkPos,ChunkPos> s2cMap=new ConcurrentHashMap<>();
    private final Map<ChunkPos,ChunkPos> c2sMap=new ConcurrentHashMap<>();
    private ChunkPos lastS;
    public RandomTransformer(ServerPlayer player)
    {
        super(player);
        tick();
    }

    private void put(ChunkPos s,ChunkPos c)
    {
        remove(c);
        s2cMap.put(s,c);
        c2sMap.put(c,s);
    }
    private void remove(ChunkPos c)
    {
        if(c2sMap.containsKey(c))
        {
            ChunkPos oldS=c2sMap.remove(c);
            s2cMap.remove(oldS);
        }
    }
    private void request(ChunkPos c)
    {
        ChunkPos s;
        do
        {
            s = new ChunkPos(lastS.x() + getRandom(randomChunkRange), lastS.z() + getRandom(randomChunkRange));
        }
        while(c2sMap.containsValue(s));
        put(s,c);
    }
    private ChunkPos init(ChunkPos s)
    {
        if(c2sMap.containsValue(s))return s2cMap.get(s);
        ChunkPos c;
        do
        {
            c = new ChunkPos(s.x() + getRandom(randomChunkRange), s.z() + getRandom(randomChunkRange));
        }
        while(c2sMap.containsKey(c));
        put(s,c);
        return c;
    }
    @Override
    public Map<ChunkPos,ChunkPos> getS2c()
    {
        return Map.copyOf(s2cMap);
    }
    private void onChunkPosChanged()
    {
        ChunkPos newC=init(lastS);
        List<ChunkPos> updatedChunks=new ArrayList<>();
        for (ChunkPos c : c2sMap.keySet())
        {
            try
            {
                if ((Util.chunkPosDistance(c, newC) > 1
                        && !Util.isVisible(s2cTransform(player.getEyePosition()), player.getYRot(), c))
                        ||Util.chunkPosDistance(c, newC) > viewDistance+2)
                    updatedChunks.add(c);
            }
            catch (Exception e){throw new RuntimeException();}
        }
        for(ChunkPos c:updatedChunks)remove(c);
        for (int i = -viewDistance-2; i <= viewDistance+2; i++)
            for (int j = -viewDistance-2; j <= viewDistance+2; j++)
            {
                ChunkPos newBorder = new ChunkPos(newC.x() + i, newC.z() + j);
                if (!c2sMap.containsKey(newBorder))
                    request(newBorder);
            }
    }
    private void updateChunkPos()
    {
        ChunkPos newPos=player.chunkPosition();
        if(lastS==newPos)return;
        lastS=newPos;
        onChunkPosChanged();
    }
    @Override
    public void tick()
    {
        updateChunkPos();
        addLoadingTicket();
    }
    @Override
    public ChunkPos s2cTransform(ChunkPos pos) throws ChunkUnusedException
    {
        if(!s2cMap.containsKey(pos))throw new ChunkUnusedException();
        return s2cMap.get(pos);
    }
    @Override
    public ChunkPos c2sTransform(ChunkPos pos)
    {
        if(!c2sMap.containsKey(pos))
        {
            request(pos);
        }
        return c2sMap.get(pos);
    }
    private void addLoadingTicket()
    {
        for(ChunkPos pos:s2cMap.keySet())player.level().getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, pos, 2);
    }
}
