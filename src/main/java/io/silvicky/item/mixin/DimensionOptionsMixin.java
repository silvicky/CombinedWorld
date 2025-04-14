package io.silvicky.item.mixin;

import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

@Mixin(DimensionOptions.class)
public class DimensionOptionsMixin extends DimensionOptions implements DimensionOptionsMixinInterface  {
    @Unique
    long seed;
    @Override
    @Unique
    public long item_storage$getSeed() {
        return this.seed;
    }
    public DimensionOptionsMixin(long seed,RegistryEntry<DimensionType> dimensionTypeEntry, ChunkGenerator chunkGenerator) {

        super(dimensionTypeEntry,chunkGenerator);
        this.seed=seed;
    }


    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;create(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Function<RecordCodecBuilder.Instance<DimensionOptionsMixin>, ? extends App<RecordCodecBuilder.Mu<DimensionOptionsMixin>, DimensionOptionsMixin>> inject1(Function<RecordCodecBuilder.Instance<DimensionOptionsMixin>, ? extends App<RecordCodecBuilder.Mu<DimensionOptionsMixin>, DimensionOptionsMixin>> builder) {
        return (instance) -> instance.group(Codec.LONG.lenientOptionalFieldOf("seed",0L).stable().forGetter(DimensionOptionsMixin::item_storage$getSeed), DimensionType.REGISTRY_CODEC.fieldOf("type").forGetter(DimensionOptionsMixin::dimensionTypeEntry), ChunkGenerator.CODEC.fieldOf("generator").forGetter(DimensionOptionsMixin::chunkGenerator)).apply(instance, instance.stable(DimensionOptionsMixin::new));
    }
}
