package io.silvicky.item.backrooms;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * The patterns that are available, in registration order, and the file they can come from.
 *
 * <p>Code registers the built-in demos; then {@link #load()} either reads {@code config/AbyssPatterns.json} or
 * writes the built-ins out as an editable starting point. A pattern in the file replaces the built-in of the
 * same name, and patterns may be reordered freely - the order matters, because {@link #forLevel} takes the
 * first pattern that contains a level, and because it is the order a user sees.
 *
 * <p>Loading happens once per server start rather than at mod init, because the addon registers its patterns
 * during its own initialiser and Fabric gives no ordering between mods beyond the dependency edge.
 */
public final class AbyssPatterns
{
    private static final Map<String, AbyssPattern> byName = new LinkedHashMap<>();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** The key the client is told it is in when a pattern does not name one. */
    public static final Identifier DEFAULT_WINDOW_DIMENSION = Identifier.parse("abyss:window");

    private AbyssPatterns() {}

    private static Path file()
    {
        return Path.of(FabricLoader.getInstance().getConfigDir().toString(), "AbyssPatterns.json");
    }

    public static synchronized void register(AbyssPattern pattern)
    {
        byName.put(pattern.name(), pattern);
    }

    public static synchronized List<AbyssPattern> all()
    {
        return List.copyOf(byName.values());
    }

    /** The first registered pattern that has a band in this level, or null when none does. */
    public static @Nullable AbyssPattern forLevel(ResourceKey<Level> level)
    {
        for (AbyssPattern pattern : all()) if (pattern.contains(level)) return pattern;
        return null;
    }

    public static @Nullable AbyssPattern byName(String name)
    {
        return byName.get(name);
    }

    /**
     * The window key for a level, taken from the pattern that contains it.
     *
     * <p>Per pattern rather than global: the window is not a real level, so this key exists only to be sent, and two
     * patterns may legitimately want different names. A level belonging to no pattern gets the default, which is the
     * same "not in a window" answer {@link #forLevel} gives.
     */
    public static Identifier windowDimension(ResourceKey<Level> level)
    {
        AbyssPattern pattern = forLevel(level);
        return pattern == null ? DEFAULT_WINDOW_DIMENSION : pattern.windowDimension();
    }

    /** The window keys of every registered pattern, each with a level it applies to. */
    public record PatternKey(Identifier dimension, ResourceKey<Level> bandLevel) {}

    /**
     * One entry per pattern, naming its window key and a level the pattern contains.
     *
     * <p>The login packet carries a level set, and the client resolves the announced dimension against it; with a
     * per-pattern key, every pattern's key has to be present. A level is included so a caller can also map a key
     * back to a band if it ever needs to.
     */
    public static List<PatternKey> windowKeys()
    {
        List<PatternKey> out = new ArrayList<>();
        for (AbyssPattern pattern : all())
            if (!pattern.bands().isEmpty())
                out.add(new PatternKey(pattern.windowDimension(), pattern.bands().getFirst().level()));
        return out;
    }

    /**
     * A window key written in a pattern object, or the default.
     *
     * <p>An unparseable value is reported and replaced by the default rather than thrown: a typo in a cosmetic
     * setting should not stop a server from starting.
     */
    private static Identifier windowDimension(JsonObject object)
    {
        if (object == null || !object.has("window_dimension") || object.get("window_dimension").isJsonNull())
            return DEFAULT_WINDOW_DIMENSION;
        String value = object.get("window_dimension").getAsString();
        if (value.isBlank()) return DEFAULT_WINDOW_DIMENSION;
        try
        {
            return Identifier.parse(value);
        }
        catch (Exception e)
        {
            LOGGER.error("[abyss] window_dimension \"{}\" is not a valid identifier; using {}",
                    value, DEFAULT_WINDOW_DIMENSION);
            return DEFAULT_WINDOW_DIMENSION;
        }
    }

    /**
     * Reads the editable file, or writes it out from the registered patterns when it does not exist.
     *
     * <p>A malformed file is reported and then ignored: the built-ins stay registered, so a typo costs the
     * custom patterns, not the world.
     */
    public static synchronized void load()
    {
        Path path = file();
        try
        {
            if (!Files.exists(path))
            {
                write(path);
                LOGGER.info("[abyss] wrote {} with {} built-in patterns", path, byName.size());
                return;
            }
            try (Reader reader = Files.newBufferedReader(path))
            {
                JsonElement root = JsonParser.parseReader(reader);
                //Both shapes: a bare array, which is what this file has always been, or an object with a "patterns"
                //array, which an earlier revision briefly wrote. Reading both means no existing file is rejected,
                //and the array form is still what gets written.
                JsonArray array = root.isJsonArray() ? root.getAsJsonArray()
                        : root.isJsonObject() && root.getAsJsonObject().has("patterns")
                                ? root.getAsJsonObject().getAsJsonArray("patterns")
                                : null;
                if (array == null)
                {
                    LOGGER.error("[abyss] {} has no patterns array; keeping the built-ins", path);
                    return;
                }
                Map<String, AbyssPattern> loaded = new LinkedHashMap<>();
                for (JsonElement element : array)
                {
                    AbyssPattern pattern = parse(element.getAsJsonObject());
                    loaded.put(pattern.name(), pattern);
                }
                byName.clear();
                byName.putAll(loaded);
                //The key that is also a band's level is the one value worth objecting to: the client would be told it
                //is in that level while the server keeps it in another, and anything that compares the two - a
                //teleport, a portal, a command - would see a dimension it does not expect. Warned, not refused,
                //because the world's owner is the judge of what a place should be called.
                for (AbyssPattern pattern : byName.values())
                    if (pattern.contains(ResourceKey.create(Registries.DIMENSION, pattern.windowDimension())))
                        LOGGER.warn("[abyss] {} names its window {} but that is also one of its bands' levels; "
                                    + "the client will be told it is in a level it is not in",
                                pattern.name(), pattern.windowDimension());
                LOGGER.info("[abyss] loaded {} patterns from {}", byName.size(), path);
                for (AbyssPattern pattern : byName.values())
                    LOGGER.debug("[abyss] {}: window key {}", pattern.name(), pattern.windowDimension());
            }
        }
        catch (Exception e)
        {
            LOGGER.error("[abyss] cannot use {}; keeping the built-in patterns", path, e);
        }
    }

    private static AbyssPattern parse(JsonObject object)
    {
        String name = object.get("name").getAsString();
        int height = object.get("band_height").getAsInt();
        boolean cyclic = object.has("cyclic") && object.get("cyclic").getAsBoolean();
        JsonArray bands = object.getAsJsonArray("bands");
        List<AbyssPattern.Band> parsed = new ArrayList<>(bands.size());
        for (JsonElement element : bands)
        {
            JsonObject band = element.getAsJsonObject();
            ResourceKey<Level> level = ResourceKey.create(Registries.DIMENSION,
                    Identifier.parse(band.get("level").getAsString()));
            int sourceY = band.has("source_y") ? band.get("source_y").getAsInt() : 0;
            parsed.add(new AbyssPattern.Band(level, sourceY));
        }
        return new AbyssPattern(name, height, cyclic, parsed, windowDimension(object));
    }

    private static void write(Path path) throws IOException
    {
        JsonArray array = new JsonArray();
        for (AbyssPattern pattern : byName.values())
        {
            JsonObject object = new JsonObject();
            object.addProperty("name", pattern.name());
            object.addProperty("band_height", pattern.bandHeight());
            object.addProperty("cyclic", pattern.cyclic());
            //Beside name and height: it describes this pattern, and a pattern that wants to be called something else
            //should be able to say so in the same object as everything else about it.
            object.addProperty("window_dimension", pattern.windowDimension().toString());
            JsonArray bands = new JsonArray();
            for (AbyssPattern.Band band : pattern.bands())
            {
                JsonObject entry = new JsonObject();
                entry.addProperty("level", band.level().identifier().toString());
                entry.addProperty("source_y", band.sourceY());
                bands.add(entry);
            }
            object.add("bands", bands);
            array.add(object);
        }
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path))
        {
            gson.toJson(array, writer);
        }
    }
}
