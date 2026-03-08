package io.silvicky.item.mixin;

import io.netty.channel.ChannelFutureListener;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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
public class ServerCommonPacketListenerImplMixin
{
    @Shadow
    @Final
    protected Connection connection;
    @Shadow
    private long closedListenerTime;
    @Unique
    private static Set<Packet<?>> modified= Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public ServerCommonPacketListenerImplMixin(long closedListenerTime)
    {
        this.closedListenerTime = closedListenerTime;
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",at=@At("HEAD"), cancellable = true)
    public void inject1(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci)
    {
        if(connection.getReceiving() == PacketFlow.CLIENTBOUND)return;
        ServerCommonPacketListenerImpl instance=(ServerCommonPacketListenerImpl) (Object)this;
        if(!(instance instanceof ServerGamePacketListenerImpl gamePacketListener))return;
        VecTransformer vecTransformer=VecTransformer.getInstance(gamePacketListener.getPlayer());
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
            /*if (packet instanceof ClientboundForgetLevelChunkPacket unloadChunkS2CPacket)
            {
                unloadChunkS2CPacket.pos = vecTransformer.s2cTransform(unloadChunkS2CPacket.pos);
                return;
            }*/
            if (packet instanceof ClientboundLightUpdatePacket lightUpdateS2CPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(lightUpdateS2CPacket.x, lightUpdateS2CPacket.z));
                lightUpdateS2CPacket.x = pos.x;
                lightUpdateS2CPacket.z = pos.z;
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
                chunkRenderDistanceCenterS2CPacket.x = pos.x;
                chunkRenderDistanceCenterS2CPacket.z = pos.z;
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
                Vec3 pos = new Vec3(particleS2CPacket.x, 0, particleS2CPacket.z);
                particleS2CPacket.x = pos.x;
                particleS2CPacket.z = pos.z;
                return;
            }
            if (packet instanceof ClientboundTeleportEntityPacket clientboundTeleportEntityPacket)
            {
                if (!clientboundTeleportEntityPacket.relatives().contains(Relative.X))
                {
                    clientboundTeleportEntityPacket.change().position = vecTransformer.s2cTransform(clientboundTeleportEntityPacket.change().position);
                }
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
            if (packet instanceof ClientboundMoveVehiclePacket clientboundMoveVehiclePacket)
            {
                clientboundMoveVehiclePacket.position = vecTransformer.s2cTransform(clientboundMoveVehiclePacket.position);
                return;
            }
            if (packet instanceof ClientboundLevelChunkWithLightPacket clientboundLevelChunkWithLightPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(clientboundLevelChunkWithLightPacket.x, clientboundLevelChunkWithLightPacket.z));
                clientboundLevelChunkWithLightPacket.x = pos.x;
                clientboundLevelChunkWithLightPacket.z = pos.z;
                return;
            }
            if (packet instanceof ClientboundEntityPositionSyncPacket clientboundEntityPositionSyncPacket)
            {
                clientboundEntityPositionSyncPacket.values().position = vecTransformer.s2cTransform(clientboundEntityPositionSyncPacket.values().position);
                return;
            }
            if (packet instanceof ClientboundDamageEventPacket clientboundDamageEventPacket)
            {
                if (clientboundDamageEventPacket.sourcePosition.isPresent())
                    clientboundDamageEventPacket.sourcePosition = Optional.of(vecTransformer.s2cTransform(clientboundDamageEventPacket.sourcePosition.get()));
                return;
            }
            if (packet instanceof ClientboundAddEntityPacket clientboundAddEntityPacket)
            {
                Vec3 pos = vecTransformer.s2cTransform(new Vec3(clientboundAddEntityPacket.getX(), 0, clientboundAddEntityPacket.getZ()));
                clientboundAddEntityPacket.x = pos.x;
                clientboundAddEntityPacket.z = pos.z;
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
            ci.cancel();
        }
    }
}
