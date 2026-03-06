package io.silvicky.item.backrooms;

import net.minecraft.util.math.*;

import static net.minecraft.util.math.MathHelper.floor;

public class VecTransformer
{
    //TODO tmp
    public static VecTransformer instance=new VecTransformer();
    public ChunkPos s2cTransform(ChunkPos pos)
    {
        return new ChunkPos(pos.x^1,pos.z^1);
    }
    public BlockPos s2cTransform(BlockPos pos)
    {
        ChunkPos chunkPos=new ChunkPos(pos);
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return pos.add(transformerChunkPos.getStartPos()).add(chunkPos.getStartPos().multiply(-1));
    }
    public ChunkSectionPos s2cTransform(ChunkSectionPos pos)
    {
        ChunkPos chunkPos=pos.toChunkPos();
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return ChunkSectionPos.from(
                pos.getX()+transformerChunkPos.x-chunkPos.x,
                pos.getY(),
                pos.getZ()+transformerChunkPos.z-chunkPos.z);
    }
    public Vec3d s2cTransform(Vec3d pos)
    {
        BlockPos blockPos=BlockPos.ofFloored(pos);
        BlockPos transformedBlockPos=s2cTransform(blockPos);
        return pos.add(Vec3d.of(transformedBlockPos)).add(Vec3d.of(blockPos.multiply(-1)));
    }
    public ChunkPos c2sTransform(ChunkPos pos)
    {
        return new ChunkPos(pos.x^1,pos.z^1);
    }
    public BlockPos c2sTransform(BlockPos pos)
    {
        ChunkPos chunkPos=new ChunkPos(pos);
        ChunkPos transformerChunkPos=c2sTransform(chunkPos);
        return pos.add(transformerChunkPos.getStartPos()).add(chunkPos.getStartPos().multiply(-1));
    }
    public Vec3d c2sTransform(Vec3d pos)
    {
        BlockPos blockPos=BlockPos.ofFloored(pos);
        BlockPos transformedBlockPos=c2sTransform(blockPos);
        return pos.add(Vec3d.of(transformedBlockPos)).add(Vec3d.of(blockPos.multiply(-1)));
    }
    public static boolean isCrossingChunkBorder(Box box)
    {
        Box box1=box.expand(2.0E-5F);
        return floor(box1.minX)>>4!=floor(box1.maxX)>>4
                ||floor(box1.minZ)>>4!=floor(box1.maxZ)>>4;
    }
    public static ChunkPos getChunkPos(double x,double z)
    {
        return new ChunkPos(floor(x)>>4,floor(z)>>4);
    }
}
