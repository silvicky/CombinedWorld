package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public class EntityMixin
{
    @Shadow
    private Vec3 position;

    @ModifyVariable(method = "move",at=@At("STORE"),name = "vec33")
    private Vec3 inject1(Vec3 value, @Local(name = "vec32")Vec3 vec32,@Local(name = "e")double e)
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
    @ModifyVariable(method = "move",at=@At("STORE"),name = "vec35")
    private Vec3 inject2(Vec3 value, @Local(name = "vec32")Vec3 vec32)
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
}
