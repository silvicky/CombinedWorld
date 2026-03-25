package io.silvicky.item.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.HashMap;
import java.util.Map;

import static io.silvicky.item.command.world.ImportWorld.*;

@Mixin(WorldGenSettings.class)
public class WorldGenSettingsMixin
{
    @ModifyArg(method = "of",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldGenSettings;<init>(Lnet/minecraft/world/level/levelgen/WorldOptions;Lnet/minecraft/world/level/levelgen/WorldDimensions;)V"),index = 1)
    private static WorldDimensions inject1(WorldDimensions dimensionOptionsRegistryHolder)
    {
        HashMap<ResourceKey<LevelStem>, LevelStem> dimensions = new HashMap<>();
        for(Map.Entry<ResourceKey<LevelStem>, LevelStem> i:dimensionOptionsRegistryHolder.dimensions().entrySet())
        {
            if(!deletedDimensions.contains(i.getKey()))dimensions.put(i.getKey(),i.getValue());
        }
        for(Map.Entry<ResourceKey<LevelStem>, LevelStem> i:newDimensions.entrySet())
        {
            if(!deletedDimensions.contains(i.getKey()))dimensions.put(i.getKey(),i.getValue());
        }
        return new WorldDimensions(dimensions);
    }
}
