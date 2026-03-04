package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityPositionSyncS2CPacket.class)
public class EntityPositionSyncS2CPacketMixin
{
    @ModifyReturnValue(method = "create",at=@At("RETURN"))
    private static EntityPositionSyncS2CPacket inject1(EntityPositionSyncS2CPacket original)
    {
        original.values().position=original.values().position.add(16,0,16);
        return original;
    }
}
