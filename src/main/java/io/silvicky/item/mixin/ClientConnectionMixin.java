package io.silvicky.item.mixin;

import io.netty.channel.ChannelFutureListener;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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

@Mixin(ClientConnection.class)
public class ClientConnectionMixin
{
    @Shadow @Final private NetworkSide side;
    @Unique
    private static Set<Packet<?>> modified= Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    @Inject(method = "handlePacket",at=@At("HEAD"))
    private static <T extends PacketListener> void inject1(Packet<T> packet, PacketListener listener, CallbackInfo ci)
    {
        if (listener.getSide() == NetworkSide.CLIENTBOUND)
        {
            return;
        }
        if (modified.contains(packet)) return;
        modified.add(packet);
        if (packet instanceof PlayerInteractBlockC2SPacket playerInteractBlockC2SPacket)
        {
            playerInteractBlockC2SPacket.getBlockHitResult().blockPos= VecTransformer.instance.c2sTransform(playerInteractBlockC2SPacket.getBlockHitResult().blockPos);
            playerInteractBlockC2SPacket.getBlockHitResult().pos=VecTransformer.instance.c2sTransform(playerInteractBlockC2SPacket.getBlockHitResult().pos);
            return;
        }
        if (packet instanceof PlayerMoveC2SPacket playerMoveC2SPacket)
        {
            if (playerMoveC2SPacket.changesPosition())
            {
                Vec3d pos=VecTransformer.instance.c2sTransform(new Vec3d(playerMoveC2SPacket.x,0,playerMoveC2SPacket.z));
                playerMoveC2SPacket.x = pos.x;
                playerMoveC2SPacket.z = pos.z;
            }
            return;
        }
        if (packet instanceof VehicleMoveC2SPacket vehicleMoveC2SPacket)
        {
            vehicleMoveC2SPacket.position=VecTransformer.instance.c2sTransform(vehicleMoveC2SPacket.position);
            return;
        }
        if(packet instanceof PlayerInteractEntityC2SPacket playerInteractEntityC2SPacket)
        {
            if(playerInteractEntityC2SPacket.type instanceof PlayerInteractEntityC2SPacket.InteractAtHandler interact)
            {
                interact.pos=VecTransformer.instance.c2sTransform(interact.pos);
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
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At("HEAD"))
    public void inject2(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci)
    {
        if(side==NetworkSide.CLIENTBOUND)return;
        if (modified.contains(packet)) return;
        modified.add(packet);
        if(packet instanceof BundleS2CPacket bundleS2CPacket)
        {
            for(Packet<?> packet1:bundleS2CPacket.getPackets())
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
