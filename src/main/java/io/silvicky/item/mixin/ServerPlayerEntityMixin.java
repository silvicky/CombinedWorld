package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.cfg.JSONConfig.useStorage;
import static io.silvicky.item.common.Util.getDimensionId;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin
{
    @Shadow public abstract ServerWorld getEntityWorld();

    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getLevelProperties()Lnet/minecraft/world/WorldProperties;"))
    private void inject1(TeleportTarget teleportTarget, CallbackInfoReturnable<ServerPlayerEntity> cir)
    {
        //TODO Check if warp is allowed
        Identifier source=getEntityWorld().getRegistryKey().getValue();
        Identifier target=teleportTarget.world().getRegistryKey().getValue();
        ServerPlayerEntity entity=(ServerPlayerEntity) (Object)this;
        StateSaver stateSaver=StateSaver.getServerState(getEntityWorld().getServer());
        if(getDimensionId(source.toString()).equals(getDimensionId(target.toString())))return;
        savePos(entity, stateSaver);
        if(!source.getNamespace().equals(target.getNamespace()))
        {
            if(useStorage)saveInventory(entity, stateSaver);
        }
    }
    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;teleportSpectatingPlayers(Lnet/minecraft/world/TeleportTarget;Lnet/minecraft/server/world/ServerWorld;)V"))
    private void inject2(TeleportTarget teleportTarget, CallbackInfoReturnable<ServerPlayerEntity> cir, @Local(ordinal=1) ServerWorld serverWorld2)
    {
        Identifier source=serverWorld2.getRegistryKey().getValue();
        Identifier target=teleportTarget.world().getRegistryKey().getValue();
        ServerPlayerEntity entity=(ServerPlayerEntity) (Object)this;
        StateSaver stateSaver=StateSaver.getServerState(getEntityWorld().getServer());
        if(getDimensionId(source.toString()).equals(getDimensionId(target.toString())))return;
        if(!source.getNamespace().equals(target.getNamespace()))
        {
            if(useStorage)
            {
                try
                {
                    loadInventory(entity, teleportTarget.world(), stateSaver);
                }
                catch(Exception e)
                {
                    //TODO Any way to rollback?
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
