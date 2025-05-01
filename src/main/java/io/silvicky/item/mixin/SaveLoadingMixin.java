package io.silvicky.item.mixin;

import io.silvicky.item.command.ImportWorld;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.SaveLoading;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SaveLoading.LoadContextSupplierContext.class)
public class SaveLoadingMixin {
    @Inject(method="<init>",at=@At("TAIL"))
    private void inject1(ResourceManager resourceManager, DataConfiguration dataConfiguration, RegistryWrapper.WrapperLookup wrapperLookup, DynamicRegistryManager.Immutable immutable, CallbackInfo ci)
    {
        ImportWorld.wrapper=wrapperLookup;
    }
}
