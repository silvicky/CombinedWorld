package io.silvicky.item.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static io.silvicky.item.backrooms.DarknessManager.isRenderModified;

@Mixin(LayerLightSectionStorage.class)
public class LayerLightSectionStorageMixin
{
    @Redirect(method = "swapSectionMap",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LightChunkGetter;onLightUpdate(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V"))
    private void inject1(LightChunkGetter instance, LightLayer lightLayer, SectionPos sectionPos)
    {
        if(instance instanceof ServerChunkCache cache
                && cache.getLevel() instanceof ServerLevel level
                && isRenderModified(level))
        {
            for (int i = -1; i <= 1; i++)
                for (int j = -1; j <= 1; j++)
                    for (int k = -1; k <= 1; k++)
                    {
                        instance.onLightUpdate(lightLayer, sectionPos.offset(i, j, k));
                    }
        }
        else instance.onLightUpdate(lightLayer,sectionPos);
    }
}
