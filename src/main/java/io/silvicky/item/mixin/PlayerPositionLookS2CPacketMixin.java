package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerPositionLookS2CPacket.class)
public class PlayerPositionLookS2CPacketMixin
{
    @ModifyReturnValue(method = "of",at=@At("RETURN"))
    private static PlayerPositionLookS2CPacket inject1(PlayerPositionLookS2CPacket original)
    {
        if(!original.relatives().contains(PositionFlag.X))
        {
            original.change().position().x += 16;
            original.change().position().z += 16;
        }
        return original;
    }
}
