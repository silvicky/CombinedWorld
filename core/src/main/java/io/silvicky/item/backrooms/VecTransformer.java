package io.silvicky.item.backrooms;

import io.silvicky.item.StateSaver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.silvicky.item.common.Util.LOGGER;

public abstract class VecTransformer
{
    public static final Map<String,Class<? extends VecTransformer>> registry=Map.of(
            "nop", NopTransformer.class,
            "random", RandomTransformer.class,
            "linear", LinearTransformer.class,
            "xor", XorTransformer.class
    );
    /**
     * Per-player transformers, keyed by the player.
     *
     * <p>This has to be a concurrent map. {@link #getInstance} is called from the netty thread by
     * ConnectionMixin's packet hook on every inbound packet, and from the server thread by the chunk
     * tracking and collision mixins. A {@link WeakHashMap} is not safe under that: it can lose an
     * insertion, so a later lookup builds a <em>fresh</em> transformer, and a fresh one starts with empty
     * state - which silently stops remapping chunks, or draws new offsets and moves the player. A corrupted
     * map is worse than a slow leak, so this is a {@link ConcurrentHashMap} and {@link #computeIfAbsent}
     * makes the build-and-store atomic.
     *
     * <p>KNOWN LEAK, not fixed here. Nothing ever removes an entry: the mod registers no disconnect hook,
     * and a weak key would not have helped either, because every transformer holds a strong reference to
     * its own player, so an entry can never become unreachable. Every player who has ever joined therefore
     * stays in this map, holding themselves, their transformer, and for {@code random} and {@code xor} their
     * whole chunk map. This existed before the map type changed. The fix is a disconnect event that calls
     * something like {@code instances.remove(player)}, and it is deliberately NOT added here because it is
     * unrequested new behaviour. See CONVENTION.md, "Still open".
     */
    private static final Map<ServerPlayer,VecTransformer> instances=new ConcurrentHashMap<>();
    public static VecTransformer getInstance(ServerPlayer player)
    {
        return instances.computeIfAbsent(player, VecTransformer::getInstanceByDimension);
    }

    /**
     * Gives the player a brand new transformer, replacing whatever they had.
     *
     * <p>This runs on <em>every</em> teleport, not just cross-dimension ones, and that is deliberate. A fresh
     * {@code linear} or {@code random} shuffle each time is what stops a player from correlating two visits
     * and inferring the S-space layout - see CONVENTION.md section 1.5. An earlier version reused the
     * instance when the level had not changed, which quietly removed that protection.
     *
     * <p>The cost is real and worth knowing about: each rebuild re-derives the whole mapping and re-adds
     * the loading tickets for it, so a session that teleports a lot accumulates tickets. See the
     * {@code XorTransformer} and {@code RandomTransformer} {@code addLoadingTicket} methods, which have no
     * matching {@code removeTicket}.
     */
    public static void refreshInstance(ServerPlayer player)
    {
        instances.put(player, getInstanceByDimension(player));
    }
    /** The player's current transformer without building one, for diagnostics only. */
    public static VecTransformer peekInstance(ServerPlayer player)
    {
        return instances.get(player);
    }

    /**
     * Bumped every time this transformer's mapping changes.
     *
     * <p>The chunk tracking view keeps a copy of the mapping rather than a reference to it, so it needs
     * some way to notice that the copy has gone stale. A view rebuilt from a map that has since been
     * replaced asks the server to send chunks the live map no longer knows about, and those packets get
     * thrown away on the way out - which leaves the client standing in unloaded space. Comparing this
     * counter is how the view notices.
     *
     * <p>It is per transformer, not global, so one player's reshuffle does not invalidate every other
     * player's cached view. Two transformers can hold the same number, so a cache has to compare the
     * transformer identity as well - which is why this is exposed rather than used on its own.
     */
    private volatile long mapVersion;
    /** @see #mapVersion */
    public long mapVersion() {return mapVersion;}
    protected void bumpMap() {mapVersion++;}

    /**
     * Whether this server chunk already has a position on the player's map.
     *
     * <p>Decides what an absolute teleport does. Landing somewhere that is already on the map is a
     * correction or a void recovery, not a new arrival, and must keep the current shuffle - reshuffling
     * there is what turned a single bad frame into a teleport every tick. Landing somewhere with no
     * position yet needs one initialised; see {@link #ensureMapped}.
     *
     * <p>True by default, which is right for every transform that is a pure function of the position and
     * therefore has an answer for everywhere. Only {@code random}, which stores a table, needs to say no.
     */
    public boolean isMapped(ChunkPos serverPos) {return true;}

    /**
     * Makes sure {@code serverPos} has a position on the player's map, initialising one if it does not.
     *
     * <p>This is the "else initialise a new mapping" half of the absolute-teleport rule in §2.6.2. It is
     * deliberately narrower than drawing a whole new shuffle: only the destination needs a position, and
     * rebuilding everything around it would move every other chunk the player can see for no reason.
     *
     * @return whether the chunk is mapped afterwards
     */
    public boolean ensureMapped(ChunkPos serverPos) {return true;}

    /**
     * Rebuild whatever this transformer keeps around the player, for use after a teleport.
     *
     * <p>Walking triggers the rebuild on its own when a page border is crossed. A teleport does not: it can
     * land the player well away from where the map was built, and if the destination happens to be already
     * mapped then nothing notices and the player arrives inside a hole. A no-op for transforms that keep
     * nothing position-dependent.
     */
    public void recentre() {}

    /**
     * Notes that the player has acted at a position in their own map, so this transformer's mapping for
     * that spot must not move for a moment.
     *
     * <p>An interaction is converted twice: the incoming click is turned from the player's map into a real
     * position, and the result is turned back when the outcome is sent. Those two conversions happen at
     * different times on different threads. If the mapping changes in between, they disagree, and the
     * player is told a different block changed than the one they hit - the block they broke reappears.
     *
     * <p>Only meaningful for a transform that stores a table; the default does nothing.
     */
    public void pinAt(Vec3 clientPos) {}
    private static VecTransformer getInstanceByDimension(ServerPlayer player)
    {
        try
        {
            return registry.getOrDefault(StateSaver.getServerState(player.level().getServer())
                            .chunkTransformer
                            .get(player.level().dimension.identifier()), NopTransformer.class)
                    .getConstructor(ServerPlayer.class)
                    .newInstance(player);
        }
        catch (Exception e){return new NopTransformer(player);}
    }
    private static final Random random=new Random();
    public static int getRandom(int randomRange){return random.nextInt(randomRange*2+1)-randomRange;}
    public static final Vec3 INF=new Vec3(1e9,1e9,1e9);
    final ServerPlayer player;
    final int viewDistance;
    public VecTransformer(ServerPlayer player)
    {
        this.player=player;
        this.viewDistance = player.level().getChunkSource().chunkMap.getPlayerViewDistance(player);
    }

    /**
     * @return the per-chunk server-to-client mapping, or null when no remapping applies.
     */
    public abstract Map<ChunkPos,ChunkPos> getS2c();
    public abstract void tick();

    /**
     * Records every time the player enters a new chunk, in both coordinate systems at once.
     *
     * <p>Read the line like this. {@code dS} is how far the player actually walked - it is always 0 or 1,
     * because they just crossed one chunk border. {@code dC} is how far their own map says they moved, and
     * that is the number to look at:
     *
     * <ul>
     *   <li>{@code dC} of 0 or 1 in each axis - the border was crossed cleanly and the map is behaving.
     *   <li>{@code dC} large, with {@code dS} still 0 or 1 - this is the shuffle seam. The new chunk was
     *       placed somewhere unrelated to the old one, which is what {@code random} is <em>supposed</em> to
     *       do, but it also means the player's view jumped. See {@link #movementDistanceSq}.
     * </ul>
     *
     * <p>A big {@code dC} here is not by itself a bug. A large {@code dC} <em>combined with</em> a "moved
     * wrongly" line in the log is, because that is vanilla's anti-cheat reacting to the seam.
     */
    protected void logChunkEntrance(ChunkPos fromS, ChunkPos toS)
    {
        ChunkPos fromC=safeClient(fromS);
        ChunkPos toC=safeClient(toS);
        String name=player.getGameProfile().name();
        String extra=diagnosticSuffix();
        if(fromC==null||toC==null)
        {
            LOGGER.info("[remap] {} | S {} -> {} | C <not mapped> | unmapped: the server has no C position"
                    + " for at least one of these chunks"+extra, name, fromS, toS);
            return;
        }
        LOGGER.info("[remap] {} | S {} -> {} | C {} -> {} | dS=({},{}) dC=({},{}){}",
                name, fromS, toS, fromC, toC,
                toS.x()-fromS.x(), toS.z()-fromS.z(),
                toC.x()-fromC.x(), toC.z()-fromC.z(), extra);
    }
    /** Extra per-transformer detail for the log line, or "" when there is nothing useful to add. */
    protected String diagnosticSuffix() {return "";}
    /** The client position for a server chunk, or null when it is not currently mapped. */
    protected ChunkPos safeClient(ChunkPos serverPos)
    {
        try{return s2cTransform(serverPos);}
        catch (Exception e){return null;}
    }
    /** The client position for a server position, or null when its chunk is not currently mapped. */
    public Vec3 safeClient(Vec3 serverPos)
    {
        try{return s2cTransform(serverPos);}
        catch (Exception e){return null;}
    }

    /**
     * The squared distance to hand vanilla's movement check, measured the way the game is supposed to
     * judge it: in the player's own map, in C-space.
     *
     * <p>There is one case where that is impossible, and it is the case that used to break everything. See
     * CONVENTION.md section 1.2: each chunk is shuffled <em>on its own</em>, so two chunks that touch on
     * the real map can be hundreds of blocks apart on the player's map. When the player crosses such a
     * seam the two ends of the move sit in different S chunks, and the C distance between them measures
     * nothing the player did - it is just the size of the shuffle. It is not even bounded: the pool
     * reaches {@code randomChunkRange} chunks away, so "distance walked" comes out as 200 blocks when the
     * player took a single step.
     *
     * <p>Vanilla's {@code handlePlayerPositionChange} rejects any move whose squared distance exceeds
     * {@code 100}, which is 10 blocks. A seam only one chunk wide scores 256. So while the C distance was
     * used across a seam, vanilla decided the player had teleported, called {@code teleport()}, and
     * {@link ServerGamePacketListenerImplMixin} then rebuilt the entire shuffle. That is the loop where the
     * player is teleported every tick and keeps landing somewhere new, usually with no chunks around it -
     * the void.
     *
     * <p>So: same chunk on both ends, measure in C as designed. Different chunks, the player crossed a
     * seam, and the C distance is meaningless rather than merely large, so the real S distance is used
     * instead. Inside a chunk the anti-cheat is untouched.
     *
     * <p>When a chunk cannot be converted at all, this returns {@code 1e9}. That is the pre-existing
     * crash-avoidance: vanilla reads a huge value as "moved wrongly", snaps the player back, and carries
     * on. See CONVENTION.md Part 3.
     *
     * @param fromS start of the move on the real map
     * @param toS   end of the move on the real map
     * @return squared distance, which is what the vanilla call site compares against its limit
     */
    public double movementDistanceSq(Vec3 fromS, Vec3 toS)
    {
        try
        {
            return playerMove(fromS,toS).lengthSqr();
        }
        catch (Exception e)
        {
            //Only a genuine error reaches this now - a missing mapping is handled inside playerMove. Keep
            //1e9 for it: it is the crash-avoidance, and it is counted so the diagnostic can tell the two
            //apart. If fail= climbs, something is actually broken rather than merely unmapped.
            failedFrames++;
            return 1e9;
        }
    }

    /**
     * The player's own movement between two server positions, expressed in the player's own map.
     *
     * <p>This is the movement the game is judged on, and it is a <em>C-space</em> quantity. The player sees
     * themselves in C, they walk in C, and the speed and reach rules are about the world as they see it —
     * see CONVENTION.md 1.3. Both the movement the player is given and the distance the anti-cheat
     * measures come from this one method, so they cannot drift apart and disagree about whether a move was
     * legal.
     *
     * <p>**There is no seam to subtract, and an earlier version of this subtracted one. It was wrong, and
     * wrongly-signed wrong: it produced a 96-block "move" for a 0.3-block step, which vanilla rejects as a
     * 31-block-per-tick leap, so the player was corrected and teleported on every single border crossing.
     * A playtest log showed the teleports clustered exactly on the crossings.</p>
     *
     * <p>Why there is nothing to subtract. Work it through for one crossing. The client is at
     * {@code x=15.9} in its own chunk {@code Q} and steps to {@code x=16.2}, which is inside chunk
     * {@code Q+1}. Let {@code A} be the server chunk whose image is {@code Q} and {@code B} the one whose
     * image is {@code Q+1}. The measurement in C is
     *
     * <pre>  (Q+1)*16 + 0.2  -  (Q*16 + 15.9)  =  0.3 blocks</pre>
     *
     * <p>which is the step the player took. That number does not care that {@code B} happens to sit six
     * chunks away from {@code A} on the real map: those two facts are independent, and in the player's own
     * map the step is simply 0.3 blocks.
     *
     * <p>The 96-block jump is real, but it is a property of the <em>server</em> coordinates, not of the
     * player's. It is what {@code firstGoodX} and friends look like when a crossing happens, and it is the
     * server's business, not something to fold into a measurement taken in the other frame. Subtracting
     * that gap from a C-space measurement is not "removing an artefact" — it is adding the entire
     * server-side discontinuity to the player's step.
     *
     * <p>So: the C-space displacement <em>is</em> the movement, and this method returns it unchanged.
     *
     * <p>The one case that needs handling is a chunk the map does not cover, where there is no C-space
     * answer to give at all. That is undefined, not enormous, so the real movement is used and it is
     * counted in {@link #unmappedFrames}. Returning a huge value there is what used to make a transient gap
     * in the mapping into an infinite teleport loop — see CONVENTION.md 2.6.1.
     */
    public Vec3 playerMove(Vec3 fromS, Vec3 toS)
    {
        Vec3 cFrom=safeClient(fromS);
        Vec3 cTo=safeClient(toS);
        if(cFrom==null||cTo==null)
        {
            //The player's map has no position for one of these ends, so there is no C-space measurement to
            //make. That is very different from "the move was enormous", and reporting it as enormous is
            //what makes the server drag the player back.
            //
            //The S-space difference is NOT a safe substitute. The visibility rule drops the pages behind the
            //player, and the player's PREVIOUS page is one of them, so its C position is routinely gone by
            //the time the next move packet arrives. The transform never touches the offset inside a page,
            //so across a shuffled seam the S difference is however far apart the two real pages are - easily
            //100 blocks for a one-step walk. Vanilla reads that as a teleport, corrects the player, and the
            //cycle repeats.
            //
            //So an unmeasurable move is reported as no horizontal movement at all. The cost is that a
            //genuine speed hack landing on an unmapped page escapes this one check. That is the right way
            //round: a false correction traps a legitimate player, while a missed speed check only helps
            //someone cheating in a way they would have to work quite hard to arrange. Counted in
            //unmapped= so the frequency is visible rather than silent.
            unmappedFrames++;
            return new Vec3(0,toS.y-fromS.y,0);
        }
        return cTo.subtract(cFrom);
    }
    protected static ChunkPos chunkOf(Vec3 pos)
    {
        return pos==null?null:ChunkPos.containing(BlockPos.containing(pos));
    }
    /**
     * How many moves had no C-space answer at all because a chunk was not currently mapped.
     *
     * <p>Reported in the diagnostic because it used to be invisible. It was the reason a whole
     * teleport-per-tick loop could run without a single line of evidence in the log: the old code reported
     * these as {@code 1e9}, which is indistinguishable from a genuine "moved wrongly" further down.
     */
    protected long unmappedFrames;
    /** How many moves hit a real error rather than a missing mapping. Should stay 0. */
    protected long failedFrames;

    /**
     * The mapping the chunk tracking view should send to the client.
     *
     * <p>This is the half of the visibility rule that actually reaches the player, and it is separate from
     * {@link #getS2c()} on purpose. {@code getS2c()} answers "what is mapped", which is a question about the
     * server's own bookkeeping. This answers "what should be shown", and for a shuffled world the two sets
     * differ.
     *
     * <p>A page can be mapped and still not be visible: the tracking view is built on a square-distance
     * test and knows nothing about facing, so it would happily send the pages behind the player. Those get
     * rendered, and the result is that the shuffle is running but completely invisible — the player cannot
     * see the effect the whole mechanic exists to produce. Handing the view a visibility-filtered table is
     * what makes the Backrooms effect appear.
     *
     * <p>Defaults to the whole table, which is correct for every transform whose C and S coordinates agree
     * and therefore have nothing to hide. An identity transformer returning null lands on
     * {@link ChunkRemap#IDENTITY} and every chunk-tracking injection becomes a no-op; a constant offset
     * should override with {@link ChunkRemap#translation} so no table is ever materialised.
     */
    public ChunkRemap getRemap()
    {
        return ChunkRemap.mapping(getS2c());
    }
    public abstract ChunkPos s2cTransform(ChunkPos pos) throws ChunkUnusedException;
    public BlockPos s2cTransform(BlockPos pos) throws ChunkUnusedException
    {
        ChunkPos chunkPos=ChunkPos.containing(pos);
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return pos.offset(transformerChunkPos.getWorldPosition()).subtract(chunkPos.getWorldPosition());
    }
    public SectionPos s2cTransform(SectionPos pos) throws ChunkUnusedException
    {
        ChunkPos chunkPos=pos.chunk();
        ChunkPos transformerChunkPos=s2cTransform(chunkPos);
        return SectionPos.of(
                transformerChunkPos.x(),
                pos.getY(),
                transformerChunkPos.z());
    }
    public Vec3 s2cTransform(Vec3 pos) throws ChunkUnusedException
    {
        BlockPos blockPos= BlockPos.containing(pos);
        BlockPos transformedBlockPos=s2cTransform(blockPos);
        return pos.add(Vec3.atLowerCornerOf(transformedBlockPos)).subtract(Vec3.atLowerCornerOf(blockPos));
    }
    public abstract ChunkPos c2sTransform(ChunkPos pos);
    public BlockPos c2sTransform(BlockPos pos) throws ChunkUnusedException
    {
        ChunkPos chunkPos=ChunkPos.containing(pos);
        ChunkPos transformerChunkPos=requireMapped(c2sTransform(chunkPos));
        return pos.offset(transformerChunkPos.getWorldPosition()).subtract(chunkPos.getWorldPosition());
    }
    public Vec3 c2sTransform(Vec3 pos) throws ChunkUnusedException
    {
        BlockPos blockPos= BlockPos.containing(pos);
        BlockPos transformedBlockPos=c2sTransform(blockPos);
        return pos.add(Vec3.atLowerCornerOf(transformedBlockPos)).subtract(Vec3.atLowerCornerOf(blockPos));
    }
    public SectionPos c2sTransform(SectionPos pos) throws ChunkUnusedException
    {
        ChunkPos chunkPos=pos.chunk();
        ChunkPos transformerChunkPos=requireMapped(c2sTransform(chunkPos));
        return SectionPos.of(
                transformerChunkPos.x(),
                pos.getY(),
                transformerChunkPos.z());
    }
    /**
     * Turns "this page is not in the player's world" into an exception, rather than a null that the callers
     * would dereference.
     *
     * <p>{@link #c2sTransform} returns null for a page the player cannot see, and the derived overloads
     * above all dereference its result. They turn that null into {@link ChunkUnusedException} here so the
     * failure is an ordinary, already-handled condition at the call site: the outbound path discards the
     * packet, and movement reports no displacement. Nothing downstream has to know about null at all.
     */
    protected static ChunkPos requireMapped(ChunkPos mapped) throws ChunkUnusedException
    {
        if(mapped==null)throw new ChunkUnusedException();
        return mapped;
    }
}
