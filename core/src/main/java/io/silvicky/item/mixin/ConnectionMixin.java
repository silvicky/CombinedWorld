package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.TransformAudit;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.game.*;
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

/**
 * The client-to-server boundary.
 *
 * <p>This is the one place in the mod where client view coordinates legitimately become server world
 * coordinates. Every packet handled here is {@code [C]}, and everything the packet is rewritten to is
 * {@code [S]}. Any transform attempted further downstream is a double transform - see
 * {@link io.silvicky.item.backrooms.CoordinateSpace} for the marker legend.
 */
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
            //[C>S]
            TransformAudit.site("c2s/UseItemOn");
            try
            {
                playerInteractBlockC2SPacket.hitResult().blockPos= vecTransformer.c2sTransform(playerInteractBlockC2SPacket.hitResult().blockPos);
                playerInteractBlockC2SPacket.hitResult().location =vecTransformer.c2sTransform(playerInteractBlockC2SPacket.hitResult().location);
            }
            finally { TransformAudit.endSite(); }
            return;
        }
        if (packet instanceof ServerboundMovePlayerPacket playerMoveC2SPacket)
        {
            if (playerMoveC2SPacket.hasPosition())
            {
                //[C>S]
                double rawX=playerMoveC2SPacket.x, rawZ=playerMoveC2SPacket.z;
                TransformAudit.site("c2s/MovePlayer");
                Vec3 pos;
                try
                {
                    pos=vecTransformer.c2sTransform(new Vec3(rawX,0,rawZ));
                }
                finally { TransformAudit.endSite(); }
                playerMoveC2SPacket.x = pos.x;
                playerMoveC2SPacket.z = pos.z;
                //Record both ends so TransformAudit can tell, on the server thread, whether a later site is
                //being handed coordinates that are already S-field.
                TransformAudit.recordMove(impl.getPlayer(),rawX,rawZ,pos.x,pos.z);
            }
            return;
        }
        if (packet instanceof ServerboundMoveVehiclePacket vehicleMoveC2SPacket)
        {
            //[C>S]
            PositionAndRotation pr=vehicleMoveC2SPacket.movingTo();
            TransformAudit.site("c2s/MoveVehicle");
            try
            {
                vehicleMoveC2SPacket.movingTo=new PositionAndRotation.Immutable(vecTransformer.c2sTransform(pr.position()),pr.yRot(), pr.xRot());
            }
            finally { TransformAudit.endSite(); }
            return;
        }
        if (packet instanceof ServerboundAcceptTeleportationPacket acceptTeleportationC2SPacket)
        {
            //[C>S]
            // 26.3 includes the client-side position in the teleport acknowledgement.
            // The server validates it in server coordinates, so undo the view transform.
            TransformAudit.site("c2s/AcceptTeleport");
            Vec3 pos;
            try
            {
                pos = vecTransformer.c2sTransform(
                        new Vec3(acceptTeleportationC2SPacket.x, acceptTeleportationC2SPacket.y, acceptTeleportationC2SPacket.z)
                );
            }
            finally { TransformAudit.endSite(); }
            acceptTeleportationC2SPacket.x = pos.x;
            acceptTeleportationC2SPacket.y = pos.y;
            acceptTeleportationC2SPacket.z = pos.z;
            return;
        }
        if(packet instanceof ServerboundInteractPacket playerInteractEntityC2SPacket)
        {
            //[C>S]
            TransformAudit.site("c2s/Interact");
            try
            {
                playerInteractEntityC2SPacket.location =vecTransformer.c2sTransform(playerInteractEntityC2SPacket.location);
            }
            finally { TransformAudit.endSite(); }
            return;
        }
        if(packet instanceof ServerboundSetStructureBlockPacket){return;}//TODO there are 2 pos!
        TransformAudit.site("c2s/reflect:" + packet.getClass().getSimpleName());
        try
        {
            for (Field field : packet.getClass().getDeclaredFields())
            {
                if (BlockPos.class.isAssignableFrom(field.getType()))
                {
                    //[C>S]
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
        finally { TransformAudit.endSite(); }
    }
}
