package io.silvicky.item.backrooms;

import net.minecraft.world.level.Level;

/**
 * Read/write access to an entity's level.
 *
 * <p>Needed for cross-band interaction. Vanilla resolves an interaction target in the player's own level:
 *
 * <pre>
 *   ServerLevel level = this.player.level();
 *   Entity target = level.getEntityOrPart(packet.entityId());
 *   ...
 *   this.player.attack(target);
 * </pre>
 *
 * so an action on an entity in another band has to run with {@code player.level()} temporarily pointing at that
 * band - otherwise the lookup, the range test, the damage source and the hurt bookkeeping all describe the
 * wrong world.
 *
 * <p>Implemented by the mixin on {@code Entity}; the mixin's methods are merged into the target, so this has to
 * be an interface rather than a cast (a Mixin class does not exist at runtime).
 */
public interface EntityLevelAccess
{
    Level item_storage$level();

    void item_storage$setLevel(Level level);
}
