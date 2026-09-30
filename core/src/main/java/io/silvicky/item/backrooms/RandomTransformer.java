package io.silvicky.item.backrooms;

import io.silvicky.item.common.Util;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.silvicky.item.cfg.JSONConfig.randomChunkRange;
import static io.silvicky.item.common.Util.LOGGER;

public class RandomTransformer extends VecTransformer
{
    private final Map<ChunkPos,ChunkPos> s2cMap=new ConcurrentHashMap<>();
    private final Map<ChunkPos,ChunkPos> c2sMap=new ConcurrentHashMap<>();
    private final Set<RandomTransformer> self=new java.util.concurrent.CopyOnWriteArraySet<>();
    private static final Set<RandomTransformer> LIVE=ConcurrentHashMap.newKeySet();
    private long misses,dry,late,maxMapSize,inWindowBusy;

    static
    {
        //Registered once, statically. Registering per instance meant the newest transformer overwrote the
        //previous one's snapshot, so the reported map looked healthy while the counters - which are global -
        //were dominated by a different, older instance. That made the numbers look self-contradictory: a
        //169-entry map in a 225-position window cannot be exhausted, yet every request reported one dry.
        TransformAudit.section("random-map", RandomTransformer::describeAll);
    }

    /**
     * How many of the window's positions are occupied, and how big the map really is.
     *
     * <p>A request can only come back dry if every position in the window is taken, so this pair distinguishes
     * "the window is genuinely full" from "the window is the wrong size" from "this is a different instance
     * than the one being described". Only measured when a request actually fails, so it costs nothing
     * normally.
     */
    private int windowBusy()
    {
        int base=poolHalfWidth();
        int busy=0;
        for (int x=-base;x<=base;x++)
            for (int z=-base;z<=base;z++)
                if (s2cMap.containsKey(new ChunkPos(lastS.x()+x,lastS.z()+z)))busy++;
        return busy;
    }

    /** Every live transformer's state, so the counters and the snapshot finally refer to the same thing. */
    private static String describeAll()
    {
        long totalDry=0,totalMiss=0,totalLate=0,totalLeaked=0,totalReleased=0;
        RandomTransformer worst=null;
        for (RandomTransformer t : LIVE)
        {
            totalDry+=t.dry; totalMiss+=t.misses; totalLate+=t.late;
            totalLeaked+=t.leakedTickets(); totalReleased+=t.released;
            if (worst==null||t.dry>worst.dry)worst=t;
        }
        if (LIVE.isEmpty())return "no random transformers";
        String head=String.format("instances=%d misses=%d dry=%d late=%d leakedTickets=%d ticketsReleased=%d | ",
                LIVE.size(),totalMiss,totalDry,totalLate,totalLeaked,totalReleased);
        if (worst==null)return head+"none";
        return head+String.format(
                "worst: s2c=%d c2s=%d pool=+%d inWindowBusy=%d desync=%d holes=%d centre=%s sCentre=%s ticks=%d",
                worst.s2cMap.size(),worst.c2sMap.size(),worst.poolHalfWidth(),worst.inWindowBusy,
                worst.desyncs,worst.holes,worst.lastCentre,worst.lastS,worst.ticks);
    }

    private long ticks;
    private ChunkPos lastS;
    private ChunkPos lastCentre;
    private int desyncs;
    private int holes;
    private final Set<ChunkPos> ticketed=ConcurrentHashMap.newKeySet();
    private long released;
    private static boolean warnedPoolTooSmall=false;
    public RandomTransformer(ServerPlayer player)
    {
        super(player);
        if (!warnedPoolTooSmall && randomChunkRange<viewDistance+2)
        {
            warnedPoolTooSmall=true;
            LOGGER.warn("[backrooms] randomChunkRange={} is smaller than viewDistance+2={}, so the "
                            +"server-chunk pool cannot cover the client view. The window is widened at "
                            +"runtime, but raise randomChunkRange in ItemStorage.json to match your view "
                            +"distance.",randomChunkRange,viewDistance+2);
        }
        tick();
        LIVE.add(this);
    }

    private void put(ChunkPos s,ChunkPos c)
    {
        remove(c);
        //put() can also be handed a server position that is already serving a different client chunk.
        //That chunk's reverse entry has to go as well, otherwise c2sMap keeps claiming a client chunk is
        //covered when s2cMap no longer points at it. The hole is then permanent, because
        //onChunkPosChanged() sees the stale key and never re-requests it, and c2sTransform() hands back
        //the wrong server position for that client chunk.
        ChunkPos displaced=s2cMap.put(s,c);
        if(displaced!=null)c2sMap.remove(displaced);
        c2sMap.put(c,s);
    }
    private void remove(ChunkPos c)
    {
        if(c2sMap.containsKey(c))
        {
            ChunkPos oldS=c2sMap.remove(c);
            s2cMap.remove(oldS);
        }
    }
    private void request(ChunkPos c)
    {
        ChunkPos free=findFreeServerPos();
        if (free==null)
        {
            //The pool is dry. Leave the client chunk unmapped rather than taking a position that is already
            //serving something: a hole is refilled on the next border move, whereas a displaced chunk stays
            //wrong until something happens to re-request it.
            dry++;
            inWindowBusy=windowBusy();
            maxMapSize=Math.max(maxMapSize,s2cMap.size());
            TransformAudit.count("pool-dry");
            return;
        }
        put(free, c);
    }

    /**
     * The half-width of the window that server positions are drawn from.
     *
     * <p>The view needs {@code (2*(viewDistance+2)+1)^2} distinct server positions. With the window centred on
     * the same point the view is centred on, that is exactly what this provides, so a little slack on top
     * covers the churn as the view slides. The floor of {@code randomChunkRange} is kept because it is a
     * configured minimum.
     *
     * <p>An earlier version clamped this to exactly {@code viewDistance + 2} on the theory that no slack was
     * needed, because a simulation of the allocate/prune/request cycle measured zero forced displacements
     *at view distances 6 to 12. That simulation centred the view on the same point as the window and had no
     *frustum culling, so it never reproduced the case that actually occurs: the window centred on
     * {@code lastS} while the view is centred on {@code newC}, which are far apart. A live session then
     *reported {@code inWindowBusy=225} - the window completely full - together with {@code pool-dry}, which
     *cannot both happen when the window is the size of the demand.
     */
    private int poolHalfWidth()
    {
        return Math.max(randomChunkRange,viewDistance+4);
    }

    /**
     * A server position that no client chunk is currently using.
     *
     * <p>Returns {@code null} when the window is exhausted rather than reusing one, because every position
     * in it is serving a chunk the player can currently see and stealing it would move that chunk.
     *
     * <p>This probes for a free position instead of materialising the window, because it runs for every
     * uncovered border chunk on every chunk-border move and a {@code (2*base+1)^2} list of ChunkPos per
     * call is far too much allocation.
     */
    private ChunkPos findFreeServerPos()
    {
        //Centred on the CLIENT position, not on lastS.
        //
        //The window used to be centred on lastS - the player's own server chunk - while the demand is a
        //square of viewDistance+2 around newC, the player's client chunk. Those two points are up to
        //poolHalfWidth apart, so the view's far corner sat up to poolHalfWidth + viewDistance + 2 chunks from
        //lastS, entirely outside the window being searched. The allocator could therefore report the window
        //full (inWindowBusy=225) while still failing to place a chunk that the view needed - observed as
        //pool-dry alongside holes=0.
        //
        //It also explains chunks behind the player being re-served after a teleport: a teleport moves lastS,
        //which moved the window, which put chunks the player had left outside the new window and forced
        //them to be allocated again. Centring on newC removes both effects, because newC is what the view is
        //actually built around and it moves smoothly rather than jumping.
        ChunkPos centre=lastCentre!=null?lastCentre:lastS;
        int base=poolHalfWidth();
        for (int probe=0; probe<64; probe++)
        {
            ChunkPos pos=new ChunkPos(centre.x()+getRandom(base),centre.z()+getRandom(base));
            if (!s2cMap.containsKey(pos))return pos;
        }
        for (int x=-base; x<=base; x++)
        {
            for (int z=-base; z<=base; z++)
            {
                ChunkPos pos=new ChunkPos(centre.x()+x,centre.z()+z);
                if (!s2cMap.containsKey(pos))return pos;
            }
        }
        return null;
    }
    private ChunkPos init(ChunkPos s)
    {
        if(s2cMap.containsKey(s))return s2cMap.get(s);
        ChunkPos c=findFreeClientPos(s);
        if (c!=null)put(s,c);
        return c;
    }

    /**
     * A client position that no server chunk is currently using - the mirror of
     * {@link #findFreeServerPos()}. This one is the <em>centre</em> of the view that
     * {@link #onChunkPosChanged()} builds a {@code viewDistance + 2} square around, so it has to be drawn
     * from a window at least that large rather than the small {@code randomChunkRange} pool, which could
     * place the centre somewhere the rest of the view cannot reach.
     *
     * <p>Returns {@code null} when exhausted. This one is allowed to be a little different from its mirror:
     * it is only asked for a single chunk - the player's own - and a failure there is reported rather than
     * papered over, because without a centre the whole view is undefined.
     */
    private ChunkPos findFreeClientPos(ChunkPos s)
    {
        //Centred on s, which is the server chunk being given a client position. This one is the mirror of
        //findFreeServerPos and is called for a single chunk - the player's - so it has no separate view centre
        //to use. Its window therefore still has to be wide enough to reach past the view square from s, which
        //is why poolHalfWidth deliberately exceeds viewDistance + 2.
        int base=poolHalfWidth();
        for (int probe=0; probe<64; probe++)
        {
            ChunkPos pos=new ChunkPos(s.x()+getRandom(base),s.z()+getRandom(base));
            if (!c2sMap.containsKey(pos))return pos;
        }
        for (int x=-base;x<=base;x++)
        {
            for (int z=-base;z<=base;z++)
            {
                ChunkPos pos=new ChunkPos(s.x()+x,s.z()+z);
                if (!c2sMap.containsKey(pos))return pos;
            }
        }
        TransformAudit.count("no-free-centre");
        return null;
    }
    @Override
    public Map<ChunkPos,ChunkPos> getS2c()
    {
        //Hand over the live map rather than a copy. It is already a ConcurrentHashMap, and this is called
        //on every applyChunkTrackingView, where a Map.copyOf would allocate a fresh ~841-entry table per
        //call. More importantly a snapshot goes stale the moment onChunkPosChanged() mutates the map, and
        //a tracking view answering contains/forEach from a stale copy reports chunks as untracked.
        return s2cMap;
    }
    private void onChunkPosChanged()
    {
        //Published before the allocation loop, because findFreeServerPos searches around lastCentre and this
        //is the new centre. Previously it was only set in snapshot(), at the end, so the whole pass allocated
        //against the previous centre - one chunk-behind, which is the same lag the cache TTL originally had.
        ChunkPos newC=init(lastS);
        lastCentre=newC;
        if (newC==null)
        {
            //No client position could be allocated for the player's own chunk, so there is no centre to
            //build a view around. Leave the existing mapping alone rather than corrupting it; the pool is
            //sized to make this unreachable, so seeing it means the view is far larger than assumed.
            TransformAudit.count("no-centre");
            return;
        }
        List<ChunkPos> updatedChunks=new ArrayList<>();
        for (ChunkPos c : c2sMap.keySet())
        {
            try
            {
                if ((Util.chunkPosDistance(c, newC) > 1
                        && !Util.isVisible(s2cTransform(player.getEyePosition()), player.getYRot(), c))
                        ||Util.chunkPosDistance(c, newC) > viewDistance+2)
                    updatedChunks.add(c);
            }
            catch (ChunkUnusedException e)
            {
                //Only the player's own chunk is ever mapped into client space here, and it is in the map
                //because init() just put it there. Rethrowing a bare RuntimeException as this used to would
                //abort the whole update mid-way, leaving the view half-pruned and never re-requested.
                TransformAudit.count("prune-lookup-miss");
                updatedChunks.add(c);
            }
        }
        for(ChunkPos c:updatedChunks)remove(c);
        for (int i = -viewDistance-2; i <= viewDistance+2; i++)
            for (int j = -viewDistance-2; j <= viewDistance+2; j++)
            {
                ChunkPos newBorder = new ChunkPos(newC.x() + i, newC.z() + j);
                if (!c2sMap.containsKey(newBorder))
                    request(newBorder);
            }
        snapshot();
    }

    /**
     * Records the state of the two maps so the periodic dump can report it.
     *
     * <p>This replaces a previous check that only logged on failure and stopped after eight reports. That
     * was a mistake: the failure it was watching for is silent and persistent, so capping the reports meant
     * the first few were drowned out and the rest never appeared - the same reason a capped warning is
     * useless for a bug that needs one line of evidence. Counting is cheap (two map sizes plus a walk of the
     * view square, once per chunk-border move) and the dump is the only output.
     *
     * <p>Desync is counted rather than reported, because it is the one condition that means the maps are
     * internally inconsistent and every lookup built on them is suspect.
     */
    private void snapshot()
    {
        desyncs=0;
        holes=0;
        for (Map.Entry<ChunkPos,ChunkPos> e : c2sMap.entrySet())
        {
            ChunkPos back=s2cMap.get(e.getValue());
            if (back==null||!back.equals(e.getKey()))desyncs++;
        }
        ChunkPos centre=s2cMap.get(lastS);
        if (centre!=null)
        {
            for (int i=-viewDistance-2;i<=viewDistance+2;i++)
                for (int j=-viewDistance-2;j<=viewDistance+2;j++)
                    if (!c2sMap.containsKey(new ChunkPos(centre.x()+i,centre.z()+j)))holes++;
        }
        else holes++;
        if (desyncs>0)TransformAudit.count("map-desync");
        if (holes>0)TransformAudit.count("view-holes");
        //No longer assigned here: onChunkPosChanged publishes lastCentre up front, before allocating, so
        //that findFreeServerPos searches around the centre the view is being built around.
    }

    /** One line of live state for this instance alone. */
    public String describe()
    {
        int edge=2*(viewDistance+2)+1;
        return String.format(
                "s2c=%d c2s=%d pool=+%d (view wants %d) inWindowBusy=%d desync=%d holes=%d centre=%s "
                        +"sCentre=%s leakedTickets=%d misses=%d dry=%d",
                s2cMap.size(),c2sMap.size(),poolHalfWidth(),edge*edge,inWindowBusy,desyncs,holes,
                lastCentre,lastS,leakedTickets(),misses,dry);
    }
    private void updateChunkPos()
    {
        ChunkPos newPos=player.chunkPosition();
        if(lastS==newPos)return;
        lastS=newPos;
        onChunkPosChanged();
    }
    @Override
    public void tick()
    {
        ticks++;
        updateChunkPos();
        addLoadingTicket();
    }
    @Override
    public ChunkPos s2cTransform(ChunkPos pos) throws ChunkUnusedException
    {
        ChunkPos mapped=s2cMap.get(pos);
        if(mapped==null)
        {
            //This is the count that was previously dead. Every server-to-client lookup for a chunk the
            //client believes it can see lands here when the mapping is missing, and the caller either drops
            //the packet or falls back to the identity - which is what the client then renders as a hole or
            //a stale chunk.
            TransformAudit.unmapped();
            TransformAudit.count("s2c-miss");
            throw new ChunkUnusedException();
        }
        return mapped;
    }
    @Override
    public ChunkPos c2sTransform(ChunkPos pos)
    {
        ChunkPos mapped=c2sMap.get(pos);
        if(mapped==null)
        {
            //A client position with no server chunk yet. This runs on the netty thread for every inbound
            //packet, so it must not block, and it must not throw: doing either loses the packet.
            request(pos);
            mapped=c2sMap.get(pos);
            if (mapped==null)
            {
                misses++;
                TransformAudit.unmapped();
                TransformAudit.count("c2s-miss");
                TransformAudit.siteFail("c2s-miss");
                return pos;
            }
            late++;
            TransformAudit.count("c2s-late");
        }
        return mapped;
    }
    private void addLoadingTicket()
    {
        var source=player.level().getChunkSource();
        for(ChunkPos pos:s2cMap.keySet())source.addTicketWithRadius(TicketType.ENDER_PEARL,pos,2);
        //Release anything this transformer has ticketed that the mapping has since dropped. A previous
        //version tracked only the positions newly ticketed on the immediately preceding pass, which meant a
        //position held for many ticks was no longer being considered and stranded its ticket on the way out -
        //it still reported 383 leaked after the first attempt at this fix. Comparing the whole set is the
        //only version that is actually correct.
        for(ChunkPos pos:ticketed)
            if(!s2cMap.containsKey(pos))
            {
                source.removeTicketWithRadius(TicketType.ENDER_PEARL,pos,2);
                ticketed.remove(pos);
                released++;
            }
        for(ChunkPos pos:s2cMap.keySet())ticketed.add(pos);
    }
    @Override
    void release()
    {
        var source=player.level().getChunkSource();
        for(ChunkPos pos:ticketed)source.removeTicketWithRadius(TicketType.ENDER_PEARL,pos,2);
        TransformAudit.count("transformer-released");
        LIVE.remove(this);
    }
    /**
     * Tickets this transformer has added for chunks it has since stopped mapping, which is the number that
     * should now stay at zero.
     */
    private long leakedTickets()
    {
        long live=0;
        for(ChunkPos pos:ticketed)if(s2cMap.containsKey(pos))live++;
        return ticketed.size()-live;
    }
}
