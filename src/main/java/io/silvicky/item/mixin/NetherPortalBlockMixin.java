package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static io.silvicky.item.common.Util.*;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin
{
    @ModifyVariable(method = "getPortalDestination", at = @At(value = "STORE"),ordinal =0)
    private ResourceKey<Level> injected(ResourceKey<Level> registryKey, @Local(argsOnly = true) ServerLevel world) {
        ResourceKey<Level> registryKey0=world.dimension();
        String path=registryKey0.identifier().getPath();

        if(registryKey0.identifier().getPath().endsWith(OVERWORLD))
        {
            return ResourceKey.create(ResourceKey.createRegistryKey(registryKey0.registry()),
                    Identifier.fromNamespaceAndPath(registryKey0.identifier().getNamespace(),
                            path.substring(0,path.length()-9)+NETHER));
        }
        else if(registryKey0.identifier().getPath().endsWith(NETHER))
        {
            return ResourceKey.create(ResourceKey.createRegistryKey(registryKey0.registry()),
                    Identifier.fromNamespaceAndPath(registryKey0.identifier().getNamespace(),
                            path.substring(0,path.length()-10)+OVERWORLD));
        }
        else
        {
            return registryKey0;
        }
    }
}
