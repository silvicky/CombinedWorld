package io.silvicky.item.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.backrooms.DarknessManager.getCalculation;
import static io.silvicky.item.backrooms.DarknessManager.isCalculationModified;

@Mixin(LightEngine.class)
public class LightEngineMixin
{
    @Shadow
    @Final
    protected LightChunkGetter chunkSource;

    @Inject(method = "getLightValue",at=@At("HEAD"),cancellable = true)
    private void inject1(BlockPos blockPos, CallbackInfoReturnable<Integer> cir)
    {
        if(chunkSource instanceof ServerChunkCache cache
        &&cache.getLevel() instanceof ServerLevel level
        &&isCalculationModified(level))
        {
            cir.setReturnValue(getCalculation(level));
        }
    }
}
