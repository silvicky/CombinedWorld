package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChunkMap.TrackedEntity.class)
public class TrackedEntityMixin
{
    @Shadow
    @Final
    private Entity entity;

    @ModifyVariable(method = "updatePlayer", at = @At("STORE"), name = "deltaToPlayer")
    private Vec3 inject1(Vec3 vec3, @Local(argsOnly = true)ServerPlayer player)
    {
        try
        {
            Vec3 entityPos = entity.position();
            Vec3 playerPos = player.position();
            VecTransformer transformer=VecTransformer.getInstance(player);
            return transformer.s2cTransform(playerPos).subtract(transformer.s2cTransform(entityPos));
        }
        catch (Exception e){return vec3;}
    }
}
