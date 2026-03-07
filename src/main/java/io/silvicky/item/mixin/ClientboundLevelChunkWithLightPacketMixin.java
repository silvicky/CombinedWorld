package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(ClientboundLevelChunkWithLightPacket.class)
public class ClientboundLevelChunkWithLightPacketMixin
{
    @Shadow public int x;

    @Shadow public int z;

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)V",at=@At("TAIL"))
    public void inject1(LevelChunk chunk, LevelLightEngine lightProvider, BitSet skyBits, BitSet blockBits, CallbackInfo ci)
    {
        ChunkPos pos=VecTransformer.instance.s2cTransform(chunk.getPos());
        this.x = pos.x;
        this.z = pos.z;
    }
}
