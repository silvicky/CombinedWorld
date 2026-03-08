package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import io.silvicky.item.helper.PositionedAccess;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ChunkTrackingView.class)
public interface ChunkTrackingViewMixin
{
    @Inject(method = "difference",at= @At("HEAD"), cancellable = true)
    private static void inject1(ChunkTrackingView chunkTrackingView, ChunkTrackingView chunkTrackingView2, Consumer<ChunkPos> consumer, Consumer<ChunkPos> consumer2, CallbackInfo ci)
    {
        if (!chunkTrackingView.equals(chunkTrackingView2))
        {
            if (chunkTrackingView instanceof ChunkTrackingView.Positioned positioned
                    && chunkTrackingView2 instanceof ChunkTrackingView.Positioned positioned2)
            {
                VecTransformer.getInstance(((PositionedAccess)chunkTrackingView).item_storage$getPlayer()).differenceInChunkTrackingView(positioned,positioned2,consumer,consumer2);
            }
            else
            {
                chunkTrackingView.forEach(consumer2);
                chunkTrackingView2.forEach(consumer);
            }
            ci.cancel();
        }
    }
}
