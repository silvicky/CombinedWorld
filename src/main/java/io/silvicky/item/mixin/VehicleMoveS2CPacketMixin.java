package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VehicleMoveS2CPacket.class)
public class VehicleMoveS2CPacketMixin
{
    @ModifyReturnValue(method = "fromVehicle",at=@At("RETURN"))
    private static VehicleMoveS2CPacket inject1(VehicleMoveS2CPacket original)
    {
        original.position= VecTransformer.instance.s2cTransform(original.position);
        return original;
    }
}
