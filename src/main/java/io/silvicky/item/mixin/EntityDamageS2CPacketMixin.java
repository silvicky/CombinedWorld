package io.silvicky.item.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(EntityDamageS2CPacket.class)
public class EntityDamageS2CPacketMixin
{
    @Shadow public Optional<Vec3d> sourcePosition;

    @Inject(method = "<init>(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;)V",at=@At("TAIL"))
    private void inject1(Entity entity, DamageSource damageSource, CallbackInfo ci)
    {
        if(this.sourcePosition.isPresent())this.sourcePosition=Optional.of(this.sourcePosition.get().add(16,0,16));
    }
}
