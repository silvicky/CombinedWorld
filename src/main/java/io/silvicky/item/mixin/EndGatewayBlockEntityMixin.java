package io.silvicky.item.mixin;

import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static io.silvicky.item.InventoryManager.END;

@Mixin(EndGatewayBlockEntity.class)
public class EndGatewayBlockEntityMixin {
    @Redirect(method="getOrCreateExitPortalPos",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerWorld;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"))
    private RegistryKey<World> inject1(ServerWorld instance)
    {
        RegistryKey<World> registryKey=instance.getRegistryKey();
        if(registryKey.getValue().getPath().endsWith(END))return World.END;
        else return registryKey;
    }
}
