package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public class RegionPos extends Point2
{
    public static final int regionBits = 2;//TODO

    public static final int regionSize = 1 << regionBits;

    public static final int chunkBits = 4;

    public static final int chunkSize = 1 << chunkBits;

    public RegionPos(int x, int z)
    {
        super(x, z);
    }

    public static RegionPos of(ChunkPos chunkPos)
    {
        return new RegionPos(chunkPos.x() >> regionBits, chunkPos.z() >> regionBits);
    }

    public static RegionPos of(BlockPos pos)
    {
        return new RegionPos(pos.getX() >> (regionBits + chunkBits), pos.getZ() >> (regionBits + chunkBits));
    }

    public BlockPos at(int x, int y, int z)
    {
        return new BlockPos(((this.x * chunkSize) << regionBits) + x, y, ((this.z * chunkSize) << regionBits) + z);
    }

    public RegionPos add(int x, int z)
    {
        return new RegionPos(this.x + x, this.z + z);
    }
}
