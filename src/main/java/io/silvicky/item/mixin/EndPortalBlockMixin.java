package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.EndPlatformFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.InventoryManager.*;

@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {
    @ModifyVariable(method = "createTeleportTarget", at = @At(value = "STORE"),ordinal =0)
    private RegistryKey<World> injected(RegistryKey<World> registryKey, @Local(argsOnly = true) ServerWorld world) {
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
    /*@ModifyVariable(method = "createTeleportTarget", at = @At(value = "STORE"))
    private boolean injected2(boolean bl, @Local RegistryKey<World> registryKey) {
        return registryKey.getValue().getPath().endsWith(END);
    }*/
    @Inject(method = "createTeleportTarget",at = @At(value = "INVOKE_ASSIGN",  shift = At.Shift.AFTER, target = "Lnet/minecraft/server/world/ServerWorld;getServer()Lnet/minecraft/server/MinecraftServer;"), cancellable = true)
    private void injected3(ServerWorld world, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTarget> cir
    , @Local(ordinal = 0, argsOnly = true) ServerWorld serverWorl, @Local RegistryKey<World> registryKey)
    {
        ServerWorld serverWorld = world.getServer().getWorld(registryKey);
        boolean bl=registryKey.getValue().getPath().endsWith(END);
        BlockPos blockPos = bl ? ServerWorld.END_SPAWN_POS : serverWorld.getSpawnPos();
        Vec3d vec3d = blockPos.toBottomCenterPos();
        if (bl) {
                EndPlatformFeature.generate(serverWorld, BlockPos.ofFloored(vec3d).down(), true);
                if (entity instanceof ServerPlayerEntity) {
                    vec3d = vec3d.subtract(0.0, 1.0, 0.0);
                }
            } else {
                if (entity instanceof ServerPlayerEntity serverPlayerEntity) {
                    if(toOverworld(world.getServer(),serverPlayerEntity.getRespawnTarget(false, TeleportTarget.NO_OP).world()).equals(toOverworld(world.getServer(),serverWorld))){
                    cir.setReturnValue(serverPlayerEntity.getRespawnTarget(false, TeleportTarget.NO_OP));
                    return;
                    }
                }

                vec3d = entity.getWorldSpawnPos(serverWorld, blockPos).toBottomCenterPos();
            }
            cir.setReturnValue(new TeleportTarget(serverWorld, vec3d, entity.getVelocity(), 0, entity.getPitch(), TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET.then(TeleportTarget.ADD_PORTAL_CHUNK_TICKET)));
    }
}
