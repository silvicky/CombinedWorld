package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

import static io.silvicky.item.cfg.JSONConfig.linearChunkRange;

/**
 * A constant translation: every chunk moves by the same {@code (xOffset, zOffset)}.
 *
 * <p>Because the shift is uniform, vanilla's containment and border tests already give the right answer on
 * the server coordinates - see {@link ChunkRemap} - so this dimension only needs two ints rather than a
 * per-chunk table. {@link #getS2c()} is retained for API compatibility but is never called by the
 * tracking-view path, which uses {@link #getRemap()}.
 */
public class LinearTransformer extends VecTransformer
{
    private final int xOffset;
    private final int zOffset;
    public LinearTransformer(ServerPlayer player)
    {
        super(player);
        xOffset=getRandom(linearChunkRange);
        zOffset=getRandom(linearChunkRange);
    }

    @Override
    public ChunkRemap getRemap()
    {
        return ChunkRemap.translation(xOffset,zOffset);
    }

    @Override
    public Map<ChunkPos, ChunkPos> getS2c()
    {
        //Kept for compatibility only; getRemap() is what the chunk-tracking path consumes.
        ChunkPos center=player.chunkPosition();
        Map<ChunkPos,ChunkPos> s2c=new HashMap<>();
        for(int x=-viewDistance-1;x<=viewDistance+1;x++)
            for(int z=-viewDistance-1;z<=viewDistance+1;z++)
            {
                ChunkPos s=new ChunkPos(center.x()+x,center.z()+z);
                s2c.put(s,s2cTransform(s));
            }
        return s2c;
    }

    @Override
    public void tick() {}

    @Override
    public ChunkPos s2cTransform(ChunkPos pos) {return new ChunkPos(pos.x()+xOffset,pos.z()+zOffset);}

    @Override
    public ChunkPos c2sTransform(ChunkPos pos) {return new ChunkPos(pos.x()-xOffset,pos.z()-zOffset);}
}
