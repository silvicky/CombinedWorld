package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import io.silvicky.item.StateSaver;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Map;

import static io.silvicky.item.StateSaver.server;
import static io.silvicky.item.common.Util.*;

@Mixin(SavedDataStorage.class)
public abstract class SavedDataStorageMixin
{
    @Shadow @Final private DataFixer fixerUpper;
    @Unique
    private static byte playerEquipmentSlotFix(byte i)
    {
        if(i==-106)return 40;
        if(i<0)return (byte) (i+64);//for my own ignorance
        if(i<playerInventorySize)return i;
        return (byte) (i-64);
    }
    @Unique
    private ListTag fixInventory(ListTag inventory, boolean fixSlot, int lastVersion, int currentVersion)
    {
        ListTag newInventory=new ListTag();
        for(Tag j:inventory)
        {
            try
            {
                CompoundTag item = (CompoundTag) j;
                byte slot = item.getByte(SLOT).orElseThrow();
                item.remove(SLOT);
                item = (CompoundTag) fixerUpper.update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item), lastVersion, currentVersion).getValue();
                item.putByte(SLOT, fixSlot ? playerEquipmentSlotFix(slot) : slot);
                newInventory.add(item);
            }
            catch (Exception ignored){}
        }
        return newInventory;
    }
    @Unique
    private void fixSavedDat(CompoundTag savedDat, int lastVersion, int currentVersion)
    {
        try
        {
            ListTag ender = savedDat.getList(ENDER).orElseThrow();
            ListTag newEnder = fixInventory(ender, false, lastVersion, currentVersion);
            savedDat.put(ENDER, newEnder);
        }
        catch (Exception ignored) {}
        try
        {
            ListTag inventory = savedDat.getList(INVENTORY).orElseThrow();
            ListTag newInventory = fixInventory(inventory, true, lastVersion, currentVersion);
            savedDat.put(INVENTORY, newInventory);
        }
        catch (Exception ignored) {}
    }
    @Unique
    private void fixSaved(ListTag saved, int lastVersion, int currentVersion)
    {
        for (Tag i : saved) {
            fixSavedDat((CompoundTag) i,lastVersion,currentVersion);
        }
    }
    @Unique
    private void fixSavedMap(CompoundTag saved, int lastVersion, int currentVersion)
    {
        for (Tag i : saved.values())
        {
            for(Tag j:((CompoundTag)i).values())
            {
                fixSavedDat((CompoundTag) j, lastVersion, currentVersion);
            }
        }
    }
    @Unique
    private void fixDragon(CompoundTag dragon, Identifier level)
    {
        try
        {
            Dynamic<Tag> dragonFight = new Dynamic<>(NbtOps.INSTANCE, dragon);
            Dynamic<Tag> newFight = dragonFight.renameField("NeedsStateScanning", "needs_state_scanning")
                    .renameField("DragonKilled", "dragon_killed")
                    .renameField("PreviouslyKilled", "previously_killed")
                    .renameField("Dragon", "dragon_uuid")
                    .renameField("ExitPortalLocation", "exit_portal_location")
                    .renameField("Gateways", "gateways");
            boolean isRespawning = dragonFight.get("IsRespawning").asBoolean(false);
            if (isRespawning)
            {
                newFight = newFight.set("respawn_stage", newFight.createString("start")).set("respawn_time", newFight.createInt(0));
            }
            newFight = newFight.remove("IsRespawning");
            CompoundTag result=new CompoundTag();
            result.put("data",newFight.getValue());
            NbtUtils.addCurrentDataVersion(result);
            Path path=level.resolveAgainst(server.getWorldPath(LevelResource.ROOT).resolve("dimensions")).resolve("data").resolve("minecraft").resolve("ender_dragon_fight.dat");
            NbtIo.writeCompressed(result,path);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
    @Inject(method = "readSavedData",at= @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderLookup$Provider;createSerializationContext(Lcom/mojang/serialization/DynamicOps;)Lnet/minecraft/resources/RegistryOps;",shift = At.Shift.AFTER))
    public <T extends SavedData> void inject1(SavedDataType<T> type, CallbackInfoReturnable<T> cir, @Local(name = "tag") CompoundTag nbtCompound)
    {
        if(!type.id().equals(StateSaver.id))return;
        final int lastVersion= NbtUtils.getDataVersion(nbtCompound,-1);
        final int currentVersion= SharedConstants.getCurrentVersion().dataVersion().version();
        try
        {
            CompoundTag data = nbtCompound.getCompound("data").orElseThrow();
            try
            {
                ListTag saved = data.getList(SAVED).orElseThrow();
                fixSaved(saved, lastVersion, currentVersion);
            }
            catch (Exception ignored) {}
            try
            {
                CompoundTag saved = data.getCompound(SAVED_MAP).orElseThrow();
                fixSavedMap(saved, lastVersion, currentVersion);
            }
            catch (Exception ignored) {}
            try
            {
                CompoundTag saved = data.getCompound("dragon").orElseThrow();
                for(Map.Entry<String, Tag> entry:saved.entrySet())fixDragon((CompoundTag) entry.getValue(), Identifier.parse(entry.getKey()));
                data.remove("dragon");
            }
            catch (Exception ignored) {}
        }
        catch (Exception ignored){}
        NbtUtils.addDataVersion(nbtCompound,currentVersion);
    }
}
