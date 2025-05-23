package io.silvicky.item.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.level.WorldGenSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.HashMap;
import java.util.Map;

import static io.silvicky.item.command.world.ImportWorld.*;

@Mixin(WorldGenSettings.class)
public class WorldGenSettingsMixin {
    @ModifyArg(method = "encode(Lcom/mojang/serialization/DynamicOps;Lnet/minecraft/world/gen/GeneratorOptions;Lnet/minecraft/world/dimension/DimensionOptionsRegistryHolder;)Lcom/mojang/serialization/DataResult;",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenSettings;<init>(Lnet/minecraft/world/gen/GeneratorOptions;Lnet/minecraft/world/dimension/DimensionOptionsRegistryHolder;)V"),index = 1)
    private static DimensionOptionsRegistryHolder inject1(DimensionOptionsRegistryHolder dimensionOptionsRegistryHolder)
    {
        HashMap<RegistryKey<DimensionOptions>, DimensionOptions> dimensions = new HashMap<>();
        for(Map.Entry<RegistryKey<DimensionOptions>, DimensionOptions> i:dimensionOptionsRegistryHolder.dimensions().entrySet())
        {
            if(!deletedDimensions.contains(i.getKey()))dimensions.put(i.getKey(),i.getValue());
        }
        for(Map.Entry<RegistryKey<DimensionOptions>, DimensionOptions> i:newDimensions.entrySet())
        {
            if(!deletedDimensions.contains(i.getKey()))dimensions.put(i.getKey(),i.getValue());
        }
        return new DimensionOptionsRegistryHolder(dimensions);
    }
}
