package io.silvicky.item.backrooms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * A vertical window over a concatenation of bands.
 *
 * <h2>What is real and what is shown</h2>
 *
 * <p>Every band names a real level and a slice of it (see {@link AbyssPattern}). A player physically stands in
 * one band, at that band's own coordinates. The client is told something else: that it is in one dimension
 * {@link #WINDOW_HEIGHT} tall, and it is sent chunks assembled from {@link #WINDOW_LAYERS} consecutive bands.
 *
 * <p>Client coordinates are window coordinates:
 *
 * <pre>
 *   clientY = serverY + WINDOW_MIN_Y + slot * bandHeight - sourceY(band)
 *   slot    = position - windowStart        (0, 1 or 2)
 * </pre>
 *
 * <p><b>Where the player is in the pattern is state, not a property of the level.</b> That is the difference
 * from the first version, which read the band off {@code layerIndex(player.level())}. Deriving it from the
 * level made three things impossible: the same level appearing twice in one window, a pattern whose order the
 * user chooses, and a cyclic pattern with no end. Here {@code position} is an absolute index into the pattern,
 * and the only thing the level is used for is deciding where a player who has been moved from outside belongs
 * (see {@link AbyssPattern#indexOf}).
 *
 * <h2>Travelling</h2>
 *
 * <p>Leaving a band by falling or climbing is a real teleport into the next band's level, at the Y that keeps
 * the client continuous. Because the band and the offset change together, the player sees a step of at most
 * one block. Two consecutive bands that name the same level make this a teleport inside one level, which is the
 * cheapest case of all: vanilla sends a position packet and nothing else.
 *
 * <p>The window then slides to keep the player in the middle band. A slide re-sends the view only when the
 * window's contents actually change: for a cyclic pattern whose bands are all the same, they never do, so an
 * endless descent costs one position packet per period and no chunks at all.
 */
public final class AbyssWindow
{
    /** How many bands the client is shown at once. */
    public static final int WINDOW_LAYERS = 3;
    /** Height the client is told the dimension has: the only band height one dimension type can serve. */
    public static final int WINDOW_HEIGHT = 192;
    /** Height of one band, forced by the above. */
    public static final int BAND_HEIGHT = WINDOW_HEIGHT / WINDOW_LAYERS;
    /** Sections in one band. */
    public static final int SECTIONS_PER_BAND = BAND_HEIGHT / 16;
    /** Sections in the window. */
    public static final int WINDOW_SECTIONS = WINDOW_HEIGHT / 16;

    /** Lowest Y of the window. 0 because 192 fits inside {@code DimensionType}'s range with room to spare. */
    public static final int WINDOW_MIN_Y = 0;

    /**
     * How close to a band's edge the player may get before the window slides, in blocks.
     *
     * <p>The slide is invisible because the bands move with the player: terrain and camera shift together by
     * exactly one band height. Doing it a few blocks early means the seam crossing itself - where the band
     * changes by one and the layer changes by one, cancelling exactly - carries no slide at all.
     */
    /**
     * How close to the top of a band the player may get before the upward crossing is attempted.
     *
     * <p>Retained for the crossing threshold below; the sliding it used to name is gone. A band is
     * {@link #BAND_HEIGHT} tall and the levels really are that tall, so a player's local Y never reaches the
     * band height and the plain {@code local >= BAND_HEIGHT} test can never fire. This is the point where the
     * next band up is entered instead.
     */
    public static final int SLIDE_BUFFER = 4;

    /**
     * The dimension type sent instead of the band's own, so the client builds a 192-tall level.
     *
     * <p>A datapack entry, so it is in the synced registry and resolvable by a vanilla client.
     */
    public static final Identifier WINDOW_TYPE = Identifier.parse("silvicky:abyss_window");
    /** The key the client is told it is in when a pattern does not name one. */
    public static final Identifier DEFAULT_WINDOW_DIMENSION = AbyssPatterns.DEFAULT_WINDOW_DIMENSION;

    /**
     * The key the client is told it is in, for a player standing in this level.
     *
     * <p>A datapack identifier, so the client can resolve it as a dimension key; the <em>level</em> it names need not
     * exist, because the player is always physically inside a band. Whatever the pattern says - see
     * {@link AbyssPattern#windowDimension()}. A level that belongs to no pattern gets the default.
     */
    public static Identifier windowDimension(ResourceKey<Level> level)
    {
        return AbyssPatterns.windowDimension(level);
    }

    /** The key for a player's current band. */
    public static Identifier windowDimension(ServerPlayer player)
    {
        AbyssPattern pattern = AbyssPatterns.forLevel(player.level().dimension());
        return pattern == null ? DEFAULT_WINDOW_DIMENSION : pattern.windowDimension();
    }

    public static ResourceKey<Level> windowKey(ResourceKey<Level> level)
    {
        return ResourceKey.create(Registries.DIMENSION, windowDimension(level));
    }

    /** A player's place in a pattern, plus which bands are currently shown. */
    public record Window(AbyssPattern pattern, int position, int windowStart)
    {
        /** Which band slot the player is physically in: 0 bottom, 1 middle, 2 top. */
        public int slot()
        {
            return position - windowStart;
        }

        public AbyssPattern.Band band()
        {
            return pattern.band(position);
        }

        public List<AbyssPattern.Band> bands()
        {
            List<AbyssPattern.Band> out = new ArrayList<>(WINDOW_LAYERS);
            for (int i = 0; i < WINDOW_LAYERS; i++) out.add(pattern.band(windowStart + i));
            return out;
        }
    }

    private static final Map<ServerPlayer, Window> windows = new ConcurrentHashMap<>();
    private static final Map<ServerPlayer, Boolean> ready = new ConcurrentHashMap<>();
    private static final Map<ServerPlayer, TicketKey> tickets = new ConcurrentHashMap<>();
    private static final Map<ServerPlayer, Set<ChunkPos>> incomplete = new ConcurrentHashMap<>();
    private static final Map<ServerPlayer, Integer> ticks = new ConcurrentHashMap<>();
    /**
     * The seam teleport is in flight: from `setWindow` until the client has been told where it now stands.
     *
     * <p>Used to suppress the respawn packet, which would rebuild the client's level for a move that stays in
     * the same dimension.
     */
    private static final Set<ServerPlayer> seamTeleports = ConcurrentHashMap.newKeySet();

    /**
     * Set while vanilla is handling the client's acknowledgement of a position packet.
     *
     * <p>That acknowledgement is the one place where the reported position is measured against the teleport's
     * destination, and for a falling player the two are separated by however far they fell while the packet was
     * in flight. At terminal velocity that is ~4 blocks a tick, so vanilla's ten-block allowance covers about
     * 130 ms of round trip - less than any real client. Measured, the seam crossing is reported as
     * "moved too quickly" and answered with a teleport back to the destination, which is the hitch and the snap.
     *
     * <p>So the acknowledgement of a teleport is not movement and is not measured here. See
     * {@code ServerGamePacketListenerImplMixin}, which sets this around the call.
     */
    private static final ThreadLocal<Boolean> IN_TELEPORT_ACK = ThreadLocal.withInitial(() -> false);

    /**
     * Columns whose chunk finished loading, waiting to be handled on the server thread.
     *
     * <p>Fabric fires {@code ServerChunkEvents.CHUNK_LOAD} from inside the chunk-status task chain, so the
     * handler records and the server thread drains. Touching {@code PlayerChunkSender.pendingChunks} - an
     * unsynchronised {@code LongOpenHashSet} - from that thread loses inserts, and a lost insert is a chunk the
     * client is never told about.
     */
    private static final Set<LayerColumn> loadedColumns = ConcurrentHashMap.newKeySet();

    private record LayerColumn(ResourceKey<Level> level, ChunkPos pos) {}
    private record TicketKey(ChunkPos pos, int windowStart, String pattern, int radius) {}

    private AbyssWindow() {}

    // ---------------------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------------------

    /**
     * Players whose {@code player.level()} is temporarily pointed at another band.
     *
     * <p>Used by cross-band entity actions, which have to run vanilla's handler with the player in the target's
     * level (vanilla resolves the target via {@code player.level().getEntityOrPart(id)}). While that is true the
     * stored window <b>must</b> be trusted: {@link #window} re-derives whenever the stored band's level differs
     * from the player's, and re-deriving here would rewrite the window with the *target* band as the player's
     * own. That silently corrupts every later tick - the real own band becomes foreign and its entities are
     * copied at the wrong slot - and it makes the reach test measure against the wrong frame, which is why an
     * action on a copy was rejected before it could be dispatched.
     */
    private static final Set<ServerPlayer> levelSwaps = ConcurrentHashMap.newKeySet();

    /** Marks the player's level as pointed elsewhere, for the duration of a cross-band action. */
    public static void beginLevelSwap(ServerPlayer player)
    {
        levelSwaps.add(player);
    }

    public static void endLevelSwap(ServerPlayer player)
    {
        levelSwaps.remove(player);
    }

    /**
     * This player's window, creating it on first use.
     *
     * <p>The stored window is trusted unless the player is demonstrably somewhere else: their level is not the
     * band's level. That test is skipped while a seam teleport is in flight, because during it the player
     * object still reports the level they are leaving while the packets being sent already belong to the level
     * they are entering - recomputing there would undo the very move that keeps the client continuous.
     */
    public static @Nullable Window window(@Nullable ServerPlayer player)
    {
        if (player == null) return null;
        Window stored = windows.get(player);
        if (stored != null && stored.band() != null)
        {
            if (seamTeleports.contains(player) || levelSwaps.contains(player)) return stored;
            if (stored.band().level().equals(player.level().dimension())) return stored;
        }
        AbyssPattern pattern = AbyssPatterns.forLevel(player.level().dimension());
        if (pattern == null)
        {
            windows.remove(player);
            return null;
        }
        int position = pattern.indexOf(player.level().dimension(), player.getY());
        if (position < 0)
        {
            windows.remove(player);
            return null;
        }
        Window fresh = new Window(pattern, position, initialStart(pattern, position));
        //Loud on purpose: this path runs only when the stored window was missing or contradicted the player's
        //level, and it discards whatever window was there. If it fires during an ordinary session, something is
        //reading the window in a frame where {@code player.level()} does not match it - which is how a
        //cross-band action used to corrupt the own band.
        LOGGER.info("[abyss] window derived for {}: {} position {} start {} (level {}, y={})",
                player.getName().getString(), pattern.name(), position, fresh.windowStart(),
                player.level().dimension().identifier(), Math.round(player.getY()));
        setWindow(player, fresh);
        return fresh;
    }

    /** Whether this player is being shown a window at all. */
    public static boolean handles(@Nullable ServerPlayer player)
    {
        return window(player) != null;
    }

    private static int initialStart(AbyssPattern pattern, int position)
    {
        return clampStart(pattern, position - WINDOW_LAYERS / 2);
    }

    /** Acyclic patterns cannot scroll past either end; cyclic ones can scroll forever. */
    private static int clampStart(AbyssPattern pattern, int start)
    {
        if (pattern.cyclic()) return start;
        int max = Math.max(0, pattern.length() - WINDOW_LAYERS);
        return Mth.clamp(start, 0, max);
    }

    public static int slot(@Nullable ServerPlayer player)
    {
        Window window = window(player);
        return window == null ? 0 : window.slot();
    }

    public static AbyssPattern.@Nullable Band band(@Nullable ServerPlayer player)
    {
        Window window = window(player);
        return window == null ? null : window.band();
    }

    /** Client Y minus server Y for this player. Negative when the band sits below the window origin. */
    public static int bandOffset(@Nullable ServerPlayer player)
    {
        Window window = window(player);
        if (window == null || window.band() == null) return 0;
        return WINDOW_MIN_Y + window.slot() * BAND_HEIGHT - window.band().sourceY();
    }

    /**
     * The offset in effect now, and the one before it, per player.
     *
     * <p>Every component here is concurrent because the outbound hook reads it off the netty thread. The old
     * value is kept rather than cleared on purpose: a packet generated before a slide can arrive after it, and
     * the only way to read that packet's coordinates is with the frame it was written in. Discarding the old
     * value would turn one late packet into a 64-block teleport.
     */
    private static final Map<ServerPlayer, int[]> offsets = new ConcurrentHashMap<>();

    /** Records the current offset, remembering the previous one when it changed. */
    private static void noteOffset(ServerPlayer player)
    {
        int now = bandOffset(player);
        offsets.compute(player, (p, state) ->
                state == null || state[0] == now ? new int[] {now} : new int[] {now, state[0]});
    }

    /** The frames an inbound coordinate may have been written in. */
    private static int[] candidateOffsets(ServerPlayer player)
    {
        int[] state = offsets.get(player);
        return state == null ? new int[] {bandOffset(player)} : state;
    }

    /** Applies a window change, keeping the offset history that inbound packets are read against. */
    private static void setWindow(ServerPlayer player, Window window)
    {
        //Purely structural: this must not call window(player), which on a miss builds a window by calling this
        //method. Reading the maps directly is what keeps the two from recursing into each other.
        Window previous = windows.put(player, window);
        noteOffset(player);
        if (offsetOf(previous) != offsetOf(window))
        {
            LOGGER.info("[abyss] offset for {} {} -> {} (position {} -> {}, start {} -> {}, slot {} -> {})",
                    player.getName().getString(), offsetOf(previous), offsetOf(window),
                    previous == null ? "?" : previous.position(), window.position(),
                    previous == null ? "?" : previous.windowStart(), window.windowStart(),
                    previous == null ? "?" : previous.slot(), window.slot());
            //Only when the own band keeps its LEVEL. A crossing into another level makes vanilla re-send that
            //level's entities by itself (the teleport redoes the player's tracking), so touching them here would
            //be work done twice on entities the client may not even hold yet. A change of slot inside one level -
            //which a pattern with two slices of the same level produces - is the case vanilla cannot see, and it
            //is the case that leaves a stationary entity drawn a band away.
            boolean sameLevel = previous != null && previous.band() != null && window.band() != null
                                && previous.band().level().equals(window.band().level());
            if (sameLevel) AbyssEntityCopies.repositionOwnBand(player);
            //The shuffle's remembered bases go either way, so the next delta of each surviving entity is
            //absolute and lands in the new frame.
            if (player.connection instanceof EntityRemapAccess access) access.item_storage$resetEntityRemapper();
        }
    }

    /** The Y offset a window implies, without creating one. */
    private static int offsetOf(@Nullable Window window)
    {
        if (window == null || window.band() == null) return 0;
        return WINDOW_MIN_Y + window.slot() * BAND_HEIGHT - window.band().sourceY();
    }

    /**
     * The server Y an inbound client Y refers to, choosing the frame that puts it nearest the player.
     *
     * <p>This is the inbound half of the slide problem. A slide moves the client's whole coordinate system by
     * one band while the player does not move at all, so a packet written before the slide and read after it is
     * one band out. Both readings are tried and the one landing closest to where the server already has the
     * player is taken: a move or a click the player actually made is near them in exactly one of the two
     * frames. Getting this wrong is what vanilla reports as "moved too quickly".
     */
    /**
     * The slot the inbound conversion chose for a given converted block position.
     *
     * <p>Keyed by position, not by player: every move packet also converts a position, and a per-player slot
     * would be overwritten by the next move before the click it belongs to is handled. With the position as the
     * key, the click's own conversion is the one that is looked up.
     */
    private static final Map<ServerPlayer, Map<BlockPos, SlotRecord>> posSlots = new ConcurrentHashMap<>();

    /**
     * A recorded slot, aged by server tick.
     *
     * <p>Ageing is what makes the record trustworthy. The slot a position belongs to changes as the window
     * moves, so an entry that outlives the click it came from would dispatch a later action on the same
     * coordinates to whichever band happened to be right back then - the player's own block would stay solid
     * while the same block in another band was destroyed. Only the current and previous tick are accepted, which
     * covers the packet being handled a tick after it was converted without letting the cache live on.
     */
    private record SlotRecord(int slot, long tick) {}

    /** Incremented once per server tick, for {@link SlotRecord}. */
    private static volatile long tickCounter;

    /** A client Y resolved into server coordinates, with the slot it came from (-1 when no slot matched). */
    private record Resolution(double value, int slot) {}

    /**
     * Resolves a clicked client Y to the band that was clicked, recording the slot.
     *
     * <p>This is for <b>aimed</b> coordinates only - a block the player pointed at - because there the question
     * is genuinely "which band's copy is this". Bands' client-Y ranges are disjoint, so containment is
     * unambiguous.
     *
     * <p>It must <b>not</b> be used for the player's own position. Doing so was the cause of the layer
     * teleports: a player standing in layer 1 at client y=68 who falls four blocks arrives at client y=63.9,
     * which is inside the band <em>below</em>. Containment then reattributed their own position to layer 0, the
     * server jumped them 60 blocks, and because the band index never changed the window did not move. See
     * {@link #toServerY} for the player's own frame.
     */
    private static Resolution resolveClick(ServerPlayer player, double clientY)
    {
        Window window = window(player);
        if (window == null) return new Resolution(clientY, -1);

        for (int slot = 0; slot < WINDOW_LAYERS; slot++)
        {
            AbyssPattern.Band band = window.pattern().band(window.windowStart() + slot);
            if (band == null) continue;
            double value = clientY - slotOffset(slot, band);
            if (value >= band.sourceY() && value < band.sourceY() + BAND_HEIGHT)
                return new Resolution(value, slot);
        }
        return new Resolution(clientY - bandOffset(player), -1);
    }

    /**
     * Resolves the player's own client Y into the band they are standing in.
     *
     * <p>The player's frame is the one band they are in, not the whole window. Subtracting that band's offset
     * is therefore the whole conversion, and it is stable while they fall, climb, or cross a seam - the crossing
     * changes the offset by exactly one band and the position by exactly the opposite amount, so the answer
     * stays continuous. Anything that tests containment instead will jump them a band as soon as they pass a
     * seam inside the window, which is what made a fall of four blocks teleport the player between layers.
     *
     * <p>The previous offset is also tried, because a packet written just before a window change can be read
     * just after it; the nearer answer is the intended one.
     */
    public static double toServerY(ServerPlayer player, double clientY)
    {
        if (window(player) == null) return clientY;
        int[] candidates = candidateOffsets(player);
        double best = clientY - candidates[0];
        double bestDistance = Math.abs(best - player.getY());
        for (int i = 1; i < candidates.length; i++)
        {
            double value = clientY - candidates[i];
            double distance = Math.abs(value - player.getY());
            if (distance < bestDistance)
            {
                best = value;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The offset of one slot: client Y minus server Y for a position in that slot's band. */
    private static int slotOffset(int slot, AbyssPattern.Band band)
    {
        return WINDOW_MIN_Y + slot * BAND_HEIGHT - band.sourceY();
    }

    /**
     * The Y offset for an arbitrary slot of a band, for code outside this class that places things in the
     * window - the entity copies above all.
     *
     * @param slot the slot the band is shown in, which is {@code position - windowStart}, not the player's own
     */
    public static int slotOffsetY(int slot, AbyssPattern.Band band)
    {
        return slotOffset(slot, band);
    }

    /**
     * The Y range of a band's slice inside its own level.
     *
     * <p>A band is a slice, but a level may be taller than one band, and it may hold more than one slice. An
     * entity belongs to a slice only if it stands inside it; an entity elsewhere in the same level is not
     * visible through this band at all.
     */
    public static boolean inSlice(AbyssPattern.Band band, double serverY)
    {
        return serverY >= band.sourceY() && serverY < band.sourceY() + BAND_HEIGHT;
    }

    /**
     * Whether a bounding box overlaps a band's slice.
     *
     * <p>Used instead of testing the feet alone, because a position exactly on a boundary is common: an entity
     * standing on the highest block of a band has its feet at {@code sourceY + BAND_HEIGHT}, one tenth of a block
     * outside the slice the feet test uses, and would be dropped from the band it is standing in.
     */
    public static boolean inSlice(AbyssPattern.Band band, double minY, double maxY)
    {
        return maxY > band.sourceY() && minY < band.sourceY() + BAND_HEIGHT;
    }

    /**
     * The client Y a server position should be measured at, for distance checks.
     *
     * <p>Vanilla's distance is undefined here, and the reason is not the offset - it is that a window can show
     * the same level more than once. A block in the spiral has three client images, 64 apart, so "how far away
     * is it" has three answers and the only meaningful one is the nearest. Reach is judged against the nearest
     * image, which is also the one the player is looking at when the answer is small.
     *
     * <p>This is the vertical counterpart of {@code VecTransformer.s2cTransform} returning the mapped position:
     * both exist so that the game measures what the player sees, not what the server stores.
     */
    public static double nearestClientY(ServerPlayer player, double serverY)
    {
        Window window = window(player);
        if (window == null) return serverY;
        double eye = clientY(player);
        double best = serverY + bandOffset(player);
        double bestDistance = Math.abs(best - eye);
        for (int slot = 0; slot < WINDOW_LAYERS; slot++)
        {
            AbyssPattern.Band band = window.pattern().band(window.windowStart() + slot);
            if (band == null) continue;
            //Every band counts, including the ones whose level is not the player's. This is a measurement made
            //in the client's frame, and the client draws all three bands as one dimension - a block in the band
            //above is one block away at the seam, not sixty-four. Filtering by level here made its nearest image
            //come out a whole band off, which put it out of reach and got the click rejected before dispatch
            //ever ran.
            //
            //A band whose slice does not contain the block has no image of it: with a single source level all
            //bands qualify, but a pattern taking slices from a taller level must not invent an image in a band
            //that shows a different part of it.
            if (serverY < band.sourceY() || serverY >= band.sourceY() + BAND_HEIGHT) continue;
            double image = serverY + slotOffset(slot, band);
            double distance = Math.abs(image - eye);
            if (distance < bestDistance)
            {
                best = image;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A server position in the player's level, seen at its nearest image in the window. */
    public static double nearestClientY(ServerPlayer player, Vec3 serverPos)
    {
        return nearestClientY(player, serverPos.y);
    }

    /**
     * The level a block action at this server position belongs to, or null when it belongs to the player's own
     * level (the normal case).
     *
     * <p>The model here is "the server simulates the client's valid spaces": the player acts on what the
     * client shows them, and if that block lives in another band's level, the action happens there. The client
     * cannot tell the difference, because it only ever sees one dimension.
     *
     * <p><b>Not derivable from Y alone.</b> Every band in a pattern spans the same Y range (0..63 for a 64-tall
     * band), so a Y cannot say which band it came from - only which bands *could* contain it. The slot the
     * inbound conversion chose is therefore recorded against the converted position ({@link #posSlots}), so a
     * click and the action it triggers are guaranteed to mean the same band. Falling back to a Y scan is only
     * for actions that did not come through a coordinate conversion.
     */
    public static @Nullable ServerLevel actionLevel(ServerPlayer player, BlockPos pos)
    {
        Window window = window(player);
        if (window == null) return null;
        MinecraftServer server = player.level().getServer();
        if (server == null) return null;

        //Use the slot the click actually addressed, recorded when the inbound position was converted.
        //
        //Deriving it here instead - "whichever band's image is nearest my eye" - is wrong, and produced the
        //opposite failure: a block low in the player's own band has a nearer image in the band *below*, because
        //that band's copy sits at the same client Y as the player's feet. Every click was then dispatched one
        //band down, so the player's own terrain never changed, the block they were aiming at stayed solid, and
        //the holes appeared in the level underneath - which is what they then fell through.
        //
        //With no record there is no safe answer: every band spans the same Y range, so a lone position cannot
        //say which band it belongs to. Do nothing, and the action runs in the player's own level as vanilla
        //would - the conservative behaviour rather than a guess that edits the wrong world.
        Map<BlockPos, SlotRecord> slots = posSlots.get(player);
        SlotRecord record = slots == null ? null : slots.get(pos);
        //Expired or absent: no safe answer, so the action stays in the player's own level. See the note below.
        if (record == null || tickCounter - record.tick() > 1) return null;
        int chosen = record.slot();

        AbyssPattern.Band band = window.pattern().band(window.windowStart() + chosen);
        if (band == null) return null;
        if (band.level().equals(player.level().dimension())) return null;
        return server.getLevel(band.level());
    }

    /**
     * The pattern position the last click addressed, or null when there is no trustworthy record.
     *
     * <p>Recorded when the inbound position was converted, so it says which band the player was actually
     * pointing at - not which one is nearest their eye, which is ambiguous because every band spans the same Y
     * range.
     *
     * <p>Unlike {@link #actionLevel} this answers for the player's <b>own</b> band too, because a placement that
     * crosses a seam needs to know where it is starting from even when the clicked block is in the own band.
     */
    public static @Nullable Integer clickPosition(ServerPlayer player, BlockPos pos)
    {
        Window window = window(player);
        if (window == null) return null;
        Map<BlockPos, SlotRecord> slots = posSlots.get(player);
        SlotRecord record = slots == null ? null : slots.get(pos);
        if (record == null || tickCounter - record.tick() > 1) return null;
        return window.windowStart() + record.slot();
    }

    /** The pattern position this player currently occupies. */
    public static int bandPosition(@Nullable ServerPlayer player)
    {
        Window window = window(player);
        return window == null ? 0 : window.position();
    }

    /** The band at an arbitrary pattern position, for code reasoning about a seam rather than about the player. */
    public static AbyssPattern.@Nullable Band bandAt(@Nullable ServerPlayer player, int position)
    {
        Window window = window(player);
        return window == null ? null : window.pattern().band(position);
    }

    /** The level at an arbitrary pattern position. */
    public static @Nullable ServerLevel levelAt(ServerPlayer player, int position)
    {
        MinecraftServer server = player.level().getServer();
        AbyssPattern.Band band = bandAt(player, position);
        return server == null || band == null ? null : server.getLevel(band.level());
    }


    /**
     * Every client section at which a section of the player's own level is visible, in slot order.
     *
     * <p>The section analogue of {@link #imageYs}. A batched block update has to be applied at each copy, and - 
     * more importantly - must not be applied anywhere else. An earlier version shifted the section by the
     * player's own band offset unconditionally, which wrote this level's section data into a slot showing a
     * <b>different</b> level; that is how untouched terrain turned into air.
     */
    public static List<SectionPos> imageSections(ServerPlayer player, SectionPos serverSection)
    {
        Window window = window(player);
        List<SectionPos> out = new ArrayList<>(WINDOW_LAYERS);
        if (window == null)
        {
            out.add(serverSection);
            return out;
        }
        ResourceKey<Level> level = player.level().dimension();
        double sample = serverSection.minBlockY();
        for (int slot = 0; slot < WINDOW_LAYERS; slot++)
        {
            AbyssPattern.Band band = window.pattern().band(window.windowStart() + slot);
            if (band == null || !band.level().equals(level)) continue;
            if (sample < band.sourceY() || sample >= band.sourceY() + BAND_HEIGHT) continue;
            int shift = slotOffset(slot, band) / 16;
            out.add(SectionPos.of(serverSection.x(), serverSection.y() + shift, serverSection.z()));
        }
        if (out.isEmpty()) out.add(serverSection);
        return out;
    }

    /**
     * The level of the band {@code step} bands away from the player's, or null when the pattern ends there.
     *
     * <p>For placement across a band boundary: a click on the top face of a band's highest block asks for a
     * block one block above it, which physically lives at the *bottom* of the next band. Vanilla has no way to
     * express that - the position is outside the level - so the action is re-targeted here.
     */
    public static @Nullable ServerLevel adjacentLevel(ServerPlayer player, int step)
    {
        Window window = window(player);
        if (window == null) return null;
        MinecraftServer server = player.level().getServer();
        if (server == null) return null;
        AbyssPattern.Band band = window.pattern().band(window.position() + step);
        if (band == null) return null;
        return server.getLevel(band.level());
    }

    /** Lowest Y of the band {@code step} bands away. */
    public static int adjacentSourceY(ServerPlayer player, int step)
    {
        Window window = window(player);
        if (window == null) return 0;
        AbyssPattern.Band band = window.pattern().band(window.position() + step);
        return band == null ? 0 : band.sourceY();
    }

    /**
     * Pushes a block's new state to this player at every image the window shows of it.
     *
     * <p>Needed after a cross-level action: the target level broadcasts its own block updates to the players
     * tracking that chunk <em>in that level</em>, and this player is not one of them - they are in another
     * band. Without this the block changes on the server and nothing happens on the client, which is the same
     * symptom as a rejected click.
     */
    public static void sendBlockUpdate(ServerPlayer player, ResourceKey<Level> level, BlockPos pos)
    {
        //The client-space images are resolved **once** and carried into the repeat.
        //
        //Recomputing them was wrong, and produced a visible glitch: place a block at your feet while crossing a
        //seam and the first update lands where you are looking, while the repeat - one tick later, after the window
        //has recentred - resolved the same server position to a *different* slot's Y. The client was told the block
        //was air at one height and solid at another, which reads as the block vanishing and reappearing, and the
        //disagreement about what is under the player is what shoved them off it.
        List<BlockPos> images = images(player, level, pos);
        sendImages(player, level, pos, images);
        //And once more next tick, after the client's own prediction for this position has been acknowledged.
        repeats.computeIfAbsent(player, p -> ConcurrentHashMap.newKeySet())
               .add(new PendingUpdate(level, pos, images));
    }

    /** The client-space positions one server block occupies in this player's window, as of now. */
    private static List<BlockPos> images(ServerPlayer player, ResourceKey<Level> level, BlockPos pos)
    {
        BlockPos client = pos;
        try
        {
            client = VecTransformer.getInstance(player).s2cTransform(pos);
        }
        catch (ChunkUnusedException ignored)
        {
            //Page not visible through the shuffle; fall through with the raw position and let the window
            //mapping put it where it can.
        }
        List<BlockPos> out = new ArrayList<>();
        for (double imageY : imageYs(player, level, pos.getY()))
            out.add(client.offset(0, (int) Math.round(imageY) - client.getY(), 0));
        return out;
    }

    /**
     * Sends one block's state to a list of already-resolved client positions.
     *
     * <p>The positions are in the client's frame, so they bypass the outbound hook: converting them again is not
     * idempotent, and a block at y=62 sent as 62 was re-read as a server Y and mapped to a slot, landing at 126.
     * Breaking downward then looked like a dead click while breaking upward survived, because 0 -> 64 -> 64 happens
     * to be stable.
     */
    private static void sendImages(ServerPlayer player, ResourceKey<Level> level, BlockPos pos,
                                   List<BlockPos> images)
    {
        if (player.connection == null) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ServerLevel source = server.getLevel(level);
        if (source == null) return;

        //The positions are already in the client's frame. Sending them through the connection would run
        //them through the outbound hook again, which converts a second time - and that is not idempotent: a block
        //at y=62 sent as 62 is re-read as a server Y and mapped to whichever slot the *player's* level occupies,
        //landing at 126 instead.
        boolean was = TRANSLATED.get();
        TRANSLATED.set(true);
        try
        {
            for (BlockPos image : images)
            {
                LOGGER.info("[abyss] update {} at server {} -> client {} state {}",
                            level.identifier(), pos, image, source.getBlockState(pos));
                player.connection.send(new ClientboundBlockUpdatePacket(image, source.getBlockState(pos)));
            }
        }
        finally
        {
            TRANSLATED.set(was);
        }
    }

    /**
     * Whether the packet being sent has already been placed in the client's frame.
     *
     * <p>Per thread: sends happen on the server thread here, but the hook also runs from wherever a plugin sends,
     * and a shared flag would let one thread's packet skip conversion because another thread happened to be
     * mid-send.
     */
    private static final ThreadLocal<Boolean> TRANSLATED = ThreadLocal.withInitial(() -> false);

    /**
     * Sends a packet whose coordinates are already in the client's frame.
     *
     * <p>Every packet the mod builds for a copy of an entity in another band is of this kind: it was built from
     * client-space numbers, so the outbound hook must not convert it again. Converting twice is not idempotent
     * - the same reason a block update built here is sent this way - and for entity packets it would also
     * record the copy's id as a base for an entity that has none.
     */
    public static void sendPreTranslated(ServerPlayer player, Packet<?> packet)
    {
        if (player.connection == null) return;
        boolean was = TRANSLATED.get();
        TRANSLATED.set(true);
        try
        {
            player.connection.send(packet);
        }
        finally
        {
            TRANSLATED.set(was);
        }
    }

    /** True when the current thread is sending a packet that needs no further window conversion. */
    public static boolean isTranslated()
    {
        return TRANSLATED.get();
    }

    /**
     * Every client Y at which a block in {@code level} is visible, in slot order.
     *
     * <p>A window may show one level more than once - the spiral shows it three times - so a single server
     * block has several images on the client. They are not decoration: the player can see and click any of
     * them, so an update has to reach all of them or the copies disagree.
     *
     * <p>The level is a parameter rather than "the player's level" because a block action can be dispatched to
     * another band (see {@link #actionLevel}), and the resulting update has to be shown at the images of the
     * level it actually happened in.
     */
    public static List<Double> imageYs(ServerPlayer player, ResourceKey<Level> level, double serverY)
    {
        Window window = window(player);
        List<Double> out = new ArrayList<>(WINDOW_LAYERS);
        if (window == null)
        {
            out.add(serverY);
            return out;
        }
        for (int slot = 0; slot < WINDOW_LAYERS; slot++)
        {
            AbyssPattern.Band band = window.pattern().band(window.windowStart() + slot);
            if (band == null || !band.level().equals(level)) continue;
            if (serverY < band.sourceY() || serverY >= band.sourceY() + BAND_HEIGHT) continue;
            out.add(serverY + slotOffset(slot, band));
        }
        //Not inside any band - the caller still needs somewhere to put it, and the player's own offset is the
        //least surprising answer.
        if (out.isEmpty()) out.add(serverY + bandOffset(player));
        return out;
    }

    public static double clientY(ServerPlayer player)
    {
        return player.getY() + bandOffset(player);
    }

    /** A server Y as the client should be told it. */
    public static double toClientY(ServerPlayer player, double serverY)
    {
        return serverY + bandOffset(player);
    }

    public static Vec3 toClient(ServerPlayer player, Vec3 serverPos)
    {
        return serverPos.add(0, bandOffset(player), 0);
    }

    public static BlockPos toClient(ServerPlayer player, BlockPos serverPos)
    {
        return serverPos.offset(0, bandOffset(player), 0);
    }

    public static SectionPos toClient(ServerPlayer player, SectionPos serverPos)
    {
        return SectionPos.of(serverPos.x(), serverPos.y() + bandOffset(player) / 16, serverPos.z());
    }

    public static Vec3 toServer(ServerPlayer player, Vec3 clientPos)
    {
        return clientPos.add(0, toServerY(player, clientPos.y) - clientPos.y, 0);
    }

    /**
     * The frame offset a click at this client Y belongs to.
     *
     * <p>A click carries two coordinates that vanilla cross-checks against each other: the block, and the point
     * inside it. They must be converted with <b>the same</b> offset, or vanilla measures a gap of one band and
     * rejects the packet as "too far away from hit block". That is what happened while the block went through
     * containment and the point through the player's own frame: one landed at y=4 and the other stayed at y=68.
     *
     * <p>So this is the single source of truth for a click's frame, and both coordinates use it.
     */
    public static int clickOffset(ServerPlayer player, double clientY)
    {
        Window window = window(player);
        if (window == null) return 0;
        for (int slot = 0; slot < WINDOW_LAYERS; slot++)
        {
            AbyssPattern.Band band = window.pattern().band(window.windowStart() + slot);
            if (band == null) continue;
            double value = clientY - slotOffset(slot, band);
            if (value >= band.sourceY() && value < band.sourceY() + BAND_HEIGHT)
                return slotOffset(slot, band);
        }
        return bandOffset(player);
    }

    /** A click's point, converted with the same offset as its block. */
    public static Vec3 toServerClick(ServerPlayer player, BlockPos clientBlock, Vec3 clientPos)
    {
        int offset = clickOffset(player, clientBlock.getY());
        return clientPos.add(0, -offset, 0);
    }

    public static BlockPos toServer(ServerPlayer player, BlockPos clientPos)
    {
        //A block position is an aimed coordinate: which band's copy did they click?
        Resolution resolved = resolveClick(player, clientPos.getY());
        int y = (int) Math.round(resolved.value());
        BlockPos converted = clientPos.offset(0, y - clientPos.getY(), 0);
        if (resolved.slot() >= 0)
        {
            Map<BlockPos, SlotRecord> slots = posSlots.computeIfAbsent(player, p -> new ConcurrentHashMap<>());
            //Bounded: the map exists to carry one conversion to the action it triggers, not to remember the
            //history of every block the player has ever aimed at.
            if (slots.size() > 256) slots.clear();
            slots.put(converted.immutable(), new SlotRecord(resolved.slot(), tickCounter));
        }
        return converted;
    }

    /**
     * Movement as the client experienced it, for vanilla's own move check.
     *
     * <p>Horizontal movement still belongs to the X/Z shuffle, so it goes through the transformer. The vertical
     * term is the plain server difference once both ends are in the same frame - which they are, because
     * {@link #toServerY} already resolved the packet's frame before this is asked. A raw server-space distance
     * would be the 64-block band jump instead of the couple of blocks the player actually fell.
     */
    public static double movementDistanceSq(ServerPlayer player, Vec3 fromS, Vec3 toS)
    {
        //The answer to a teleport is not movement: the player was moved by the server, and everything between
        //the destination and where they really are happened while the client was not allowed to say so.
        if (IN_TELEPORT_ACK.get()) return 0.0;
        double horizontal;
        try
        {
            Vec3 moved = VecTransformer.getInstance(player).playerMove(fromS, toS);
            horizontal = moved.x * moved.x + moved.z * moved.z;
        }
        catch (Exception e)
        {
            double dx = toS.x - fromS.x;
            double dz = toS.z - fromS.z;
            horizontal = dx * dx + dz * dz;
        }
        double dy = toS.y - fromS.y;
        return horizontal + dy * dy;
    }

    public static void forget(ServerPlayer player)
    {
        if (player == null) return;
        TicketKey key = tickets.remove(player);
        if (key != null) releaseTickets(player, key);
        windows.remove(player);
        offsets.remove(player);
        posSlots.remove(player);
        ready.remove(player);
        incomplete.remove(player);
        ticks.remove(player);
        seamTeleports.remove(player);
        levelSwaps.remove(player);
        copyFailures.remove(player);
        resendQueue.remove(player);
        AbyssEntityCopies.drop(player);
    }

    public static boolean isSeamTeleport(ServerPlayer player)
    {
        return seamTeleports.contains(player);
    }

    public static void markIncomplete(ServerPlayer player, ChunkPos pos)
    {
        incomplete.computeIfAbsent(player, p -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    // ---------------------------------------------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------------------------------------------

    /**
     * One tick of housekeeping for the whole server: drain the chunk-load queue, then each player's window.
     *
     * <p>Runs at the <b>start</b> of the tick, before player ticks, so a slide's position packet goes out before
     * any chunk packet of the same tick and nothing can cross the slide carrying the old offset.
     */
    public static void tick(MinecraftServer server)
    {
        tickCounter++;
        drainLoadedColumns(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            repeatUpdates(player);
            tickPlayer(player);
        }
    }

    /**
     * A block update to send again next tick, because the first one can be discarded by the client.
     *
     * <p>{@code ClientLevel.setServerVerifiedBlockState} consults the prediction handler first and returns
     * without applying anything while a local prediction is outstanding at that position - the client is still
     * waiting for its own placement to be acknowledged. A cross-band update sent during that window is recorded
     * and then thrown away, which is why an edit only appeared after re-entering the world and re-sending the
     * chunk.
     *
     * <p>One repeat, one tick later, is past the acknowledgement; it is applied and sticks. Repeats are not
     * chained, so this cannot become a stream.
     */
    private record PendingUpdate(ResourceKey<Level> level, BlockPos pos, List<BlockPos> images) {}

    private static final Map<ServerPlayer, Set<PendingUpdate>> repeats = new ConcurrentHashMap<>();

    private static void repeatUpdates(ServerPlayer player)
    {
        Set<PendingUpdate> pending = repeats.remove(player);
        if (pending == null || pending.isEmpty()) return;
        for (PendingUpdate update : pending) sendImages(player, update.level(), update.pos(), update.images());
    }

    private static void tickPlayer(ServerPlayer player)
    {
        Window before = window(player);
        if (before == null)
        {
            if (windows.containsKey(player)) forget(player);
            return;
        }

        keepWindowLoaded(player);

        drainResend(player);

        if (!columnsLoaded(player, player.chunkPosition())) ready.put(player, false);
        else if (!ready.getOrDefault(player, false))
        {
            ready.put(player, true);
            resendChunks(player);
        }

        //A band change moves the player *and* recentres the window, together, inside `travel`. There is no
        //separate slide any more: a window shift that is not attached to a player movement would translate
        //the whole world by a band with no physical event to justify it, which is precisely the "position
        //moved but the chunks did not" glitch, and it cannot be made seamless while chunk packets are
        //throttled.
        travel(player, before);

        //After travel, so a crossing's new window is what the copies are built from in the same tick.
        //
        //Guarded: the copies are a cosmetic layer over entities the mod did not create, and processing an arbitrary
        //modded entity can hit a shape nobody anticipated - that is how a boat whose metadata was all defaults
        //crashed the whole server tick. A missing copy is a visible bug; a dead server is not an acceptable
        //failure mode for either. Logged with the player, once per second rather than once per tick.
        try
        {
            AbyssEntityCopies.tick(player);
        }
        catch (RuntimeException e)
        {
            long now = System.currentTimeMillis();
            Long last = copyFailures.get(player);
            if (last == null || now - last >= 1000L)
            {
                copyFailures.put(player, now);
                LOGGER.error("[abyss] entity copy pass failed for {}; skipping this tick",
                        player.getName().getString(), e);
            }
        }

        if (ticks.merge(player, 1, Integer::sum) % 20 == 0) healIncomplete(player);
    }

    /** When a copy pass last failed, so a repeating fault cannot flood the log. */
    private static final Map<ServerPlayer, Long> copyFailures = new ConcurrentHashMap<>();

    /**
     * How far past a band's edge the feet must be before that counts as a crossing.
     *
     * <p>Without it, standing on the highest block of a band is *itself* the crossing condition: that block's top
     * is exactly {@code sourceY + BAND_HEIGHT}, so the player's feet sit exactly on the threshold and every tick
     * qualifies. The player then bounces between the band they are supported in and the band above, which has no
     * floor at that height - the "block disappears and I get squeezed off" report. A margin makes the boundary
     * belong to the band that actually holds the ground beneath it.
     *
     * <p>5 cm: far below one tick of any movement, and far above the noise in a position the client reports.
     */
    private static final double CROSS_MARGIN = 0.05;

    /**
     * A crossing's destination Y, moved up out of solid terrain if it landed inside any.
     *
     * <p>The concatenated world has no geometry of its own at a seam: the top block of one band and the bottom
     * block of the band above are adjacent in the window but unrelated in the levels. So a destination computed by
     * pure arithmetic can be inside a block, and collision will then eject the player - a squeeze that looks like a
     * glitch because nothing in the log is wrong.
     *
     * <p>Bounded to four steps: a floor-to-ceiling column of blocks would otherwise be an unbounded loop, and
     * four is already more than a player can be inside at once.
     */
    private static double arrivalY(ServerLevel level, double x, double z, double y)
    {
        for (int guard = 0; guard < 4; guard++)
        {
            BlockPos pos = BlockPos.containing(x, y, z);
            net.minecraft.world.phys.shapes.VoxelShape shape =
                    level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) return y;
            double top = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            //Already standing on it rather than inside it.
            if (y >= top - 1.0E-7) return y;
            y = top;
        }
        return y;
    }

    /**
     * Moves the player into the band they have walked into, and slides the window to follow them.
     *
     * <p>Falling or climbing out of a band is a real teleport. The destination Y is chosen so that the client's
     * own coordinate does not change:
     *
     * <pre>
     *   local      = serverY - sourceY(band)
     *   serverY'   = local -/+ bandHeight + sourceY(target)
     * </pre>
     *
     * <p>The window is updated <em>before</em> the teleport, because the teleport sends its position packet
     * synchronously and the outbound hook reads the offset from this state.
     */
    private static boolean travel(ServerPlayer player, Window current)
    {
        AbyssPattern.Band band = current.band();
        if (band == null) return false;
        double local = player.getY() - band.sourceY();

        //A band is one band tall on the client but a level may hold the player anywhere, so a position more
        //than a band outside its slice is not a crossing - it is the window being out of step with where the
        //player actually is. Teleporting on such a value is how a frame error became a layer change: at
        //local = -60 the downward branch computes local + 64 = 4 and moves the player a band down to y=4
        //without any descent.
        //
        //One band outside the slice is a legitimate crossing: at local = -0.1 the player has just left the
        //bottom, and at local = 64.008 they are standing on the highest block and have just left the top. Only
        //beyond one band is the window genuinely out of step. The guard used to test |local| >= BAND_HEIGHT,
        //which made `recover` swallow every upward crossing - y=64.008 was "re-derived" and the player was
        //pushed back to 63.99, so a level could never be ascended from below.
        if (local < -BAND_HEIGHT || local >= 2 * BAND_HEIGHT)
        {
            recover(player, current);
            return false;
        }

        int next;
        double targetY;
        if (local < 0)
        {
            next = current.position() - 1;
            AbyssPattern.Band target = current.pattern().band(next);
            if (target == null) return false;
            targetY = local + BAND_HEIGHT + target.sourceY();
        }
        else if (local >= BAND_HEIGHT + CROSS_MARGIN)
        {
            next = current.position() + 1;
            AbyssPattern.Band target = current.pattern().band(next);
            if (target == null) return false;
            targetY = local - BAND_HEIGHT + target.sourceY();
        }
        else
        {
            next = current.position();
            targetY = Double.NaN;
        }

        if (!Double.isNaN(targetY))
        {
            AbyssPattern.Band target = current.pattern().band(next);
            if (target == null) return false;

            //**The seam is not solid, so the arrival must not be inside terrain.** A band's top edge is the exact
            //crossing threshold, and the band above has nothing at its own y=-1 - which means a player standing on
            //an isolated block at the top of a band is, on the server, about to be moved somewhere with no floor.
            //They fall, come back down, and land at `64 - (however far they fell)`, which is *inside* that same
            //block. Collision then ejects them, they are at 64.0 again, the threshold fires again, and the pair of
            //crossings repeats: the block appears to vanish and the player is "squeezed off" it, with nothing in
            //the log because every step was individually a legitimate crossing.
            ServerLevel targetLevel = player.level().getServer() == null ? null
                    : player.level().getServer().getLevel(target.level());
            if (targetLevel != null)
            {
                double safe = arrivalY(targetLevel, player.getX(), player.getZ(), targetY);
                if (safe != targetY)
                    LOGGER.info("[abyss] arrival clamped for {}: {} -> {} in {} (would have been inside terrain)",
                            player.getName().getString(), Math.round(targetY * 100.0) / 100.0,
                            Math.round(safe * 100.0) / 100.0, target.level().identifier().getPath());
                targetY = safe;
            }
            //Recentre, and do it here rather than later. `slot = position - windowStart` is what decides
            //which band the player is standing in, and the window only renders slots 0..2. Keeping the start
            //on a crossing puts the player in slot -1 or 3 - a band the client is not being sent at all - and
            //no later slide fixes it, because the slide condition is about approaching an edge and a player
            //who has just crossed is at the *start* of their new band. That was the "cannot see the layer
            //above until I approach it" report: they were centred on nothing.
            //
            //So the invariant is: a player is always in the middle slot, except where a pattern ends. The
            //cost is that every crossing shifts the whole composed world by one band, which the client can
            //only absorb with a full re-send - see the ordering below.
            int start = clampStart(current.pattern(), next - WINDOW_LAYERS / 2);
            Window nextWindow = new Window(current.pattern(), next, start);

            //The window update, the teleport and the re-send are one transition, and the order matters:
            //  - the seam flag spans all of it, because between `setWindow` and the teleport the stored window
            //    deliberately disagrees with `player.level()`, and anything that re-derives the window in that
            //    gap discards the move;
            //  - the position packet goes out first. It is the only packet the client has to acknowledge, and it
            //    is the head of the stream rather than the tail: everything vanilla does with the player's
            //    movement waits for it, so putting the near columns ahead of it froze the player for however long
            //    those took to cross the wire. Both go out in one burst, so the client applies the new position
            //    and the new terrain in the same tick.
            //  - the rest of the view is queued behind that and drained at the chunk sender's pace, because a
            //    crossing changes the composition of every column and marking them all pending at once is a
            //    single-tick stall of the whole view.
            seamTeleports.add(player);
            long started = System.nanoTime();
            try
            {
                setWindow(player, nextWindow);
                boolean bandsChanged = !nextWindow.bands().equals(current.bands());
                sendTeleport(player, target, targetY);
                if (bandsChanged)
                {
                    resendNearChunks(player);
                    flushChunks(player);
                    resendChunks(player, 1);
                }
            }
            finally
            {
                seamTeleports.remove(player);
            }
            //Kept in the log because it is the server's share of the hitch a crossing is felt as; anything
            //beyond a millisecond or two is work that could be moved off the transition.
            LOGGER.info("[abyss] seam crossed {} -> {} at client y={}; local {} -> {} (onGround {}, window {} -> {}); {} ms",
                    band.level().identifier(), target.level().identifier(), targetY + bandOffset(player),
                    Math.round(local * 100.0) / 100.0, Math.round((targetY - target.sourceY()) * 100.0) / 100.0,
                    player.onGround(), current.position(), next,
                    (System.nanoTime() - started) / 1_000_000L);
            //Whether the bands around the player are actually simulating, which is what decides if a mob in one of
            //them walks and can be damaged. Reported once per crossing, on one line: "frozen in the next band"
            //has two very different causes - chunks that never reach ENTITY_TICKING, or something else - and this
            //is the single fact that tells them apart. Silent when every band is ticking.
            reportTicking(player);
            return true;
        }

        return false;
    }

    /**
     * Reports the bands of the window that are not entity-ticking, once per crossing.
     *
     * <p>Nothing is printed when all of them are, so a healthy crossing stays one line. This is a diagnostic with a
     * known expiry: `isPositionEntityTicking` answers the whole pipeline - ticket, loading tracker, simulation
     * tracker, chunk status - with one boolean, which is what a report of "frozen until I enter their band" needs.
     */
    private static void reportTicking(ServerPlayer player)
    {
        Window window = window(player);
        MinecraftServer server = player.level().getServer();
        if (window == null || server == null) return;
        BlockPos probe = player.blockPosition();
        List<String> cold = new ArrayList<>();
        for (AbyssPattern.Band band : window.bands())
        {
            ServerLevel level = band == null ? null : server.getLevel(band.level());
            if (level == null || level.isPositionEntityTicking(probe)) continue;
            cold.add(band.level().identifier().getPath() + "@" + band.sourceY());
        }
        if (cold.isEmpty()) return;
        LOGGER.warn("[abyss] not entity-ticking for {}: {} (bands {}); a mob there will not walk or take damage",
                player.getName().getString(), cold, window.pattern().name());
    }

    /**
     * Puts the window and the player back in agreement after the two drifted apart.
     *
     * <p>The window is rebuilt from the player's real level and position, and the player is pushed to the
     * nearest edge of their band if they are outside its slice. Nothing is dispatched: a desync is a bookkeeping
     * fault, and answering it with a teleport into another level is how a frame error turned into a descent.
     */
    private static void recover(ServerPlayer player, Window current)
    {
        AbyssPattern pattern = current.pattern();
        int position = pattern.indexOf(player.level().dimension(), player.getY());
        if (position < 0)
        {
            windows.remove(player);
            return;
        }
        AbyssPattern.Band band = pattern.band(position);
        if (band == null) return;

        setWindow(player, new Window(pattern, position, clampStart(pattern, position - WINDOW_LAYERS / 2)));
        if (recovered.add(player))
            LOGGER.warn("[abyss] window was out of step with {} (y={}); re-derived, no teleport",
                        player.getName().getString(), player.getY());
        double local = player.getY() - band.sourceY();
        if (local >= 0 && local < BAND_HEIGHT) return;
        //Both edges are nudged *into* the slice, which is right, but the upper one is ``sourceY + BAND_HEIGHT -
        //0.01`` - inside the highest block of the band when one exists there. The same clamp the crossing uses
        //applies, or recovery would eject the player exactly as a crossing did.
        double edge = local < 0 ? band.sourceY() + 0.01 : band.sourceY() + BAND_HEIGHT - 0.01;
        ServerLevel own = player.level().getServer() == null ? null
                : player.level().getServer().getLevel(band.level());
        if (own != null) edge = arrivalY(own, player.getX(), player.getZ(), edge);
        sendTeleport(player, pattern.band(position), edge);
    }

    /** Keeps the recovery warning to one per player rather than one per tick. */
    private static final Set<ServerPlayer> recovered = ConcurrentHashMap.newKeySet();

    /** Marks the call this thread is inside as the client's answer to a position packet. */
    public static void beginSeamAck()
    {
        IN_TELEPORT_ACK.set(true);
    }

    public static void endSeamAck()
    {
        IN_TELEPORT_ACK.set(false);
    }

    /** Whether this thread is inside the client's acknowledgement of a position packet. */
    public static boolean isTeleportAck()
    {
        return IN_TELEPORT_ACK.get();
    }

    /**
     * Sends the teleport itself.
     *
     * <p>No seam-flag handling here: {@link #travel} owns the flag for the whole transition, and nesting a
     * second add/remove would clear it while the window update is still in flight.
     */
    private static void sendTeleport(ServerPlayer player, AbyssPattern.@Nullable Band target, double y)
    {
        if (target == null) return;
        ServerLevel level = player.level().getServer() == null
                ? null : player.level().getServer().getLevel(target.level());
        if (level == null) return;

        Vec3 pos = new Vec3(player.getX(), y, player.getZ());
        TeleportTransition transition = new TeleportTransition(level, pos, player.getDeltaMovement(),
                                                              player.getYRot(), player.getXRot(),
                                                              TeleportTransition.DO_NOTHING);
        //Inside this call vanilla sends the respawn packet and the position packet. The respawn is cancelled by
        //the outbound hook while the seam flag is set; the position packet is converted into window coordinates
        //and does go out, which is what makes a crossing seamless - the player moves from the top of the window
        //to the bottom of it while the composed world shifts down with them.
        player.teleport(transition);

        //The connection's own idea of where the player is has to follow the move. `teleport` resets it for both
        //branches, but it is the reference the client's acknowledgement and its next move packet are measured
        //against, so it is stated here too rather than relied upon.
        if (player.connection != null) player.connection.resetPosition();
    }

    // ---------------------------------------------------------------------------------------------------
    // Chunks
    // ---------------------------------------------------------------------------------------------------

    /** A chunk became available in a pattern level. <b>Called off the server thread.</b> */
    public static void onLayerChunkLoaded(ServerLevel level, LevelChunk chunk)
    {
        if (AbyssPatterns.forLevel(level.dimension()) == null) return;
        loadedColumns.add(new LayerColumn(level.dimension(), chunk.getPos()));
    }

    /**
     * Turns recorded chunk loads into re-sends, on the server thread.
     *
     * <p>A column is composed at send time, so a band that was still generating is padded as air. Nothing asks
     * for that column again while the player's own chunk stays put, so this is what asks.
     */
    private static void drainLoadedColumns(MinecraftServer server)
    {
        if (loadedColumns.isEmpty()) return;
        List<LayerColumn> batch = new ArrayList<>(loadedColumns);
        loadedColumns.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            Window window = window(player);
            if (window == null || player.connection == null) continue;
            ServerLevel host = (ServerLevel) player.level();
            for (LayerColumn loaded : batch)
            {
                if (!containsLevel(window, loaded.level())) continue;
                ChunkPos pos = loaded.pos();
                if (!player.getChunkTrackingView().contains(pos)) continue;
                LevelChunk chunk = host.getChunkSource().getChunkNow(pos.x(), pos.z());
                if (chunk == null) continue;
                player.connection.chunkSender.markChunkPendingToSend(chunk);
                Set<ChunkPos> pending = incomplete.get(player);
                if (pending != null) pending.remove(pos);
            }
        }
    }

    private static boolean containsLevel(Window window, ResourceKey<Level> level)
    {
        for (AbyssPattern.Band band : window.bands())
            if (band != null && band.level().equals(level)) return true;
        return false;
    }

    /** Whether every band of the window has a chunk at this column. */
    public static boolean columnsLoaded(ServerPlayer player, ChunkPos pos)
    {
        Window window = window(player);
        if (window == null) return false;
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        for (AbyssPattern.Band band : window.bands())
        {
            if (band == null) return false;
            ServerLevel level = server.getLevel(band.level());
            if (level == null || level.getChunkSource().getChunkNow(pos.x(), pos.z()) == null) return false;
        }
        return true;
    }

    /**
     * Re-sends columns that were composed while a band was still loading.
     *
     * <p>The chunk-load hook catches most of these, but not all: a chunk that finished before the player had a
     * window is never seen as an event, and a column re-composed by a slide can lose a band. Once a second,
     * send again anything that is now complete, and forget columns that have left the view.
     */
    private static void healIncomplete(ServerPlayer player)
    {
        Set<ChunkPos> pending = incomplete.get(player);
        if (pending == null || pending.isEmpty()) return;
        if (player.connection == null) return;
        ServerLevel host = (ServerLevel) player.level();

        for (ChunkPos pos : pending)
        {
            if (!player.getChunkTrackingView().contains(pos))
            {
                pending.remove(pos);
                continue;
            }
            if (!columnsLoaded(player, pos)) continue;
            LevelChunk chunk = host.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null) continue;
            player.connection.chunkSender.markChunkPendingToSend(chunk);
            pending.remove(pos);
        }
    }

    /**
     * Holds every band of the window around the player's chunk.
     *
     * <p>Composition reads the band chunks off the server thread's chunk cache, so the bands nobody is standing
     * in need tickets of their own. {@code PLAYER_LOADING} has no timeout, and it loads without simulating;
     * {@code ENDER_PEARL} was the first choice and carries a 40-tick timeout, which unloaded the lower bands two
     * seconds after the player stopped moving.
     *
     * <p>The tickets are released explicitly when the window moves, because they never expire.
     */
    private static void keepWindowLoaded(ServerPlayer player)
    {
        Window window = window(player);
        if (window == null) return;
        int radius = viewRadius(player);
        TicketKey key = new TicketKey(player.chunkPosition(), window.windowStart(), window.pattern().name(), radius);
        if (key.equals(tickets.get(player)))
        {
            //The tickets were added for this exact window already - but a ticket can be lost without the key
            //changing: a chunk unload drops the ones attached to it, and this cache would then never re-add them,
            //which is a permanent freeze for every band. Checking once a second costs two map lookups per band and
            //turns "frozen forever" into "frozen for at most a second".
            if (ticks.getOrDefault(player, 0) % 20 == 0) reassertTickets(player, window, key);
            return;
        }

        TicketKey old = tickets.put(player, key);
        if (old != null) releaseTickets(player, old);

        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        for (AbyssPattern.Band band : window.bands())
        {
            ServerLevel level = band == null ? null : server.getLevel(band.level());
            if (level == null) continue;
            //Two tickets, because the two flags do different things and neither implies the other:
            //  - PLAYER_LOADING (doesLoad) feeds the loading tracker, which decides chunk status;
            //  - PLAYER_SIMULATION (doesSimulate) feeds the simulation tracker, which decides whether entities,
            //    block entities and random ticks run.
            //The bands nobody stands in need the second one or a mob in the band above is a statue: no AI, no
            //gravity, no despawn. `TicketStorage.addTicket` updates the two trackers independently, on the
            //`doesLoad` and `doesSimulate` flags, so a single ticket cannot do both and `FORCED` would also
            //persist the ticket into the save.
            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, key.pos(), key.radius());
            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SIMULATION, key.pos(), key.radius());
        }
    }

    /**
     * The ticket types present at a chunk, for diagnostics.
     *
     * <p>{@code simLevel == 33} alone cannot tell "no ticket at all" apart from "a ticket whose level is too
     * high", and those have different fixes - so the list is worth the widener it costs.
     */
    public static List<String> ticketsAt(ServerLevel level, ChunkPos pos)
    {
        return level.getChunkSource().chunkMap.getDistanceManager().ticketStorage
                    .getTickets(pos.pack())
                    .stream()
                    .map(ticket -> ticket.getType().toString() + ":" + ticket.getTicketLevel())
                    .toList();
    }

    /**
     * Re-adds a band's tickets if its simulation level says none is in effect.
     *
     * <p>Separated from {@link #keepWindowLoaded} so the check is explicit and visible: everything about the
     * "frozen in the neighbouring band" report comes down to whether the player's chunk in that level carries a
     * simulation ticket, and this is the only place the mod can notice that it does not.
     */
    private static void reassertTickets(ServerPlayer player, Window window, TicketKey key)
    {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        for (AbyssPattern.Band band : window.bands())
        {
            ServerLevel level = band == null ? null : server.getLevel(band.level());
            if (level == null) continue;
            int simLevel = level.getChunkSource().chunkMap.getDistanceManager()
                                .getChunkLevel(key.pos().pack(), true);
            //31 is ENTITY_TICKING; anything above means this band's entities are not ticked.
            if (simLevel <= 31) continue;
            LOGGER.warn("[abyss] simulation ticket for {} was missing at {} (simLevel {}); re-adding with radius {}",
                    band.level().identifier().getPath(), key.pos(), simLevel, key.radius());
            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, key.pos(), key.radius());
            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SIMULATION, key.pos(), key.radius());
        }
    }

    private static void releaseTickets(ServerPlayer player, TicketKey key)
    {
        AbyssPattern pattern = AbyssPatterns.byName(key.pattern());
        MinecraftServer server = player.level().getServer();
        if (pattern == null || server == null) return;
        for (int i = 0; i < WINDOW_LAYERS; i++)
        {
            AbyssPattern.Band band = pattern.band(key.windowStart() + i);
            ServerLevel level = band == null ? null : server.getLevel(band.level());
            if (level != null)
            {
                level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, key.pos(), key.radius());
                level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_SIMULATION, key.pos(), key.radius());
            }
        }
    }

    private static int viewRadius(ServerPlayer player)
    {
        if (player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view)
            return view.viewDistance() + 2;
        return 10;
    }

    /**
     * Horizontal movement in the player's X/Z map, vertical movement as the player actually walked.
     *
     * <p>For the movement statistics: in a window the server's Y changes by a whole band at a crossing while
     * the player's does not, and counting that as distance walked inflates the fly-speed and walk-speed
     * counters by a band per crossing - which shows up as the player "sprinting" through the floor.
     */
    public static Vec3 playerMove(ServerPlayer player, Vec3 fromS, Vec3 toS)
    {
        Vec3 horizontal;
        try
        {
            horizontal = VecTransformer.getInstance(player).playerMove(fromS, toS);
        }
        catch (Exception e)
        {
            horizontal = new Vec3(toS.x - fromS.x, 0, toS.z - fromS.z);
        }
        return new Vec3(horizontal.x, toS.y - fromS.y, horizontal.z);
    }

    /**
     * Queues every chunk the player tracks after the window's contents changed.
     *
     * <p>The queue is drained a few columns per tick rather than marked all at once. A crossing changes the
     * composition of the whole view, and an integrated server's chunk sender ignores its batch quota and puts
     * everything pending on the wire in one tick - so marking all of them composed the entire view in a single
     * server tick (about five thousand sections) and handed the client the same amount to mesh. That is the
     * hitch that was felt on every crossing. Drained, the player's own area still goes first and the rest
     * arrives over the following second, the way ordinary chunk loading does.
     */
    private static void resendChunks(ServerPlayer player)
    {
        resendChunks(player, -1);
    }

    /**
     * @param skipRadius columns within this many chunks of the player are left out of the queue; a seam
     *                   transition has just sent them and re-queuing them would duplicate the whole near box.
     */
    private static void resendChunks(ServerPlayer player, int skipRadius)
    {
        if (player.connection == null) return;
        ChunkPos centre = player.chunkPosition();
        List<ChunkPos> columns = new ArrayList<>();
        player.getChunkTrackingView().forEach(pos ->
        {
            if (skipRadius >= 0
                && Math.abs(pos.x() - centre.x()) <= skipRadius
                && Math.abs(pos.z() - centre.z()) <= skipRadius) return;
            columns.add(pos);
        });
        columns.sort((a, b) -> Integer.compare(distanceSquared(centre, a), distanceSquared(centre, b)));
        resendQueue.put(player, new ArrayDeque<>(columns));
    }

    private static int distanceSquared(ChunkPos from, ChunkPos to)
    {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        return dx * dx + dz * dz;
    }

    /** Columns still to be re-sent for the current window, nearest first. */
    private static final Map<ServerPlayer, Deque<ChunkPos>> resendQueue = new ConcurrentHashMap<>();

    /** How many columns a window re-send puts back on the wire in one tick. */
    private static final int RESEND_PER_TICK = 24;

    private static void drainResend(ServerPlayer player)
    {
        Deque<ChunkPos> queue = resendQueue.get(player);
        if (queue == null || player.connection == null) return;
        ServerLevel host = (ServerLevel) player.level();
        for (int queued = 0; queued < RESEND_PER_TICK && !queue.isEmpty(); )
        {
            ChunkPos pos = queue.poll();
            if (!player.getChunkTrackingView().contains(pos)) continue;
            LevelChunk chunk = host.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null) continue;
            player.connection.chunkSender.markChunkPendingToSend(chunk);
            queued++;
        }
        if (queue.isEmpty()) resendQueue.remove(player);
    }

    /**
     * Queues the columns around the player for a seam transition: the 3x3 they are standing in.
     *
     * <p>This is the terrain the client needs first, and the rest of the view follows through
     * {@link #resendChunks(ServerPlayer, int)} at the sender's pace. A column is twelve sections of composed
     * terrain, so the difference between this and the whole view is the difference between the client having
     * its surroundings on the tick it is moved and having them a second later.
     */
    private static void resendNearChunks(ServerPlayer player)
    {
        if (player.connection == null) return;
        ServerLevel host = (ServerLevel) player.level();
        ChunkPos centre = player.chunkPosition();
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                ChunkPos pos = new ChunkPos(centre.x() + dx, centre.z() + dz);
                if (!player.getChunkTrackingView().contains(pos)) continue;
                LevelChunk chunk = host.getChunkSource().getChunkNow(pos.x(), pos.z());
                if (chunk != null) player.connection.chunkSender.markChunkPendingToSend(chunk);
            }
        }
    }

    /**
     * Sends one batch of queued chunks immediately, instead of waiting for the player tick.
     *
     * <p>The sender sorts what it has queued by distance from the player, so the batch that goes out here is
     * the terrain around them - which is exactly what has to be on the wire before a band-change position
     * packet moves them into it. Everything further out follows at the sender's normal pace.
     */
    private static void flushChunks(ServerPlayer player)
    {
        if (player.connection == null) return;
        player.connection.chunkSender.sendNextChunks(player);
    }

    /** Sends the player's own position again so the client picks up the new offset. */
    private static void resyncPosition(ServerPlayer player)
    {
        if (player.connection == null) return;
        //Not the (x,y,z,yaw,pitch) overload: that zeroes the motion and would stop a player mid-fall.
        player.connection.teleport(new PositionMoveRotation(
                new Vec3(player.getX(), player.getY(), player.getZ()),
                player.getDeltaMovement(),
                player.getYRot(), player.getXRot()), Set.of());
    }
}
