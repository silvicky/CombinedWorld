package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.stream.Stream;

@Mixin(Entity.class)
public class EntityMixin
{
    @Shadow
    private Vec3 position;

    @ModifyVariable(method = "move",at=@At("STORE"),name = "checkTo")
    private Vec3 inject1(Vec3 value, @Local(name = "movement")Vec3 vec32,@Local(name = "checkDistance")double e)
    {
        Entity instance=(Entity)(Object)this;
        if(!(instance instanceof ServerPlayer player))return value;
        VecTransformer transformer=VecTransformer.getInstance(player);
        try
        {
            return transformer.c2sTransform(transformer.s2cTransform(position).add(vec32.normalize().scale(e)));
        }
        catch (Exception exception)
        {
            return value;
        }
    }
    @ModifyVariable(method = "move",at=@At("STORE"),name = "newPosition")
    private Vec3 inject2(Vec3 value, @Local(name = "movement")Vec3 vec32)
    {
        Entity instance=(Entity)(Object)this;
        if(!(instance instanceof ServerPlayer player))return value;
        VecTransformer transformer=VecTransformer.getInstance(player);
        try
        {
            return transformer.c2sTransform(transformer.s2cTransform(position).add(vec32));
        }
        catch (Exception exception)
        {
            return value;
        }
    }
    @Redirect(method = "isInWall",at= @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;betweenClosedStream(Lnet/minecraft/world/phys/AABB;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> inject3(AABB aABB)
    {
        Entity instance=(Entity) (Object)this;
        if(!(instance instanceof ServerPlayer player))return BlockPos.betweenClosedStream(aABB);
        try
        {
            VecTransformer transformer = VecTransformer.getInstance(player);
            AABB aABB2 = aABB.move(transformer.s2cTransform(aABB.getBottomCenter()).subtract(aABB.getBottomCenter()));
            return BlockPos.betweenClosedStream(aABB2).map(transformer::c2sTransform);
        }
        catch (Exception e)
        {
            return BlockPos.betweenClosedStream(aABB);
        }
    }
}
