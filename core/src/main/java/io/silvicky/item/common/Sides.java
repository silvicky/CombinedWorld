package io.silvicky.item.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Tells server-side execution from client-side execution, so an injection can stand down on the client.
 *
 * <p><b>Why this exists rather than a config-level filter.</b> This is a pure server-side mod: nothing here
 * may change what a client computes. The obvious way to enforce that is to set {@code environment: server}
 * in {@code fabric.mod.json} and move the mixins into the {@code server} array. That does not work here, and
 * the reason is structural rather than a matter of configuration:
 *
 * <ul>
 *   <li>In singleplayer the integrated server shares one JVM <b>and one set of classes</b> with the client.
 *       {@code Player}, {@code Item}, {@code Level}, {@code Entity} and {@code Connection} are not two
 *       classes that happen to look alike - they are literally the same bytecode. There is no second copy to
 *       weave, so a hook on them is woven into the client whether or not the config says so.</li>
 *   <li>Side filtering could not rescue it either, because Fabric decides sides from
 *       {@code getEnvironmentType()}, and a singleplayer launch reports {@code CLIENT} even though a server is
 *       running. Server-only mixins are simply skipped there, so the mod would go dead in the one place it is
 *       developed and tested.</li>
 * </ul>
 *
 * <p>That leaves exactly one discriminator that works in both topologies: the objects themselves. The
 * integrated server only ever handles {@link ServerLevel} and {@link ServerPlayer}; the client only ever
 * holds {@code ClientLevel} and {@code LocalPlayer}. Those types are distinct, so an instanceof test
 * separates the two threads inside a single JVM without consulting any environment flag.
 *
 * <p><b>What this does and does not promise.</b> The woven bytecode still exists on the client - it cannot
 * be removed while singleplayer is supported - but every call into it returns immediately with vanilla's own
 * answer, so client behaviour is unchanged. The rule being honoured is about behaviour, not about the
 * presence of a no-op branch.
 *
 * <p><b>Do not "optimise" these into the config.</b> Moving the mixins to the {@code server} array is the
 * obvious-looking cleanup and it silently disables the entire mod in singleplayer. That regression is easy to
 * miss because a dedicated server still behaves perfectly.
 */
public final class Sides
{
    private Sides() {}

    /** True when this level is a real server level, false for the client's own level. */
    public static boolean isServer(Level level)
    {
        return level instanceof ServerLevel;
    }

    /** True when this entity is a real server player, false for the client's local player. */
    public static boolean isServer(Entity entity)
    {
        return entity instanceof ServerPlayer;
    }
}
