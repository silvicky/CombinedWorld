package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.silvicky.item.backrooms.AbyssPattern;
import io.silvicky.item.backrooms.AbyssWindow;
import io.silvicky.item.backrooms.CrossBandAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * Runs a block action in the band's level rather than always in the player's own.
 *
 * <p><b>The model.</b> The server simulates the client's valid spaces. A player sees one concatenated
 * dimension, so a block in the band above is as reachable as one in their own band; if it lives in another
 * {@code ServerLevel}, the action is performed there. Nothing on the client distinguishes the two.
 *
 * <h2>How the dispatch works</h2>
 *
 * <p>{@code ServerPlayerGameMode} holds a {@code level} field that both actions read, and {@code useItemOn}
 * also takes the level as a parameter. So a dispatched action swaps the field (and the argument) for the
 * duration of the call and restores it afterwards.
 *
 * <p><b>Both methods are wrapped rather than injected at HEAD/RETURN.</b> An exception thrown inside the action
 * skips a RETURN injection, which would leave the field pointing at another band for the rest of the session -
 * every later action in the player's own band would then run in the wrong level, and the block they aimed at
 * would never change. {@code try}/{@code finally} is the only shape that cannot leave that state behind, and
 * that is why these are {@code @WrapMethod} rather than a pair of {@code @Inject}s.
 *
 * <p>Passing the target level as the {@code useItemOn} argument rather than rewriting it with a separate
 * injection also removes an ordering hazard: two injections at the same point have no defined order, so a
 * field swap and an argument rewrite could disagree about which level the call was using.
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin
{
    @Shadow @Final protected ServerPlayer player;
    @Shadow protected ServerLevel level;

    /** Set while a dispatched action is running, so a nested action cannot swap twice. */
    @Unique private boolean item_storage$dispatching;

    /**
     * A cross-band block that is being mined, and the band's level.
     *
     * <p>Breaking is not a single call in survival. {@code handleBlockBreakAction} only records the target and
     * starts the progress bar; the work is done <em>per tick</em> by {@code tick()}, which reads
     * {@code this.level} to advance {@code destroyPos}. Restoring the level as soon as the START packet returns
     * therefore means the mining continues against the player's own band, where the block is not the one being
     * mined - so it never finishes, and the client keeps the block it was told to keep. That is the case that
     * looked like "dispatch happened but the break failed".
     *
     * <p>So the target is remembered and re-applied for the duration of the mining, and cleared once the block
     * is gone.
     */
    @Unique private @Nullable ServerLevel item_storage$pendingLevel;
    @Unique private @Nullable BlockPos item_storage$pendingPos;

    /**
     * The level a block action should run in, or null when it runs in the player's own.
     *
     * <p>The answer comes from the slot the click addressed, recorded when its position was converted; see
     * {@link AbyssWindow#actionLevel}. It cannot be derived from the position here: every band spans the same Y
     * range, so the position alone does not say which band it came from.
     */
    @Unique
    private @Nullable ServerLevel item_storage$target(BlockPos pos)
    {
        if (item_storage$dispatching) return null;
        if (!AbyssWindow.handles(player)) return null;
        return AbyssWindow.actionLevel(player, pos);
    }

    /**
     * Swaps in the target level, runs the action, restores the field and pushes the result to the client.
     *
     * <p>The update is sent because the target level broadcasts block changes only to players tracking that
     * chunk <em>in that level</em>; this player is in another band, so without it the block changes on the
     * server and nothing at all happens on their screen.
     */
    @Unique
    private void item_storage$dispatch(ServerLevel target, BlockPos pos, Runnable action)
    {
        item_storage$dispatch(target, pos, null, action);
    }

    /**
     * @param placeTarget the exact block a placement must go to, or null to let vanilla compute it
     */
    @Unique
    private void item_storage$dispatch(ServerLevel target, BlockPos pos, @Nullable BlockPos placeTarget,
                                       Runnable action)
    {
        ServerLevel previous = level;
        item_storage$dispatching = true;
        level = target;
        //Also publish it for UseOnContext and BlockPlaceContext, which read player.level() and derive the place
        //position themselves; neither can be handed the parameter.
        CrossBandAction.enter(target, placeTarget);
        try
        {
            action.run();
        }
        finally
        {
            CrossBandAction.exit();
            level = previous;
            item_storage$dispatching = false;
            AbyssWindow.sendBlockUpdate(player, target.dimension(), pos);
        }
    }

    // ------------------------------------------------------------------------------------------- breaking

    @WrapMethod(method = "handleBlockBreakAction")
    private void abyss$break(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction,
                             int maxY, int sequence, Operation<Void> original)
    {
        ServerLevel target = item_storage$target(pos);
        if (target == null)
        {
            //Any action that is not a dispatched start ends whatever mining was in progress.
            item_storage$clearPending(pos);
            original.call(pos, action, direction, maxY, sequence);
            return;
        }
        item_storage$dispatch(target, pos, () -> original.call(pos, action, direction, maxY, sequence));
        item_storage$log("break", target, pos);

        //A finished or cancelled action needs no continuation; a survival START does.
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && !player.getAbilities().instabuild)
        {
            item_storage$pendingLevel = target;
            item_storage$pendingPos = pos.immutable();
        }
        else
        {
            item_storage$clearPending(pos);
        }
    }

    @Unique
    private void item_storage$clearPending(BlockPos pos)
    {
        if (item_storage$pendingPos == null) return;
        if (pos != null && !item_storage$pendingPos.equals(pos)) return;
        item_storage$pendingLevel = null;
        item_storage$pendingPos = null;
    }

    /**
     * Advances a cross-band mining operation in the band it belongs to.
     *
     * <p>See {@link #item_storage$pendingLevel}. The swap lasts exactly one vanilla {@code tick()} call and is
     * restored in {@code finally}, so an exception cannot leave the game mode pointing at another level.
     */
    @WrapMethod(method = "tick")
    private void abyss$tick(Operation<Void> original)
    {
        ServerLevel pending = item_storage$pendingLevel;
        BlockPos pos = item_storage$pendingPos;
        if (pending == null || pos == null || item_storage$dispatching)
        {
            original.call();
            return;
        }

        ServerLevel previous = level;
        item_storage$dispatching = true;
        level = pending;
        try
        {
            original.call();
        }
        finally
        {
            level = previous;
            item_storage$dispatching = false;
            if (pending.getBlockState(pos).isAir())
            {
                item_storage$log("break finished", pending, pos);
                AbyssWindow.sendBlockUpdate(player, pending.dimension(), pos);
                item_storage$pendingLevel = null;
                item_storage$pendingPos = null;
            }
        }
    }

    // --------------------------------------------------------------------------------------------- using

    @WrapMethod(method = "useItemOn")
    private InteractionResult abyss$useItemOn(ServerPlayer usingPlayer, Level usingLevel, ItemStack stack,
                                              InteractionHand hand, BlockHitResult hit,
                                              Operation<InteractionResult> original)
    {
        if (hit == null) return original.call(usingPlayer, usingLevel, stack, hand, hit);

        BlockPos pos = hit.getBlockPos();
        ServerLevel target = item_storage$target(pos);

        //One line per placement attempt, before the decision, because the failure this is meant to explain is
        //vanilla's "The height limit for building is 63" - which the *listener* shows after this method returns
        //without consuming the action. So a rejection here and a rejection there look identical from outside; the
        //inputs do not. Printed for every attempt rather than only failures, because the interesting case is the
        //one where the mod believed it had a target and vanilla disagreed.
        LOGGER.debug("[abyss] place attempt: item {} at hit {} face {} in {} (y bounds {})-({}) | own band {}, target {}, player y={}",
                stack.getItem(), pos.toShortString(), hit.getDirection(),
                usingLevel.dimension().identifier().getPath(),
                usingLevel.getMinY(), usingLevel.getMaxY(),
                usingPlayer.level().dimension().identifier().getPath(),
                target == null ? "none" : target.dimension().identifier().getPath(),
                Math.round(usingPlayer.getY()));

        //Which band the click is in, and where a placement would land.
        //
        //The seam case used to be guarded on `target == null`, i.e. it only ran when the clicked block was in the
        //player's OWN level. That is the rarer case. Standing at a seam and clicking the top face of the band
        //below means the clicked block belongs to a foreign band, so `target` was non-null, the seam branch was
        //skipped, and the placement asked for a position one above that band's ceiling - which vanilla refused
        //with "The height limit for building is 63". The log that found this showed it exactly: target layer0,
        //face up, placed 64,64,61, Fail.
        Direction face = hit.getDirection();
        int step = face == Direction.UP ? 1 : face == Direction.DOWN ? -1 : 0;
        //The band the click addressed, which is the one a seam leaves from - not necessarily the player's own.
        Integer clickedPosition = AbyssWindow.clickPosition(usingPlayer, pos);
        int herePosition = clickedPosition != null ? clickedPosition : AbyssWindow.bandPosition(usingPlayer);
        AbyssPattern.Band hereBand = AbyssWindow.bandAt(usingPlayer, herePosition);
        ServerLevel clickedLevel = target != null ? target : (ServerLevel) usingLevel;

        if (step != 0 && hereBand != null && clickedLevel.isOutsideBuildHeight(pos.relative(face)))
        {
            AbyssPattern.Band thereBand = AbyssWindow.bandAt(usingPlayer, herePosition + step);
            ServerLevel there = thereBand == null ? null : AbyssWindow.levelAt(usingPlayer, herePosition + step);
            if (there == null || thereBand == null)
            {
                //The pattern ends here, so there is no band to place into and vanilla is right to refuse. A refusal is a
                //bug signal rather than routine play, so it stays visible above debug.
                LOGGER.warn("[abyss] place across seam refused: no band at position {}", herePosition + step);
                return InteractionResult.FAIL;
            }

            //The block the click asks for, expressed in the band above or below. Vanilla would compute
            //`clicked.relative(direction)` inside that level, which is one block too high: a click on the lower
            //band's ceiling at y=63 asks for y=64, and in the upper band that is y=0, not y=1.
            int targetY = pos.getY() + face.getStepY()
                          - hereBand.sourceY() - step * AbyssWindow.geometry(usingPlayer).bandHeight()
                          + thereBand.sourceY();
            BlockPos landing = new BlockPos(pos.getX(), targetY, pos.getZ());

            BlockHitResult reHit = new BlockHitResult(Vec3.atCenterOf(landing), face, landing, false);
            InteractionResult[] result = new InteractionResult[1];
            item_storage$dispatch(there, landing, landing,
                    () -> result[0] = original.call(usingPlayer, there, stack, hand, reHit));
            LOGGER.debug("[abyss] place across seam: {} at {} in {} [{},{}] -> {} in {} [{},{}] consumed={}",
                    stack.getItem(), pos.toShortString(), clickedLevel.dimension().identifier().getPath(),
                    herePosition, hereBand.sourceY(), landing.toShortString(),
                    there.dimension().identifier().getPath(), herePosition + step, thereBand.sourceY(),
                    result[0] != null && result[0].consumesAction());
            if (result[0] != null && result[0].consumesAction())
                AbyssWindow.sendBlockUpdate(usingPlayer, there.dimension(), landing);
            return result[0] == null ? InteractionResult.PASS : result[0];
        }

        if (target == null) return original.call(usingPlayer, usingLevel, stack, hand, hit);

        InteractionResult[] result = new InteractionResult[1];
        item_storage$dispatch(target, pos,
                () -> result[0] = original.call(usingPlayer, target, stack, hand, hit));
        item_storage$log("use", target, pos);

        //A placement changes the block *next to* the one clicked, and that change was broadcast to the target
        //level's own trackers - which does not include a player looking at it from another band. Updating only
        //the clicked block left placed blocks invisible until the chunk was re-sent.
        BlockPos placed = hit.getDirection() == null ? pos : pos.relative(hit.getDirection());
        if (result[0] != null && result[0].consumesAction())
            AbyssWindow.sendBlockUpdate(usingPlayer, target.dimension(), placed);
        //The outcome, and the two positions involved. A block that is consumed but placed *elsewhere* than the
        //face asked for (a stair, a door, a block that snaps) shows up here as a disagreement between `placed`
        //and the state vanilla reports at the clicked position.
        LOGGER.debug("[abyss] place result: {} at {} in {} -> {} (consumed={}, placed={}, state-at-clicked={})",
                stack.getItem(), pos.toShortString(), target.dimension().identifier().getPath(),
                result[0], result[0] != null && result[0].consumesAction(), placed.toShortString(),
                target.getBlockState(pos));
        return result[0];
    }

    /**
     * Builds the place context against the band's level instead of the player's.
     *
     * <p>{@code useItemOn} constructs {@code new UseOnContext(player, hand, hit)}, and that constructor derives
     * its level from {@code player.level()} - the {@code Level} parameter is never consulted. So a dispatch to
     * another band still built a place context for the player's own world, and {@code BlockItem.place} acted
     * there: the log said the right level while nothing appeared in it.
     *
     * <p>Wrapped <b>here</b>, on a server-only method, rather than in {@code UseOnContext} itself. An earlier
     * version mixed into that constructor and crashed every client right-click with {@code VerifyError: Bad type
     * on operand stack}: a constructor's {@code this} is {@code uninitializedThis} until the delegated
     * constructor runs, so MixinExtras could not pass the mixin instance as the handler receiver. This shape has
     * no receiver problem and never runs on the client.
     */
    @WrapOperation(method = "useItemOn",
            at = @At(value = "NEW",
                     target = "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/item/context/UseOnContext;"))
    private UseOnContext abyss$placeContext(net.minecraft.world.entity.player.Player usingPlayer,
                                             InteractionHand hand, BlockHitResult hit,
                                             Operation<UseOnContext> original)
    {
        ServerLevel band = CrossBandAction.level();
        if (band == null) return original.call(usingPlayer, hand, hit);
        //The five-argument constructor is the one that takes the level; it is protected in vanilla and opened
        //by the access widener.
        return new UseOnContext(band, usingPlayer, hand, usingPlayer.getItemInHand(hand), hit);
    }

    /** One line per dispatched action, at debug, so a failing case can be told apart from a rejected one. */
    @Unique
    private void item_storage$log(String what, ServerLevel target, BlockPos pos)
    {
        LOGGER.debug("[abyss] {} dispatched to {} at {} (player in {}, y={})",
                    what, target.dimension().identifier(), pos,
                    player.level().dimension().identifier(), player.getY());
    }
}
