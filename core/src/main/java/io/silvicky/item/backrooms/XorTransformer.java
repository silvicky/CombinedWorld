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
        //Same mapping as getS2c(), without materialising it. addLoadingTicket() runs from tick() for every
        //player every tick, and getS2c() would build and then copy two (2*viewDistance+5)^2 maps per call.
        ChunkPos center=player.chunkPosition();
        ChunkPos cc=s2cTransform(center);
        for(int x=-viewDistance-2;x<=viewDistance+2;x++)
            for(int z=-viewDistance-2;z<=viewDistance+2;z++)
            {
                player.level().getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL,c2sTransform(new ChunkPos(cc.x()+x,cc.z()+z)),2);
            }
    }
}
