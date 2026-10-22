package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.EntityLevelAccess;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import java.util.Objects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.stream.Stream;

@Mixin(Entity.class)
public class EntityMixin implements EntityLevelAccess
{
    @Shadow
    private Vec3 position;

    /**
     * The entity's level, which {@code Entity.level()} returns.
     *
     * <p>Exposed so a cross-band action can point the player at the band it is acting on for the duration of
     * the call - vanilla resolves interaction targets by asking the *player's* level for the id.
     */
    @Shadow
    private net.minecraft.world.level.Level level;

    @Override
    public net.minecraft.world.level.Level item_storage$level()
    {
        return level;
    }

    @Override
    public void item_storage$setLevel(net.minecraft.world.level.Level level)
    {
        this.level = level;
    }

    @ModifyVariable(method = "move",at=@At("STORE"),name = "checkTo")
    private Vec3 inject1(Vec3 value, @Local(name = "movement")Vec3 vec32,@Local(name = "checkDistance")double e)
    {
        Entity instance=(Entity)(Object)this;
        if(!(instance instanceof ServerPlayer player))return value;
        VecTransformer transformer=VecTransformer.getInstance(player);
        try
        {
            return transformer.c2sTransform(transformer.s2cTransform(position).add(vec32.normalize().scale(e)));
        }
        catch (Exception exception)
        {
            return value;
        }
    }
    @ModifyVariable(method = "move",at=@At("STORE"),name = "newPosition")
    private Vec3 inject2(Vec3 value, @Local(name = "movement")Vec3 vec32)
    {
        Entity instance=(Entity)(Object)this;
        if(!(instance instanceof ServerPlayer player))return value;
        VecTransformer transformer=VecTransformer.getInstance(player);
        try
        {
            return transformer.c2sTransform(transformer.s2cTransform(position).add(vec32));
        }
        catch (Exception exception)
        {
            return value;
        }
    }
    @Redirect(method = "isInWall",at= @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;betweenClosedStream(Lnet/minecraft/world/phys/AABB;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> inject3(AABB aABB)
    {
        Entity instance=(Entity) (Object)this;
        if(!(instance instanceof ServerPlayer player))return BlockPos.betweenClosedStream(aABB);
        try
        {
            VecTransformer transformer = VecTransformer.getInstance(player);
            AABB aABB2 = aABB.move(transformer.s2cTransform(aABB.getBottomCenter()).subtract(aABB.getBottomCenter()));
            //Candidates come from the player's own map and have to go back to real coordinates. A candidate
            //whose page has been pruned cannot be converted, and a method reference cannot report that - so
            //filter those out rather than letting the exception escape into the stream.
            return BlockPos.betweenClosedStream(aABB2)
                            .filter(pos->transformer.isMapped(ChunkPos.containing(pos)))
                            .map(pos->
                            {
                                try{return transformer.c2sTransform(pos);}
                                catch (ChunkUnusedException e){return null;}
                            })
                            .filter(Objects::nonNull);
        }
        catch (Exception e)
        {
            return BlockPos.betweenClosedStream(aABB);
        }
    }
}
