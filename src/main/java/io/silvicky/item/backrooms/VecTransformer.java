package io.silvicky.item.backrooms;

import io.silvicky.item.common.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VecTransformer
{
    private static final Map<ServerPlayer,VecTransformer> instances=new WeakHashMap<>();
    public static VecTransformer getInstance(ServerPlayer player)
    {
        return instances.computeIfAbsent(player, VecTransformer::new);
    }
    public static void refreshInstance(ServerPlayer player)
    {
        instances.put(player, new VecTransformer(player));
    }
    public static final Vec3 INF=new Vec3(1e9,1e9,1e9);
    private static final int randomRange=7;
    private final ServerPlayer player;
    private final int viewDistance;
    private ChunkPos lastS;
    private final Map<ChunkPos,ChunkPos> s2cMap=new ConcurrentHashMap<>();
    private final Map<ChunkPos,ChunkPos> c2sMap=new ConcurrentHashMap<>();

    public VecTransformer(ServerPlayer player)
    {
        this.player=player;
        this.viewDistance = player.level().getChunkSource().chunkMap.getPlayerViewDistance(player);
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
    private final Random random=new Random();
    private int getRandom(){return random.nextInt(randomRange*2+1)-randomRange;}
    private void request(ChunkPos c)
    {
        //TODO
        ChunkPos s=new ChunkPos(c.x+getRandom(),c.z+getRandom());
        while(c2sMap.containsValue(s))s=new ChunkPos(c.x+getRandom(),c.z+getRandom());
        put(s,c);
    }
    private ChunkPos init(ChunkPos s)
    {
        if(c2sMap.containsValue(s))return s2cMap.get(s);
        ChunkPos c= new ChunkPos(s.x+getRandom(),s.z+getRandom());
        while(c2sMap.containsKey(c))c=new ChunkPos(s.x+getRandom(),s.z+getRandom());
        put(s,c);
        return c;
    }
    public Map<ChunkPos,ChunkPos> getS2c()
    {
        return Map.copyOf(s2cMap);
    }
    private void onChunkPosChanged(ChunkPos newS)
    {
        ChunkPos newC=init(newS);
        List<ChunkPos> updatedChunks=new ArrayList<>();
        for (ChunkPos c : c2sMap.keySet())
        {
            try
            {
                if ((Util.chunkPosDistance(c, newC) > 1
                        && !Util.isVisible(s2cTransform(player.getEyePosition()), player.getYRot(), c))
                ||Util.chunkPosDistance(c, newC) > viewDistance)
                    updatedChunks.add(c);
            }
            catch (Exception e){throw new RuntimeException();}
        }
        for(ChunkPos c:updatedChunks)remove(c);
        for (int i = -viewDistance-2; i <= viewDistance+2; i++)
            for (int j = -viewDistance-2; j <= viewDistance+2; j++)
            {
                ChunkPos newBorder = new ChunkPos(newC.x + i, newC.z + j);
                if (!c2sMap.containsKey(newBorder))
                    request(newBorder);
            }
        lastS = newS;
    }
    private void updateChunkPos()
    {
        ChunkPos newPos=player.chunkPosition();
        if(lastS==newPos)return;
        onChunkPosChanged(newPos);
    }
    public void tick()
    {
        updateChunkPos();
        addLoadingTicket();
    }
    public ChunkPos s2cTransform(ChunkPos pos) throws ChunkUnusedException
    {
        if(!s2cMap.containsKey(pos))throw new ChunkUnusedException();
        return s2cMap.get(pos);
    }
    public BlockPos s2cTransform(BlockPos pos) throws ChunkUnusedException
    {
        ChunkPos chunkPos=new ChunkPos(pos);
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return pos.offset(transformerChunkPos.getWorldPosition()).offset(chunkPos.getWorldPosition().multiply(-1));
    }
    public SectionPos s2cTransform(SectionPos pos) throws ChunkUnusedException
    {
        ChunkPos chunkPos=pos.chunk();
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return SectionPos.of(
                transformerChunkPos.x,
                pos.getY(),
                transformerChunkPos.z);
    }
    public Vec3 s2cTransform(Vec3 pos) throws ChunkUnusedException
    {
        BlockPos blockPos= BlockPos.containing(pos);
        BlockPos transformedBlockPos=s2cTransform(blockPos);
        return pos.add(Vec3.atLowerCornerOf(transformedBlockPos)).add(Vec3.atLowerCornerOf(blockPos.multiply(-1)));
    }
    public ChunkPos c2sTransform(ChunkPos pos)
    {
        if(!c2sMap.containsKey(pos))
        {
            request(pos);
        }
        return c2sMap.get(pos);
    }
    public BlockPos c2sTransform(BlockPos pos)
    {
        ChunkPos chunkPos=new ChunkPos(pos);
        ChunkPos transformerChunkPos=c2sTransform(chunkPos);
        return pos.offset(transformerChunkPos.getWorldPosition()).offset(chunkPos.getWorldPosition().multiply(-1));
    }
    public Vec3 c2sTransform(Vec3 pos)
    {
        BlockPos blockPos= BlockPos.containing(pos);
        BlockPos transformedBlockPos=c2sTransform(blockPos);
        return pos.add(Vec3.atLowerCornerOf(transformedBlockPos)).add(Vec3.atLowerCornerOf(blockPos.multiply(-1)));
    }
    private void addLoadingTicket()
    {
        for(ChunkPos pos:s2cMap.keySet())player.level().getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, pos, 2);
    }
}
