package io.silvicky.item.mixin;

import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.common.Util.*;

@Mixin(BaseFireBlock.class)
public class BaseFireBlockMixin
{
    @Inject(method = "inPortalDimension",at=@At("HEAD"),cancellable = true)
    private static void injected(Level world, CallbackInfoReturnable<Boolean> cir)
    {
        String name=world.dimension().identifier().toString();
        cir.setReturnValue(name.endsWith(OVERWORLD)||name.endsWith(NETHER));
    }
}
