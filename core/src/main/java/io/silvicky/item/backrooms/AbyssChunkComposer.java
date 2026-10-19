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
 * Builds the 192-tall chunk the client believes it is standing in, out of {@link AbyssWindow#WINDOW_LAYERS}
 * bands of a pattern.
 *
 * <h2>Why this is a packet and not a level</h2>
 *
 * <p>The window is per player: two players in different bands must be shown different compositions, and a
 * {@code ServerLevel} has one chunk per column shared by everyone. The only per-player place to assemble a
 * window is the packet.
 *
 * <h2>What is in the packet, and what that forces</h2>
 *
 * <p>The section count is <em>not</em> in the packet: the client reads exactly its own section count
 * ({@link AbyssWindow#WINDOW_SECTIONS}) and decodes that many sections from the buffer. So every band has to be
 * present, every band has to be exactly {@link AbyssWindow#SECTIONS_PER_BAND} sections, and a missing band is
 * padded with air rather than skipped.
 *
 * <p>Heightmaps are recomputed from the composed sections, because a heightmap's values are packed at a bit
 * width derived from the height; copying a band's heightmap into the window would be read with the wrong
 * stride. Light masks are {@link BitSet}s indexed relative to the level's minimum light section, so a band's
 * bits are copied and shifted by its slot - that shift is independent of {@code min_y} and of
 * {@code sourceY}, which is why a slice taken from the middle of a tall level needs no special handling.
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

    /** The window chunk for one column, or null when the player is not being shown a window. */
    @Nullable
    public static ClientboundLevelChunkWithLightPacket compose(ServerPlayer player, ChunkPos pos)
    {
        AbyssWindow.Window window = AbyssWindow.window(player);
        if (window == null) return null;
        MinecraftServer server = player.level().getServer();
        if (server == null) return null;

        LevelChunkSection[] sections = new LevelChunkSection[AbyssWindow.WINDOW_SECTIONS];
        LevelChunkSection air = new LevelChunkSection(((ServerLevel) player.level()).palettedContainerFactory());
        for (int i = 0; i < sections.length; i++) sections[i] = air;

        boolean missing = false;
        for (int slot = 0; slot < AbyssWindow.WINDOW_LAYERS; slot++)
        {
            AbyssPattern.Band band = window.pattern().band(window.windowStart() + slot);
            if (band == null) { missing = true; continue; }
            ServerLevel level = server.getLevel(band.level());
            if (level == null) { missing = true; continue; }
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null)
            {
                //The band is not loaded yet, so this column goes out with air there. Remember it: the client
                //keeps that air until something marks the chunk pending again, and nothing will, because the
                //player's own chunk has not moved.
                missing = true;
                continue;
            }
            LevelChunkSection[] source = chunk.getSections();
            int from = band.sourceY() / 16;
            for (int i = 0; i < AbyssWindow.SECTIONS_PER_BAND; i++)
            {
                int index = from + i;
                if (index < 0 || index >= source.length) break;
                sections[slot * AbyssWindow.SECTIONS_PER_BAND + i] = source[index];
            }
            //TODO block entities: chunk.getBlockEntities() would go into the list below, with y shifted into
            //the slot and by sourceY. BlockEntityInfo is package-private with a private constructor, so this
            //needs either an access widener for that class or a reflective copy.
        }
        if (missing) AbyssWindow.markIncomplete(player, pos);

        byte[] body = encodeSections(sections);
        ClientboundLevelChunkPacketData data = chunkData(heightmaps(sections), body);
        return new ClientboundLevelChunkWithLightPacket(pos.x(), pos.z(), data, light(server, window, pos));
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
     * <p>The stored value is {@code (highest opaque y + 1) - minY}, mirroring {@code Heightmap.update}, which
     * writes {@code y + 1} and subtracts the height accessor's minimum. Both are window-relative, so the
     * subtraction cancels against a section index that is also window-relative, and the value is correct for
     * any {@code min_y} the window uses.
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
                        //Air sections are the common case in a window, so skipping them whole is what keeps
                        //this from being a section visit per column at full height.
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
     * Light for the window, slot by slot.
     *
     * <p>{@code prepareSectionData} indexes light by {@code sectionY - minLightSection}, and
     * {@code minLightSection} is {@code minY/16 - 1} on every level, so a band's section index inside its own
     * light engine is already relative. Copying bit {@code i} of that band to {@code slot * SECTIONS_PER_BAND + i + 1}
     * is right whatever {@code min_y} and whatever {@code source_y} the band has - the constants cancel.
     */
    private static ClientboundLightUpdatePacketData light(MinecraftServer server, AbyssWindow.Window window, ChunkPos pos)
    {
        BitSet skyMask = new BitSet();
        BitSet blockMask = new BitSet();
        BitSet emptySkyMask = new BitSet();
        BitSet emptyBlockMask = new BitSet();
        List<byte[]> skyUpdates = new ArrayList<>();
        List<byte[]> blockUpdates = new ArrayList<>();

        for (int slot = 0; slot < AbyssWindow.WINDOW_LAYERS; slot++)
        {
            AbyssPattern.Band band = window.pattern().band(window.windowStart() + slot);
            if (band == null) continue;
            ServerLevel level = server.getLevel(band.level());
            if (level == null) continue;
            int base = slot * AbyssWindow.SECTIONS_PER_BAND;
            int from = band.sourceY() / 16;
            for (int local = 0; local < AbyssWindow.SECTIONS_PER_BAND; local++)
            {
                //+1 because light index 0 is the section below the level's minimum Y.
                int index = base + local + 1;
                collect(level, pos, from + local, LightLayer.SKY, index, skyMask, emptySkyMask, skyUpdates);
                collect(level, pos, from + local, LightLayer.BLOCK, index, blockMask, emptyBlockMask, blockUpdates);
            }
        }
        return new ClientboundLightUpdatePacketData(skyMask, blockMask, emptySkyMask, emptyBlockMask,
                                                   skyUpdates, blockUpdates);
    }

    private static void collect(ServerLevel level, ChunkPos pos, int sectionY, LightLayer layer,
                                int index, BitSet mask, BitSet emptyMask, List<byte[]> updates)
    {
        DataLayer data = level.getLightEngine().getLayerListener(layer)
                              .getDataLayerData(SectionPos.of(pos.x(), sectionY, pos.z()));
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
     * <p>For standalone {@code ClientboundLightUpdatePacket}s, which a band's own light engine emits. Their
     * masks are band-relative and would otherwise land in the wrong slot.
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
