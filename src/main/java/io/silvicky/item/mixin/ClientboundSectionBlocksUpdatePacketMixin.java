package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.VecTransformer;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundSectionBlocksUpdatePacket.class)
public class ClientboundSectionBlocksUpdatePacketMixin
{
    @Inject(method = "<init>(Lnet/minecraft/core/SectionPos;Lit/unimi/dsi/fastutil/shorts/ShortSet;Lnet/minecraft/world/level/chunk/LevelChunkSection;)V",at=@At("TAIL"))
    public void inject1(SectionPos sectionPos, ShortSet positions, LevelChunkSection section, CallbackInfo ci)
    {
        ((ClientboundSectionBlocksUpdatePacket)(Object)this).sectionPos= VecTransformer.instance.s2cTransform(sectionPos);
    }
}
