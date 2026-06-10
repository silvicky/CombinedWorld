package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

import static io.silvicky.item.cfg.JSONConfig.*;

public class XorTransformer extends VecTransformer
{
    public XorTransformer(ServerPlayer player)
    {
        super(player);
        tick();
    }
    @Override
    public Map<ChunkPos,ChunkPos> getS2c()
    {
        ChunkPos center=player.chunkPosition();
        ChunkPos cc=s2cTransform(center);
        Map<ChunkPos,ChunkPos> s2c=new HashMap<>();
        for(int x=-viewDistance-2;x<=viewDistance+2;x++)
            for(int z=-viewDistance-2;z<=viewDistance+2;z++)
            {
                ChunkPos c=new ChunkPos(cc.x()+x,cc.z()+z);
                ChunkPos s=c2sTransform(c);
                s2c.put(s,c);
            }
        return Map.copyOf(s2c);
    }
    @Override
    public void tick()
    {
        addLoadingTicket();
    }
    @Override
    public ChunkPos s2cTransform(ChunkPos pos)
    {
        return new ChunkPos(pos.x()^xXor,pos.z()^zXor);
    }
    @Override
    public ChunkPos c2sTransform(ChunkPos pos)
    {
        return new ChunkPos(pos.x()^xXor,pos.z()^zXor);
    }
    private void addLoadingTicket()
    {
        for(ChunkPos pos:getS2c().keySet())player.level().getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, pos, 2);
    }
}
