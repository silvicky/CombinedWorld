package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
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
        //c2sTransform throws ChunkUnusedException for a page the player cannot currently see, because the
        //visibility rule prunes the pages behind them and a pruned page must STAY pruned. Every field below
        //goes through c2sOrKeep, which leaves the field untouched in that case.
        //
        //That is the fix for the "invisible but still walkable and breakable" report. This hook used to make
        //c2sTransform mint a fresh mapping for any position it had not seen, so a pruned page was silently
        //re-created the moment anything referred to it - and the player ended up inside real, solid terrain
        //the client had never been sent. Leaving the coordinate alone is safe: the vanilla handler then
        //judges that packet against a position the player really did send.
        if (packet instanceof ServerboundUseItemOnPacket playerInteractBlockC2SPacket)
        {
            //Pin BEFORE converting. This interaction is about to be converted from the player's map to a
            //real position, and the outcome is converted back when it is broadcast. If the chunk is re-rolled
            //in between, those two conversions disagree and the player is told a different block changed
            //than the one they hit - the block they broke comes straight back.
            vecTransformer.pinAt(Vec3.atCenterOf(playerInteractBlockC2SPacket.hitResult().blockPos));
            playerInteractBlockC2SPacket.hitResult().blockPos=
                    c2sOrKeep(vecTransformer,playerInteractBlockC2SPacket.hitResult().blockPos);
            playerInteractBlockC2SPacket.hitResult().location=
                    c2sOrKeep(vecTransformer,playerInteractBlockC2SPacket.hitResult().location);
            return;
        }
        if (packet instanceof ServerboundMovePlayerPacket playerMoveC2SPacket)
        {
            if (playerMoveC2SPacket.hasPosition())
            {
                Vec3 converted=c2sOrKeep(vecTransformer,
                        new Vec3(playerMoveC2SPacket.x,0,playerMoveC2SPacket.z));
                playerMoveC2SPacket.x = converted.x;
                playerMoveC2SPacket.z = converted.z;
            }
            return;
        }
        if (packet instanceof ServerboundMoveVehiclePacket vehicleMoveC2SPacket)
        {
            PositionAndRotation pr=vehicleMoveC2SPacket.movingTo();
            Vec3 converted=c2sOrKeep(vecTransformer,pr.position());
            vehicleMoveC2SPacket.movingTo=new PositionAndRotation.Immutable(converted,pr.yRot(), pr.xRot());
            return;
        }
        if (packet instanceof ServerboundAcceptTeleportationPacket acceptTeleportationC2SPacket)
        {
            // 26.3 includes the client-side position in the teleport acknowledgement.
            // The server validates it in server coordinates, so undo the view transform.
            Vec3 converted=c2sOrKeep(vecTransformer,new Vec3(acceptTeleportationC2SPacket.x,
                    acceptTeleportationC2SPacket.y, acceptTeleportationC2SPacket.z));
            acceptTeleportationC2SPacket.x = converted.x;
            acceptTeleportationC2SPacket.y = converted.y;
            acceptTeleportationC2SPacket.z = converted.z;
            return;
        }
        if(packet instanceof ServerboundInteractPacket playerInteractEntityC2SPacket)
        {
            playerInteractEntityC2SPacket.location=
                    c2sOrKeep(vecTransformer,playerInteractEntityC2SPacket.location);
            return;
        }
        if(packet instanceof ServerboundSetStructureBlockPacket){return;}//TODO there are 2 pos!
        for (Field field : packet.getClass().getDeclaredFields())
        {
            if (BlockPos.class.isAssignableFrom(field.getType()))
            {
                field.setAccessible(true);
                try
                {
                    field.set(packet,c2sOrKeep(vecTransformer,(BlockPos) field.get(packet)));
                }
                catch (IllegalAccessException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    /** Converts a client position to a server position, or hands it back unchanged if its page is pruned. */
    private static BlockPos c2sOrKeep(VecTransformer transformer,BlockPos clientPos)
    {
        try{return transformer.c2sTransform(clientPos);}
        catch (ChunkUnusedException e){return clientPos;}
    }
    private static Vec3 c2sOrKeep(VecTransformer transformer,Vec3 clientPos)
    {
        try{return transformer.c2sTransform(clientPos);}
        catch (ChunkUnusedException e){return clientPos;}
    }
}
