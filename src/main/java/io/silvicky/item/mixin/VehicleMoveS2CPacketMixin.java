package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VehicleMoveS2CPacket.class)
public class VehicleMoveS2CPacketMixin
{
    @ModifyReturnValue(method = "fromVehicle",at=@At("RETURN"))
    private static VehicleMoveS2CPacket inject1(VehicleMoveS2CPacket original)
    {
        original.position=original.position.add(16,0,16);
        return original;
    }
}
