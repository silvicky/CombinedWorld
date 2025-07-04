package io.silvicky.item.common;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.datafixer.Schemas;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.ReadView;
import net.minecraft.text.Text;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.file.Files.copy;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Util
{

    public static final String DIMENSION_ID="dimension_id";
    public static final String DIMENSION_PATH="path";
    public static final String DATA="data";
    public static final String POI="poi";
    public static final String REGION="region";
    public static final String ENTITIES="entities";
    public static final String INVENTORY="inventory";
    public static final String SAVED="saved";
    public static final String ENDER="ender";
    public static final String SLOT="Slot";
    public static final String LEVEL="level";
    public static final String REASON="reason";
    public static final String DIMENSION="dimension";
    public static final String PLAYER="player";
    public static final String OVERWORLD="overworld";
    public static final String NETHER="the_nether";
    public static final String END="the_end";
    public static final String CORD="coordination";
    public static final String TARGET="target";
    public static final String MOD_ID = "ItemStorage";
    public static final Text INVENTORY_ITEMS=Text.literal("Inventory Items");
    public static final Text ENDER_ITEMS=Text.literal("Ender Items");
    public static final Logger LOGGER = LoggerFactory.getLogger("item-storage");
    public static final int playerInventorySize=41;
    public static final int chestSize=27;
    public static final SimpleCommandExceptionType ERR_DIMENSION_NOT_FOUND=new SimpleCommandExceptionType(new LiteralMessage("Target dimension NOT FOUND!"));
    public static final SimpleCommandExceptionType ERR_ITEM=new SimpleCommandExceptionType(new LiteralMessage("Item stack error(from version change, contact your admin)!"));
    public static final SimpleCommandExceptionType ERR_NOT_BY_PLAYER=new SimpleCommandExceptionType(new LiteralMessage("This command must be executed by a player."));
    public static final SimpleCommandExceptionType ERR_NOT_ONE_PLAYER=new SimpleCommandExceptionType(new LiteralMessage("Amount of player selected must be exactly one."));
    public static final DynamicCommandExceptionType ERR_WARP_FORBIDDEN=new DynamicCommandExceptionType(new Function<>() {
        /**
         * Applies this function to the given argument.
         *
         * @param o the function argument
         * @return the function result
         */
        @Override
        public Message apply(Object o) {
            return new LiteralMessage("Warp forbidden! Reason: "+o);
        }
    });
    public static final SimpleCommandExceptionType ERR_DIMENSION_EXIST=new SimpleCommandExceptionType(new LiteralMessage("A dimension with such ID already exists!"));
    public static final SimpleCommandExceptionType ERR_NAMESPACE_EXIST=new SimpleCommandExceptionType(new LiteralMessage("Currently we only accept new namespaces, otherwise collision happens."));
    public static final SimpleCommandExceptionType ERR_FOLDER_NOT_EXIST=new SimpleCommandExceptionType(new LiteralMessage("A folder with that path does not exist!"));
    public static final SimpleCommandExceptionType ERR_LEVEL_NOT_EXIST=new SimpleCommandExceptionType(new LiteralMessage("No level.dat was found!"));
    public static final SimpleCommandExceptionType ERR_FAIL_TO_READ_LEVEL=new SimpleCommandExceptionType(new LiteralMessage("Failed to read level.dat!"));
    public static final SimpleCommandExceptionType ERR_WORLD_GEN=new SimpleCommandExceptionType(new LiteralMessage("Failed to fetch WorldGenSettings!"));
    public static final SimpleCommandExceptionType ERR_PLAYER=new SimpleCommandExceptionType(new LiteralMessage("Failed to fetch player data!"));
    public static final SimpleCommandExceptionType ERR_SAVE=new SimpleCommandExceptionType(new LiteralMessage("Failed to copy save files!"));

    public static void deleteFolder(Path path) throws IOException
    {
        if(!path.toFile().exists())return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void copyFolder(Path src, Path dest) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> {
                try {
                    copy(source, dest.resolve(src.relativize(source)),REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public static ServerPlayerEntity loadFakePlayer(NbtCompound compound, MinecraftServer server)
    {
        ConnectedClientData connectedClientData = ConnectedClientData.createDefault(new GameProfile(compound.get("UUID", Uuids.INT_STREAM_CODEC).orElseThrow(), "tmp"), false);
        ServerWorld world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(compound.getString("Dimension",server.getOverworld().getRegistryKey().getValue().toString()))));
        if(world==null)world=server.getOverworld();
        ServerPlayerEntity serverPlayerEntity = new ServerPlayerEntity(
                server, world, connectedClientData.gameProfile(), connectedClientData.syncedOptions()
        );
        ErrorReporter.Logging logging = new ErrorReporter.Logging(serverPlayerEntity.getErrorReporterContext(), LOGGER);
        ReadView readView = NbtReadView.create(logging, serverPlayerEntity.getRegistryManager(), compound);
        serverPlayerEntity.readData(readView);
        serverPlayerEntity.readGameModeData(readView);
        return serverPlayerEntity;
    }

    public static ServerPlayerEntity loadFakePlayer(Path path, MinecraftServer server) throws IOException
    {
        NbtCompound nbtCompound= NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        nbtCompound= DataFixTypes.PLAYER.update(Schemas.getFixer(),nbtCompound, NbtHelper.getDataVersion(nbtCompound,-1));
        return loadFakePlayer(nbtCompound,server);
    }

    public static String getDimensionId(ServerWorld world)
    {
        return getDimensionId(world.getRegistryKey().getValue().toString());
    }

    public static String getDimensionId(String id)
    {
        if(id.endsWith(NETHER))id=id.substring(0,id.length()- NETHER.length())+ OVERWORLD;
        if(id.endsWith(END))id=id.substring(0,id.length()- END.length())+ OVERWORLD;
        return id;
    }

    public static BlockPos transLoc(BlockPos sp, ServerWorld sw)
    {
        while((!sw.getBlockState(sp).isAir())||(!sw.getBlockState(sp.up()).isAir()))sp=sp.down();
        while(sw.getBlockState(sp.down()).isAir()&&sp.getY()>sw.getBottomY())sp=sp.down();
        if(sp.getY()==sw.getBottomY())
        {
            sp=sp.withY(sw.getLogicalHeight());
            LOGGER.warn("Spawn point not found!");
        }
        return sp;
    }

    public static List<String> getListOfPlayers(MinecraftServer server, String dimension)
    {
        List<ServerPlayerEntity> players=server.getPlayerManager().getPlayerList();
        ArrayList<String> ret=new ArrayList<>();
        for(ServerPlayerEntity player:players)
        {
            if(getDimensionId(player.getWorld()).equals(dimension))
            {
                ret.add(player.getName().getString());
            }
        }
        return ret;
    }

    public static <T> String listToString(List<T> list)
    {
        StringBuilder tot= new StringBuilder();
        boolean first=true;
        for(T t:list)
        {
            if(!first)tot.append(", ");
            tot.append(t.toString());
        }
        return tot.toString();
    }

    public static ArrayList<Pair<ItemStack,Byte>> inventoryToStack(PlayerInventory inventory)
    {
        ArrayList<Pair<ItemStack,Byte>> ret=new ArrayList<>();
        for (int i = 0; i < playerInventorySize; i++) {
            if (!inventory.getStack(i).isEmpty()) {
                ret.add(new Pair<>(inventory.getStack(i),(byte)i));
            }
        }
        return ret;
    }

    public static ArrayList<Pair<ItemStack,Byte>> enderToStack(EnderChestInventory inventory)
    {
        ArrayList<Pair<ItemStack,Byte>> ret=new ArrayList<>();
        for(int i = 0; i < inventory.size(); ++i) {
            ItemStack itemStack = inventory.getStack(i);
            if (!itemStack.isEmpty()) {
                ret.add(new Pair<>(itemStack,(byte) i));
            }
        }
        return ret;
    }

    public static void stackToInventory(PlayerInventory inventory, List<Pair<ItemStack,Byte>> stack)
    {
        inventory.clear();

        for (Pair<ItemStack, Byte> pair : stack) {
            int j = pair.getSecond();
            ItemStack itemStack = pair.getFirst();
            if (j < playerInventorySize) {
                inventory.setStack(j, itemStack);
            }
        }
    }

    public static void stackToEnder(EnderChestInventory inventory, List<Pair<ItemStack,Byte>> stack)
    {
        inventory.clear();

        for (Pair<ItemStack, Byte> pair : stack) {
            int j = pair.getSecond();
            if (j < inventory.size()) {
                inventory.setStack(j, pair.getFirst());
            }
        }
    }

    public static ServerWorld toOverworld(MinecraftServer server, ServerWorld world)
    {
        String overworldId= getDimensionId(world);
        ServerWorld sw=server.getWorld(RegistryKey.of(RegistryKey.ofRegistry(world.getRegistryKey().getRegistry()),
                Identifier.of(world.getRegistryKey().getValue().getNamespace(),
                        overworldId.substring(overworldId.indexOf(":")+1))));
        return (sw!=null?sw:world);
    }
    public static ArrayList<Pair<ItemStack,Byte>> enId(List<ItemStack> source)
    {
        ArrayList<Pair<ItemStack,Byte>> ret=new ArrayList<>();
        for(byte i=0;i<source.size();i++)
        {
            ret.add(new Pair<>(source.get(i),i));
        }
        return ret;
    }
    public static ArrayList<ItemStack> deId(List<Pair<ItemStack,Byte>> source)
    {
        ArrayList<ItemStack> ret=new ArrayList<>();
        for(Pair<ItemStack,Byte> i:source)ret.add(i.getFirst());
        return ret;
    }
    public static ItemStack packMono(List<ItemStack> source, Text name)
    {
        ItemStack ret=new ItemStack(Items.CHEST);
        ret.applyChanges(ComponentChanges.builder().add(Component.of(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(source))).build());
        ret.applyChanges(ComponentChanges.builder().add(Component.of(DataComponentTypes.CUSTOM_NAME, name)).build());
        return ret;
    }
    public static ArrayList<ItemStack> pack(List<ItemStack> source, Text name)
    {
        ArrayList<ItemStack> ret=new ArrayList<>();
        for(byte bas=0;bas<source.size();bas+=chestSize)
        {
            ArrayList<ItemStack> tmp=new ArrayList<>();
            for(byte i=bas;i<bas+chestSize&&i<source.size();i++)
            {
                tmp.add(source.get(i));
            }
            ret.add(packMono(tmp,name));
        }
        return ret;
    }
    public static void sendToAllInWorld(ServerWorld world, Packet<?> packet)
    {
        for(ServerPlayerEntity player:world.getPlayers())
        {
            player.networkHandler.sendPacket(packet);
        }
    }
}
