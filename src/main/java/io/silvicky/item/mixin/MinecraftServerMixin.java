package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.border.WorldBorderListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.common.PublicFields.server;
import static io.silvicky.item.common.Util.sendToAllInWorld;
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin
{
    @Inject(method="createWorlds",at=@At("HEAD"))
    private void inject1(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci)
    {
        server=(MinecraftServer) (Object)this;
    }
    @Inject(method="save",at=@At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerWorldProperties;setWorldBorder(Lnet/minecraft/world/border/WorldBorder$Properties;)V", shift = At.Shift.AFTER))
    private void inject2(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        for(ServerWorld serverWorld: server.getWorlds())
        {
            stateSaver.border.put(serverWorld.getRegistryKey().getValue(),serverWorld.getWorldBorder().write());
        }
    }
    @Redirect(method="createWorlds",at=@At(value = "INVOKE", target = "Lnet/minecraft/world/border/WorldBorder;addListener(Lnet/minecraft/world/border/WorldBorderListener;)V"))
    private void inject3(WorldBorder instance, WorldBorderListener listener)
    {
    }
    @Inject(method="createWorlds",at=@At("TAIL"))
    private void inject4(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci)
    {
        for(ServerWorld serverWorld:server.getWorlds())
        {
            serverWorld.getWorldBorder().addListener(new WorldBorderListener() {
                @Override
                public void onSizeChange(WorldBorder border, double size) {
                    sendToAllInWorld(serverWorld,new WorldBorderSizeChangedS2CPacket(border));
                }

                @Override
                public void onInterpolateSize(WorldBorder border, double fromSize, double toSize, long time) {
                    sendToAllInWorld(serverWorld,new WorldBorderInterpolateSizeS2CPacket(border));
                }

                @Override
                public void onCenterChanged(WorldBorder border, double centerX, double centerZ) {
                    sendToAllInWorld(serverWorld,new WorldBorderCenterChangedS2CPacket(border));
                }

                @Override
                public void onWarningTimeChanged(WorldBorder border, int warningTime) {
                    sendToAllInWorld(serverWorld,new WorldBorderWarningTimeChangedS2CPacket(border));
                }

                @Override
                public void onWarningBlocksChanged(WorldBorder border, int warningBlockDistance) {
                    sendToAllInWorld(serverWorld,new WorldBorderWarningBlocksChangedS2CPacket(border));
                }

                @Override
                public void onDamagePerBlockChanged(WorldBorder border, double damagePerBlock) {
                }

                @Override
                public void onSafeZoneChanged(WorldBorder border, double safeZoneRadius) {
                }
            });
        }
    }
}
