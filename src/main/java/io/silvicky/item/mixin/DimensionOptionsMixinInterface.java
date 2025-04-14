package io.silvicky.item.mixin;

import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;

public interface DimensionOptionsMixinInterface {
    @Unique
    long item_storage$getSeed();
}
