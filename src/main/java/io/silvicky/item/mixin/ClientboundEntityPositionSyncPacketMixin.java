package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientboundEntityPositionSyncPacket.class)
public class ClientboundEntityPositionSyncPacketMixin
{
    @ModifyReturnValue(method = "of",at=@At("RETURN"))
    private static ClientboundEntityPositionSyncPacket inject1(ClientboundEntityPositionSyncPacket original)
    {
        original.values().position= VecTransformer.instance.s2cTransform(original.values().position);
        return original;
    }
}
