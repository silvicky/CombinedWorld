package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.common.Util.*;

@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin
{
    @ModifyVariable(method = "getPortalDestination", at = @At(value = "STORE"), name = "newDimension")
    private ResourceKey<Level> injected(ResourceKey<Level> registryKey, @Local(argsOnly = true) ServerLevel world) {
        ResourceKey<Level> registryKey0=world.dimension();
        String path=registryKey0.identifier().getPath();
        if(registryKey0.identifier().getPath().endsWith(OVERWORLD))
        {
            return ResourceKey.create(ResourceKey.createRegistryKey(registryKey0.registry()),
                    Identifier.fromNamespaceAndPath(registryKey0.identifier().getNamespace(),
                            path.substring(0,path.length()-9)+ END));
        }
        else if(registryKey0.identifier().getPath().endsWith(NETHER))
        {
            return ResourceKey.create(ResourceKey.createRegistryKey(registryKey0.registry()),
                    Identifier.fromNamespaceAndPath(registryKey0.identifier().getNamespace(),
                            path.substring(0,path.length()-10)+ END));
        }
        else if(registryKey0.identifier().getPath().endsWith(END))
        {
            return ResourceKey.create(ResourceKey.createRegistryKey(registryKey0.registry()),
                    Identifier.fromNamespaceAndPath(registryKey0.identifier().getNamespace(),
                            path.substring(0,path.length()-7)+ OVERWORLD));
        }
        else
        {
            return registryKey0;
        }
    }
    @Inject(method = "getPortalDestination",at = @At(value = "INVOKE_ASSIGN",  shift = At.Shift.AFTER, target = "Lnet/minecraft/server/level/ServerLevel;getServer()Lnet/minecraft/server/MinecraftServer;"), cancellable = true)
    private void injected3(ServerLevel world, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> cir
    , @Local(name = "newDimension") ResourceKey<Level> registryKey)
    {
        ServerLevel serverWorld = world.getServer().getLevel(registryKey);
        boolean bl=registryKey.identifier().getPath().endsWith(END);
        BlockPos blockPos = bl ? ServerLevel.END_SPAWN_POINT : serverWorld.getRespawnData().pos();
        Vec3 vec3d = blockPos.getBottomCenter();
        if (bl) {
                EndPlatformFeature.createEndPlatform(serverWorld, BlockPos.containing(vec3d).below(), true);
                if (entity instanceof ServerPlayer) {
                    vec3d = vec3d.subtract(0.0, 1.0, 0.0);
                }
            } else {
                if (entity instanceof ServerPlayer serverPlayerEntity) {
                    if(toOverworld(world.getServer(),serverPlayerEntity.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING).newLevel()).equals(toOverworld(world.getServer(),serverWorld))){
                    cir.setReturnValue(serverPlayerEntity.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING));
                    return;
                    }
                }

                vec3d = entity.adjustSpawnLocation(serverWorld, blockPos).getBottomCenter();
            }
            cir.setReturnValue(new TeleportTransition(serverWorld, vec3d, entity.getDeltaMovement(), 0, entity.getXRot(), TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
    }
}
