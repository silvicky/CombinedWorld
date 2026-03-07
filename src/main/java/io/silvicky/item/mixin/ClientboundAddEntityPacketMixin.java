package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ClientboundAddEntityPacket.class)
public class ClientboundAddEntityPacketMixin
{
    @Shadow public double x;
    @Shadow public double z;

    @Inject(method = "<init>(ILjava/util/UUID;DDDFFLnet/minecraft/world/entity/EntityType;ILnet/minecraft/world/phys/Vec3;D)V",at = @At("TAIL"))
    public void inject1(int entityId, UUID uuid, double x, double y, double z, float pitch, float yaw, EntityType<?> entityType, int entityData, Vec3 velocity, double headYaw, CallbackInfo ci)
    {
        Vec3 pos= VecTransformer.instance.s2cTransform(new Vec3(x,y,z));
        this.x=pos.x;
        this.z=pos.z;
    }
}
