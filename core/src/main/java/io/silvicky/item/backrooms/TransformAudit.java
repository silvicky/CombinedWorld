package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * Detects double transforms empirically, instead of by reading the call graph.
 *
 * <p>Six attempts to find the bugs by reading the call graph all failed, because whether a given hook receives
 * an {@code [S]} or a {@code [C]} value is a property of vanilla's call graph and of this mod's own history,
 * and neither is written down anywhere. So stop guessing and measure it.
 *
 * <p>{@link io.silvicky.item.mixin.ConnectionMixin} sees the raw client coordinate of a movement packet, the
 * server coordinate it produces, and the player's own server-side position at that moment. It records all
 * three. Every transform site that runs afterwards on the server thread can then be told which field its
 * input is really in, by comparing the value it was handed against those three references.
 *
 * <p>Purely observational: nothing here changes behaviour. Counters are printed by {@link #dump()}.
 */
public final class TransformAudit
{
    private static final double EPSILON = 1.0e-6;

    /** What a transform site's input turned out to be. */
    public enum Verdict
    {
        /**
         * Input matches the value {@code ConnectionMixin} already converted. It is S-field, so transforming
         * it here is a double transform and the site is wrong.
         */
        DOUBLE,
        /**
         * Input matches the raw value the client sent. It is still C-field, so transforming it here is
         * correct and the site is right.
         */
        SINGLE,
        /**
         * Input matches the player's own server-side position rather than any packet value. It came from the
         * server's own entity state, which is definitionally S-field, so transforming it here is a double
         * transform. This is the case that distinguishes "definitely wrong" from "cannot tell from the
         * movement packet alone" - for example an eye position or a world-space AABB.
         */
        SERVER_STATE,
        /** Input matches nothing recorded. Its field has to be established by reading the call graph. */
        UNKNOWN
    }

    /**
     * @param rawX    the client-space position as the client sent it
     * @param rawZ
     * @param serverX the server-space position written back into the packet
     * @param serverZ
     * @param stateX  the player's own position on the server at that moment, before the move was applied
     * @param stateZ
     */
    private record Move(double rawX, double rawZ, double serverX, double serverZ,
                        double stateX, double stateZ) {}

    private static final Map<ServerPlayer, Move> LAST_MOVE = new ConcurrentHashMap<>();
    private static final Map<Verdict, Map<String, LongAdder>> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> EVENTS = new ConcurrentHashMap<>();
    private static final Map<String, java.util.function.Supplier<String>> SECTIONS = new ConcurrentHashMap<>();
    private static final LongAdder MOVE_PACKETS = new LongAdder();
    private static final LongAdder UNMAPPED = new LongAdder();

    static
    {
        for (Verdict verdict : Verdict.values()) COUNTERS.put(verdict, new ConcurrentHashMap<>());
    }

    private TransformAudit() {}

    /**
     * Registers a named snapshot to be evaluated by {@link #dump()}.
     *
     * <p>The body runs on whatever thread {@link #dump()} runs on, so it should only read state that is safe
     * to read from the server thread - for a {@code ConcurrentHashMap}-backed collection, a size is fine.
     */
    public static void section(String name, java.util.function.Supplier<String> body)
    {
        SECTIONS.put(name, body);
    }

    /**
     * Counts a named event, e.g. a mapping miss or a pool exhaustion.
     *
     * <p>This exists because a counter that is never incremented is worse than no counter at all: an earlier
     * version of this class reported "0 unmapped lookups" simply because nothing called {@link #unmapped()},
     * and that zero was read as evidence. Every number printed by {@link #dump()} must have a call site.
     */
    /**
     * Names the transform site currently executing on this thread, so a failure can be attributed to a
     * caller instead of only being counted.
     *
     * <p>A raw miss count is not enough. A previous run logged 478,692 misses in a minute, roughly 4,500
     * per second, which no packet flow can explain - the client does not send thousands of positioned
     * packets per second. Either the number comes from a loop rather than from packets, or it comes from a
     * packet type nobody guessed. Counting separately cannot tell those apart; naming the site can.
     *
     * <p>Thread-local because the boundary runs on the netty thread while the counters are read on the
     * server thread.
     */
    private static final ThreadLocal<String> SITE = new ThreadLocal<>();

    /** Marks the start of a transform site. Always pair with {@link #endSite()}. */
    public static void site(String name) { SITE.set(name); }

    public static void endSite() { SITE.remove(); }

    /** Counts a failure attributed to whichever site is running on this thread. */
    public static void siteFail(String event)
    {
        String s = SITE.get();
        count(event + "@" + (s == null ? "unattributed" : s));
    }

    public static void count(String event)
    {
        EVENTS.computeIfAbsent(event, _ -> new LongAdder()).increment();
    }

    /**
     * Called from the packet boundary once a movement packet has been converted to server coordinates.
     *
     * <p>Runs on the netty thread. The player's position is read without synchronising, which is a benign
     * race for a diagnostic: a torn read would only misfile one sample as {@link Verdict#UNKNOWN}.
     */
    public static void recordMove(ServerPlayer player, double rawX, double rawZ,
                                  double serverX, double serverZ)
    {
        if (player == null) return;
        MOVE_PACKETS.increment();
        LAST_MOVE.put(player, new Move(rawX, rawZ, serverX, serverZ, player.getX(), player.getZ()));
    }

    /**
     * Asks which field a transform site's input is really in.
     *
     * @param site a stable name for the injection, e.g. {@code "handlePlayerPositionChange/movedDist-0"}
     */
    public static Verdict classify(String site, ServerPlayer player, double inputX, double inputZ)
    {
        Move move = player == null ? null : LAST_MOVE.get(player);
        Verdict verdict;
        if (move == null) verdict = Verdict.UNKNOWN;
        else if (near(inputX, move.serverX) && near(inputZ, move.serverZ)) verdict = Verdict.DOUBLE;
        else if (near(inputX, move.rawX) && near(inputZ, move.rawZ)) verdict = Verdict.SINGLE;
        else if (near(inputX, move.stateX) && near(inputZ, move.stateZ)) verdict = Verdict.SERVER_STATE;
        else verdict = Verdict.UNKNOWN;
        COUNTERS.get(verdict).computeIfAbsent(site, _ -> new LongAdder()).increment();
        return verdict;
    }

    private static boolean near(double a, double b)
    {
        return Math.abs(a - b) < EPSILON;
    }

    /** Counts a position that had no mapping, so the request had to be served as the input position. */
    public static void unmapped()
    {
        UNMAPPED.increment();
    }

    public static void forget(ServerPlayer player)
    {
        LAST_MOVE.remove(player);
    }
    /**
     * How to read the site lines: a site that only ever reports {@code double} or {@code server_state} is
     * converting something that is already S-field. That is a bug only when the value it is compared
     * <em>against</em> is S-field too - if the counterpart is C-field the transform is what makes the two
     *comparable. A site reporting only {@code single} is converting a genuine C-field value.
     */
    public static void dump()
    {
        if (MOVE_PACKETS.sum() == 0 && EVENTS.isEmpty() && SECTIONS.isEmpty()) return;
        Set<String> sites = new TreeSet<>();
        for (Map<String, LongAdder> counters : COUNTERS.values()) sites.addAll(counters.keySet());

        StringBuilder sb = new StringBuilder("[s2c-audit] ")
                .append(MOVE_PACKETS.sum()).append(" move packets, ")
                .append(UNMAPPED.sum()).append(" unmapped lookups");
        for (String site : sites)
        {
            sb.append(" | ").append(site).append(":");
            boolean any = false;
            for (Verdict verdict : Verdict.values())
            {
                long n = sum(COUNTERS.get(verdict), site);
                if (n == 0) continue;
                if (any) sb.append(",");
                any = true;
                sb.append(' ').append(verdict.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(n);
            }
            if (!any) sb.append(" no samples");
        }
        if (!EVENTS.isEmpty())
        {
            sb.append(" || events:");
            for (String event : new TreeSet<>(EVENTS.keySet()))
                sb.append(' ').append(event).append('=').append(EVENTS.get(event).sum());
        }
        for (Map.Entry<String, java.util.function.Supplier<String>> entry
                : new java.util.TreeMap<>(SECTIONS).entrySet())
        {
            String body;
            try { body = entry.getValue().get(); }
            catch (Throwable t) { body = "<" + t.getClass().getSimpleName() + ">"; }
            if (body == null || body.isEmpty()) continue;
            sb.append(" || ").append(entry.getKey()).append(": ").append(body);
        }
        LOGGER.info(sb.toString());
    }

    private static long sum(Map<String, LongAdder> counters, String site)
    {
        LongAdder adder = counters.get(site);
        return adder == null ? 0L : adder.sum();
    }
}
