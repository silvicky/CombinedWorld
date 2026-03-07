package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundPlayerLookAtPacket.class)
public class ClientboundPlayerLookAtPacketMixin
{
    @Shadow
    public double x;

    @Shadow
    public double z;

    @Shadow @Final private double y;

    @Inject(method = "<init>(Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;)V",at=@At("TAIL"))
    public void inject1(EntityAnchorArgument.Anchor selfAnchor, Entity entity, EntityAnchorArgument.Anchor targetAnchor, CallbackInfo ci)
    {
        Vec3 pos= VecTransformer.instance.s2cTransform(new Vec3(x, y, z));
        this.x =pos.x;
        this.z =pos.z;
    }
    @Inject(method = "<init>(Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;DDD)V",at=@At("TAIL"))
    public void inject2(EntityAnchorArgument.Anchor selfAnchor, double targetX, double targetY, double targetZ, CallbackInfo ci)
    {
        Vec3 pos= VecTransformer.instance.s2cTransform(new Vec3(targetX,targetY,targetZ));
        this.x =pos.x;
        this.z =pos.z;
    }
}
