package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.ChunkRemap;
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

import java.util.function.Consumer;

@Mixin(ChunkTrackingView.Positioned.class)
public class ChunkTrackingViewPositionedMixin implements PositionedAccess
{
    @Unique
    private ServerPlayer item_storage$player;
    @Unique
    private ChunkRemap item_storage$remap=ChunkRemap.IDENTITY;

    @Inject(method = "contains",at=@At("HEAD"), cancellable = true)
    private void inject1(int i, int j, boolean bl, CallbackInfoReturnable<Boolean> cir)
    {
        //Identity and translation both already agree with vanilla here: ChunkTrackingView.isWithinDistance
        //works purely on abs(a-b), and a constant offset shifts both arguments equally. So only a general
        //mapping needs to intercept, and the two cheap cases cost nothing at all.
        ChunkRemap remap=item_storage$getRemap();
        if(!remap.needsEnumeration())return;
        cir.setReturnValue(remap.isWithinDistance((ChunkTrackingView.Positioned)(Object)this,new ChunkPos(i,j),bl));
    }

    @Inject(method = "forEach",at=@At("HEAD"), cancellable = true)
    private void inject2(Consumer<ChunkPos> consumer, CallbackInfo ci)
    {
        //Identity and translation both enumerate exactly vanilla's chunk set, so let vanilla walk it.
        ChunkRemap remap=item_storage$getRemap();
        if(!remap.needsEnumeration())return;
        remap.forEach((ChunkTrackingView.Positioned)(Object)this,consumer);
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
        return this.item_storage$player;
    }

    @Override
    public void item_storage$setRemap(ChunkRemap remap)
    {
        this.item_storage$remap=remap==null?ChunkRemap.IDENTITY:remap;
    }

    @Override
    public ChunkRemap item_storage$getRemap()
    {
        return this.item_storage$remap;
    }
}
