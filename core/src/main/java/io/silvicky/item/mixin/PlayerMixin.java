package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class PlayerMixin
{
    @Redirect(method = "isWithinBlockInteractionRange",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 inject1(Player instance)
    {
        //[S>C] getEyePosition() is S-field. The comparison in this method is against a distance from this
        //point to the block position, and inject2 converts that block position to C as well, so both
        //operands end up in the same field and the distance is unchanged. Converting only one of the two
        //would silently corrupt the reach test.
        try
        {
            return VecTransformer.getInstance((ServerPlayer) instance).s2cTransform(instance.getEyePosition());
        }
        catch (ChunkUnusedException e){return VecTransformer.INF;}
    }
    @ModifyArg(method = "isWithinBlockInteractionRange",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;<init>(Lnet/minecraft/core/BlockPos;)V"))
    public BlockPos inject2(BlockPos pos)
    {
        //[S>C] pos is S-field, the player's view position being compared against. Pairs with inject1.
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
}
