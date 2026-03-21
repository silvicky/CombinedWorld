package io.silvicky.item.mixin;

import io.silvicky.item.StateSaver;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LayerLightSectionStorage.class)
public class LayerLightSectionStorageMixin
{
    @Redirect(method = "swapSectionMap",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LightChunkGetter;onLightUpdate(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V"))
    private void inject1(LightChunkGetter instance, LightLayer lightLayer, SectionPos sectionPos)
    {
        if(instance instanceof ServerChunkCache cache
                && cache.getLevel().getServer()!=null
                && StateSaver.getServerState(cache.getLevel().getServer()).darkness
                .getOrDefault(cache.getLevel().dimension.identifier(),false))
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
