package io.silvicky.item.backrooms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.util.Mth.floor;

public class VecTransformer
{
    //TODO tmp
    public static VecTransformer instance=new VecTransformer();
    private static final int tmp=9;
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
        return floor(box1.minX)>>4!=floor(box1.maxX)>>4
                ||floor(box1.minZ)>>4!=floor(box1.maxZ)>>4;
    }
    public static ChunkPos getChunkPos(double x, double z)
    {
        return new ChunkPos(floor(x)>>4,floor(z)>>4);
    }
}
