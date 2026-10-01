package io.silvicky.item.mixin;

import io.netty.channel.ChannelFutureListener;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.EntityPositionRemapper;
import io.silvicky.item.backrooms.EntityRemapAccess;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.*;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin implements EntityRemapAccess
{
    @Shadow
    @Final
    protected Connection connection;
    @Unique
    private static Set<Packet<?>> modified= Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /**
     * Per-connection, because it is per-player: what each client has been told about each entity.
     *
     * <p>Entity packets mostly carry an <i>offset</i> rather than a position, and an offset cannot be
     * converted without knowing the position it is relative to - which is a position this client holds and
     * the packet does not state. So the base has to be remembered per player. It cannot be shared between
     * players, because two players have different maps and therefore different bases.
     *
     * <p>See {@link EntityPositionRemapper} for why an offset is usually correct as-is and only has to be
     * replaced when the entity crosses a page boundary.
     */
    @Unique
    private final EntityPositionRemapper item_storage$entityRemapper=new EntityPositionRemapper();
    @Override
    public EntityPositionRemapper item_storage$entityRemapper(){return item_storage$entityRemapper;}
    @Override
    public void item_storage$resetEntityRemapper(){item_storage$entityRemapper.reset();}
    /**
     * Set while a replacement entity packet is being sent, so the re-entry does not convert it again.
     *
     * <p>An instance field, not a static one, so it cannot be tripped by another player's send on another
     * thread. A static flag here would be a race between two players.
     */
    @Unique
    private boolean replacing;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",at=@At("HEAD"), cancellable = true)
    public void inject1(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci)
    {
        if(connection.getReceiving() == PacketFlow.CLIENTBOUND)return;
        //Our own replacement, on its way out. It was built from a packet that had already been converted.
        if(replacing)return;
        ServerCommonPacketListenerImpl instance=(ServerCommonPacketListenerImpl) (Object)this;
        if(!(instance instanceof ServerGamePacketListenerImpl gamePacketListener))return;
        VecTransformer vecTransformer=VecTransformer.getInstance(gamePacketListener.getPlayer());
        //Entity packets are handled before the `modified` cache and are exempt from it.
        //
        //That cache exists because the handlers below EDIT the packet in place, and a broadcast packet is one
        //object handed to every tracking player in turn - so without a guard the second player would be sent
        //the first player's already-converted copy. The cache is the wrong shape for entity packets, because
        //converting them is not a property of the packet but of the player it is going to. Two players have
        //two different maps, two different offsets, and two different remembered bases, so the same packet
        //object legitimately has two different correct answers. Caching by object identity would hand both
        //players the first one's answer. So entity packets skip the cache and are converted per player.
        if (isEntityPacket(packet))
        {
            try
            {
                Packet<?> translated=item_storage$entityRemapper
                                             .translate(packet,vecTransformer,
                                                        gamePacketListener.getPlayer().level());
                if(translated==null)
                {
                    //Its page is not in this player's map, so there is nowhere to put it. Dropping is right:
                    //the alternative is sending it at a position the player has never been shown.
                    ci.cancel();
                    return;
                }
                //Same object back means no change was needed - the common case, an offset packet inside one
                //page - and the original can simply go out.
                if(translated==packet)return;
                //Otherwise a replacement was built. It is a new object, so it re-enters this method; the flag
                //stops that from recursing.
                ci.cancel();
                replacing=true;
                try
                {
                    instance.send(translated,channelFutureListener);
                }
                finally
                {
                    replacing=false;
                }
            }
            catch (ChunkUnusedException e)
            {
                ci.cancel();
            }
            return;
        }
        if (modified.contains(packet)) return;
        modified.add(packet);
        try
        {
            if (packet instanceof ClientboundBundlePacket bundleS2CPacket)
            {
                for (Packet<?> packet1 : bundleS2CPacket.subPackets())
                {
                    for (Field field : packet1.getClass().getDeclaredFields())
                    {
                        if (BlockPos.class.isAssignableFrom(field.getType()))
                        {
                            field.setAccessible(true);
                            try
                            {
                                BlockPos pos = (BlockPos) field.get(packet1);
                                field.set(packet1, vecTransformer.s2cTransform(pos));
                            }
                            catch (IllegalAccessException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
                return;
            }
            if (packet instanceof ClientboundForgetLevelChunkPacket)
            {
                //this is processed elsewhere
                return;
            }
            if(packet instanceof ClientboundExplodePacket clientboundExplodePacket)
            {
                clientboundExplodePacket.center=vecTransformer.s2cTransform(clientboundExplodePacket.center);
                return;
            }
            if (packet instanceof ClientboundLightUpdatePacket lightUpdateS2CPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(lightUpdateS2CPacket.x, lightUpdateS2CPacket.z));
                lightUpdateS2CPacket.x = pos.x();
                lightUpdateS2CPacket.z = pos.z();
                return;
            }
            if (packet instanceof ClientboundChunksBiomesPacket(
                    List<ClientboundChunksBiomesPacket.ChunkBiomeData> chunkBiomeData
            ))
            {
                for (ClientboundChunksBiomesPacket.ChunkBiomeData serialized : chunkBiomeData)
                {
                    serialized.pos = vecTransformer.s2cTransform(serialized.pos);
                }
                return;
            }
            if (packet instanceof ClientboundDebugChunkValuePacket chunkValueDebugS2CPacket)
            {
                chunkValueDebugS2CPacket.chunkPos = vecTransformer.s2cTransform(chunkValueDebugS2CPacket.chunkPos);
                return;
            }
            if (packet instanceof ClientboundSetChunkCacheCenterPacket chunkRenderDistanceCenterS2CPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(chunkRenderDistanceCenterS2CPacket.x, chunkRenderDistanceCenterS2CPacket.z));
                chunkRenderDistanceCenterS2CPacket.x = pos.x();
                chunkRenderDistanceCenterS2CPacket.z = pos.z();
                return;
            }
            if (packet instanceof ClientboundTrackedWaypointPacket waypointS2CPacket)
            {
                //TODO
                return;
            }
            if (packet instanceof ClientboundSoundPacket playSoundS2CPacket)
            {
                //TODO
                return;
            }
            if (packet instanceof ClientboundLevelParticlesPacket particleS2CPacket)
            {
                Vec3 pos = vecTransformer.s2cTransform(new Vec3(particleS2CPacket.x, 0, particleS2CPacket.z));
                particleS2CPacket.x = pos.x;
                particleS2CPacket.z = pos.z;
                return;
            }
            if (packet instanceof ClientboundSectionBlocksUpdatePacket clientboundSectionBlocksUpdatePacket)
            {
                clientboundSectionBlocksUpdatePacket.sectionPos = vecTransformer.s2cTransform(clientboundSectionBlocksUpdatePacket.sectionPos);
                return;
            }
            if (packet instanceof ClientboundPlayerPositionPacket clientboundPlayerPositionPacket)
            {
                if (!clientboundPlayerPositionPacket.relatives().contains(Relative.X))
                {
                    clientboundPlayerPositionPacket.change().position = vecTransformer.s2cTransform(clientboundPlayerPositionPacket.change().position);
                }
                return;
            }
            if (packet instanceof ClientboundPlayerLookAtPacket clientboundPlayerLookAtPacket)
            {
                Vec3 pos = vecTransformer.s2cTransform(new Vec3(clientboundPlayerLookAtPacket.x, 0, clientboundPlayerLookAtPacket.z));
                clientboundPlayerLookAtPacket.x = pos.x;
                clientboundPlayerLookAtPacket.z = pos.z;
                return;
            }
            if (packet instanceof ClientboundLevelChunkWithLightPacket clientboundLevelChunkWithLightPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(clientboundLevelChunkWithLightPacket.x, clientboundLevelChunkWithLightPacket.z));
                clientboundLevelChunkWithLightPacket.x = pos.x();
                clientboundLevelChunkWithLightPacket.z = pos.z();
                return;
            }
            if (packet instanceof ClientboundDamageEventPacket clientboundDamageEventPacket)
            {
                if (clientboundDamageEventPacket.sourcePosition.isPresent())
                    clientboundDamageEventPacket.sourcePosition = Optional.of(vecTransformer.s2cTransform(clientboundDamageEventPacket.sourcePosition.get()));
                return;
            }
            for (Field field : packet.getClass().getDeclaredFields())
            {
                if (BlockPos.class.isAssignableFrom(field.getType()))
                {
                    field.setAccessible(true);
                    try
                    {
                        BlockPos pos = (BlockPos) field.get(packet);
                        field.set(packet, vecTransformer.s2cTransform(pos));
                    }
                    catch (IllegalAccessException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        catch (ChunkUnusedException e)
        {
            System.out.println("Packet discarded: "+packet);
            ci.cancel();
        }
    }

    /**
     * Whether this packet places an entity, and so has to go through {@link EntityPositionRemapper}.
     *
     * <p>Deliberately an explicit list rather than a broad {@code instanceof} on some interface. Several
     * entity packets carry no position at all - {@code ClientboundMoveEntityPacket.Rot} is rotation only,
     * {@code SetEntityMotionPacket} is velocity, {@code SetEntityLinkPacket} and {@code RemoveEntitiesPacket}
     * are ids - and sending those through the remapper would only risk touching something that has no
     * position to move. Being wrong in the other direction, a packet that does carry a position but is not
     * listed, would go out untranslated, so the list is worth keeping honest.
     */
    @Unique
    private static boolean isEntityPacket(Packet<?> packet)
    {
        return packet instanceof ClientboundMoveEntityPacket
                ||packet instanceof ClientboundEntityPositionSyncPacket
                ||packet instanceof ClientboundTeleportEntityPacket
                ||packet instanceof ClientboundAddEntityPacket
                ||packet instanceof ClientboundMoveVehiclePacket
                ||packet instanceof ClientboundMoveMinecartPacket
                ||packet instanceof ClientboundRemoveEntitiesPacket;
    }
}
