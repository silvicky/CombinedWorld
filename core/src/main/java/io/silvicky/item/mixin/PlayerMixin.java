package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class PlayerMixin
{
    @Redirect(method = "isWithinBlockInteractionRange",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 inject1(Player instance)
    {
        try
        {
            return VecTransformer.getInstance((ServerPlayer) instance).s2cTransform(instance.getEyePosition());
        }
        catch (ChunkUnusedException e){return VecTransformer.INF;}
    }
    @ModifyArg(method = "isWithinBlockInteractionRange",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;<init>(Lnet/minecraft/core/BlockPos;)V"))
    public BlockPos inject2(BlockPos pos)
    {
        Player instance = (Player) (Object) this;
        try
        {
            return VecTransformer.getInstance((ServerPlayer) instance).s2cTransform(pos);
        }
        catch (ChunkUnusedException e)
        {
            return instance.getOnPos().north(1000);
        }
    }

    // ------------------------------------------------------------------------------------- entities
    //
    // Block reach is judged in the player's own map, so entity reach has to be too, or the two disagree and
    // the player can reach blocks they cannot reach entities with, or the other way round. Both hooks below
    // do the same two things the block hooks do: move the player's eye into C, and move the thing being
    // reached into C. The distance is then measured in C, which is the only space the player is looking at.

    //The descriptor matters here. Player has two overloads named isWithinEntityInteractionRange, one taking
    //an Entity and one taking an AABB, and only the AABB one contains the calls being redirected. Naming
    //the method without its descriptor makes Mixin look in both, find no matching call in the Entity
    //overload, and fail the injection at startup under this config's defaultRequire of 1.
    @Redirect(method = "isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 inject3(Player instance)
    {
        Vec3 eye=instance.getEyePosition();
        if(!(instance instanceof ServerPlayer serverPlayer))return eye;
        try
        {
            return VecTransformer.getInstance(serverPlayer).s2cTransform(eye);
        }
        catch (ChunkUnusedException e){return VecTransformer.INF;}
    }
    @Redirect(method = "isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    public double inject4(AABB box, Vec3 eye)
    {
        //The eye arriving here is already the C-space one from inject3, so the box has to be moved to match.
        AABB moved=item_storage$toClientSpace(box);
        //A box on a page this player cannot see is not part of their world, so there is nothing to measure
        //against. Refuse, the same way inject2 refuses an unreachable block.
        if(moved==null)return Double.MAX_VALUE;
        return moved.distanceToSqr(eye);
    }
    @Redirect(method = "isWithinAttackRange",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/AttackRange;isInRange(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;D)Z"))
    public boolean inject5(AttackRange range, LivingEntity attacker, AABB box, double buffer)
    {
        if(!(attacker instanceof ServerPlayer serverPlayer))return range.isInRange(attacker,box,buffer);
        AABB moved=item_storage$toClientSpace(box);
        if(moved==null)return false;
        try
        {
            //AttackRange measures from the attacker's eye, so that has to move too. The range arithmetic
            //itself is vanilla's and is not duplicated here: effectiveMinRange and effectiveMaxRange are
            //public, and the hitbox margin is a record component, so this is the same comparison vanilla
            //would have made, with both ends in C.
            Vec3 eye=VecTransformer.getInstance(serverPlayer).s2cTransform(attacker.getEyePosition());
            double distance=Math.sqrt(moved.distanceToSqr(eye));
            double minReach=range.effectiveMinRange(attacker)-range.hitboxMargin()-buffer;
            double maxReach=range.effectiveMaxRange(attacker)+range.hitboxMargin()+buffer;
            return distance>=minReach&&distance<=maxReach;
        }
        catch (ChunkUnusedException e){return false;}
    }
    /**
     * An entity's box, moved into the player's own map, or null when it is not on a page they can see.
     *
     * <p>An entity's box is a whole volume and can straddle a page boundary, so its two corners are
     * converted separately. When they land on the same page - the normal case, since a mob is smaller than
     * a chunk - both corners move by the same amount and the box is simply translated, which is exact.
     *
     * <p>When an entity really does straddle two pages that are mapped to unrelated places, there is no
     * single correct answer: the thing the player sees is a collage. The six-argument {@link AABB}
     * constructor takes the component-wise min and max, so the result is the bounding box of the two
     * images. That is deliberately generous - it can only ever make reach slightly longer, never shorter,
     * so a mob at a seam stays hittable instead of becoming impossible to reach at all.
     *
     * <p>A page the player cannot see is refused rather than measured, and that is the same answer
     * {@link #inject2} gives for a block: geometry that is not part of the player's world has no position
     * in it to measure to.
     */
    @Unique
    private AABB item_storage$toClientSpace(AABB box)
    {
        if(!((Object)this instanceof ServerPlayer serverPlayer))return box;
        try
        {
            VecTransformer transformer=VecTransformer.getInstance(serverPlayer);
            Vec3 min=transformer.s2cTransform(new Vec3(box.minX,box.minY,box.minZ));
            Vec3 max=transformer.s2cTransform(new Vec3(box.maxX,box.maxY,box.maxZ));
            return new AABB(min.x,min.y,min.z,max.x,max.y,max.z);
        }
        catch (ChunkUnusedException e){return null;}
    }
}
