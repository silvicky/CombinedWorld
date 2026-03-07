package io.silvicky.item.backrooms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

import static net.minecraft.util.Mth.floor;

public class VecTransformer
{
    //TODO tmp
    public static VecTransformer instance=new VecTransformer();
    private static final int tmp=21;
    public ChunkPos s2cTransform(ChunkPos pos)
    {
        return new ChunkPos(pos.x^tmp,pos.z^tmp);
    }
    public BlockPos s2cTransform(BlockPos pos)
    {
        ChunkPos chunkPos=new ChunkPos(pos);
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return pos.offset(transformerChunkPos.getWorldPosition()).offset(chunkPos.getWorldPosition().multiply(-1));
    }
    public SectionPos s2cTransform(SectionPos pos)
    {
        ChunkPos chunkPos=pos.chunk();
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return SectionPos.of(
                pos.getX()+transformerChunkPos.x-chunkPos.x,
                pos.getY(),
                pos.getZ()+transformerChunkPos.z-chunkPos.z);
    }
    public Vec3 s2cTransform(Vec3 pos)
    {
        BlockPos blockPos= BlockPos.containing(pos);
        BlockPos transformedBlockPos=s2cTransform(blockPos);
        return pos.add(Vec3.atLowerCornerOf(transformedBlockPos)).add(Vec3.atLowerCornerOf(blockPos.multiply(-1)));
    }
    public ChunkPos c2sTransform(ChunkPos pos)
    {
        return new ChunkPos(pos.x^tmp,pos.z^tmp);
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
        return ChunkTrackingView.isWithinDistance(i^tmp,j^tmp,k,l^tmp,m^tmp,bl);
    }
    public void addLoadingTicket(ServerLevel level, ServerPlayer player)
    {
        int d=level.getChunkSource().chunkMap.getPlayerViewDistance(player);
        ChunkPos pos=s2cTransform(player.chunkPosition());
        for(int i=-d;i<=d;i++)
            for(int j=-d;j<=d;j++)
                level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, c2sTransform(new ChunkPos(pos.x+i,pos.z+j)), 2);
    }
    public void forEachInChunkTrackingView(ChunkTrackingView.Positioned view,Consumer<ChunkPos> consumer)
    {
        int d=view.viewDistance()+1;
        ChunkPos pos=s2cTransform(view.center());
        for(int i=-d;i<=d;i++)
            for(int j=-d;j<=d;j++)
            {
                ChunkPos pos1=c2sTransform(new ChunkPos(pos.x + i, pos.z + j));
                if (view.contains(pos1))
                    consumer.accept(pos1);
            }
    }
    private boolean isChunkTracked(ServerPlayer serverPlayer, int i, int j) {
        return serverPlayer.getChunkTrackingView().contains(i, j) && !serverPlayer.connection.chunkSender.isPending(ChunkPos.asLong(i, j));
    }
    public boolean isChunkOnTrackedBorder(ServerPlayer serverPlayer, int i, int j) {
        if (this.isChunkTracked(serverPlayer, i, j))
        {
            for (int k = -1; k <= 1; k++)
            {
                for (int l = -1; l <= 1; l++)
                {
                    ChunkPos pos=s2cTransform(new ChunkPos(i,j));
                    ChunkPos pos1=c2sTransform(new ChunkPos(pos.x+k,pos.z+l));
                    if ((k != 0 || l != 0) && !this.isChunkTracked(serverPlayer, pos1.x,pos1.z))
                    {
                        return true;
                    }
                }
            }

        }
        return false;
    }
}
