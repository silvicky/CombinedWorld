package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerLevel;

/**
 * The one thing a vanilla {@code DistanceManager} gains that other code needs: the level it belongs to.
 *
 * <p><b>Why this lives outside the mixin package.</b> A {@code DistanceManager} is constructed with a ticket
 * storage and two executors and never learns which level it is serving, so
 * {@link io.silvicky.item.mixin.DistanceManagerMixin} needs that reference to find a stored per-dimension
 * simulation distance. Supplying it means calling a method on the merged result, which needs a compile-time
 * type: a mixin class is not a supertype of the class it merges into, so {@code (DistanceManagerMixin) manager}
 * does not compile.
 *
 * <p>Declaring the method on a plain interface that the mixin implements fixes the cast - legal at compile time,
 * true at runtime once merged - but such an interface has to sit <b>outside</b> the mixin package. Mixin treats
 * {@code io.silvicky.item.mixin.*} as owned by the mixin config and loaded before the game, so anything in it
 * cannot be referenced directly from target code:
 *
 * <pre>
 *   IllegalClassLoadError: io.silvicky.item.mixin.DistanceManagerAccess is in a defined mixin package
 *   io.silvicky.item.mixin.* owned by item-storage.mixins.json and cannot be referenced directly
 * </pre>
 *
 * <p>Hence {@code io.silvicky.item.backrooms}, alongside {@link PositionedAccess}, which the tracking-view mixins
 * reach for the same reason.
 */
public interface DistanceManagerAccess
{
    void item_storage$setLevel(ServerLevel level);
}
