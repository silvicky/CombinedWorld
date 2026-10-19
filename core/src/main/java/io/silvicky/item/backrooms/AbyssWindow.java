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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    public static final int SLIDE_BUFFER = 4;

    /**
     * The dimension type sent instead of the band's own, so the client builds a 192-tall level.
     *
     * <p>A datapack entry, so it is in the synced registry and resolvable by a vanilla client.
     */
    public static final Identifier WINDOW_TYPE = Identifier.parse("silvicky:abyss_window");
    /** The one dimension key the client is told it is in, for every band. */
    public static final Identifier WINDOW_DIMENSION = Identifier.parse("abyss:window");

    public static ResourceKey<Level> windowKey()
    {
        return ResourceKey.create(Registries.DIMENSION, WINDOW_DIMENSION);
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
    private static final Set<ServerPlayer> seamTeleports = ConcurrentHashMap.newKeySet();

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
            if (seamTeleports.contains(player)) return stored;
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
        windows.put(player, fresh);
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

    public static double clientY(ServerPlayer player)
    {
        return player.getY() + bandOffset(player);
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

    public static double toServerY(ServerPlayer player, double clientY)
    {
        return clientY - bandOffset(player);
    }

    public static Vec3 toServer(ServerPlayer player, Vec3 clientPos)
    {
        return clientPos.add(0, -bandOffset(player), 0);
    }

    public static BlockPos toServer(ServerPlayer player, BlockPos clientPos)
    {
        return clientPos.offset(0, -bandOffset(player), 0);
    }

    public static void forget(ServerPlayer player)
    {
        if (player == null) return;
        TicketKey key = tickets.remove(player);
        if (key != null) releaseTickets(player, key);
        windows.remove(player);
        ready.remove(player);
        incomplete.remove(player);
        ticks.remove(player);
        seamTeleports.remove(player);
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
        drainLoadedColumns(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) tickPlayer(player);
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

        if (!columnsLoaded(player, player.chunkPosition())) ready.put(player, false);
        else if (!ready.getOrDefault(player, false))
        {
            ready.put(player, true);
            resendChunks(player);
        }

        travel(player, before);

        Window after = windows.get(player);
        if (after != null && (after.position() != before.position() || after.windowStart() != before.windowStart()))
        {
            if (!after.bands().equals(before.bands())) resendChunks(player);
            resyncPosition(player);
        }

        if (ticks.merge(player, 1, Integer::sum) % 20 == 0) healIncomplete(player);
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
    private static void travel(ServerPlayer player, Window current)
    {
        AbyssPattern.Band band = current.band();
        if (band == null) return;
        double local = player.getY() - band.sourceY();

        int next;
        double targetY;
        if (local < 0)
        {
            next = current.position() - 1;
            AbyssPattern.Band target = current.pattern().band(next);
            if (target == null) return;
            targetY = local + BAND_HEIGHT + target.sourceY();
        }
        else if (local >= BAND_HEIGHT)
        {
            next = current.position() + 1;
            AbyssPattern.Band target = current.pattern().band(next);
            if (target == null) return;
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
            //Recentred on the change, not merely kept in range. The player's band is what each slot shows, so
            //the window has to move exactly when it moves - sliding early would change the composition while
            //they are still standing in the old band, and sliding late would leave them in an edge slot with
            //no band on one side. Centring here is also what makes the "buffer" unnecessary: the composition
            //changes at the one moment it has to.
            int start = clampStart(current.pattern(), next - WINDOW_LAYERS / 2);
            windows.put(player, new Window(current.pattern(), next, start));
            teleport(player, target, targetY);
        }

        slide(player);
    }

    /**
     * Pushes a player who is not in the middle slot back towards it.
     *
     * <p>Only meaningful when the position did not change: at the clamped ends of a short acyclic pattern, or
     * after a teleport inside a band. Band changes recentre directly in {@link #travel}.
     */
    private static void slide(ServerPlayer player)
    {
        Window window = windows.get(player);
        if (window == null) return;
        AbyssPattern.Band band = window.band();
        if (band == null) return;

        double local = player.getY() - band.sourceY();
        int start = window.windowStart();
        int next = start;

        if (window.slot() <= 0 && local < SLIDE_BUFFER) next = start - 1;
        else if (window.slot() >= WINDOW_LAYERS - 1 && local > BAND_HEIGHT - SLIDE_BUFFER) next = start + 1;

        if (next == start) return;
        int clamped = clampStart(window.pattern(), next);
        if (clamped == start) return;
        windows.put(player, new Window(window.pattern(), window.position(), clamped));
    }

    private static void teleport(ServerPlayer player, AbyssPattern.@Nullable Band target, double y)
    {
        if (target == null) return;
        ServerLevel level = player.level().getServer() == null
                ? null : player.level().getServer().getLevel(target.level());
        if (level == null) return;

        Vec3 pos = new Vec3(player.getX(), y, player.getZ());
        TeleportTransition transition = new TeleportTransition(level, pos, player.getDeltaMovement(),
                                                              player.getYRot(), player.getXRot(),
                                                              TeleportTransition.DO_NOTHING);
        //The respawn packet is sent inside this call, synchronously, and the hook cancels it while this is set.
        seamTeleports.add(player);
        try
        {
            player.teleport(transition);
        }
        finally
        {
            seamTeleports.remove(player);
        }
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
        if (key.equals(tickets.get(player))) return;

        TicketKey old = tickets.put(player, key);
        if (old != null) releaseTickets(player, old);

        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        for (AbyssPattern.Band band : window.bands())
        {
            ServerLevel level = band == null ? null : server.getLevel(band.level());
            if (level != null)
                level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, key.pos(), key.radius());
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
                level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, key.pos(), key.radius());
        }
    }

    private static int viewRadius(ServerPlayer player)
    {
        if (player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view)
            return view.viewDistance() + 2;
        return 10;
    }

    /** Re-announces every chunk the player tracks, after the window's contents changed. */
    private static void resendChunks(ServerPlayer player)
    {
        ServerLevel host = (ServerLevel) player.level();
        if (player.connection == null) return;
        player.getChunkTrackingView().forEach(pos ->
        {
            LevelChunk chunk = host.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null) player.connection.chunkSender.markChunkPendingToSend(chunk);
        });
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
