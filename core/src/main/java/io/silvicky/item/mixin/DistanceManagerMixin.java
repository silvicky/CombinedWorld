package io.silvicky.item.mixin;

import io.silvicky.item.StateSaver;
import io.silvicky.item.backrooms.DistanceManagerAccess;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the stored simulation distance act as the server's cap, the way the stored view distance does.
 *
 * <p><b>The problem this solves.</b> The simulation distance is applied in one place:
 *
 * <pre>
 *   private int getPlayerTicketLevel() {
 *       return Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - this.simulationDistance);
 *   }
 * </pre>
 *
 * <p>That is the exact counterpart of the {@code Mth.clamp(player.requestedViewDistance(), 2, serverViewDistance)}
 * the view distance uses, and it is the only place the value is read. But {@link DistanceManager} is
 * constructed with {@code (TicketStorage, Executor, Executor)} and holds no reference to the level it belongs
 * to - only trackers and ticket storage. So unlike the view distance, there was no way to look up which
 * dimension's stored value applied: the correct answer was unreachable from here.
 *
 * <p>The back-reference is supplied by {@link ServerChunkCacheMixin}, which does have the level. Keeping it as a
 * separate mixin leaves {@link DistanceManager} itself untouched apart from one injected field.
 *
 * <p><b>Why a read rather than a write.</b> The command only records a value. This runs on every ticket add and
 * remove, so a change takes effect at once - no re-applying it to the running level, and no waiting for the
 * level to be rebuilt. An earlier version redirected the distance while a {@code ServerLevel} was being
 * constructed, which meant a stored value only took effect on the restart after it was set.
 *
 * <p>A dimension with no stored value is left entirely to vanilla, and the arithmetic is vanilla's own - only
 * the number subtracted at the end changes.
 */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin implements DistanceManagerAccess
{
    /** The level this manager belongs to, so a stored per-dimension value can be found. Null before setup. */
    @Unique private @Nullable ServerLevel item_storage$level;

    @Unique
    @Override
    public void item_storage$setLevel(ServerLevel level)
    {
        this.item_storage$level=level;
    }

    @Inject(method = "getPlayerTicketLevel",at=@At("HEAD"),cancellable = true)
    private void inject1(CallbackInfoReturnable<Integer> cir)
    {
        ServerLevel level=item_storage$level;
        if(level==null)return;
        Integer stored=StateSaver.getServerState(level.getServer()).ext.sim.get(level.dimension().identifier());
        if(stored==null)return;
        cir.setReturnValue(Math.max(0,ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING)-stored));
    }
}
