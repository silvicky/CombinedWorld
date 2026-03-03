package io.silvicky.item.mixin;

import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(ChunkDataS2CPacket.class)
public class ChunkDataS2CPacketMixin
{
    @Shadow public int chunkX;

    @Shadow public int chunkZ;

    @Inject(method = "<init>(Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/light/LightingProvider;Ljava/util/BitSet;Ljava/util/BitSet;)V",at=@At("TAIL"))
    public void inject1(WorldChunk chunk, LightingProvider lightProvider, BitSet skyBits, BitSet blockBits, CallbackInfo ci)
    {
        this.chunkX++;
        this.chunkZ++;
    }
}
