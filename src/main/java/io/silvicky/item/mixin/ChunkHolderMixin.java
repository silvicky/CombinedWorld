package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.BitSet;

import static io.silvicky.item.backrooms.DarknessManager.isRenderModified;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin
{
    @ModifyArg(method = "broadcastChanges",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;",ordinal = 0))
    private boolean inject2(boolean bl, @Local(argsOnly = true)LevelChunk chunk)
    {
        if(chunk.getLevel() instanceof ServerLevel serverLevel
        && isRenderModified(serverLevel))
        {
            return false;
        }
        else return bl;
    }
    @ModifyArgs(method = "broadcastChanges",at= @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLightUpdatePacket;<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)V"))
    private void inject3(Args args, @Local(argsOnly = true)LevelChunk chunk)
    {
        if(chunk.getLevel() instanceof ServerLevel serverLevel
                && isRenderModified(serverLevel))
        {
            BitSet x=args.get(2);
            BitSet y=args.get(3);
            x.or(y);
            y.or(x);
        }
    }
}
