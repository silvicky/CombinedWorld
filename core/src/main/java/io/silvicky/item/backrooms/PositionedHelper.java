package io.silvicky.item.backrooms;

import io.silvicky.item.common.Util;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.function.Consumer;

public abstract class PositionedHelper
{
    /**
     * The remap for a tracking view, or {@link ChunkRemap#IDENTITY} when none is in effect - either the
     * identity transformer is in use, or {@code applyChunkTrackingView} has not populated the view yet.
     * An identity remap always means "use vanilla", never "no chunks".
     */
    public static ChunkRemap remapOf(ChunkTrackingView view)
    {
        return view instanceof PositionedAccess access?access.item_storage$getRemap():ChunkRemap.IDENTITY;
    }

    public static void forEachKey(ChunkTrackingView view, Consumer<ChunkPos> consumer)
    {
        ChunkRemap remap=remapOf(view);
        if(!remap.needsEnumeration()||!(view instanceof ChunkTrackingView.Positioned positioned))
        {
            view.forEach(consumer);
            return;
        }
        remap.forEach(positioned,consumer);
    }

    public static void differenceInChunkTrackingView(ChunkTrackingView.Positioned view, ChunkTrackingView.Positioned other, Consumer<ChunkPos> added, Consumer<ChunkPos> removed)
    {
        remapOf(view).difference(view,other,added,removed);
    }

    public static boolean isChunkTracked(ServerPlayer serverPlayer, int i, int j) {
        return serverPlayer.getChunkTrackingView().contains(i, j) && !serverPlayer.connection.chunkSender.isPending(ChunkPos.pack(i, j));
    }

    public static boolean isChunkOnTrackedBorder(ServerPlayer serverPlayer, int i, int j) {
        if (!isChunkTracked(serverPlayer, i, j))
        {
            return false;
        }
        ChunkRemap remap=remapOf(serverPlayer.getChunkTrackingView());
        if (!remap.needsEnumeration())
        {
            //Vanilla semantics, verified against ChunkMap#isChunkOnTrackedBorder: we are on the border
            //when any of the eight neighbours is not tracked. A constant offset preserves the distance
            //between chunks, so this is also the right answer for a translation.
            for (int dx=-1;dx<=1;dx++)for (int dz=-1;dz<=1;dz++)
            {
                if (dx==0&&dz==0)continue;
                if (!isChunkTracked(serverPlayer, i+dx, j+dz))return true;
            }
            return false;
        }
        try
        {
            Map<ChunkPos,ChunkPos> s2c=remap.map();
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
        return false;
    }
}
