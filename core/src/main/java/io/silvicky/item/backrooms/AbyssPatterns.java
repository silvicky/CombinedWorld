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
                if (!root.isJsonArray())
                {
                    LOGGER.error("[abyss] {} must be a JSON array of patterns; keeping the built-ins", path);
                    return;
                }
                Map<String, AbyssPattern> loaded = new LinkedHashMap<>();
                for (JsonElement element : root.getAsJsonArray())
                {
                    AbyssPattern pattern = parse(element.getAsJsonObject());
                    loaded.put(pattern.name(), pattern);
                }
                byName.clear();
                byName.putAll(loaded);
                LOGGER.info("[abyss] loaded {} patterns from {}", byName.size(), path);
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
        return new AbyssPattern(name, height, cyclic, parsed);
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
