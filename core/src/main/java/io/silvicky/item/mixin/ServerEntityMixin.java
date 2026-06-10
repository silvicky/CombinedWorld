package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

import static io.silvicky.item.backrooms.EntityVisibilityManager.isVisible;

@Mixin(ServerEntity.class)
public class ServerEntityMixin
{
    @Redirect(method= "sendPairingData",at= @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", ordinal = 0))
    private void inject3(Consumer<Object> instance, Object t, @Local(argsOnly = true) ServerPlayer player)
    {
        if(t instanceof ClientboundAddEntityPacket entitySpawnS2CPacket&&!isVisible(player,entitySpawnS2CPacket))return;
        instance.accept(t);
    }
}
