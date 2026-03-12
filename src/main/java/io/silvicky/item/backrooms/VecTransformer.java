package io.silvicky.item.backrooms;

import io.silvicky.item.StateSaver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public abstract class VecTransformer
{
    private static final Map<String,Class<? extends VecTransformer>> registry=Map.of(
            "nop", NopTransformer.class,
            "random", RandomTransformer.class,
            "linear", LinearTransformer.class
    );
    private static final Map<ServerPlayer,VecTransformer> instances=new WeakHashMap<>();
    public static VecTransformer getInstance(ServerPlayer player)
    {
        return instances.computeIfAbsent(player, VecTransformer::getInstanceByDimension);
    }
    public static void refreshInstance(ServerPlayer player)
    {
        instances.put(player, getInstanceByDimension(player));
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
    public VecTransformer(ServerPlayer player)
    {
        this.player=player;
        this.viewDistance = player.level().getChunkSource().chunkMap.getPlayerViewDistance(player);
    }
    public abstract Map<ChunkPos,ChunkPos> getS2c();
    public abstract void tick();
    public abstract ChunkPos s2cTransform(ChunkPos pos) throws ChunkUnusedException;
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
    public abstract ChunkPos c2sTransform(ChunkPos pos);
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
}
