package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(Level.class)
public abstract class LevelMixin
{

    @Redirect(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/LevelEntityGetter;get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V"))
    private <T extends EntityAccess> void inject1(LevelEntityGetter<T> instance, AABB aABB, Consumer<T> tConsumer, @Local(argsOnly = true)Entity entity)
    {
        if(!(entity instanceof ServerPlayer player))
        {
            instance.get(aABB,tConsumer);
            return;
        }
        EntitySectionStorage<T> sectionStorage =((LevelEntityGetterAdapter<T>)instance).sectionStorage;
        VecTransformer transformer=VecTransformer.getInstance(player);
        try
        {
            AABB aABB2 = aABB.move(transformer.s2cTransform(aABB.getBottomCenter()).subtract(aABB.getBottomCenter()));
            int i = SectionPos.posToSectionCoord(aABB2.minX - 2.0);
            int j = SectionPos.posToSectionCoord(aABB2.minY - 4.0);
            int k = SectionPos.posToSectionCoord(aABB2.minZ - 2.0);
            int l = SectionPos.posToSectionCoord(aABB2.maxX + 2.0);
            int m = SectionPos.posToSectionCoord(aABB2.maxY + 0.0);
            int n = SectionPos.posToSectionCoord(aABB2.maxZ + 2.0);
            for(int x=i;x<=l;x++)for(int y=j;y<=m;y++)for(int z=k;z<=n;z++)
            {
                EntitySection<T> entitySection=sectionStorage.sections.get(transformer.c2sTransform(SectionPos.of(x,y,z)).asLong());
                if (entitySection != null
                        && !entitySection.isEmpty()
                        && entitySection.getStatus().isAccessible())
                {
                    for(T entityAccess : entitySection.getEntities().toList())
                    {
                        AABB aABB3=entityAccess.getBoundingBox();
                        AABB aABB4=aABB3.move(transformer.s2cTransform(aABB3.getBottomCenter()).subtract(aABB3.getBottomCenter()));
                        if (aABB4.intersects(aABB2))
                        {
                            tConsumer.accept(entityAccess);
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            instance.get(aABB,tConsumer);
        }
    }
}
