package io.silvicky.item.mixin;

import io.netty.channel.ChannelFutureListener;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.core.BlockPos;
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
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Mixin(Connection.class)
public abstract class ConnectionMixin
{
    @Shadow @Final private PacketFlow receiving;

    @Unique
    private static Set<Packet<?>> modified= Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    @Inject(method = "genericsFtw",at=@At("HEAD"))
    private static <T extends PacketListener> void inject1(Packet<T> packet, PacketListener listener, CallbackInfo ci)
    {
        if (listener.flow() == PacketFlow.CLIENTBOUND)
        {
            return;
        }
        if (modified.contains(packet)) return;
        modified.add(packet);
        if (packet instanceof ServerboundUseItemOnPacket playerInteractBlockC2SPacket)
        {
            playerInteractBlockC2SPacket.getHitResult().blockPos= VecTransformer.instance.c2sTransform(playerInteractBlockC2SPacket.getHitResult().blockPos);
            playerInteractBlockC2SPacket.getHitResult().location =VecTransformer.instance.c2sTransform(playerInteractBlockC2SPacket.getHitResult().location);
            return;
        }
        if (packet instanceof ServerboundMovePlayerPacket playerMoveC2SPacket)
        {
            if (playerMoveC2SPacket.hasPosition())
            {
                Vec3 pos=VecTransformer.instance.c2sTransform(new Vec3(playerMoveC2SPacket.x,0,playerMoveC2SPacket.z));
                playerMoveC2SPacket.x = pos.x;
                playerMoveC2SPacket.z = pos.z;
            }
            return;
        }
        if (packet instanceof ServerboundMoveVehiclePacket vehicleMoveC2SPacket)
        {
            vehicleMoveC2SPacket.position=VecTransformer.instance.c2sTransform(vehicleMoveC2SPacket.position);
            return;
        }
        if(packet instanceof ServerboundInteractPacket playerInteractEntityC2SPacket)
        {
            if(playerInteractEntityC2SPacket.action instanceof ServerboundInteractPacket.InteractionAtLocationAction interact)
            {
                interact.location =VecTransformer.instance.c2sTransform(interact.location);
            }
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
                    field.set(packet,VecTransformer.instance.c2sTransform(pos));
                }
                catch (IllegalAccessException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At("HEAD"))
    public void inject2(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci)
    {
        if(receiving == PacketFlow.CLIENTBOUND)return;
        if (modified.contains(packet)) return;
        modified.add(packet);
        if(packet instanceof ClientboundBundlePacket bundleS2CPacket)
        {
            for(Packet<?> packet1:bundleS2CPacket.subPackets())
            {
                for(Field field:packet1.getClass().getDeclaredFields())
                {
                    if(BlockPos.class.isAssignableFrom(field.getType()))
                    {
                        field.setAccessible(true);
                        try
                        {
                            BlockPos pos= (BlockPos) field.get(packet1);
                            field.set(packet1,VecTransformer.instance.s2cTransform(pos));
                        }
                        catch (IllegalAccessException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        if(packet instanceof ClientboundForgetLevelChunkPacket unloadChunkS2CPacket)
        {
            unloadChunkS2CPacket.pos= VecTransformer.instance.s2cTransform(unloadChunkS2CPacket.pos);
            return;
        }
        if(packet instanceof ClientboundLightUpdatePacket lightUpdateS2CPacket)
        {
            ChunkPos pos=VecTransformer.instance.s2cTransform(new ChunkPos(lightUpdateS2CPacket.x,lightUpdateS2CPacket.z));
            lightUpdateS2CPacket.x =pos.x;
            lightUpdateS2CPacket.z =pos.z;
            return;
        }
        if(packet instanceof ClientboundChunksBiomesPacket chunkBiomeDataS2CPacket)
        {
            for(ClientboundChunksBiomesPacket.ChunkBiomeData serialized:chunkBiomeDataS2CPacket.chunkBiomeData())
            {
                serialized.pos=VecTransformer.instance.s2cTransform(serialized.pos);
            }
        }
        if(packet instanceof ClientboundDebugChunkValuePacket chunkValueDebugS2CPacket)
        {
            chunkValueDebugS2CPacket.chunkPos= VecTransformer.instance.s2cTransform(chunkValueDebugS2CPacket.chunkPos);
            return;
        }
        if(packet instanceof ClientboundSetChunkCacheCenterPacket chunkRenderDistanceCenterS2CPacket)
        {
            ChunkPos pos=VecTransformer.instance.s2cTransform(new ChunkPos(chunkRenderDistanceCenterS2CPacket.x,chunkRenderDistanceCenterS2CPacket.z));
            chunkRenderDistanceCenterS2CPacket.x =pos.x;
            chunkRenderDistanceCenterS2CPacket.z =pos.z;
            return;
        }
        if(packet instanceof ClientboundTrackedWaypointPacket waypointS2CPacket)
        {
            //TODO
            return;
        }
        if(packet instanceof ClientboundSoundPacket playSoundS2CPacket)
        {
            //TODO
            return;
        }
        if(packet instanceof ClientboundLevelParticlesPacket particleS2CPacket)
        {
            Vec3 pos=new Vec3(particleS2CPacket.x,0,particleS2CPacket.z);
            particleS2CPacket.x=pos.x;
            particleS2CPacket.z=pos.z;
            return;
        }
        for(Field field:packet.getClass().getDeclaredFields())
        {
            if(BlockPos.class.isAssignableFrom(field.getType()))
            {
                field.setAccessible(true);
                try
                {
                    BlockPos pos= (BlockPos) field.get(packet);
                    field.set(packet,VecTransformer.instance.s2cTransform(pos));
                }
                catch (IllegalAccessException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
