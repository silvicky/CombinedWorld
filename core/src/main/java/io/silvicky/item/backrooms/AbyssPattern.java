package io.silvicky.item.backrooms;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An ordered list of bands, concatenated along Y.
 *
 * <p>A band says <b>which level</b> fills one slice of the window and <b>where in that level</b> the slice
 * starts. The window shows {@link AbyssWindow#WINDOW_LAYERS} consecutive entries of this list, and the client
 * is always shown the result as one dimension.
 *
 * <p>A band is a descriptor, not an index. That distinction is the whole point of this class:
 * <ul>
 *   <li>the same level may appear several times, at different heights or even at the same height;</li>
 *   <li>a band may take a slice from the middle of a taller level ({@code sourceY});</li>
 *   <li>a cyclic pattern has no end, so its length does not bound how far a player can travel.</li>
 * </ul>
 *
 * <h2>What a pattern has to satisfy</h2>
 *
 * <p><b>{@code bandHeight} is per pattern.</b> The client is told a dimension height once and builds its section
 * array, heightmaps and light range from it, so one band height means one window dimension type: the pattern's
 * {@link AbyssGeometry} says which, and two patterns in one save may use different heights. A height that is not a
 * positive multiple of 16, or one whose window cannot fit under {@code DimensionType}'s limit, is rejected at
 * registration with a message saying so, rather than producing a world whose chunks do not fit.
 *
 * <p><b>A cyclic pattern's geometry has to be periodic in Y with the same period.</b> Nothing enforces that -
 * the server will happily concatenate a level that does not repeat - but the seam then shows a visible break,
 * because the slice at the top of one band does not continue into the slice at the bottom of the next.
 *
 * <p><b>{@code sourceY} must be a multiple of 16</b> so that a band is a whole number of sections. A slice
 * that started mid-section would have to be reconstructed block by block.
 */
public final class AbyssPattern
{
    /**
     * One slice of the window.
     *
     * @param level   the level the slice is read from
     * @param sourceY lowest Y of the slice inside that level; a multiple of 16
     */
    public record Band(ResourceKey<Level> level, int sourceY) {}

    private final String name;
    private final int bandHeight;
    private final AbyssGeometry geometry;
    private final boolean cyclic;
    private final List<Band> bands;

    /**
     * The dimension key the client is told it is in while this pattern is the one in force.
     *
     * <p>Per pattern, not global: the window is not a real level, so this key exists only to be sent, and two
     * patterns may legitimately want to be called different things - a world of stacked plains and a spiral are not
     * the same place. It travels in the same packets as the bands, so it is fixed for a session like they are.
     */
    private final Identifier windowDimension;

    public AbyssPattern(String name, int bandHeight, boolean cyclic, List<Band> bands)
    {
        this(name, bandHeight, cyclic, bands, AbyssPatterns.DEFAULT_WINDOW_DIMENSION);
    }

    public AbyssPattern(String name, int bandHeight, boolean cyclic, List<Band> bands, Identifier windowDimension)
    {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("a pattern needs a name");
        if (bands.isEmpty()) throw new IllegalArgumentException(name + ": a pattern needs at least one band");
        //One geometry per pattern, so one band height per pattern rather than one per world. See AbyssGeometry for
        //why that is a dimension type as well as a number.
        try
        {
            this.geometry = AbyssGeometry.of(bandHeight);
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException(name + ": " + e.getMessage());
        }
        for (Band band : bands)
        {
            if (band.level() == null) throw new IllegalArgumentException(name + ": a band has no level");
            if (band.sourceY() % 16 != 0)
                throw new IllegalArgumentException(name + ": source_y " + band.sourceY() + " is not a multiple of 16");
        }
        this.name = name;
        this.bandHeight = bandHeight;
        this.cyclic = cyclic;
        this.bands = List.copyOf(bands);
        this.windowDimension = windowDimension == null
                ? AbyssPatterns.DEFAULT_WINDOW_DIMENSION : windowDimension;
    }

    /** This pattern's vertical geometry: band height, window height and where the window starts. */
    public AbyssGeometry geometry()
    {
        return geometry;
    }

    /** The dimension key the client is told it is in while this pattern is in force. */
    public Identifier windowDimension()
    {
        return windowDimension;
    }

    public String name()
    {
        return name;
    }

    public int bandHeight()
    {
        return bandHeight;
    }

    public boolean cyclic()
    {
        return cyclic;
    }

    public List<Band> bands()
    {
        return bands;
    }

    public int length()
    {
        return bands.size();
    }

    /** Sections in one band. */
    public int sectionsPerBand()
    {
        return geometry.sectionsPerBand();
    }

    /**
     * The band at an absolute pattern index, or null past the end of an acyclic pattern.
     *
     * <p>Cyclic patterns wrap, so there is no far end: index 1000 of a one-band pattern is that band. The
     * modulo is floored, so negative indices wrap the same way and a player can descend past the start.
     */
    public @Nullable Band band(int index)
    {
        if (cyclic) return bands.get(Math.floorMod(index, bands.size()));
        if (index < 0 || index >= bands.size()) return null;
        return bands.get(index);
    }

    public boolean contains(ResourceKey<Level> level)
    {
        for (Band band : bands) if (band.level().equals(level)) return true;
        return false;
    }

    /**
     * Where a player who is standing in {@code level} at {@code y} belongs in this pattern.
     *
     * <p>Prefers a band whose slice actually contains {@code y}, so a player teleported into the middle of a
     * tall level starts in the band they can see. Falls back to the first band of that level, which is the
     * sensible answer for a player standing above or below every slice.
     *
     * @return the absolute band index, or -1 when this pattern has no band in that level
     */
    public int indexOf(ResourceKey<Level> level, double y)
    {
        int first = -1;
        for (int i = 0; i < bands.size(); i++)
        {
            Band band = bands.get(i);
            if (!band.level().equals(level)) continue;
            if (first < 0) first = i;
            if (y >= band.sourceY() && y < band.sourceY() + bandHeight) return i;
        }
        return first;
    }

    @Override
    public String toString()
    {
        return name + (cyclic ? " (cyclic)" : "") + " " + bands;
    }
}
