package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;

/**
 * The identity transformer: every position maps to itself.
 *
 * <p>It is also the fallback for an unconfigured dimension and for a failed reflection, so this is the
 * overwhelmingly common case. It returns a null {@link #getS2c()}, which {@link #getRemap()} turns into
 * {@link ChunkRemap#IDENTITY} and makes every chunk-tracking injection in this mod a no-op, leaving vanilla
 * to run its own tracking view. That matters because the remap path is not free: it allocates an
 * {@code (2*viewDistance+5)^2} entry map per tracking-view update and walks it on every containment query.
 */
public class NopTransformer extends VecTransformer
{
    public NopTransformer(ServerPlayer player)
    {
        super(player);
    }

    @Override
    public Map<ChunkPos,ChunkPos> getS2c()
    {
        return null;
    }

    @Override
    public void tick() {}

    @Override
    public ChunkPos s2cTransform(ChunkPos pos) {return pos;}

    @Override
    public ChunkPos c2sTransform(ChunkPos pos) {return pos;}
}
