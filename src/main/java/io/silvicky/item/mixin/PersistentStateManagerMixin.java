package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.nbt.*;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.common.Util.*;

@Mixin(PersistentStateManager.class)
public abstract class PersistentStateManagerMixin {
    @Shadow @Final private DataFixer dataFixer;
    @Unique
    private static byte playerEquipmentSlotFix(byte i)
    {
        if(i==-106)return 40;
        if(i<0)return (byte) (i+64);//for my own ignorance
        if(i<playerInventorySize)return i;
        return (byte) (i-64);
    }
    @Unique
    private NbtList fixInventory(NbtList inventory, boolean fixSlot, int lastVersion, int currentVersion)
    {
        NbtList newInventory=new NbtList();
        for(NbtElement j:inventory)
        {
            try
            {
                NbtCompound item = (NbtCompound) j;
                byte slot = item.getByte(SLOT).orElseThrow();
                item.remove(SLOT);
                item = (NbtCompound) dataFixer.update(TypeReferences.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item), lastVersion, currentVersion).getValue();
                item.putByte(SLOT, fixSlot ? playerEquipmentSlotFix(slot) : slot);
                newInventory.add(item);
            }
            catch (Exception ignored){}
        }
        return newInventory;
    }
    @Unique
    private void fixSavedDat(NbtCompound savedDat, int lastVersion, int currentVersion)
    {
        try
        {
            NbtList ender = savedDat.getList(ENDER).orElseThrow();
            NbtList newEnder = fixInventory(ender, false, lastVersion, currentVersion);
            savedDat.put(ENDER, newEnder);
        }
        catch (Exception ignored) {}
        try
        {
            NbtList inventory = savedDat.getList(INVENTORY).orElseThrow();
            NbtList newInventory = fixInventory(inventory, true, lastVersion, currentVersion);
            savedDat.put(INVENTORY, newInventory);
        }
        catch (Exception ignored) {}
    }
    @Unique
    private void fixSaved(NbtList saved, int lastVersion, int currentVersion)
    {
        for (NbtElement i : saved) {
            fixSavedDat((NbtCompound) i,lastVersion,currentVersion);
        }
    }
    @Inject(method = "readFromFile",at= @At(value = "INVOKE", target = "Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;getOps(Lcom/mojang/serialization/DynamicOps;)Lnet/minecraft/registry/RegistryOps;",shift = At.Shift.AFTER))
    public <T extends PersistentState> void inject1(PersistentStateType<T> type, CallbackInfoReturnable<T> cir, @Local NbtCompound nbtCompound)
    {
        if(!type.id().equals(MOD_ID))return;
        final int lastVersion= NbtHelper.getDataVersion(nbtCompound,-1);
        final int currentVersion= SharedConstants.getGameVersion().dataVersion().id();
        try
        {
            NbtCompound data=nbtCompound.getCompound("data").orElseThrow();
            NbtList saved=data.getList(SAVED).orElseThrow();
            fixSaved(saved,lastVersion,currentVersion);
        }
        catch(Exception ignored) {}
        NbtHelper.putDataVersion(nbtCompound,currentVersion);
    }
}
