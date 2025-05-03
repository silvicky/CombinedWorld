package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import io.silvicky.item.ItemStorage;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.nbt.*;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.StateSaver.*;

@Mixin(PersistentStateManager.class)
public abstract class PersistentStateManagerMixin {
    @Shadow @Final private DataFixer dataFixer;
    @Inject(method = "readFromFile",at= @At(value = "INVOKE", target = "Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;getOps(Lcom/mojang/serialization/DynamicOps;)Lnet/minecraft/registry/RegistryOps;",shift = At.Shift.AFTER))
    public <T extends PersistentState> void inject1(PersistentStateType<T> type, CallbackInfoReturnable<T> cir, @Local NbtCompound nbtCompound)
    {
        if(!type.id().equals(ItemStorage.MOD_ID))return;
        final int lastVersion= NbtHelper.getDataVersion(nbtCompound,-1);
        final int currentVersion= SharedConstants.getGameVersion().getSaveVersion().getId();
        try
        {
            NbtCompound data=nbtCompound.getCompound("data").get();
            NbtList saved=data.getList(SAVED).get();
            for (NbtElement i : saved) {
                NbtCompound savedDat=(NbtCompound) i;
                try
                {
                    NbtList ender = savedDat.getList(ENDER).get();
                    NbtList newEnder=new NbtList();
                    for(NbtElement j:ender)
                    {
                        NbtCompound item=(NbtCompound) j;
                        byte slot= item.getByte(SLOT).get();
                        item.remove(SLOT);
                        item=(NbtCompound) dataFixer.update(TypeReferences.ITEM_STACK,new Dynamic<>(NbtOps.INSTANCE, item),lastVersion,currentVersion).getValue();
                        item.putByte(SLOT,slot);
                        newEnder.add(item);
                    }
                    savedDat.put(ENDER,newEnder);
                }
                catch(Exception ignored){}
                try
                {
                    NbtList inventory = savedDat.getList(INVENTORY).get();
                    NbtList newInventory=new NbtList();
                    for(NbtElement j:inventory)
                    {
                        NbtCompound item=(NbtCompound) j;
                        byte slot= item.getByte(SLOT).get();
                        item.remove(SLOT);
                        item=(NbtCompound) dataFixer.update(TypeReferences.ITEM_STACK,new Dynamic<>(NbtOps.INSTANCE, item),lastVersion,currentVersion).getValue();
                        item.putByte(SLOT,slot);
                        newInventory.add(item);
                    }
                    savedDat.put(INVENTORY,newInventory);
                }
                catch(Exception ignored){}
            }
            NbtHelper.putDataVersion(nbtCompound,currentVersion);
        }
        catch(Exception ignored) {}
    }
}
