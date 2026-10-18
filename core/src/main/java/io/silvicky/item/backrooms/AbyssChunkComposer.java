package io.silvicky.item.backrooms;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the 3072-tall chunk the client believes it is standing in, out of three 1024-tall layers.
 *
 * <h2>Why this is a packet and not a level</h2>
 *
 * <p>The window has to be per player: two players on different layers must be shown different bands. A
 * {@code ServerLevel} has one chunk per column shared by everyone, so a shared level cannot hold two windows.
 * The only place a window can be per player is the packet, which is where this class works - the same place
 * {@code ClientboundLevelChunkWithLightPacket}'s x/z are already rewritten for the Backrooms shuffle.
 *
 * <h2>What is in the packet, and what that forces</h2>
 *
 * <p>The section count is <em>not</em> in the packet. The client reads exactly its own
 * {@code ClientLevel} section count ({@link AbyssWindow#WINDOW_SECTIONS}) and decodes that many sections from
 * the buffer. So every band has to be present, every band has to be exactly
 * {@link AbyssWindow#SECTIONS_PER_LAYER} sections, and a missing layer is padded with air rather than
 * skipped.
 *
 * <p>Heightmaps are recomputed from the composed sections, because a heightmap's values are packed at a bit
 * width derived from the height. Copying a layer's heightmap into a taller window would be read with the
 * wrong stride. Light is different: its masks are {@link BitSet}s, so a layer's bits are copied and shifted by
 * the band's section offset instead.
 *
 * <p><b>Known gap, deliberately left in this demo:</b> block entities (chests, signs, spawners) are not
 * composed. Their packet record is package-private with a private constructor, and this demo's layers contain
 * none. See {@link #compose} for where they would go.
 */
public final class AbyssChunkComposer
{
    /** {@code ClientboundLevelChunkPacketData(Map<Heightmap.Types, long[]>, byte[], List<BlockEntityInfo>)}. */
    private static final Constructor<?> CHUNK_DATA;

    static
    {
        try
        {
            CHUNK_DATA = ClientboundLevelChunkPacketData.class.getDeclaredConstructor(Map.class, byte[].class, List.class);
            CHUNK_DATA.setAccessible(true);
        }
        catch (ReflectiveOperationException e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    private AbyssChunkComposer() {}

    /**
     * The window chunk for one column, or null when the player is not in a windowed layer.
     */
    @Nullable
    public static ClientboundLevelChunkWithLightPacket compose(ServerPlayer player, ChunkPos pos)
    {
        if (!AbyssWindow.handles(player)) return null;
        MinecraftServer server = player.level().getServer();
        int anchor = AbyssWindow.anchor(player);

        LevelChunkSection[] sections = new LevelChunkSection[AbyssWindow.WINDOW_SECTIONS];
        LevelChunkSection air = new LevelChunkSection(((ServerLevel) player.level()).palettedContainerFactory());
        for (int i = 0; i < sections.length; i++) sections[i] = air;

        boolean missing = false;
        for (int band = 0; band < AbyssWindow.WINDOW_LAYERS; band++)
        {
            ServerLevel level = AbyssWindow.layer(server, anchor + band);
            if (level == null) { missing = true; continue; }
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null)
            {
                //The band is not generating yet, so this column goes out with air there. Remember it: the
                //client will keep that air until something marks the chunk pending again, and nothing will,
                //because the player's own chunk has not moved.
                missing = true;
                continue;
            }
            LevelChunkSection[] source = chunk.getSections();
            System.arraycopy(source, 0, sections, band * AbyssWindow.SECTIONS_PER_LAYER,
                             Math.min(source.length, AbyssWindow.SECTIONS_PER_LAYER));
            //TODO block entities: chunk.getBlockEntities() would go into the list below, with y shifted by
            //band * LAYER_HEIGHT. BlockEntityInfo is package-private with a private constructor, so this
            //needs either an access widener for that class or a reflective copy.
        }
        //Only ever adds. A complete column is removed from the set by the sweep in AbyssWindow, which is also
        //the only place that knows the column has since become complete.
        if (missing) AbyssWindow.markIncomplete(player, pos);

        byte[] body = encodeSections(sections);
        ClientboundLevelChunkPacketData data = chunkData(heightmaps(sections), body);
        ClientboundLightUpdatePacketData light = light(server, pos, anchor);
        return new ClientboundLevelChunkWithLightPacket(pos.x(), pos.z(), data, light);
    }

    /** Exactly {@code sum(section.getSerializedSize())} bytes, one section after another. */
    private static byte[] encodeSections(LevelChunkSection[] sections)
    {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(estimate(sections)));
        try
        {
            for (LevelChunkSection section : sections) section.write(buffer);
            return ByteBufUtil.getBytes(buffer);
        }
        finally
        {
            buffer.release();
        }
    }

    private static int estimate(LevelChunkSection[] sections)
    {
        int size = 16;
        for (LevelChunkSection section : sections) size += section.getSerializedSize();
        return size;
    }

    /**
     * Recomputes every heightmap the client is sent.
     *
     * <p>The stored value is {@code (highest opaque y + 1) - minY}, this mirroring {@code Heightmap.update},
     * which writes {@code y + 1} and subtracts the height accessor's minimum. Both are window-relative, so the
     * subtraction cancels against a section index that is also window-relative: the value written here is
     * correct for any {@code min_y} the window happens to use.
     */
    private static Map<Heightmap.Types, long[]> heightmaps(LevelChunkSection[] sections)
    {
        int bits = Mth.ceillog2(AbyssWindow.WINDOW_HEIGHT + 1);
        Map<Heightmap.Types, long[]> result = new EnumMap<>(Heightmap.Types.class);
        for (Heightmap.Types type : Heightmap.Types.values())
        {
            if (!type.sendToClient()) continue;
            SimpleBitStorage storage = new SimpleBitStorage(bits, 256);
            for (int x = 0; x < 16; x++)
            {
                for (int z = 0; z < 16; z++)
                {
                    int height = 0;
                    scan:
                    for (int sectionIndex = sections.length - 1; sectionIndex >= 0; sectionIndex--)
                    {
                        LevelChunkSection section = sections[sectionIndex];
                        //Air sections are the common case in a 3072-tall window, so skipping them whole is
                        //what keeps this from being 192 section visits per column.
                        if (section.hasOnlyAir()) continue;
                        for (int y = 15; y >= 0; y--)
                        {
                            if (type.isOpaque().test(section.getBlockState(x, y, z)))
                            {
                                height = sectionIndex * 16 + y + 1;
                                break scan;
                            }
                        }
                    }
                    storage.set(z * 16 + x, height);
                }
            }
            result.put(type, storage.getRaw());
        }
        return result;
    }

    /**
     * Light for the window, band by band.
     *
     * <p>{@code prepareSectionData} indexes light by {@code sectionY - minLightSection}, and
     * {@code minLightSection} is {@code minY/16 - 1} on every level, so the band's first section lands on
     * index {@code band * SECTIONS_PER_LAYER + 1} whatever the two levels' {@code min_y} values are - the
     * constant cancels. Masks are copied bit by bit in ascending order, which is the order the stream codec
     * expects the data arrays in.
     */
    private static ClientboundLightUpdatePacketData light(MinecraftServer server, ChunkPos pos, int anchor)
    {
        BitSet skyMask = new BitSet();
        BitSet blockMask = new BitSet();
        BitSet emptySkyMask = new BitSet();
        BitSet emptyBlockMask = new BitSet();
        List<byte[]> skyUpdates = new ArrayList<>();
        List<byte[]> blockUpdates = new ArrayList<>();

        for (int band = 0; band < AbyssWindow.WINDOW_LAYERS; band++)
        {
            ServerLevel level = AbyssWindow.layer(server, anchor + band);
            if (level == null) continue;
            int base = band * AbyssWindow.SECTIONS_PER_LAYER;
            for (int local = 0; local < AbyssWindow.SECTIONS_PER_LAYER; local++)
            {
                //+1 because light index 0 is the section below minY.
                int index = base + local + 1;
                collect(level, pos, local, LightLayer.SKY, index, skyMask, emptySkyMask, skyUpdates);
                collect(level, pos, local, LightLayer.BLOCK, index, blockMask, emptyBlockMask, blockUpdates);
            }
        }
        return new ClientboundLightUpdatePacketData(skyMask, blockMask, emptySkyMask, emptyBlockMask,
                                                   skyUpdates, blockUpdates);
    }

    private static void collect(ServerLevel level, ChunkPos pos, int localSection, LightLayer layer,
                                int index, BitSet mask, BitSet emptyMask, List<byte[]> updates)
    {
        DataLayer data = level.getLightEngine().getLayerListener(layer)
                              .getDataLayerData(SectionPos.of(pos.x(), localSection, pos.z()));
        if (data == null || data.isEmpty())
        {
            emptyMask.set(index);
            return;
        }
        mask.set(index);
        updates.add(data.copy().getData());
    }

    private static ClientboundLevelChunkPacketData chunkData(Map<Heightmap.Types, long[]> heightmaps, byte[] body)
    {
        try
        {
            return (ClientboundLevelChunkPacketData) CHUNK_DATA.newInstance(heightmaps, body, List.of());
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("cannot build chunk data", e);
        }
    }

    /**
     * The same light payload, moved up by {@code sectionShift} sections.
     *
     * <p>For standalone {@code ClientboundLightUpdatePacket}s, which the layer's own light engine emits. Their
     * masks are layer-relative and would otherwise land 1024 or 2048 blocks too low.
     */
    public static ClientboundLightUpdatePacketData shift(ClientboundLightUpdatePacketData data, int sectionShift)
    {
        if (sectionShift == 0) return data;
        return new ClientboundLightUpdatePacketData(
                shiftMask(data.skyYMask(), sectionShift),
                shiftMask(data.blockYMask(), sectionShift),
                shiftMask(data.emptySkyYMask(), sectionShift),
                shiftMask(data.emptyBlockYMask(), sectionShift),
                data.skyUpdates(),
                data.blockUpdates());
    }

    private static BitSet shiftMask(BitSet mask, int shift)
    {
        BitSet result = new BitSet();
        for (int bit = mask.nextSetBit(0); bit >= 0; bit = mask.nextSetBit(bit + 1))
            result.set(bit + shift);
        return result;
    }
}
