package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.PositionedHelper;
import io.silvicky.item.backrooms.VecTransformer;
import io.silvicky.item.backrooms.PositionedAccess;
import io.silvicky.item.worldgen.WorldGenUtil;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public class ChunkMapMixin
{
    @Redirect(method = "getPlayers", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkOnTrackedBorder(Lnet/minecraft/server/level/ServerPlayer;II)Z",ordinal = 0))
    private boolean inject1(ChunkMap instance, ServerPlayer serverPlayer, int i, int j)
    {
        return PositionedHelper.isChunkOnTrackedBorder(serverPlayer,i,j);
    }
    @Inject(method="applyChunkTrackingView",at=@At("HEAD"))
    private void inject2(ServerPlayer serverPlayer, ChunkTrackingView chunkTrackingView, CallbackInfo ci)
    {
        if(!(chunkTrackingView instanceof ChunkTrackingView.Positioned))return;
        ((PositionedAccess)chunkTrackingView).item_storage$setPlayer(serverPlayer);
        ((PositionedAccess)chunkTrackingView).item_storage$setS2cMap(VecTransformer.getInstance(serverPlayer).getS2c());
    }
    @Inject(method = "updateChunkTracking",at=@At("HEAD"))
    private void inject3(ServerPlayer serverPlayer, CallbackInfo ci)
    {
        VecTransformer.getInstance(serverPlayer).tick();
    }
    @ModifyArg(method = "<init>",at=@At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)Lnet/minecraft/world/level/levelgen/RandomState;", ordinal = 1),index = 0)
    private NoiseGeneratorSettings inject4(NoiseGeneratorSettings settings, @Local(argsOnly = true)ChunkGenerator generator)
    {
        return WorldGenUtil.getNoise(generator);
    }
}
