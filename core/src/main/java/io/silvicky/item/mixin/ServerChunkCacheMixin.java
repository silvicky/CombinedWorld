package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.DistanceManagerAccess;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands a {@link DistanceManager} the level it belongs to.
 *
 * <p>A {@link DistanceManager} is built from a ticket storage and two executors, and never learns which level it
 * is serving. {@link DistanceManagerMixin} needs that to find a stored per-dimension simulation distance, and
 * this is the only place that knows both halves: the cache is created per level and holds the level.
 *
 * <p>Runs at the tail of the constructor, where both fields are assigned - the level first, then the manager
 * taken from the chunk map. The handler declares no parameters and reads the shadowed fields instead, so it does
 * not have to restate a long constructor descriptor that vanilla is free to change.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin
{
    @Shadow @Final private DistanceManager distanceManager;

    @Shadow @Final private ServerLevel level;

    @Inject(method = "<init>",at=@At("TAIL"))
    private void inject1(CallbackInfo ci)
    {
        ((DistanceManagerAccess)distanceManager).item_storage$setLevel(level);
    }
}
