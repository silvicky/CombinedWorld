package io.silvicky.item.backrooms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The band level, and the exact placement position, a cross-band action is running against.
 *
 * <p>Two things are needed by code that cannot be handed them explicitly:
 *
 * <ul>
 *   <li><b>The level.</b> {@code ServerPlayerGameMode.useItemOn} builds
 *       {@code new UseOnContext(player, hand, hit)}, and that constructor reads {@code player.level()} - so the
 *       place context, and everything {@code BlockItem.place} derives from it, otherwise runs against the
 *       player's own band.</li>
 *   <li><b>The position.</b> {@code UseOnContext} derives its place position as
 *       {@code hitPos.relative(direction)}. Across a seam that lands one block too high: a click on the top face
 *       of the lower band's ceiling (y=63) asks for y=64, which in the upper band is y=0, but vanilla computes
 *       y=1 in that level - inside its floor - and the placement fails.</li>
 * </ul>
 *
 * <p>Thread-local: a dispatch runs inside one server thread's call stack, and a shared field would leak one
 * player's band into another's action on the same tick.
 */
public final class CrossBandAction
{
    private static final ThreadLocal<ServerLevel> current = new ThreadLocal<>();
    private static final ThreadLocal<BlockPos> target = new ThreadLocal<>();

    private CrossBandAction() {}

    public static void enter(ServerLevel level, @Nullable BlockPos placeTarget)
    {
        current.set(level);
        if (placeTarget == null) target.remove();
        else target.set(placeTarget);
    }

    public static void enter(ServerLevel level)
    {
        enter(level, null);
    }

    public static void exit()
    {
        target.remove();
        current.remove();
    }

    /** The band level an action in progress should use, or null when no action is being dispatched. */
    public static @Nullable ServerLevel level()
    {
        return current.get();
    }

    /**
     * The block position the current action must place at, or null to let vanilla compute it.
     *
     * <p>Set only for a placement whose position crosses a band boundary, where the vanilla computation is not
     * expressible at all.
     */
    public static @Nullable BlockPos placeTarget()
    {
        return target.get();
    }

    /** The level to use: the dispatched one when there is one, otherwise the given fallback. */
    public static <T extends net.minecraft.world.level.Level> T orElse(T fallback)
    {
        ServerLevel level = current.get();
        if (level == null) return fallback;
        @SuppressWarnings("unchecked") T cast = (T) level;
        return cast;
    }
}
