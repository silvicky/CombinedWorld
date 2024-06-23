package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import javax.swing.text.html.parser.Entity;

import static io.silvicky.item.InventoryManager.*;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {
    @ModifyVariable(method = "createTeleportTarget", at = @At(value = "STORE"),ordinal =0)
    private RegistryKey<World> injected(RegistryKey<World> registryKey, @Local(argsOnly = true) ServerWorld world) {
        RegistryKey<World> registryKey0=world.getRegistryKey();
        String path=registryKey0.getValue().getPath();

        if(registryKey0.getValue().getPath().endsWith(OVERWORLD))
        {
            return RegistryKey.of(RegistryKey.ofRegistry(registryKey0.getRegistry()),
                    Identifier.of(registryKey0.getValue().getNamespace(),
                            path.substring(0,path.length()-9)+NETHER));
        }
        else if(registryKey0.getValue().getPath().endsWith(NETHER))
        {
            return RegistryKey.of(RegistryKey.ofRegistry(registryKey0.getRegistry()),
                    Identifier.of(registryKey0.getValue().getNamespace(),
                            path.substring(0,path.length()-10)+OVERWORLD));
        }
        else
        {
            return registryKey0;
        }
    }
}
