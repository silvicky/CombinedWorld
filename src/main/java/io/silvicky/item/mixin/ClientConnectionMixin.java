package io.silvicky.item.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin
{
    @Unique
    private static Set<Packet<?>> modified= Collections.newSetFromMap(new WeakHashMap<>());
    @Inject(method = "handlePacket",at=@At("HEAD"))
    private static <T extends PacketListener> void inject1(Packet<T> packet, PacketListener listener, CallbackInfo ci)
    {
        if(modified.contains(packet))return;
        modified.add(packet);
        if(listener.getSide()== NetworkSide.CLIENTBOUND)
        {
            if(packet instanceof ChunkDeltaUpdateS2CPacket chunkDeltaUpdateS2CPacket)
            {
                chunkDeltaUpdateS2CPacket.sectionPos.x++;
                chunkDeltaUpdateS2CPacket.sectionPos.z++;
            }
            if(packet instanceof BundleS2CPacket bundleS2CPacket)
            {
                for(Packet<?> packet1:bundleS2CPacket.getPackets())
                {
                    for (Field field : packet1.getClass().getDeclaredFields())
                    {
                        if (BlockPos.class.isAssignableFrom(field.getType()))
                        {
                            field.setAccessible(true);
                            try
                            {
                                BlockPos pos = (BlockPos) field.get(packet);
                                pos.x += 16;
                                pos.z += 16;
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
            for(Field field:packet.getClass().getDeclaredFields())
            {
                if(BlockPos.class.isAssignableFrom(field.getType()))
                {
                    field.setAccessible(true);
                    try
                    {
                        BlockPos pos= (BlockPos) field.get(packet);
                        pos.x+=16;
                        pos.z+=16;
                    }
                    catch (IllegalAccessException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        else
        {
            if(packet instanceof PlayerInteractBlockC2SPacket playerInteractBlockC2SPacket)
            {
                playerInteractBlockC2SPacket.getBlockHitResult().getBlockPos().x-=16;
                playerInteractBlockC2SPacket.getBlockHitResult().getBlockPos().z-=16;
                playerInteractBlockC2SPacket.getBlockHitResult().getPos().x-=16;
                playerInteractBlockC2SPacket.getBlockHitResult().getPos().z-=16;
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
                        pos.x-=16;
                        pos.z-=16;
                    }
                    catch (IllegalAccessException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
