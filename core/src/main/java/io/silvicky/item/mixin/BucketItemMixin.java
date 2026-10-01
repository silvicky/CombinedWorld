package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Pours liquid onto the block the player was aiming at, rather than the one next to it in the real world.
 *
 * <p><b>The rule, and why it is the same one blocks already follow.</b> A player aims in their own map, so the
 * block they mean is the one one step from the clicked block <b>along the face they clicked</b>, measured in
 * their map. Placing a block already works this way - see {@link BlockPlaceContextMixin}, which steps in C and
 * converts back. Pouring did not, and the two disagreed about the same gesture:
 *
 * <pre>
 *   clicked   S(143, 67, -144)  face east
 *   vanilla   S(144, 67, -144)  the S-neighbour - a different part of the world entirely
 *   this      the C-neighbour of that block, along east
 * </pre>
 *
 * <p>So a pour could land tens of blocks from the clicked block in real space, on ground the player never saw,
 * or be refused outright because that unrelated block happened to be solid. From a playtest: clicking sand on
 * its east face reported "placing into sand, replaceable=false", and the pour silently did nothing.
 *
 * <p><b>Why the direction is safe but the position is not.</b> A {@link Direction} is page-invariant - "east"
 * means the same compass bearing on every page - so the face the player clicked carries over untouched. Only
 * the position has to be re-derived, by moving one block in C and converting that result back.
 *
 * <p><b>Where this hooks.</b> {@code BucketItem.use} computes
 * {@code directionOffsetPos = pos.relative(direction)} once, and that single value is what gets placed, what
 * gets range-checked by {@code mayUseItemAt}, and what {@code emptyContents} tests for replaceability.
 * Correcting it there fixes all three at once, with no need to touch the pour logic itself.
 *
 * <p>Falls back to vanilla's own step when the conversion is not possible - a pruned page, or no transformer
 * yet - because refusing to pour would be a worse failure than pouring where vanilla would have.
 */
@Mixin(BucketItem.class)
public class BucketItemMixin
{
    @Redirect(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos stepInC(BlockPos instance, Direction direction, @Local(argsOnly = true) Player player)
    {
        //The client reaches this too while predicting. Let vanilla place in its own space.
        if(!(player instanceof ServerPlayer serverPlayer))return instance.relative(direction);
        try
        {
            VecTransformer transformer=VecTransformer.getInstance(serverPlayer);
            //Into the player's map, one step along the face they clicked, then back to the real world.
            //The result is the block they were actually aiming at.
            return transformer.c2sTransform(transformer.s2cTransform(instance).relative(direction));
        }
        catch (ChunkUnusedException offMap)
        {
            //The page holding the target is not in the player's world, so there is no honest answer. Vanilla's
            //step is the only thing left, and it is at least the same block vanilla would have used.
            return instance.relative(direction);
        }
    }
}
