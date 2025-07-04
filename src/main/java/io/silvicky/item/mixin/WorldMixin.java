package io.silvicky.item.mixin;

import io.silvicky.item.StateSaver;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static io.silvicky.item.common.PublicFields.*;
@Mixin(World.class)
public abstract class WorldMixin
{
    @Shadow
    public WorldBorder border;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void injected(MutableWorldProperties properties, RegistryKey<World> registryRef, DynamicRegistryManager registryManager, RegistryEntry<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates, CallbackInfo ci)
    {
        if(registryRef.getValue().equals(Identifier.of("minecraft:overworld")))return;
        StateSaver stateSaver=StateSaver.getServerState(server);
        WorldBorder.Properties properties1=stateSaver.border.get(registryRef.getValue());
        if(properties1!=null)border.load(properties1);
    }
}
