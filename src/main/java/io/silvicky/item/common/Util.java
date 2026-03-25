package io.silvicky.item.common;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import io.silvicky.item.StateSaver;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.resources.Identifier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.NonNull;
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

import static io.silvicky.item.InventoryManager.loadInventory;
import static io.silvicky.item.InventoryManager.saveInventory;
import static io.silvicky.item.cfg.JSONConfig.useStorage;
import static java.nio.file.Files.copy;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Util
{

    public static final String DIMENSION_ID="dimension_id";
    public static final String DIMENSION_PATH="path";
    public static final String INVENTORY="inventory";
    public static final String SAVED="saved";
    public static final String SAVED_MAP="saved_map";
    public static final String ENDER="ender";
    public static final String SLOT="Slot";
    public static final String LEVEL="level";
    public static final String REASON="reason";
    public static final String DIMENSION="dimension";
    public static final String NAMESPACE="namespace";
    public static final String PLAYER="player";
    public static final String OVERWORLD="overworld";
    public static final String NETHER="the_nether";
    public static final String END="the_end";
    public static final String CORD="coordination";
    public static final String TARGET="target";
    public static final String MOD_ID = "item";
    public static final Component INVENTORY_ITEMS= Component.literal("Inventory Items");
    public static final Component ENDER_ITEMS= Component.literal("Ender Items");
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
            public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NonNull FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc) throws IOException {
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

    public static ServerPlayer loadFakePlayer(CompoundTag compound, MinecraftServer server)
    {
        CommonListenerCookie connectedClientData = CommonListenerCookie.createInitial(new GameProfile(compound.read("UUID", UUIDUtil.CODEC).orElseThrow(), "tmp"), false);
        ServerLevel world=server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(compound.getStringOr("Dimension",server.overworld().dimension().identifier().toString()))));
        if(world==null)world=server.overworld();
        ServerPlayer serverPlayerEntity = new ServerPlayer(
                server, world, connectedClientData.gameProfile(), connectedClientData.clientInformation()
        );
        ProblemReporter.ScopedCollector logging = new ProblemReporter.ScopedCollector(serverPlayerEntity.problemPath(), LOGGER);
        ValueInput readView = TagValueInput.create(logging, serverPlayerEntity.registryAccess(), compound);
        serverPlayerEntity.load(readView);
        return serverPlayerEntity;
    }

    public static ServerPlayer loadFakePlayer(Path path, MinecraftServer server) throws IOException
    {
        CompoundTag nbtCompound= NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        nbtCompound= DataFixTypes.PLAYER.updateToCurrentVersion(DataFixers.getDataFixer(),nbtCompound, NbtUtils.getDataVersion(nbtCompound,-1));
        return loadFakePlayer(nbtCompound,server);
    }

    public static Identifier getDimensionId(ServerLevel world)
    {
        return getDimensionId(world.dimension().identifier());
    }

    public static String getDimensionId(String id)
    {
        if(id.endsWith(NETHER))id=id.substring(0,id.length()- NETHER.length())+ OVERWORLD;
        if(id.endsWith(END))id=id.substring(0,id.length()- END.length())+ OVERWORLD;
        return id;
    }
    public static Identifier getDimensionId(Identifier id)
    {
        return Identifier.fromNamespaceAndPath(id.getNamespace(),getDimensionId(id.getPath()));
    }
    public static List<String> getListOfPlayers(MinecraftServer server, Identifier dimension)
    {
        List<ServerPlayer> players=server.getPlayerList().getPlayers();
        ArrayList<String> ret=new ArrayList<>();
        for(ServerPlayer player:players)
        {
            if(getDimensionId(player.level()).equals(dimension))
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
            first=false;
            tot.append(t.toString());
        }
        return tot.toString();
    }

    public static ArrayList<Pair<ItemStack,Byte>> inventoryToStack(Inventory inventory)
    {
        ArrayList<Pair<ItemStack,Byte>> ret=new ArrayList<>();
        for (int i = 0; i < playerInventorySize; i++) {
            if (!inventory.getItem(i).isEmpty()) {
                ret.add(new Pair<>(inventory.getItem(i),(byte)i));
            }
        }
        return ret;
    }

    public static ArrayList<Pair<ItemStack,Byte>> enderToStack(PlayerEnderChestContainer inventory)
    {
        ArrayList<Pair<ItemStack,Byte>> ret=new ArrayList<>();
        for(int i = 0; i < inventory.getContainerSize(); ++i) {
            ItemStack itemStack = inventory.getItem(i);
            if (!itemStack.isEmpty()) {
                ret.add(new Pair<>(itemStack,(byte) i));
            }
        }
        return ret;
    }

    public static void stackToInventory(Inventory inventory, List<Pair<ItemStack,Byte>> stack)
    {
        inventory.clearContent();

        for (Pair<ItemStack, Byte> pair : stack) {
            int j = pair.getSecond();
            ItemStack itemStack = pair.getFirst();
            if (j < playerInventorySize) {
                inventory.setItem(j, itemStack);
            }
        }
    }

    public static void stackToEnder(PlayerEnderChestContainer inventory, List<Pair<ItemStack,Byte>> stack)
    {
        inventory.clearContent();

        for (Pair<ItemStack, Byte> pair : stack) {
            int j = pair.getSecond();
            if (j < inventory.getContainerSize()) {
                inventory.setItem(j, pair.getFirst());
            }
        }
    }

    public static ServerLevel toOverworld(MinecraftServer server, ServerLevel world)
    {
        ServerLevel sw=server.getLevel(ResourceKey.create(Registries.DIMENSION,getDimensionId(world)));
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
    public static ItemStack packMono(List<ItemStack> source, Component name)
    {
        ItemStack ret=new ItemStack(Items.CHEST);
        ret.applyComponentsAndValidate(DataComponentPatch.builder().set(TypedDataComponent.createUnchecked(DataComponents.CONTAINER, ItemContainerContents.fromItems(source))).build());
        ret.applyComponentsAndValidate(DataComponentPatch.builder().set(TypedDataComponent.createUnchecked(DataComponents.CUSTOM_NAME, name)).build());
        return ret;
    }
    public static ArrayList<ItemStack> pack(List<ItemStack> source, Component name)
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
    public static void fakeTeleportTo(ServerPlayer player, TeleportTransition teleportTarget, StateSaver stateSaver)
    {
        Identifier source=player.level().dimension().identifier();
        Identifier target=teleportTarget.newLevel().dimension().identifier();
        boolean groupChanged = !source.getNamespace().equals(target.getNamespace());
        if(groupChanged)
        {
            if(useStorage)saveInventory(player, stateSaver);
        }
        player.setServerLevel(teleportTarget.newLevel());
        player.setPos(teleportTarget.position());
        player.setDeltaMovement(teleportTarget.deltaMovement());
        player.setYRot(teleportTarget.yRot());
        player.setXRot(teleportTarget.xRot());
        if(groupChanged)
        {
            if(useStorage)
            {
                try
                {
                    loadInventory(player, teleportTarget.newLevel(), stateSaver);
                }
                catch(Exception e)
                {
                    //TODO Any way to rollback?
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
