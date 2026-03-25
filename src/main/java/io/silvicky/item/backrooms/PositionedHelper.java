package io.silvicky.item.backrooms;

import io.silvicky.item.common.Util;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class PositionedHelper
{
    public static boolean isWithinDistance(ChunkTrackingView.Positioned view, ChunkPos s, boolean bl)
    {
        try
        {
            Map<ChunkPos,ChunkPos> s2c=((PositionedAccess)(Object)view).item_storage$getS2cMap();
            ChunkPos c = s2c.get(s);
            ChunkPos lastC=s2c.get(view.center());
            return ChunkTrackingView.isWithinDistance(c.x(), c.z(), view.viewDistance(), lastC.x(), lastC.z(), bl);
        }
        catch (NullPointerException e){return false;}
    }

    private static Map<ChunkPos,ChunkPos> listChunkTrackingViewContent(ChunkTrackingView.Positioned view)
    {
        Map<ChunkPos,ChunkPos> src= ((PositionedAccess)(ChunkTrackingView)view).item_storage$getS2cMap();
        Map<ChunkPos,ChunkPos> ret=new HashMap<>();
        for(Map.Entry<ChunkPos,ChunkPos> entry:src.entrySet())if(isWithinDistance(view, entry.getKey(), true))ret.put(entry.getKey(),entry.getValue());
        return Map.copyOf(ret);
    }

    public static void forEachKey(ChunkTrackingView view, Consumer<ChunkPos> consumer)
    {
        if(view instanceof ChunkTrackingView.Positioned positioned)
        {
            for (ChunkPos pos : listChunkTrackingViewContent(positioned).keySet())
            {
                consumer.accept(pos);
            }
        }
        else view.forEach(consumer);
    }

    public static void differenceInChunkTrackingView(ChunkTrackingView.Positioned chunkTrackingView, ChunkTrackingView.Positioned chunkTrackingView2, Consumer<ChunkPos> consumer, Consumer<ChunkPos> consumer2)
    {
        Map<ChunkPos,ChunkPos> list=listChunkTrackingViewContent(chunkTrackingView);
        Map<ChunkPos,ChunkPos> list2=listChunkTrackingViewContent(chunkTrackingView2);
        for(ChunkPos i:list.keySet())if(!(list2.containsKey(i)&&list2.get(i).equals(list.get(i))))consumer2.accept(i);
        for(ChunkPos i:list2.keySet())if(!(list.containsKey(i)&&list.get(i).equals(list2.get(i))))consumer.accept(i);
    }

    public static boolean isChunkTracked(ServerPlayer serverPlayer, int i, int j) {
        return serverPlayer.getChunkTrackingView().contains(i, j) && !serverPlayer.connection.chunkSender.isPending(ChunkPos.pack(i, j));
    }

    public static boolean isChunkOnTrackedBorder(ServerPlayer serverPlayer, int i, int j) {
        if (isChunkTracked(serverPlayer, i, j))
        {
            try
            {
                Map<ChunkPos,ChunkPos> s2c=((PositionedAccess) serverPlayer.getChunkTrackingView()).item_storage$getS2cMap();
                ChunkPos pos = s2c.get(new ChunkPos(i, j));
                for(ChunkPos pos1:s2c.keySet())
                {
                    if (Util.chunkPosDistance(pos,s2c.get(pos1))==1 && !isChunkTracked(serverPlayer, pos1.x(), pos1.z()))
                    {
                        return true;
                    }
                }
            }
            catch (Exception ignored){}
        }
        return false;
    }
}
