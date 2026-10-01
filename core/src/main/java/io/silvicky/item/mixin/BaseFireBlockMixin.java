package io.silvicky.item.mixin;

import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.Level;
import io.silvicky.item.common.Sides;
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
        //Portal placement is a server decision. The client asks the same question while rendering, and this
        //hook would otherwise answer it for the client too - see Sides for why a config cannot do this.
        if(!Sides.isServer(world))return;
        String name=world.dimension().identifier().toString();
        cir.setReturnValue(name.endsWith(OVERWORLD)||name.endsWith(NETHER));
    }
}
