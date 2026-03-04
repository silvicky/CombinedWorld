package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(EntitySpawnS2CPacket.class)
public class EntitySpawnS2CPacketMixin
{
    @Shadow public double x;
    @Shadow public double z;

    @Inject(method = "<init>(ILjava/util/UUID;DDDFFLnet/minecraft/entity/EntityType;ILnet/minecraft/util/math/Vec3d;D)V",at = @At("TAIL"))
    public void inject1(int entityId, UUID uuid, double x, double y, double z, float pitch, float yaw, EntityType<?> entityType, int entityData, Vec3d velocity, double headYaw, CallbackInfo ci)
    {
        Vec3d pos= VecTransformer.instance.s2cTransform(new Vec3d(x,y,z));
        this.x=pos.x;
        this.z=pos.z;
    }
}
