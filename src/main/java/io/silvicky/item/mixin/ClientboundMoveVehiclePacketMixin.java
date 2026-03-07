package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientboundMoveVehiclePacket.class)
public class ClientboundMoveVehiclePacketMixin
{
    @ModifyReturnValue(method = "fromEntity",at=@At("RETURN"))
    private static ClientboundMoveVehiclePacket inject1(ClientboundMoveVehiclePacket original)
    {
        original.position= VecTransformer.instance.s2cTransform(original.position);
        return original;
    }
}
