package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkMap.class)
public class ChunkMapMixin
{
    @Redirect(method = "getPlayers", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkOnTrackedBorder(Lnet/minecraft/server/level/ServerPlayer;II)Z",ordinal = 0))
    private boolean inject1(ChunkMap instance, ServerPlayer serverPlayer, int i, int j)
    {
        return VecTransformer.instance.isChunkOnTrackedBorder(serverPlayer,i,j);
    }
}
