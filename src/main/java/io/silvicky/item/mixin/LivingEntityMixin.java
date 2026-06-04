package io.silvicky.item.mixin;

import io.silvicky.item.StateSaver;
import io.silvicky.item.common.WeightedSelector;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static io.silvicky.item.InventoryManager.loadPos;
import static io.silvicky.item.InventoryManager.removePos;
import static io.silvicky.item.StateSaver.server;

@Mixin(LivingEntity.class)
public class LivingEntityMixin
{
    @Inject(method = "onBelowWorld",at=@At("HEAD"), cancellable = true)
    private void inject1(CallbackInfo ci)
    {
        LivingEntity instance=(LivingEntity) (Object)this;
        if(!(instance instanceof ServerPlayer player))return;
        StateSaver stateSaver=StateSaver.getServerState(instance.level().getServer());
        Identifier source=instance.level().dimension.identifier();
        WeightedSelector<Identifier> selector= stateSaver.ext.noclipVoid.computeIfAbsent(source, _->new WeightedSelector<>());
        if(selector.asMap().isEmpty())return;
        Identifier target=selector.select();
        if(target==null)ci.cancel();
        else
        {
            ServerLevel level=player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,target));
            try{loadPos(server, player, level, stateSaver);removePos(player,stateSaver,source);}
            catch (Exception e){throw new RuntimeException(e);}
        }
    }
}
