package io.silvicky.item.mixin;

import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.BitSet;
import java.util.List;

import static io.silvicky.item.backrooms.DarknessManager.getRender;
import static io.silvicky.item.backrooms.DarknessManager.isRenderModified;

@Mixin(ClientboundLightUpdatePacketData.class)
public abstract class ClientboundLightUpdatePacketDataMixin
{
    @Shadow
    protected abstract void prepareSectionData(ChunkPos chunkPos, LevelLightEngine levelLightEngine, LightLayer lightLayer, int i, BitSet bitSet, BitSet bitSet2, List<byte[]> list);

    @Redirect(method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)V",at= @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLightUpdatePacketData;prepareSectionData(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/lighting/LevelLightEngine;Lnet/minecraft/world/level/LightLayer;ILjava/util/BitSet;Ljava/util/BitSet;Ljava/util/List;)V"))
    private void inject2(ClientboundLightUpdatePacketData instance, ChunkPos chunkPos, LevelLightEngine levelLightEngine, LightLayer lightLayer, int i, BitSet bitSet, BitSet bitSet2, List<byte[]> list)
    {
        if(levelLightEngine.levelHeightAccessor instanceof ServerLevel level
                && isRenderModified(level))
        {
            bitSet.set(i);
            list.add(new DataLayer(getRender(level)).getData());
        }
        else prepareSectionData(chunkPos,levelLightEngine,lightLayer,i,bitSet,bitSet2,list);

    }
}
