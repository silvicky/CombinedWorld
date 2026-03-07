package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientboundPlayerPositionPacket.class)
public class ClientboundPlayerPositionPacketMixin
{
    @ModifyReturnValue(method = "of",at=@At("RETURN"))
    private static ClientboundPlayerPositionPacket inject1(ClientboundPlayerPositionPacket original)
    {
        if(!original.relatives().contains(Relative.X))
        {
            original.change().position= VecTransformer.instance.s2cTransform(original.change().position);
        }
        return original;
    }
}
