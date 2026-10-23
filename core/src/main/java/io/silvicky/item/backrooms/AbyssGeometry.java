package io.silvicky.item.backrooms;

import net.minecraft.resources.Identifier;

/**
 * The vertical dimensions of one pattern: how tall a band is, and therefore how tall the window the client is
 * shown is.
 *
 * <p>This used to be three global constants, which meant exactly one band height per world. It is per pattern
 * instead because the two things a pattern can describe are not the same size: a spiral whose terrain repeats every
 * 64 blocks wants 64-block bands, and a deep abyss with 1024-block layers wants 1024-block bands, and both should be
 * able to exist in one save at once.
 *
 * <p><b>One geometry is one client dimension type.</b> The client is told a height once, when the spawn info is
 * sent, and builds its section array, heightmaps and light range from it. So a pattern's band height decides which
 * window dimension type it is announced with - see {@link #windowType()} - and two patterns with different heights
 * must be shown different types. A pattern's height never changes while a player is in it, so this is consistent.
 *
 * <h2>What {@code minY} is for</h2>
 *
 * <p>Both the band height and the window height must be multiples of 16 (sections), and {@code DimensionType} caps
 * {@code min_y + height} at 2032. A 64-tall band gives a 192-tall window that fits from 0; a 1024-tall band gives a
 * 3072-tall window, which can only exist if it starts below zero. So the window's lowest Y is derived from its
 * height rather than assumed: 0 while it fits, and the smallest multiple of 16 that brings the top back under the cap
 * once it does not.
 *
 * <p>The offset is invisible to the player - it is the same convention as the band offset, applied once to the whole
 * window - and it does not affect a band's own level, whose coordinates are never shifted.
 *
 * @param bandHeight height of one band, a positive multiple of 16
 * @param minY       lowest Y of the window the client is shown, a multiple of 16, zero or negative
 */
public record AbyssGeometry(int bandHeight, int minY)
{
    /** How many bands the client is shown at once. Constant: the window is always three bands. */
    public static final int LAYERS = 3;

    /** The highest {@code min_y + height} a {@code DimensionType} accepts. */
    public static final int MAX_TOP = 2032;

    /** The lowest {@code min_y} a {@code DimensionType} accepts. */
    public static final int MIN_BOTTOM = -2032;

    /** The smallest band height that leaves a floor and a ceiling with a gap between them. */
    public static final int MIN_BAND_HEIGHT = 32;

    /** The geometry every world used before band heights were per pattern, and the fallback outside a window. */
    public static final AbyssGeometry DEFAULT = of(64);

    /** One section, in blocks. Everything above is a multiple of this. */
    private static final int SECTION = 16;

    public AbyssGeometry
    {
        if (bandHeight <= 0 || bandHeight % SECTION != 0)
            throw new IllegalArgumentException("band height " + bandHeight + " is not a positive multiple of 16");
        if (bandHeight < MIN_BAND_HEIGHT)
            throw new IllegalArgumentException("band height " + bandHeight + " is below the minimum of " + MIN_BAND_HEIGHT);
        if (minY % SECTION != 0)
            throw new IllegalArgumentException("window min_y " + minY + " is not a multiple of 16");
        if (minY + bandHeight * LAYERS > MAX_TOP)
            throw new IllegalArgumentException("window " + bandHeight * LAYERS + " tall starting at " + minY
                                               + " reaches " + (minY + bandHeight * LAYERS) + ", above the "
                                               + MAX_TOP + " limit");
        if (minY < MIN_BOTTOM)
            throw new IllegalArgumentException("window " + bandHeight * LAYERS + " tall would have to start at "
                                               + minY + ", below the " + MIN_BOTTOM + " limit; the tallest band "
                                               + "height that fits is " + maxBandHeight());
    }

    /**
     * The geometry for a band height, with the window placed as high as the limit allows.
     *
     * <p>{@code min_y} is 0 whenever the window fits above it, so every world that used one band height before gets
     * exactly the coordinates it had. Only a window too tall for that - above 2032 - is pushed down, and then by the
     * least amount that works, so a client's Y values stay as close to the naive ones as possible.
     */
    public static AbyssGeometry of(int bandHeight)
    {
        int window = bandHeight * LAYERS;
        if (window <= MAX_TOP) return new AbyssGeometry(bandHeight, 0);
        int below = window - MAX_TOP;
        int minY = -(Math.floorDiv(below + SECTION - 1, SECTION) * SECTION);
        return new AbyssGeometry(bandHeight, minY);
    }

    /**
     * The tallest band height a window can serve.
     *
     * <p>A window may span from {@link #MIN_BOTTOM} to {@link #MAX_TOP}, so 4064 blocks over three bands: 1344, the
     * largest multiple of 16 that leaves the whole window inside both limits.
     */
    public static int maxBandHeight()
    {
        return (MAX_TOP - MIN_BOTTOM) / LAYERS / SECTION * SECTION;
    }

    public int windowHeight()
    {
        return bandHeight * LAYERS;
    }

    public int sectionsPerBand()
    {
        return bandHeight / SECTION;
    }

    public int windowSections()
    {
        return windowHeight() / SECTION;
    }

    /**
     * Client Y minus server Y for a band in a slot.
     *
     * <pre>
     *   clientY = serverY + minY + slot * bandHeight - sourceY(band)
     * </pre>
     */
    public int slotOffset(int slot, int sourceY)
    {
        return minY + slot * bandHeight - sourceY;
    }

    /**
     * The dimension type the client is told it is in.
     *
     * <p>Named after the height, so a world that wants a second band height only has to add a matching
     * {@code dimension_type/abyss_window_<height>.json}. The 64 case keeps the unsuffixed name it has always had,
     * so existing worlds and packs are unaffected.
     */
    public Identifier windowType()
    {
        return bandHeight == 64
                ? AbyssWindow.WINDOW_TYPE
                : Identifier.fromNamespaceAndPath(AbyssWindow.WINDOW_TYPE.getNamespace(),
                                                  AbyssWindow.WINDOW_TYPE.getPath() + "_" + bandHeight);
    }

    @Override
    public String toString()
    {
        return "band " + bandHeight + ", window " + windowHeight() + " from " + minY;
    }
}
