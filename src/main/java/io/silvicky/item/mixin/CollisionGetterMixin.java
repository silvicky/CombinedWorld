package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CollisionGetter.class)
public interface CollisionGetterMixin
{
    @ModifyArg(method = "findSupportingBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockCollisions;<init>(Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;ZLjava/util/function/BiFunction;)V"))
    private AABB inject1(AABB value, @Local(argsOnly = true) Entity entity)
    {
        if(!(entity instanceof ServerPlayer player1))return value;
        try
        {
            Vec3 v = VecTransformer.getInstance(player1).s2cTransform(player1.position());
            return entity.getBoundingBox().move(v.x - entity.getX(), v.y - entity.getY(), v.z - entity.getZ()).deflate(1.0e-5F);
        }
        catch (Exception exception)
        {
            return value;
        }
    }
    @ModifyArg(method = "findSupportingBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D"))
    private Position inject2(Position value, @Local(argsOnly = true) Entity entity)
    {
        if(!(entity instanceof ServerPlayer player1))return value;
        try
        {
            return VecTransformer.getInstance(player1).s2cTransform(player1.position());
        }
        catch (Exception exception)
        {
            return value;
        }
    }
    @ModifyVariable(method = "findSupportingBlock", at = @At(value="STORE",ordinal = 1),name = "blockPos")
    private BlockPos inject3(BlockPos value, @Local(argsOnly = true) Entity entity)
    {
        if(!(entity instanceof ServerPlayer player1))return value;
        return VecTransformer.getInstance(player1).c2sTransform(value);
    }
    @ModifyVariable(method = "getBlockCollisionsFromContext", at = @At("HEAD"), argsOnly = true)
    private AABB inject4(AABB value, @Local(argsOnly = true)CollisionContext collisionContext)
    {
        if(!(collisionContext instanceof EntityCollisionContext entityCollisionContext))return value;
        if(!(entityCollisionContext.getEntity() instanceof ServerPlayer player1))return value;
        try
        {
            return value.move(VecTransformer.getInstance(player1).s2cTransform(value.getBottomCenter()).subtract(value.getBottomCenter()));
        }
        catch (Exception e){return value;}
    }
}
