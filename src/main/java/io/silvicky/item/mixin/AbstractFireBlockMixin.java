package io.silvicky.item.mixin;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.InventoryManager.*;

@Mixin(AbstractFireBlock.class)
public class AbstractFireBlockMixin {
    @Inject(method = "isOverworldOrNether",at=@At("HEAD"),cancellable = true)
    private static void injected(World world, CallbackInfoReturnable<Boolean> cir)
    {
        String name=world.getRegistryKey().getValue().toString();
        cir.setReturnValue(name.endsWith(OVERWORLD)||name.endsWith(NETHER));
    }
}
