package io.silvicky.item.backrooms;

import io.silvicky.item.StateSaver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class VecTransformer
{
    public static final Map<String,Class<? extends VecTransformer>> registry=Map.of(
            "nop", NopTransformer.class,
            "random", RandomTransformer.class,
            "linear", LinearTransformer.class,
            "xor", XorTransformer.class
    );
    /**
     * Per-player transformers, keyed by the player.
     *
     * <p>This has to be a concurrent map: {@link #getInstance} is called from the netty thread by
     * ConnectionMixin's packet hook on every inbound packet, and from the server thread by the chunk
     * tracking and collision mixins. A {@link WeakHashMap} there lost insertions under that race, so a
     * later lookup would build a <em>fresh</em> transformer, and a fresh one starts with empty state -
     *which silently stops remapping chunks, or draws new offsets and moves the player.
     *
     * <p>Weak keys cannot be used here either: every transformer holds a strong reference to its own
     * player, so the entries could never be collected. Entries are dropped on disconnect instead.
     */
    private static final Map<ServerPlayer,VecTransformer> instances=new ConcurrentHashMap<>();
    public static VecTransformer getInstance(ServerPlayer player)
    {
        return instances.computeIfAbsent(player, VecTransformer::getInstanceByDimension);
    }

    /**
     * Rebuilds a player's transformer, but only when the level they are on has actually changed.
     *
     * <p>Rebuilding is not free of consequences: a fresh transformer starts with an empty state, so the whole
     * view has to be re-allocated, and for a per-chunk mapping that means re-deriving the entire set of
     * positions. A session that rebuilt on every teleport accumulated a large multiple of that, because the
     * mod teleports constantly - respawn, server-side position corrections, and the void recovery chain all
     * route through {@code ServerGamePacketListenerImpl.teleport}.
     *
     * <p>A same-dimension teleport does not invalidate a chunk mapping. The mapping is keyed by chunk
     * position, and a teleport within a level does not change which chunks exist. Only a level change does,
     * which is the case the original comment on the call site was about.
     */
    public static void refreshInstance(ServerPlayer player)
    {
        VecTransformer existing=instances.get(player);
        if (existing!=null && existing.isOnSameLevelAs(player)) return;
        VecTransformer replacement=getInstanceByDimension(player);
        instances.put(player,replacement);
        if (existing!=null) existing.release();
    }
    public static void removeInstance(ServerPlayer player)
    {
        VecTransformer existing=instances.remove(player);
        if (existing!=null) existing.release();
    }
    private static VecTransformer getInstanceByDimension(ServerPlayer player)
    {
        try
        {
            return registry.getOrDefault(StateSaver.getServerState(player.level().getServer())
                            .chunkTransformer
                            .get(player.level().dimension.identifier()), NopTransformer.class)
                    .getConstructor(ServerPlayer.class)
                    .newInstance(player);
        }
        catch (Exception e){return new NopTransformer(player);}
    }
    private static final Random random=new Random();
    public static int getRandom(int randomRange){return random.nextInt(randomRange*2+1)-randomRange;}
    public static final Vec3 INF=new Vec3(1e9,1e9,1e9);
    final ServerPlayer player;
    final int viewDistance;
    /** The level this transformer was built for, so a same-level teleport can reuse it. */
    final Object level;
    public VecTransformer(ServerPlayer player)
    {
        this.player=player;
        this.level=player.level();
        this.viewDistance = player.level().getChunkSource().chunkMap.getPlayerViewDistance(player);
    }

    /**
     * @return the per-chunk server-to-client mapping, or null when no remapping applies.
     */
    public abstract Map<ChunkPos,ChunkPos> getS2c();
    public abstract void tick();

    /**
     * Whether this transformer is still valid for the level the player is on now.
     *
     * <p>A mapping is anchored to chunks, and chunks do not change identity when a player is teleported
     * within a level, so a same-level teleport leaves it intact. A level change does not: the positions in
     * the map refer to chunks in a level the player has left.
     */
    boolean isOnSameLevelAs(ServerPlayer player)
    {
        return this.level==player.level();
    }

    /**
     * Gives up anything this transformer holds outside the player's own session, so that a discarded
     * instance stops pinning server chunks. Subclasses that allocate level tickets must override this.
     */
    void release() {}

    /**
     * How this transformer's mapping should be represented, and the only thing the chunk-tracking path
     * consults. The default wraps {@link #getS2c()}, so an identity transformer that returns null lands on
     * {@link ChunkRemap#IDENTITY} and every chunk-tracking injection becomes a no-op. A transformer whose
     * mapping is a constant offset should override this with {@link ChunkRemap#translation} so no
     * per-chunk table is ever materialised.
     */
    public ChunkRemap getRemap()
    {
        return ChunkRemap.mapping(getS2c());
    }
    public abstract ChunkPos s2cTransform(ChunkPos pos) throws ChunkUnusedException;
    public BlockPos s2cTransform(BlockPos pos) throws ChunkUnusedException
    {
        ChunkPos chunkPos=ChunkPos.containing(pos);
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return pos.offset(transformerChunkPos.getWorldPosition()).subtract(chunkPos.getWorldPosition());
    }
    public SectionPos s2cTransform(SectionPos pos) throws ChunkUnusedException
    {
        ChunkPos chunkPos=pos.chunk();
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return SectionPos.of(
                transformerChunkPos.x(),
                pos.getY(),
                transformerChunkPos.z());
    }
    public Vec3 s2cTransform(Vec3 pos) throws ChunkUnusedException
    {
        BlockPos blockPos= BlockPos.containing(pos);
        BlockPos transformedBlockPos=s2cTransform(blockPos);
        return pos.add(Vec3.atLowerCornerOf(transformedBlockPos)).subtract(Vec3.atLowerCornerOf(blockPos));
    }
    public abstract ChunkPos c2sTransform(ChunkPos pos);
    public BlockPos c2sTransform(BlockPos pos)
    {
        ChunkPos chunkPos=ChunkPos.containing(pos);
        ChunkPos transformerChunkPos=c2sTransform(chunkPos);
        return pos.offset(transformerChunkPos.getWorldPosition()).subtract(chunkPos.getWorldPosition());
    }
    public Vec3 c2sTransform(Vec3 pos)
    {
        BlockPos blockPos= BlockPos.containing(pos);
        BlockPos transformedBlockPos=c2sTransform(blockPos);
        return pos.add(Vec3.atLowerCornerOf(transformedBlockPos)).subtract(Vec3.atLowerCornerOf(blockPos));
    }
    public SectionPos c2sTransform(SectionPos pos)
    {
        ChunkPos chunkPos=pos.chunk();
        ChunkPos transformerChunkPos=c2sTransform(chunkPos);
        return SectionPos.of(
                transformerChunkPos.x(),
                pos.getY(),
                transformerChunkPos.z());
    }
}
