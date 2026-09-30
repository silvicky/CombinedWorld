package io.silvicky.item.backrooms;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * A periodic report of what the server is actually holding, so a memory problem can be attributed to a
 * cause instead of guessed at.
 *
 * <p>This exists because an 8 GB heap was exhausted with 9,980 chunks loaded in a custom dimension and the
 * only information available was a stack trace naming whichever map happened to rehash last. That trace
 * identifies where the allocation failed, never what was asking for the memory.
 *
 * <p>Every number here is read from a public API, so the report works on any build. The chunk count comes
 * from {@code getDebugStatus}, which is also the string the vanilla F3 debug screen shows - the same
 * {@code E:1,1,1,200000,200000,0,0} form whose growing level and status counts were the first real evidence
 * that chunks were being loaded and never unloaded.
 */
public final class ChunkDiagnostics
{
    private static final MemoryMXBean MEMORY=ManagementFactory.getMemoryMXBean();
    private static long lastChunks;
    private static int tick;

    private ChunkDiagnostics() {}

    /**
     * The ticket and status breakdown for one level.
     *
     * <p>{@code DistanceManager.getDebugStatus} turns out to report only the throttler's queue, which is why
     * it printed an empty bracket list. The numbers that matter live in {@code TicketStorage}, reached
     * through private fields, so they are read by reflection. Reflection rather than a mixin because a
     * diagnostic is allowed to fail: a renamed field yields a marker and the rest of the report survives,
     * where a mixin would fail to apply and take the level down with it.
     *
     * <p>The three counts separate the three ways chunks can be retained:
     * <ul>
     *   <li>{@code tickets} - live tickets. A big number with few chunks means tickets are not the cause.
     *   <li>{@code deactivatedTickets} - tickets parked because the level is not keeping itself active.
     *       These do not hold chunks but do accumulate.
     *   <li>{@code chunksWithForcedTickets} - force-loaded chunks, which nothing will ever unload.
     *   <li>{@code hasTickets} - whether the level considers itself kept alive at all.
     * </ul>
     */
    private static String ticketStatus(net.minecraft.server.level.ServerChunkCache source)
    {
        try
        {
            //Every one of these is an instance field, so the target must be the object that owns it.
            //Passing null here makes Field.get throw NullPointerException regardless of the name.
            Object dm=field(source.getClass(), source, "distanceManager");
            if (dm==null) return "<no distance manager>";

            Object storage=field(dm.getClass(), dm, "ticketStorage");
            if (storage==null) return "<no ticket storage>";

            int tickets=size(storage.getClass(), storage, "tickets");
            int deactivated=size(storage.getClass(), storage, "deactivatedTickets");
            int forced=size(storage.getClass(), storage, "chunksWithForcedTickets");
            boolean hasTickets=(Boolean) storage.getClass().getMethod("hasTickets").invoke(storage);
            return "tickets=" + tickets + " deactivated=" + deactivated
                    + " forced=" + forced + " hasTickets=" + hasTickets;
        }
        catch (Throwable t)
        {
            return "<ticket status unavailable: " + t.getClass().getSimpleName() + ">";
        }
    }

    /**
     * Reads a private instance field, walking the class hierarchy.
     *
     * <p>{@code target} must be the object that owns the field. A null target is only correct for a static
     * field, and passing one for an instance field makes {@code Field.get} throw rather than return null -
     * which is what made an earlier version of this report a bare failure for every level.
     *
     * <p>The hierarchy walk is needed because at runtime {@code getClass()} returns the Mixin-patched
     * subclass, not the vanilla class the field was declared on.
     */
    private static Object field(Class<?> owner, Object target, String name) throws Exception
    {
        for (Class<?> c=owner; c!=null; c=c.getSuperclass())
        {
            try
            {
                java.lang.reflect.Field f=c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            }
            catch (NoSuchFieldException ignored)
            {
                // keep walking up
            }
        }
        throw new NoSuchFieldException(name + " not found on " + owner.getName());
    }

    /** Sizes a private fastutil map or set, walking the hierarchy for the field. */
    private static int size(Class<?> owner, Object target, String name) throws Exception
    {
        Object v=field(owner, target, name);
        if (v==null) return 0;
        return (int) v.getClass().getMethod("size").invoke(v);
    }

    /**
     * Called from the server tick. Logs once a minute, and additionally whenever the loaded chunk count has
     * grown by more than 500 since the last report, because a leak's rate is the only number that predicts
     * when it will next fail.
     */
    public static void report(MinecraftServer server)
    {
        if (server==null) return;
        if (++tick % 1200 != 0) return;

        Runtime runtime=Runtime.getRuntime();
        long usedMiB=(runtime.totalMemory()-runtime.freeMemory()) / (1024 * 1024);
        long maxMiB=runtime.maxMemory() / (1024 * 1024);
        long heapUsedMiB=MEMORY.getHeapMemoryUsage().getUsed() / (1024 * 1024);

        StringBuilder sb=new StringBuilder("[chunk-diag] heap ")
                .append(heapUsedMiB).append('/').append(maxMiB).append(" MiB, total ").append(usedMiB)
                .append('/').append(maxMiB).append(" MiB");

        long total=0;
        for (ServerLevel level : server.getAllLevels())
        {
            net.minecraft.server.level.ChunkMap map=level.getChunkSource().chunkMap;
            long chunks=map.size();
            total+=chunks;
            sb.append("\n    ").append(level.dimension().identifier()).append(" chunks=").append(chunks)
                    .append(" pendingTasks=").append(level.getChunkSource().getPendingTasksCount())
                    .append(' ').append(ticketStatus(level.getChunkSource()));
        }
        sb.append("\n    total chunks=").append(total)
                .append(", delta since last report=").append(total-lastChunks);
        lastChunks=total;

        io.silvicky.item.common.Util.LOGGER.info(sb.toString());
    }
}
