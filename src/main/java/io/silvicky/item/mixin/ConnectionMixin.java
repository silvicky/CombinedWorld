package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
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
    @Unique
    private static Set<Packet<?>> modified= Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    @Inject(method = "genericsFtw",at=@At("HEAD"))
    private static <T extends PacketListener> void inject1(Packet<T> packet, PacketListener listener, CallbackInfo ci)
    {
        if (listener.flow() == PacketFlow.CLIENTBOUND) return;
        if(!(listener instanceof ServerGamePacketListenerImpl impl))return;
        VecTransformer vecTransformer=VecTransformer.getInstance(impl.getPlayer());
        if (modified.contains(packet)) return;
        modified.add(packet);
        if (packet instanceof ServerboundUseItemOnPacket playerInteractBlockC2SPacket)
        {
            playerInteractBlockC2SPacket.getHitResult().blockPos= vecTransformer.c2sTransform(playerInteractBlockC2SPacket.getHitResult().blockPos);
            playerInteractBlockC2SPacket.getHitResult().location =vecTransformer.c2sTransform(playerInteractBlockC2SPacket.getHitResult().location);
            return;
        }
        if (packet instanceof ServerboundMovePlayerPacket playerMoveC2SPacket)
        {
            if (playerMoveC2SPacket.hasPosition())
            {
                Vec3 pos=vecTransformer.c2sTransform(new Vec3(playerMoveC2SPacket.x,0,playerMoveC2SPacket.z));
                playerMoveC2SPacket.x = pos.x;
                playerMoveC2SPacket.z = pos.z;
            }
            return;
        }
        if (packet instanceof ServerboundMoveVehiclePacket vehicleMoveC2SPacket)
        {
            vehicleMoveC2SPacket.position=vecTransformer.c2sTransform(vehicleMoveC2SPacket.position);
            return;
        }
        if(packet instanceof ServerboundInteractPacket playerInteractEntityC2SPacket)
        {
            if(playerInteractEntityC2SPacket.action instanceof ServerboundInteractPacket.InteractionAtLocationAction interact)
            {
                interact.location =vecTransformer.c2sTransform(interact.location);
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
                    field.set(packet,vecTransformer.c2sTransform(pos));
                }
                catch (IllegalAccessException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
