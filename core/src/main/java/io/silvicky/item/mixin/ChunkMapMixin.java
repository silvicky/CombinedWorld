package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import io.silvicky.item.backrooms.ChunkRemap;
import io.silvicky.item.backrooms.PositionedHelper;
import io.silvicky.item.backrooms.VecTransformer;
import io.silvicky.item.backrooms.PositionedAccess;
import io.silvicky.item.worldgen.WorldGenUtil;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin
{
    /**
     * Vanilla's own border test, so the identity case can defer to it instead of reimplementing it.
     *
     * <p>Private on the target, hence the shadow. Declared package-private abstract because Java forbids
     * {@code abstract private}; Mixin matches a shadow on name and descriptor, not on modifiers, so the
     * access widening is only in the mixin class and never reaches the target.
     *
     * <p>The original hook had no way to reach this method, which is why
     * {@link PositionedHelper#isChunkOnTrackedBorder} duplicates the logic - and that duplicate runs once per
     * chunk of the view, per player, per tracking update.
     */
    @Shadow
    protected abstract boolean isChunkOnTrackedBorder(ServerPlayer serverPlayer, int i, int j);

    @Redirect(method = "getPlayers", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkOnTrackedBorder(Lnet/minecraft/server/level/ServerPlayer;II)Z",ordinal = 0))
    private boolean inject1(ChunkMap instance, ServerPlayer serverPlayer, int i, int j)
    {
        //i,j are the chunk coordinates vanilla is iterating the *player's view* in, so they are positions on
        //the player's map. The helper converts them to server positions before using them against server
        //state, which is the correct direction.
        //
        //Bails out immediately for the identity remap. getPlayers calls this once per chunk in the view, per
        //player, per tracking update, so with the nop transformer - the default for an unconfigured
        //dimension, and the one the OOM session ran - every one of those calls was doing a redundant chunk
        //tracking lookup plus up to eight neighbour lookups to reproduce an answer vanilla already computes
        //for itself. Pure overhead on the hot path, and the identity case is the one where the helper's
        //answer is provably identical to vanilla's.
        if (PositionedHelper.remapOf(serverPlayer.getChunkTrackingView()).isIdentity())
        {
            return isChunkOnTrackedBorder(serverPlayer,i,j);
        }
        return PositionedHelper.isChunkOnTrackedBorder(serverPlayer,i,j);
    }
    @Inject(method="applyChunkTrackingView",at=@At("HEAD"))
    private void inject2(ServerPlayer serverPlayer, ChunkTrackingView chunkTrackingView, CallbackInfo ci)
    {
        if(!(chunkTrackingView instanceof PositionedAccess access))return;
        //Hand over the player and nothing else. The view derives its own remap from the player's current
        //transformer, re-deriving whenever that transformer's map changes - see PositionedAccess#getRemap.
        //
        //Pushing a snapshot here instead is what caused the void: this hook only runs when vanilla rebuilds
        //the view, which it does solely when the player's real chunk or the view distance changes. A
        //reshuffle replaces the entire map while leaving the real chunk alone, so the view kept serving the
        //previous shuffle's chunk set, the server tried to send server chunks the live map had forgotten, and
        //those packets were discarded - leaving the client in unloaded space.
        access.item_storage$setPlayer(serverPlayer);
    }
    @Inject(method = "updateChunkTracking",at=@At("HEAD"))
    private void inject3(ServerPlayer serverPlayer, CallbackInfo ci)
    {
        VecTransformer.getInstance(serverPlayer).tick();
    }
    @Redirect(method = "<init>",at=@At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;JZLnet/minecraft/world/level/block/state/BlockState;ILnet/minecraft/world/level/levelgen/NoiseRouter;)Lnet/minecraft/world/level/levelgen/RandomState;",ordinal = 0))
    private RandomState inject5(HolderGetter<NormalNoise> noises, long seed, boolean useLegacyRandom, BlockState defaultBlock, int seaLevel, NoiseRouter noiseRouter, @Local(argsOnly = true)ChunkGenerator generator, @Local RegistryAccess registryAccess)
    {
        return WorldGenUtil.getNoise(registryAccess, seed, generator);
    }

    /**
     * The stored view distance is the server's cap, substituted at the moment it is asked for.
     *
     * <p><b>Why here.</b> This is the only place the effective view distance is decided, and it decides it the
     * way we want anyway:
     *
     * <pre>
     *   return Mth.clamp(player.requestedViewDistance(), 2, this.serverViewDistance);
     * </pre>
     *
     * <p>Vanilla already takes the smaller of what the client asked for and what the server allows. That clamp
     * is kept, with only its upper bound replaced, so a client asking for less than the stored value still gets
     * less - the override is a ceiling, never a floor. A dimension with no stored value is left entirely to
     * vanilla.
     *
     * <p><b>Why a read rather than a write.</b> The command only records a value; nothing here has to be
     * re-applied for it to take effect, because this runs on every tracking update - {@code updateChunkTracking}
     * and {@code updatePlayer} both call it. An earlier version redirected the distance while a
     * {@code ServerLevel} was being constructed and blocked the later global setter from changing it, which
     * meant a stored value only ever took effect on the restart after it was set: the level was already built by
     * the time any command could run, and nothing rebuilt it.
     *
     * <p>{@code getPlayerViewDistance} is final, so it cannot be overwritten, but injection is unaffected by
     * that and it is already opened up in the access widener.
     */
    @Inject(method = "getPlayerViewDistance",at=@At("HEAD"),cancellable = true)
    private void inject6(ServerPlayer player, CallbackInfoReturnable<Integer> cir)
    {
        Integer stored=StateSaver.getServerState(player.level().getServer()).ext.view.get(player.level().dimension.identifier());
        if(stored==null)return;
        cir.setReturnValue(Mth.clamp(player.requestedViewDistance(),2,stored));
    }
}
