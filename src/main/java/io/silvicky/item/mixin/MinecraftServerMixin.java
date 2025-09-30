package io.silvicky.item.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static io.silvicky.item.common.PublicFields.server;
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin
{
    @Inject(method="createWorlds",at=@At("HEAD"))
    private void inject1(CallbackInfo ci)
    {
        server=(MinecraftServer) (Object)this;
    }
}
