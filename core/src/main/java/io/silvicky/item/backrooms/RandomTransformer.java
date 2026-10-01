package io.silvicky.item.backrooms;

import io.silvicky.item.common.Util;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.silvicky.item.cfg.JSONConfig.randomChunkRange;
import static io.silvicky.item.cfg.JSONConfig.remapPinMillis;

public class RandomTransformer extends VecTransformer
{
    private final Map<ChunkPos,ChunkPos> s2cMap=new ConcurrentHashMap<>();
    private final Map<ChunkPos,ChunkPos> c2sMap=new ConcurrentHashMap<>();
    private ChunkPos lastS;
    /**
     * Half-width of the server-position pool, in chunks.
     *
     * <p>Pinned to the view's own half-width ({@code viewDistance+2}) and never below the configured
     * {@code randomChunkRange}, because a pool smaller than the view it feeds can never fill it. With
     * {@code viewDistance} 4 that is a 13x13 view inside a 13x13 pool, so a server owner who lowers
     * {@code randomChunkRange} below 3 gets it ignored rather than a broken world.
     */
    private final int poolRadius;
    private long dryPools;
    /**
     * Pages announced to the client since the last log line.
     *
     * <p>Read this, it is the proof the mechanic is happening and the measure of its cost. A border crossing
     * should re-announce roughly one edge of the square - about 13 to 26 pages - because that is how many
     * were pruned and re-rolled. Zero means the client is never being told and the shuffle is invisible
     * again. A number in the hundreds on a single line means something re-rolled the whole world, which
     * should never happen on an ordinary crossing. See {@link #showPage}.
     */
    private long resentPages;
    /**
     * How many chunks around the player are exempt from the frustum prune, in chunks.
     *
     * <p>Chebyshev distance, so {@code 0} is the player's own chunk alone and {@code 1} is that chunk plus
     * its eight neighbours.
     *
     * <p><b>This is 1 for a reason. Do not reduce it.</b> When the player walks from chunk A into chunk B,
     * their bounding box straddles both. If A were pruned and unmapped, the collision lookups that still
     * reach back into A would fail to convert, and the player collides with the newly-mapped terrain
     * instead of walking into it. Keeping A and the whole ring of neighbours mapped is what prevents
     * that, and it has been verified many times to be necessary.
     *
     * <p>The cost is that a 3x3 block of the remapped region is therefore always mapped, and mapped means
     * sent to the client, so it is visible. That is a deliberate trade: correct collision, at the price of
     * a visible 3x3 patch. Anything that wants the remapped region to look invisible has to be achieved
     * somewhere that does not unmap the chunks the player is standing in - not by shrinking this.
     */
    private static final int frustumExemptRadius=1;
    /**
     * Per-instance, so two players never draw the same shuffle and a reshuffle is not the static
     * {@code VecTransformer.random} that {@link VecTransformer#getRandom} uses for offsets.
     */
    private final Random random=new Random();
    public RandomTransformer(ServerPlayer player)
    {
        super(player);
        poolRadius=Math.max(randomChunkRange,viewDistance+2);
        pinHoldMillis=remapPinMillis;
        tick();
    }

    private void put(ChunkPos s,ChunkPos c)
    {
        remove(c);
        //If s is already mapped to some other client chunk, that old entry has to go too. Without this the
        //two maps stop being inverses of each other: c2sMap would still point the old client chunk at s
        //while s2cMap pointed s at the new one, and every later remove() on that old chunk would then
        //delete the new chunk's mapping instead. Once that happens nothing in this class can be trusted.
        ChunkPos displaced=s2cMap.put(s,c);
        if(displaced!=null&&!displaced.equals(c))c2sMap.remove(displaced);
        c2sMap.put(c,s);
        bumpMap();
        showPage(s);
    }
    /**
     * Makes sure the client actually holds the terrain of a page, at the client position just assigned to it.
     *
     * <p><b>Without this the shuffle is invisible, and the only symptom is that it is invisible.</b> A page
     * being given a new client position is not, by itself, an event the client hears about. The only thing
     * that tells a client a chunk exists is {@code PlayerChunkSender.pendingChunks}, and the only two
     * things that ever put a chunk in there are:
     *
     * <ol>
     *   <li>{@code ChunkMap#applyChunkTrackingView}, which runs the add/remove diff - and
     *       {@code updateChunkTracking} only calls it when the player's own chunk or the view distance
     *       changed. A re-roll changes neither, so <b>the diff never runs</b> and nothing is added;</li>
     *   <li>{@code ChunkMap#onChunkReadyToSend}, for chunks that have just finished generating. The
     *       re-rolled-to chunk is normally an old, long-generated one, so this does not fire either.</li>
     * </ol>
     *
     * <p>So the client kept the terrain it had been sent for that position, while the server moved on to a
     * different page. That is the "remap is seldom seen" report, and it is also the "mismatch after
     * travelling a long time" report, and it is why blocks the player can see stop responding: the client
     * is aiming at terrain the server has already thrown away. All three are the same bug.
     *
     * <p>{@code pendingChunks} is a set, so re-marking a page the client already has costs one hash insert
     * and changes nothing. A page whose chunk is not loaded yet returns null here and is picked up later by
     * {@code onChunkReadyToSend}, which is the correct retry.
     *
     * <p>No forget is sent. The client replaces the whole chunk when the new one arrives, so the old
     * terrain is overwritten in place: the page comes back holding different terrain, which is the mechanic,
     * rather than vanishing and leaving a hole the player can fall through.
     */
    private void showPage(ChunkPos serverChunk)
    {
        try
        {
            ServerPlayer p=player;
            if(p==null||p.connection==null||p.level()==null)return;
            LevelChunk chunk=p.level().getChunkSource().chunkMap.getChunkToSend(serverChunk.pack());
            if(chunk!=null)
            {
                p.connection.chunkSender.markChunkPendingToSend(chunk);
                resentPages++;
            }
        }
        catch (Exception ignored)
        {
            //Best effort. A page that cannot be announced is still mapped and still simulated correctly, so
            //the worst case is the old symptom for that one page, not a corrupt map.
        }
    }
    /**
     * A chunk's client position, pinned so a re-roll cannot land between an action and its result.
     *
     * <p>There is a window in which a chunk's client position MUST NOT change, and it is the window between
     * the client acting on a position and the server telling them what happened. Breaking a block is two
     * conversions at two different times: the click comes in and is converted C to S here on the netty
     * thread, and the result is converted back S to C when the break is broadcast. If the chunk's client
     * position is re-rolled in between - because the player walked a border and the visibility rule pruned
     * it, and then something referred to it again - the two conversions disagree. The server breaks the
     * right block, and tells the client a different one changed. The player watches the block they broke
     * reappear, which is the symptom, and it was the one symptom that survived every other fix.
     *
     * <p>Pinning only covers the gap; it does not make the mapping permanent. After the hold expires the
     * chunk becomes disposable again, which is the whole point of the mechanic.
     */
    private final Map<ChunkPos,Long> pinnedUntil=new ConcurrentHashMap<>();
    /** The hold, read from config. It must cover a full network round trip - see Cfg#remapPinMillis. */
    private final long pinHoldMillis;

    /**
     * Whether a chunk's client position is currently pinned, and therefore must not be re-rolled.
     *
     * <p>Checked before a fresh mapping is made. This is what stops a re-roll from silently moving a chunk
     * out from under an action the player has already taken.
     */
    public boolean isPinned(ChunkPos serverChunk)
    {
        Long until=pinnedUntil.get(serverChunk);
        return until!=null&&until>System.currentTimeMillis();
    }
    /** Holds a chunk's current client position for {@link #pinHoldMillis}. */
    public void pin(ChunkPos serverChunk)
    {
        if(s2cMap.containsKey(serverChunk))
            pinnedUntil.put(serverChunk,System.currentTimeMillis()+pinHoldMillis);
    }
    /**
     * A chunk the player has acted on, which must keep its client position for a moment.
     *
     * <p>Called when the player interacts with a block. The click arrives in the player's map and is
     * converted to a real position; the outcome is converted back when it is broadcast. If the chunk moved in
     * between, the player is told about a different block than the one they hit, and the block they broke
     * comes straight back. Holding the position closes that window.
     */
    @Override
    public void pinAt(Vec3 clientPos)
    {
        //Careful with the spaces here. The position arrives in the player's map, so the chunk it names is a
        //*client* chunk. The pin table is keyed by *server* chunk, because that is what `remove` and
        //`findFreeServerPos` have in hand when they ask whether a chunk is frozen. So the chunk has to be
        //translated through c2sMap before it can be pinned.
        //
        //Passing the client chunk straight in is not a no-op, it is worse than one. Both maps are keyed by
        //ChunkPos over the same range, so a client chunk is quite often a real key of s2cMap as well - just
        //for a different, unrelated page. The freeze then lands on that page instead of the one being
        //broken, and the chunk the player actually hit stays free to re-roll. See CONVENTION.md 2.10.
        ChunkPos clientChunk=chunkOf(clientPos);
        if(clientChunk==null)return;
        ChunkPos serverChunk=c2sMap.get(clientChunk);
        if(serverChunk!=null)pin(serverChunk);
    }
    private void remove(ChunkPos c)
    {
        //A pinned chunk keeps its client position: dropping it here is what makes a block break revert,
        //because the client is told about the change at whatever position the chunk has been given since.
        ChunkPos server=c2sMap.get(c);
        if(server!=null&&isPinned(server))return;
        if(c2sMap.containsKey(c))
        {
            ChunkPos oldS=c2sMap.remove(c);
            s2cMap.remove(oldS);
            bumpMap();
        }
    }
    private void request(ChunkPos c)
    {
        ChunkPos s=findFreeServerPos();
        if(s==null)
        {
            //Every candidate server position is taken. Say so rather than quietly reusing an occupied
            //one: the old fallback of handing back lastS+(range+1,range+1) overwrote whatever lived there
            //and broke the s2c/c2s correspondence, which is far worse than failing to place a chunk.
            dryPools++;
            return;
        }
        put(s,c);
    }
    /**
     * A server position for a new client chunk, or null when the pool around the player is full.
     *
     * <p>The pool is centred on the player's own chunk and has to be at least as large as the view it
     * feeds, otherwise the view cannot be filled. The view is {@code (2*(viewDistance+2)+1)^2} client
     * positions, so the pool radius is pinned to the view's own half-width and never drops below the
     * configured {@code randomChunkRange}. Sizing it this way is what makes exhaustion impossible rather
     * than merely unlikely.
     *
     * <p>The candidates are shuffled before the scan. That is not decoration: scanning in a fixed order
     * always hands out the same corner slot, so a freshly shuffled world would place the player's own
     * chunk at exactly {@code lastS+(-poolRadius,-poolRadius)} every single time. Two fresh transforms in
     * a playtest log did precisely that. The position would then be predictable from the player's real
     * coordinates, which is precisely what the shuffle exists to prevent - see CONVENTION.md 1.5.
     */
    private ChunkPos findFreeServerPos()
    {
        List<ChunkPos> pool=poolAround(lastS);
        for (ChunkPos s : pool)
        {
            if (!s2cMap.containsKey(s)&&!isPinned(s))return s;
        }
        return null;
    }
    private ChunkPos init(ChunkPos s)
    {
        if(s2cMap.containsKey(s))return s2cMap.get(s);
        ChunkPos c=findFreeClientPos(s);
        if(c==null)
        {
            //Same failure as request(): report it, and leave the player on a chunk that is already mapped
            //rather than corrupting the pair of maps to give them a fresh one.
            dryPools++;
            return s2cMap.get(s);
        }
        put(s,c);
        return c;
    }
    private ChunkPos findFreeClientPos(ChunkPos s)
    {
        List<ChunkPos> pool=poolAround(s);
        for (ChunkPos c : pool)
        {
            if (!c2sMap.containsKey(c))return c;
        }
        return null;
    }
    /**
     * The pool of candidate positions around a centre, in a random order.
     *
     * <p>Only ever for pages the player is meant to see. A page pruned for being behind the player must not
     * get a fresh mapping on demand, or walking backwards silently re-creates it somewhere else and the
     * player ends up standing in terrain that was never sent - present and accessible, but invisible. That
     * is the worst of the three outcomes, and it is what {@link #c2sTransform} used to do.
     */
    private List<ChunkPos> poolAround(ChunkPos centre)
    {
        List<ChunkPos> pool=new ArrayList<>((poolRadius*2+1)*(poolRadius*2+1));
        for(int x=-poolRadius;x<=poolRadius;x++)
            for(int z=-poolRadius;z<=poolRadius;z++)
                pool.add(new ChunkPos(centre.x()+x,centre.z()+z));
        Collections.shuffle(pool,random);
        return pool;
    }
    @Override
    public Map<ChunkPos,ChunkPos> getS2c()
    {
        return Map.copyOf(s2cMap);
    }
    private void onChunkPosChanged()
    {
        ChunkPos newC=init(lastS);
        if(newC==null)
        {
            //init could not give the player's own chunk a client position. The pool is sized so this
            //should not happen, but if it ever does the only safe thing is to leave the existing map alone
            //and wait for the next border crossing - pruning now would use a null centre and throw.
            return;
        }
        //Two different questions, and they must not be confused. That confusion is what made the shuffle
        //look inert for several rounds.
        //
        //  WHAT IS SENT: the whole square, in every direction, always. See getRemap(). Filtering this by
        //  facing produced the "invisible chunks, all behind me" report, because a page that is not sent is
        //  a void the player can walk into.
        //
        //  WHAT GETS RE-ROLLED: the pages behind the player, on a border crossing. That is the mechanic, and
        //  the prune below is where it happens. A page that is pruned loses its identity, and the request
        //  loop then gives it a different server page, so the client must forget the old one and be sent the
        //  new one - and the world behind you visibly becomes somewhere else.
        //
        //An earlier version applied the facing test to the request loop as well, which silently undid the
        //prune in the same pass: nothing was ever dropped, so nothing was ever re-rolled, and no remapping
        //could be observed at all. The prune re-rolls; the request refills. Keep them apart.
        int half=viewDistance+2;
        Vec3 eyeC=safeClient(player.getEyePosition());
        List<ChunkPos> updatedChunks=new ArrayList<>();
        for (ChunkPos c : c2sMap.keySet())
        {
            if(!retained(c,newC,eyeC))updatedChunks.add(c);
        }
        for(ChunkPos c:updatedChunks)remove(c);
        //Refill the entire square, unconditionally. These pages were just re-rolled and must be replaced, or
        //the player has a hole behind them.
        for (int i = -half; i <= half; i++)
            for (int j = -half; j <= half; j++)
            {
                ChunkPos candidate = new ChunkPos(newC.x() + i, newC.z() + j);
                if (c2sMap.containsKey(candidate))continue;
                request(candidate);
            }
    }
    /**
     * Whether a page keeps its identity, or is re-rolled to a different server page.
     *
     * <p>This is the mechanic, and it is the ONLY place facing is consulted. A page that is pruned here is
     * given a different server page by the request loop, so the world behind the player becomes somewhere
     * else the moment a border is crossed - which is the intended BACKROOMS behaviour. The world in front
     * keeps its pages, so it looks unchanged while you walk, and cannot be backtracked through.
     *
     * <p>What is pruned is not what is SENT. The full square is always sent regardless; see getRemap().
     * Conflating the two produced a void behind the player.
     */
    private boolean retained(ChunkPos c,ChunkPos newC,Vec3 eyeC)
    {
        int distance=Util.chunkPosDistance(c,newC);
        if(distance>viewDistance+2)return false;
        if(distance<=frustumExemptRadius)return true;
        if(eyeC==null)return true;
        return Util.isVisible(eyeC,player.getYRot(),c);
    }
    private void updateChunkPos()
    {
        ChunkPos newPos=player.chunkPosition();
        //player.chunkPosition() hands back a cached ChunkPos that is only replaced when the player really
        //does cross a border, so comparing references here is a correct "did the chunk change" test, and it
        //avoids rebuilding the view on every tick.
        if(lastS==newPos)return;
        ChunkPos previous=lastS;
        lastS=newPos;
        onChunkPosChanged();
        logChunkEntrance(previous==null?newPos:previous,newPos);
    }
    @Override
    protected String diagnosticSuffix()
    {
        return " | view=+"+(viewDistance+2)+" pool=+"+poolRadius+" map="+s2cMap.size()
                +" dry="+dryPools+" unmapped="+unmappedFrames+" fail="+failedFrames
                +" resent="+takeResent();
    }
    /** Reads and clears the per-crossing re-send count, so each log line reports its own crossings. */
    private long takeResent()
    {
        long n=resentPages;
        resentPages=0;
        return n;
    }
    @Override
    public void tick()
    {
        updateChunkPos();
        addLoadingTicket();
    }
    @Override
    public ChunkPos s2cTransform(ChunkPos pos) throws ChunkUnusedException
    {
        if(!s2cMap.containsKey(pos))throw new ChunkUnusedException();
        return s2cMap.get(pos);
    }
    @Override
    public ChunkPos c2sTransform(ChunkPos pos)
    {
        if(!c2sMap.containsKey(pos))
        {
            //Do NOT mint here. This used to call request(), and that is what produced the worst failure mode
            //reported from play: pages the visibility rule had deliberately pruned were silently given a
            //brand new position the moment anything referred to them, so walking backwards re-created the
            //discarded world somewhere else. The player was then standing in real, solid, breakable terrain
            //that the client had never been sent and could not see.
            //
            //A pruned page must stay pruned. Returning null says "this position is not part of the player's
            //world", which every caller already handles - movement reports no displacement, the outbound
            //path discards the packet - and none of them can invent geometry the player was never shown.
            return null;
        }
        return c2sMap.get(pos);
    }
    @Override
    public ChunkRemap getRemap()
    {
        //The whole table. A previous version filtered this by facing, and that was the "no remapping
        //observed" report: pages behind the player were excluded from what the client is sent, so the half
        //that was supposed to change on a border crossing was simply never delivered, and the half that was
        //delivered never changed. Nothing visibly moved.
        //
        //The remapping is visible because the square is RE-ROLLED as the player crosses a border - pages
        //keep their client position but get a different server page behind them, so the client must forget
        //the old page and be sent the new one. That is ChunkMap's own difference handling, in
        //ChunkTrackingViewMixin#inject1, and it is the part that has to work for the effect to appear.
        return ChunkRemap.mapping(s2cMap);
    }

    /**
     * Rebuild the view around the player's current position, whether or not they crossed a border.
     *
     * <p>{@link #onChunkPosChanged} normally runs only when the player's own page changes, and that is the
     * right trigger for walking. It is the wrong trigger for a teleport, though: a join places the player
     * wherever they spawn, which can be several pages from where the transformer was built, and the
     * placement pool is wide enough that the destination is already mapped. So nothing rebuilds the view,
     * the client is sent pages around the old centre, and the player arrives in a hole.
     *
     * <p>Safe to call when nothing moved - {@link #init} returns the existing mapping and the prune and
     * request are both no-ops.
     */
    public void recentre()
    {
        ChunkPos here=player.chunkPosition();
        if(here==null||here.equals(lastS))return;
        lastS=here;
        onChunkPosChanged();
    }

    @Override
    public boolean isMapped(ChunkPos serverPos)
    {
        return s2cMap.containsKey(serverPos);
    }
    @Override
    public boolean ensureMapped(ChunkPos serverPos)
    {
        if(s2cMap.containsKey(serverPos))return true;
        return init(serverPos)!=null;
    }
    private void addLoadingTicket()
    {
        for(ChunkPos pos:s2cMap.keySet())player.level().getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, pos, 2);
    }
}
