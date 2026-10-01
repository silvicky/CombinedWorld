package io.silvicky.item.mixin;

import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Casts a player's view ray in the map they are looking at, instead of the real one.
 *
 * <p><b>Why the ray cannot be trusted as vanilla casts it.</b> The eye is real, but the heading is not.
 * A player aims at what they see, which is a position in their own map, so the direction is a C heading. Cast
 * from the real eye along that heading through the real world and it arrives nowhere near the target.
 *
 * <p><b>Why the clicked block cannot be used instead.</b> It is the wrong answer. The crosshair ray that fills
 * the packet is built with a clip context that skips fluids, so it looks straight through water and reports
 * the sand behind it, while the bucket's own ray includes fluids and finds the water. Substituting the
 * clicked block therefore fixes pouring - which uses the block actually clicked - and still leaves collecting
 * broken, because collecting needs the block the eye found.
 *
 * <p><b>The walk.</b> Step along the heading in C, translating each sample back to S, and stop at the first
 * real block. Reach is about 5.5 blocks, so at a 0.05 step that is roughly 110 samples, once per interaction.
 * The walk is the player's to make: nothing here restricts it to the page the player is standing on, because
 * a neighbour page is real geometry they can see and aim at. Each sample is resolved against whichever page
 * owns that C coordinate, so crossing a page is just another step rather than the end of the ray. That is what
 * makes this C-consistent - the player can do what they see.
 *
 * <p><b>Crossing a page is the awkward part.</b> Two samples one STEP apart are neighbours only if the walk
 * stayed on one page. Stepping onto a neighbour puts them in unrelated parts of the world, where "one step
 * apart" means nothing, and clipping between them searches a hundred blocks of terrain nobody is looking at.
 * So a step that long is detected, and the sample is tested directly instead: the walk is continuous in C
 * even where it is not continuous in S, and the block the ray is standing inside is a real answer. Only when
 * the step is short enough to be honest is vanilla's own {@code level.clip} used, so shape handling and the
 * item's fluid context stay vanilla's.
 *
 * <p>A block the fluid context cannot pick is stepped through rather than ending the walk, which is what
 * vanilla's own clip does - water is invisible to a ray that only accepts {@code SOURCE_ONLY} sources and
 * must not stop the search for the source behind it.
 *
 * <p>Falls through to vanilla on any failure. A view ray is a convenience and must never be able to break an
 * interaction.
 *
 * <p>Static, because {@code getPlayerPOVHitResult} is.
 */
@Mixin(Item.class)
public class ItemMixin
{
    /** Small enough not to step over thin blocks or a shallow flow's surface. */
    private static final double STEP = 0.05;

    /** Longest step still treated as "these two samples are neighbours". */
    private static final double MAX_NEIGHBOUR_STEP = STEP * 2;

    /**
     * The honest answer when the walk reaches nothing.
     *
     * <p>{@link BlockHitResult#miss} is vanilla's own factory for this, and callers test
     * {@link HitResult.Type} rather than the fields, so a miss declines the interaction - {@code BucketItem#use}
     * returns PASS on it - instead of acting on a guess.
     *
     * <p>This exists because the only alternative was to let the method fall through to vanilla's own cast,
     * and that cast runs in S. That is how a block got placed somewhere the player never aimed: the C walk
     * correctly found nothing, and vanilla's S-space ray then found whatever lay along the real heading.
     */
    private static BlockHitResult miss()
    {
        return BlockHitResult.miss(Vec3.ZERO,Direction.UP,BlockPos.ZERO);
    }

    @Inject(method = "getPlayerPOVHitResult", at = @At("HEAD"), cancellable = true)
    private static void item_storage$castRayInC(Level level, Player player, ClipContext.Fluid fluid,
                                                 CallbackInfoReturnable<BlockHitResult> cir)
    {
        if(!(player instanceof ServerPlayer serverPlayer))return;
        try
        {
            VecTransformer transformer=VecTransformer.getInstance(serverPlayer);
            Vec3 eyeReal=player.getEyePosition();
            Vec3 eyeSeen=transformer.s2cTransform(eyeReal);
            Vec3 direction=Player.calculateViewVector(player.getXRot(),player.getYRot());
            double reach=player.blockInteractionRange();

            Vec3 previousReal=eyeReal;
            for (double t=STEP; t<=reach; t+=STEP)
            {
                Vec3 sampleSeen=eyeSeen.add(direction.scale(t));
                //Into the real world, so the level can answer.
                Vec3 sampleReal;
                try
                {
                    sampleReal=transformer.c2sTransform(sampleSeen);
                }
                catch (ChunkUnusedException offMap)
                {
                    //Not part of the player's world, so not something they can be aiming at. Leave the
                    //previous sample alone: the next mapped sample will find the step long and re-anchor.
                    continue;
                }

                //Are these two samples neighbours, or did the walk just change page?
                boolean neighbours=previousReal.distanceToSqr(sampleReal)
                                   <=MAX_NEIGHBOUR_STEP*MAX_NEIGHBOUR_STEP;
                BlockPos pos=BlockPos.containing(sampleReal);
                BlockState state=level.getBlockState(pos);
                //Whether this ray is allowed to stop on this block at all. level.clip applies this itself,
                //but the direct hit below does not, and a seam used to let the ray stop on water that the
                //item's own fluid context says is invisible - so a bucket aimed through flowing water
                //collected the flow rather than the source behind it.
                boolean pickable=!state.isAir()
                                 &&(state.getFluidState().isEmpty()||fluid.canPick(state.getFluidState()));
                if(pickable)
                {
                    BlockHitResult hit=null;
                    if(neighbours)
                    {
                        //Real geometry over an honest segment, with the item's own fluid context. This is
                        //what makes water selectable here when the crosshair's ray passes through it.
                        hit=level.clip(new ClipContext(previousReal,sampleReal,
                                                       ClipContext.Block.OUTLINE,fluid,player));
                        if(hit!=null&&hit.getType()!=HitResult.Type.BLOCK)hit=null;
                    }
                    if(hit==null&&!neighbours)
                    {
                        //The walk is continuous in C even where it is not in S, so the face is still known
                        //from the heading. The sample is inside a block, so the ray has genuinely hit one.
                        hit=new BlockHitResult(sampleReal,
                                               Direction.getApproximateNearest(-direction.x,-direction.y,-direction.z),
                                               pos,false);
                    }
                    if(hit!=null)
                    {
                        //Reach, judged exactly the way a block click is judged - in C, by the same method -
                        //so a click and a bucket cannot disagree about the same target. Without this the air
                        //path had no reach gate whatsoever and would happily act on a block in a page the
                        //player cannot touch: pouring into the C-neighbour, or collecting from across a
                        //seam. That is the same rule vanilla applies before it acts on a click.
                        if(!player.isWithinBlockInteractionRange(pos,1.0))
                        {
                            cir.setReturnValue(miss());
                            return;
                        }
                        cir.setReturnValue(hit);
                        return;
                    }
                }
                //Anything the ray is allowed to look straight through - a flow under SOURCE_ONLY, a plant -
                //is stepped over. Stopping here instead is what made a bucket aimed through moving water
                //report the flow and never reach the still source behind it.
                //Re-anchor either way. A stale sample only causes one more re-anchor, and this keeps the
                //segment handed to level.clip short, which is the whole point.
                previousReal=sampleReal;
            }
            //Nothing in reach in the player's own map. The answer is MISS and stays MISS: falling through to
            //vanilla here handed the interaction to an S-space ray, which then found whatever happened to lie
            //along the real heading and placed a block there - the void placement, in a direction the player
            //never aimed at and possibly on a page they cannot see. A ray that found nothing has found
            //nothing; a second opinion from the wrong space can only invent an answer.
            cir.setReturnValue(miss());
        }
        catch (Exception failed)
        {
            //A failed walk knows nothing about what the player is aiming at, so it must not fall back to
            //vanilla's cast for the same reason. Refuse rather than guess in the wrong space.
            cir.setReturnValue(miss());
        }
    }
}
