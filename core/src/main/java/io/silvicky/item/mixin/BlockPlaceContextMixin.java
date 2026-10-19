package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.CrossBandAction;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockPlaceContext.class)
public class BlockPlaceContextMixin
{
    @Redirect(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/BlockHitResult;)V", at= @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos inject1(BlockPos instance, Direction direction, @Local(argsOnly = true)Player player,
                             @Local(argsOnly = true)Level level)
    {
        //A cross-band placement names its position outright. Vanilla's `clicked.relative(direction)` is off by
        //one block across a seam - a click on the lower band's ceiling at y=63 asks for y=64, which is y=0 of the
        //upper band, but relative() computes y=1 in that level and lands inside its floor.
        BlockPos target=CrossBandAction.placeTarget();
        if(target!=null&&level==CrossBandAction.level())return target;

        if(!(player instanceof ServerPlayer serverPlayer))return instance.relative(direction);
        try
        {
            VecTransformer transformer=VecTransformer.getInstance(serverPlayer);
            return transformer.c2sTransform(transformer.s2cTransform(instance).relative(direction));
        }
        catch (Exception e)
        {
            return instance.relative(direction);
        }
    }
}
