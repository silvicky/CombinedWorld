package io.silvicky.item.mixin;

import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.LookAtS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LookAtS2CPacket.class)
public class LookAtS2CPacketMixin
{
    @Shadow
    public double targetX;

    @Shadow
    public double targetZ;

    @Inject(method = "<init>(Lnet/minecraft/command/argument/EntityAnchorArgumentType$EntityAnchor;Lnet/minecraft/entity/Entity;Lnet/minecraft/command/argument/EntityAnchorArgumentType$EntityAnchor;)V",at=@At("TAIL"))
    public void inject1(EntityAnchorArgumentType.EntityAnchor selfAnchor, Entity entity, EntityAnchorArgumentType.EntityAnchor targetAnchor, CallbackInfo ci)
    {
        this.targetX+=16;
        this.targetZ+=16;
    }
    @Inject(method = "<init>(Lnet/minecraft/command/argument/EntityAnchorArgumentType$EntityAnchor;DDD)V",at=@At("TAIL"))
    public void inject2(EntityAnchorArgumentType.EntityAnchor selfAnchor, double targetX, double targetY, double targetZ, CallbackInfo ci)
    {
        this.targetX+=16;
        this.targetZ+=16;
    }
}
