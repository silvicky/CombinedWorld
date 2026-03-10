package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.PositionedHelper;
import io.silvicky.item.backrooms.PositionedAccess;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(ChunkTrackingView.Positioned.class)
public class ChunkTrackingViewPositionedMixin implements PositionedAccess
{
    @Unique
    private ServerPlayer item_storage$player;
    @Unique
    private Map<ChunkPos,ChunkPos> item_storage$s2cMap;
    @Inject(method = "contains",at=@At("HEAD"), cancellable = true)
    private void inject1(int i, int j, boolean bl, CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(PositionedHelper.isWithinDistance((ChunkTrackingView.Positioned) (Object)this,new ChunkPos(i,j),bl));
    }
    @Inject(method = "forEach",at=@At("HEAD"), cancellable = true)
    private void inject2(Consumer<ChunkPos> consumer, CallbackInfo ci)
    {
        //TODO
        PositionedHelper.forEachKey((ChunkTrackingView.Positioned) (Object)this,consumer);
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

    @Override
    public void item_storage$setS2cMap(Map<ChunkPos, ChunkPos> s2cMap)
    {
        this.item_storage$s2cMap=s2cMap;
    }

    @Override
    public Map<ChunkPos, ChunkPos> item_storage$getS2cMap()
    {
        return this.item_storage$s2cMap;
    }
}
