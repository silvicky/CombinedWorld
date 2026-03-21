package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ClientboundLightUpdatePacketData.class)
public class ClientboundLightUpdatePacketDataMixin
{
    @ModifyArg(method = "prepareSectionData",at= @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private Object inject1(Object e, @Local(argsOnly = true)LevelLightEngine lightEngine)
    {
        if(lightEngine.levelHeightAccessor instanceof ServerLevel level
                && StateSaver.getServerState(level.getServer()).darkness
                .getOrDefault(level.dimension.identifier(),false))
        {
            return new DataLayer().getData();
        }
        else return e;
    }
}
