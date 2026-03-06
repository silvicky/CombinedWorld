package io.silvicky.item.mixin;

import io.silvicky.item.command.world.ImportWorld;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.WorldLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldLoader.DataLoadContext.class)
public class SaveLoadingMixin {
    @Inject(method= "<init>",at=@At("TAIL"))
    private void inject1(ResourceManager resourceManager, WorldDataConfiguration dataConfiguration, HolderLookup.Provider wrapperLookup, RegistryAccess.Frozen immutable, CallbackInfo ci)
    {
        ImportWorld.wrapper=wrapperLookup;
    }
}
