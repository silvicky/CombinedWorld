package io.silvicky.item.backrooms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static net.minecraft.util.Mth.floor;

public class VecTransformer
{
    //TODO tmp
    public static Map<ServerPlayer,VecTransformer> instances=new WeakHashMap<>();
    public static VecTransformer getInstance(ServerPlayer player)
    {
        return instances.computeIfAbsent(player, VecTransformer::new);
    }
    public static Vec3 INF=new Vec3(1e9,1e9,1e9);
    private static final int tmp=21;
    private final ServerPlayer player;
    private final int viewDistance;
    private ChunkPos lastS;
    private final Map<ChunkPos,ChunkPos> s2cMap=new ConcurrentHashMap<>();
    private final Map<ChunkPos,ChunkPos> c2sMap=new ConcurrentHashMap<>();
    private final Queue<ChunkPos> pendingRemoval=new ArrayDeque<>();

    public VecTransformer(ServerPlayer player)
    {
        this.player=player;
        this.viewDistance = player.level().getChunkSource().chunkMap.getPlayerViewDistance(player);
        this.lastS=player.chunkPosition();
        init(lastS);
        onChunkPosChanged(player.chunkPosition());
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
            pendingRemoval.add(oldS);
        }
    }
    private void request(ChunkPos c)
    {
        //TODO
        put(new ChunkPos(c.x^tmp,c.z^tmp),c);
    }
    private ChunkPos init(ChunkPos s)
    {
        //TODO
        if(s2cMap.containsKey(s))return s2cMap.get(s);
        ChunkPos result= new ChunkPos(s.x^tmp,s.z^tmp);
        put(s,result);
        return result;
    }
    public static int chunkPosDistance(ChunkPos a,ChunkPos b)
    {
        return Math.max(Math.abs(a.x-b.x),Math.abs(a.z-b.z));
    }
    private void onChunkPosChanged(ChunkPos newS)
    {
        ChunkPos newC=init(newS);
        //TODO idk why it has to be here
        while(!pendingRemoval.isEmpty())
        {
            ChunkPos i=pendingRemoval.poll();
            if(!c2sMap.containsValue(i))s2cMap.remove(i);
        }
        for (ChunkPos i : c2sMap.keySet())
        {
            if (chunkPosDistance(i, newC) > viewDistance) remove(i);
            //if(!(i.equals(newC)||i.equals(lastC)))
        }
        for (int i = -viewDistance; i <= viewDistance; i++)
            for (int j = -viewDistance; j <= viewDistance; j++)
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
                pos.getX()+transformerChunkPos.x-chunkPos.x,
                pos.getY(),
                pos.getZ()+transformerChunkPos.z-chunkPos.z);
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
    public static boolean isCrossingChunkBorder(AABB box)
    {
        AABB box1=box.inflate(2.0E-5F);
        return !getChunkPos(box1.minX,box1.minZ).equals(getChunkPos(box1.maxX,box1.maxZ));
    }
    public static ChunkPos getChunkPos(double x, double z)
    {
        return new ChunkPos(floor(x)>>4,floor(z)>>4);
    }
    public boolean isWithinDistance(int i,int j,int k,int l,int m,boolean bl)
    {
        try
        {
            ChunkPos c1 = s2cTransform(new ChunkPos(i, j));
            ChunkPos c2 = s2cTransform(new ChunkPos(l, m));
            return ChunkTrackingView.isWithinDistance(c1.x, c1.z, k, c2.x, c2.z, bl);
        }
        catch (ChunkUnusedException e){return false;}
    }
    private void addLoadingTicket()
    {
        for(ChunkPos pos:s2cMap.keySet())player.level().getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, pos, 2);
    }
    private Set<ChunkPos> listChunkTrackingViewContent(ChunkTrackingView.Positioned view)
    {
        Set<ChunkPos> ret= Collections.synchronizedSet(new HashSet<>());
        try
        {
            int d = view.viewDistance() + 1;
            ChunkPos pos = s2cTransform(view.center());
            for (int i = -d; i <= d; i++)
                for (int j = -d; j <= d; j++)
                {
                    ChunkPos pos1 = c2sTransform(new ChunkPos(pos.x + i, pos.z + j));
                    if (view.contains(pos1))
                        ret.add(pos1);
                }
        }
        catch (Exception ignored){}
        return ret;
    }
    public void forEachInChunkTrackingView(ChunkTrackingView.Positioned view,Consumer<ChunkPos> consumer)
    {
        for(ChunkPos pos:listChunkTrackingViewContent(view))consumer.accept(pos);
    }
    public void differenceInChunkTrackingView(ChunkTrackingView.Positioned chunkTrackingView, ChunkTrackingView.Positioned chunkTrackingView2, Consumer<ChunkPos> consumer, Consumer<ChunkPos> consumer2)
    {
        Set<ChunkPos> list=listChunkTrackingViewContent(chunkTrackingView);
        Set<ChunkPos> list2=listChunkTrackingViewContent(chunkTrackingView2);
        for(ChunkPos i:list)if(!list2.contains(i))consumer2.accept(i);
        for(ChunkPos i:list2)if(!list.contains(i))consumer.accept(i);
    }
    private boolean isChunkTracked(ServerPlayer serverPlayer, int i, int j) {
        return serverPlayer.getChunkTrackingView().contains(i, j) && !serverPlayer.connection.chunkSender.isPending(ChunkPos.asLong(i, j));
    }
    public boolean isChunkOnTrackedBorder(ServerPlayer serverPlayer, int i, int j) {
        if (this.isChunkTracked(serverPlayer, i, j))
        {
            try
            {
                ChunkPos pos = s2cTransform(new ChunkPos(i, j));
                for (int k = -1; k <= 1; k++)
                {
                    for (int l = -1; l <= 1; l++)
                    {
                        ChunkPos pos1 = c2sTransform(new ChunkPos(pos.x + k, pos.z + l));
                        if ((k != 0 || l != 0) && !this.isChunkTracked(serverPlayer, pos1.x, pos1.z))
                        {
                            return true;
                        }
                    }
                }
            }
            catch (Exception ignored){}
        }
        return false;
    }
}
