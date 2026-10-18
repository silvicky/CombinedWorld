package io.silvicky.item.command.world;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * Dimensions that are added to a save at the moment it is written, rather than being known up front.
 *
 * <p>{@link ImportWorld#newDimensions} already lets a command stage a {@link LevelStem} for the next save.
 * That is fine for a command, which runs inside a live server: the holders it builds are taken from the
 * registries that server is using, and the save that follows uses the same ones.
 *
 * <p>It is not fine for anything that stages <em>before</em> a world exists, or across a world switch. A
 * {@link net.minecraft.core.Holder} is not just an id - it is a reference owned by one particular registry
 * instance, and {@code Holder.Reference.canSerializeIn} answers by identity. Encode it against a different
 * {@link RegistryAccess} and the write fails with "Element ... is not valid in current registry set", naming
 * the JSON and the field rather than the holder that caused it.
 *
 * <p>So anything holding a staged dimension asks to be consulted here instead, and resolves its holders from
 * the access it is handed - which is exactly the access the settings will be encoded with.
 *
 * <p>Registered providers are consulted on every save. They must be idempotent: the merged map already
 * contains the dimensions the world really has, and a provider should leave those alone.
 */
public final class StagedDimensions
{
    /** Contributes dimensions to a save that is about to be written. */
    public interface Provider
    {
        /**
         * @param access     the registry access the save will be encoded with, and therefore the only access
         *                   whose holders are usable
         * @param levelPath  the save being written, for logging and for anything that wants to contribute to
         *                   one world only
         * @param dimensions the merged dimension map, already containing everything the world has; add to it
         *                   only what is missing
         */
        void contribute(RegistryAccess access, Path levelPath, Map<ResourceKey<LevelStem>, LevelStem> dimensions);
    }

    private static final List<Provider> providers = new CopyOnWriteArrayList<>();

    private StagedDimensions() {}

    public static void register(Provider provider)
    {
        providers.add(provider);
    }

    /**
     * Lets every provider add its dimensions.
     *
     * <p>A failing provider must not take the save with it: the world's own settings are still perfectly
     * valid, and losing them would be much worse than not getting a demo dimension.
     */
    public static void contribute(RegistryAccess access, Path levelPath, Map<ResourceKey<LevelStem>, LevelStem> dimensions)
    {
        for (Provider provider : providers)
        {
            try
            {
                provider.contribute(access, levelPath, dimensions);
            }
            catch (Exception e)
            {
                LOGGER.error("[staged] a dimension provider failed; the save will continue without it", e);
            }
        }
    }
}
