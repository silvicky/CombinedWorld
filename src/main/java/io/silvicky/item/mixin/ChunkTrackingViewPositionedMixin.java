package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ChunkTrackingView.Positioned.class)
public class ChunkTrackingViewPositionedMixin
{
    @Shadow
    @Final
    private ChunkPos center;
    @Shadow
    @Final
    private int viewDistance;
    @Unique
    private ServerPlayer player;
    @Inject(method = "contains",at=@At("HEAD"), cancellable = true)
    private void inject1(int i, int j, boolean bl, CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(VecTransformer.instance.isWithinDistance(center.x, center.z, viewDistance, i, j, bl));
    }
    @Inject(method = "forEach",at=@At("HEAD"), cancellable = true)
    private void inject2(Consumer<ChunkPos> consumer, CallbackInfo ci)
    {
        VecTransformer.instance.forEachInChunkTrackingView((ChunkTrackingView.Positioned) (Object)this,consumer);
        ci.cancel();
    }
}
