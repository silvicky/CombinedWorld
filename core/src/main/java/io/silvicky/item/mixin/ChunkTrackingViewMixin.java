package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.ChunkRemap;
import io.silvicky.item.backrooms.PositionedHelper;
import io.silvicky.item.backrooms.PositionedAccess;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
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
        if (chunkTrackingView.equals(chunkTrackingView2)) return;
        ChunkRemap remap=PositionedHelper.remapOf(chunkTrackingView);
        ChunkRemap otherRemap=PositionedHelper.remapOf(chunkTrackingView2);
        //Identity on either side means there is nothing to remap, so let vanilla compute the difference
        //and send its own packets.
        if (remap.isIdentity()||otherRemap.isIdentity()) return;
        if (remap.kind()!=otherRemap.kind())
        {
            //Different shapes; fall back to key-wise comparison via the general path.
            PositionedHelper.forEachKey(chunkTrackingView,consumer2);
            PositionedHelper.forEachKey(chunkTrackingView2,consumer);
            ci.cancel();
            return;
        }
        if (chunkTrackingView instanceof PositionedAccess access)
        {
            consumer2 = chunkPos ->
            {
                ServerPlayer serverPlayer=access.item_storage$getPlayer();
                if(serverPlayer==null)return;
                ChunkPos mapped=access.item_storage$getRemap().toClient(chunkPos);
                if(mapped==null)return;
                if((!serverPlayer.connection.chunkSender.pendingChunks.remove(chunkPos.pack()))&&serverPlayer.isAlive())
                    serverPlayer.connection.send(new ClientboundForgetLevelChunkPacket(mapped));
            };
        }
        if (chunkTrackingView instanceof ChunkTrackingView.Positioned positioned
                && chunkTrackingView2 instanceof ChunkTrackingView.Positioned positioned2)
        {
            PositionedHelper.differenceInChunkTrackingView(positioned,positioned2,consumer,consumer2);
        }
        else
        {
            PositionedHelper.forEachKey(chunkTrackingView,consumer2);
            PositionedHelper.forEachKey(chunkTrackingView2,consumer);
        }
        ci.cancel();
    }
}
