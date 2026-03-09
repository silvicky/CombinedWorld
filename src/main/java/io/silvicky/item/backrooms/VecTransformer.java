package io.silvicky.item.backrooms;

import io.silvicky.item.helper.PositionedAccess;
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
    public static Map<ServerPlayer,VecTransformer> instances=new WeakHashMap<>();
    public static VecTransformer getInstance(ServerPlayer player)
    {
        return instances.computeIfAbsent(player, VecTransformer::new);
    }
    public static Vec3 INF=new Vec3(1e9,1e9,1e9);
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
    public static int chunkPosDistance(ChunkPos a,ChunkPos b)
    {
        return Math.max(Math.abs(a.x-b.x),Math.abs(a.z-b.z));
    }
    private void onChunkPosChanged(ChunkPos newS)
    {
        ChunkPos newC=init(newS);
        for (ChunkPos i : c2sMap.keySet())
        {
            if (chunkPosDistance(i, newC) > 1) remove(i);
            //if(!(i.equals(newC)||i.equals(lastC)))
        }
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
    public void init()
    {
        s2cMap.clear();
        c2sMap.clear();
        lastS=null;
        tick();
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
    public static boolean isCrossingChunkBorder(AABB box)
    {
        AABB box1=box.inflate(2.0E-5F);
        return !getChunkPos(box1.minX,box1.minZ).equals(getChunkPos(box1.maxX,box1.maxZ));
    }
    public static ChunkPos getChunkPos(double x, double z)
    {
        return new ChunkPos(floor(x)>>4,floor(z)>>4);
    }
    public boolean isWithinDistance(ChunkPos s,boolean bl)
    {
        try
        {
            ChunkPos c = s2cTransform(s);
            ChunkPos lastC=init(lastS);
            return ChunkTrackingView.isWithinDistance(c.x, c.z, viewDistance, lastC.x, lastC.z, bl);
        }
        catch (ChunkUnusedException e){return false;}
    }
    private void addLoadingTicket()
    {
        for(ChunkPos pos:s2cMap.keySet())player.level().getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, pos, 2);
    }
    private static Map<ChunkPos,ChunkPos> listChunkTrackingViewContent(ChunkTrackingView.Positioned view)
    {
        return ((PositionedAccess)(ChunkTrackingView)view).item_storage$getS2cMap();
    }
    public static void forEachKey(ChunkTrackingView view, Consumer<ChunkPos> consumer)
    {
        if(view instanceof ChunkTrackingView.Positioned positioned)
            for(ChunkPos pos:listChunkTrackingViewContent(positioned).keySet())consumer.accept(pos);
        else view.forEach(consumer);
    }
    public static void forEachValue(ChunkTrackingView view, Consumer<ChunkPos> consumer)
    {
        if(view instanceof ChunkTrackingView.Positioned positioned)
            for(ChunkPos pos:listChunkTrackingViewContent(positioned).values())consumer.accept(pos);
        else view.forEach(consumer);
    }
    public static void differenceInChunkTrackingView(ChunkTrackingView.Positioned chunkTrackingView, ChunkTrackingView.Positioned chunkTrackingView2, Consumer<ChunkPos> consumer, Consumer<ChunkPos> consumer2)
    {
        Map<ChunkPos,ChunkPos> list=listChunkTrackingViewContent(chunkTrackingView);
        Map<ChunkPos,ChunkPos> list2=listChunkTrackingViewContent(chunkTrackingView2);
        for(ChunkPos i:list.keySet())if(!(list2.containsKey(i)&&list2.get(i).equals(list.get(i))))consumer2.accept(i);
        for(ChunkPos i:list2.keySet())if(!(list.containsKey(i)&&list.get(i).equals(list2.get(i))))consumer.accept(i);
    }
    public boolean isChunkTracked(ServerPlayer serverPlayer, int i, int j) {
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
