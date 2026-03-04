package io.silvicky.item.mixin;

import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkDeltaUpdateS2CPacket.class)
public class ChunkDeltaUpdateS2CPacketMixin
{
    @Shadow @Final public ChunkSectionPos sectionPos;

    @Inject(method = "<init>(Lnet/minecraft/util/math/ChunkSectionPos;Lit/unimi/dsi/fastutil/shorts/ShortSet;Lnet/minecraft/world/chunk/ChunkSection;)V",at=@At("TAIL"))
    public void inject1(ChunkSectionPos sectionPos, ShortSet positions, ChunkSection section, CallbackInfo ci)
    {
        this.sectionPos.x++;
        this.sectionPos.z++;
    }
}
