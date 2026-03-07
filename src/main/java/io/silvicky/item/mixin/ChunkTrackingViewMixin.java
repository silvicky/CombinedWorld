package io.silvicky.item.mixin;

import net.minecraft.server.level.ChunkTrackingView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkTrackingView.class)
public interface ChunkTrackingViewMixin
{
    @Redirect(method = "difference",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkTrackingView$Positioned;squareIntersects(Lnet/minecraft/server/level/ChunkTrackingView$Positioned;)Z"))
    private static boolean inject1(ChunkTrackingView.Positioned instance, ChunkTrackingView.Positioned positioned)
    {
        //TODO
        return false;
    }
}
