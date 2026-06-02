package io.silvicky.item.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

import static io.silvicky.item.StateSaver.server;
import static io.silvicky.item.command.world.ImportWorld.deletedDimensions;
import static io.silvicky.item.command.world.ImportWorld.newDimensions;

@Mixin(LevelStorageSource.LevelStorageAccess.class)
public class LevelStorageAccessMixin
{
    @Shadow
    @Final
    private LevelStorageSource.LevelDirectory levelDirectory;

    @Inject(method = "saveLevelData(Lnet/minecraft/nbt/CompoundTag;)V",at = @At(value = "INVOKE",target = "Lnet/minecraft/util/Util;safeReplaceFile(Ljava/nio/file/Path;Ljava/nio/file/Path;Ljava/nio/file/Path;)V"))
    private void inject1(CompoundTag root, CallbackInfo ci)
    {
        if(server==null)return;
        WorldGenSettings settings=server.getWorldGenSettings();
        HashMap<ResourceKey<LevelStem>, LevelStem> dimensions = new HashMap<>();
        for(Map.Entry<ResourceKey<LevelStem>, LevelStem> i:settings.dimensions().dimensions().entrySet())
        {
            if(!deletedDimensions.contains(i.getKey()))dimensions.put(i.getKey(),i.getValue());
        }
        for(Map.Entry<ResourceKey<LevelStem>, LevelStem> i:newDimensions.entrySet())
        {
            if(!deletedDimensions.contains(i.getKey()))dimensions.put(i.getKey(),i.getValue());
        }
        settings=new WorldGenSettings(settings.options(), new WorldDimensions(dimensions));
        try
        {
            LevelStorageSource.writeWorldGenSettings(server.registryAccess(), levelDirectory.path(), settings);
        }
        catch (Exception e){throw new RuntimeException(e);}
    }
}
