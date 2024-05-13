package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static io.silvicky.item.InventoryManager.*;

@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {
    @ModifyVariable(method = "onEntityCollision", at = @At(value = "STORE"),ordinal =0)
    private RegistryKey<World> injected(RegistryKey<World> registryKey, @Local(argsOnly = true) World world) {
        RegistryKey<World> registryKey0=world.getRegistryKey();
        String path=registryKey0.getValue().getPath();
        if(registryKey0.getValue().getPath().endsWith(OVERWORLD))
        {
            return RegistryKey.of(RegistryKey.ofRegistry(registryKey0.getRegistry()),
                    Identifier.of(registryKey0.getValue().getNamespace(),
                            path.substring(0,path.length()-9)+END));
        }
        else if(registryKey0.getValue().getPath().endsWith(NETHER))
        {
            return RegistryKey.of(RegistryKey.ofRegistry(registryKey0.getRegistry()),
                    Identifier.of(registryKey0.getValue().getNamespace(),
                            path.substring(0,path.length()-10)+END));
        }
        else if(registryKey0.getValue().getPath().endsWith(END))
        {
            return RegistryKey.of(RegistryKey.ofRegistry(registryKey0.getRegistry()),
                    Identifier.of(registryKey0.getValue().getNamespace(),
                            path.substring(0,path.length()-7)+OVERWORLD));
        }
        else
        {
            return registryKey0;
        }
    }
    @Inject(method = "onEntityCollision",at= @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;moveToWorld(Lnet/minecraft/server/world/ServerWorld;)Lnet/minecraft/entity/Entity;"))
    public void createPlatform(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci,@Local ServerWorld serverWorld)
    {
        if(world.getRegistryKey().getValue().getNamespace().equals(MC))return;
        ServerWorld.createEndSpawnPlatform(serverWorld);
    }
}
