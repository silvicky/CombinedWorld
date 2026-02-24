package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

import static io.silvicky.item.backrooms.EntityVisibilityManager.isVisible;

@Mixin(EntityTrackerEntry.class)
public class EntityTrackerEntryMixin
{
    @Redirect(method="sendPackets",at= @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", ordinal = 0))
    private void inject3(Consumer<Object> instance, Object t, @Local(argsOnly = true) ServerPlayerEntity player)
    {
        if(t instanceof EntitySpawnS2CPacket entitySpawnS2CPacket&&!isVisible(player,entitySpawnS2CPacket))return;
        instance.accept(t);
    }
}
