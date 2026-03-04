package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.silvicky.item.backrooms.VecTransformer;
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
            original.change().position= VecTransformer.instance.s2cTransform(original.change().position);
        }
        return original;
    }
}
