package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.ChunkRemap;
import io.silvicky.item.backrooms.PositionedHelper;
import io.silvicky.item.backrooms.PositionedAccess;
import io.silvicky.item.backrooms.VecTransformer;
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
    /** Which transformer {@link #item_storage$remap} was built from, and at what version. */
    @Unique
    private VecTransformer item_storage$remapSource;
    @Unique
    private long item_storage$remapVersion=-1;

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
    public ChunkRemap item_storage$getRemap()
    {
        //Identity until the view has been given a player, which is what lets a nop dimension run vanilla's
        //tracking untouched.
        if(item_storage$player==null)return ChunkRemap.IDENTITY;
        //peekInstance, not getInstance: this runs on every containment query, and a containment query must
        //never be the thing that builds a transformer.
        VecTransformer transformer=VecTransformer.peekInstance(item_storage$player);
        if(transformer==null)return ChunkRemap.IDENTITY;
        //A cache hit is two reference/int comparisons. The identity test matters as much as the version
        //one: after a reshuffle the player gets a brand new transformer whose version starts at zero, so a
        //new instance can easily carry the same number as the old one and only the reference tells them
        //apart. Without this the view keeps serving the previous shuffle's chunk set forever.
        if(transformer!=item_storage$remapSource||transformer.mapVersion()!=item_storage$remapVersion)
        {
            item_storage$remapSource=transformer;
            item_storage$remapVersion=transformer.mapVersion();
            ChunkRemap fresh=transformer.getRemap();
            item_storage$remap=fresh==null?ChunkRemap.IDENTITY:fresh;
        }
        return item_storage$remap;
    }
}
