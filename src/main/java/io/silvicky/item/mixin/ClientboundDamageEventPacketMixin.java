package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ClientboundDamageEventPacket.class)
public class ClientboundDamageEventPacketMixin
{
    @Shadow public Optional<Vec3> sourcePosition;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V",at=@At("TAIL"))
    private void inject1(Entity entity, DamageSource damageSource, CallbackInfo ci)
    {
        if(this.sourcePosition.isPresent())this.sourcePosition=Optional.of(VecTransformer.instance.s2cTransform(sourcePosition.get()));
    }
}
