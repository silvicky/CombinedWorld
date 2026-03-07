package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientboundTeleportEntityPacket.class)
public class ClientboundTeleportEntityPacketMixin
{
    @ModifyReturnValue(method = "teleport",at=@At("RETURN"))
    private static ClientboundTeleportEntityPacket inject1(ClientboundTeleportEntityPacket original)
    {
        if(!original.relatives().contains(Relative.X))
        {
            original.change().position= VecTransformer.instance.s2cTransform(original.change().position);
        }
        return original;
    }
}
