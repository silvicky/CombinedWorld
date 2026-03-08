package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import io.silvicky.item.helper.PositionedAccess;
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
public class ChunkTrackingViewPositionedMixin implements PositionedAccess
{
    @Shadow
    @Final
    private ChunkPos center;
    @Shadow
    @Final
    private int viewDistance;
    @Unique
    private ServerPlayer item_storage$player;
    @Inject(method = "contains",at=@At("HEAD"), cancellable = true)
    private void inject1(int i, int j, boolean bl, CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(VecTransformer.getInstance(item_storage$player).isWithinDistance(center.x, center.z, viewDistance, i, j, bl));
    }
    @Inject(method = "forEach",at=@At("HEAD"), cancellable = true)
    private void inject2(Consumer<ChunkPos> consumer, CallbackInfo ci)
    {
        VecTransformer.getInstance(item_storage$player).forEachInChunkTrackingView((ChunkTrackingView.Positioned) (Object)this,consumer);
        ci.cancel();
    }

    @Override
    public void item_storage$setPlayer(ServerPlayer player)
    {
        this.item_storage$player =player;
    }

    @Override
    public ServerPlayer item_storage$getPlayer()
    {
        return item_storage$player;
    }
}
