package io.silvicky.item.command.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import io.silvicky.item.StateSaver;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.command.world.ImportWorld.*;
import static io.silvicky.item.command.list.ListWorldPlayers.getListOfPlayers;
import static io.silvicky.item.command.list.ListWorldPlayers.listToString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
public class DeleteWorld {
    private static Identifier id;
    private static boolean firstType=true;
    private static StateSaver stateSaver;
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("deleteworld")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION_ID, IdentifierArgumentType.identifier())
                                .executes(context -> deleteWorld(context.getSource(),IdentifierArgumentType.getIdentifier(context,DIMENSION_ID)))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /deleteworld <id>"),false);
        source.sendFeedback(()-> Text.literal("Delete a world."),false);
        source.sendFeedback(()-> Text.literal("If <id> is a member of a triplet, all the triplet would be deleted."),false);
        source.sendFeedback(()-> Text.literal("During the first stage, no actual change would be done until restart, when the dimension entries are deleted."),false);
        source.sendFeedback(()-> Text.literal("During the second stage, actual change would be done and no restart is needed."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int deleteWorld(ServerCommandSource source,Identifier idTmp)
    {
        if(firstType)
        {
            firstType=false;
            source.sendFeedback(()-> Text.literal("Hello, admin! This command can delete a world. The world will be lost forever(a long time). It is still strongly suggested that you backup your save first. Also you need to read the result carefully. Type this command without arguments to see the help. Type this command again if you already understand what you are doing."),false);
            return Command.SINGLE_SUCCESS;
        }
        id=Identifier.of(getDimensionId(idTmp.toString()));
        if(id.getNamespace().equals("minecraft"))
        {
            source.sendFeedback(()-> Text.literal("how dare you..."),false);
            return Command.SINGLE_SUCCESS;
        }
        final boolean isSinglet= !id.getPath().endsWith(OVERWORLD);
        Identifier idNether=null;
        Identifier idEnd=null;
        if(!isSinglet)
        {
            String tmp1 = id.getPath().substring(0, id.getPath().length() - OVERWORLD.length());
            idNether = Identifier.of(id.getNamespace(), tmp1 + NETHER);
            idEnd = Identifier.of(id.getNamespace(), tmp1 + END);
        }
        MinecraftServer server=source.getServer();
        List<String> players=getListOfPlayers(server, id.toString());
        if(!players.isEmpty())
        {
            source.sendFeedback(()-> Text.literal("Some players are still in that world:"),false);
            source.sendFeedback(()-> Text.literal(listToString(players)),false);
            source.sendFeedback(()-> Text.literal("Please evacuate them, otherwise undefined behavior might be observed."),false);
            return Command.SINGLE_SUCCESS;
        }
        stateSaver=StateSaver.getServerState(server);
        if(server.getWorld(RegistryKey.of(RegistryKeys.WORLD,id))!=null)
        {
            deletedDimensions.add(RegistryKey.of(RegistryKeys.DIMENSION,id));
            if(!isSinglet)
            {
                deletedDimensions.add(RegistryKey.of(RegistryKeys.DIMENSION,idNether));
                deletedDimensions.add(RegistryKey.of(RegistryKeys.DIMENSION,idEnd));
            }
            source.sendFeedback(()-> Text.literal("Dimension options deleted."),false);
            source.sendFeedback(()-> Text.literal("First stage is done. Restart the game and type the same command again to continue."),false);
            return Command.SINGLE_SUCCESS;
        }
        try
        {
            stateSaver.seed.remove(id);
            if (!isSinglet) {
                stateSaver.seed.remove(idNether);
                stateSaver.seed.remove(idEnd);
            }
            source.sendFeedback(() -> Text.literal("Seeds deleted."), false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendFeedback(()-> Text.literal("ERR: Failed to delete seed! Please manually delete them."),false);
        }
        try
        {
            if(!isSinglet)
            {
                if(stateSaver.dragonFight.remove(idEnd)!=null)
                {
                    source.sendFeedback(()-> Text.literal("Dragon fight deleted."),false);
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendFeedback(()-> Text.literal("ERR: Failed to delete dragon fight! Please manually delete them."),false);
        }
        try
        {
            HashSet<String> removedPlayers = new HashSet<>();
            Iterator<StateSaver.PositionInfo> it1 = stateSaver.posList.iterator();
            int cnt = 0;
            while (it1.hasNext()) {
                StateSaver.PositionInfo i = it1.next();
                if (i.dimension.equals(id.toString())) {
                    removedPlayers.add(i.player);
                    cnt++;
                    it1.remove();
                }
            }
            stateSaver.nbtList.removeIf(i -> removedPlayers.contains(i.player) && i.dimension.equals(id.getNamespace()));
            int finalCnt = cnt;
            source.sendFeedback(() -> Text.literal("Deleted " + finalCnt + " player data."), false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendFeedback(()-> Text.literal("ERR: Failed to delete player data! Please manually delete them."),false);
        }
        try
        {
            Path target = server.getSavePath(WorldSavePath.ROOT).resolve("dimensions").resolve(id.getNamespace());
            Path targetOverworld=target.resolve(id.getPath());
            deleteFolder(targetOverworld);
            if(!isSinglet)
            {
                Path targetNether=target.resolve(idNether.getPath());
                Path targetEnd=target.resolve(idEnd.getPath());
                deleteFolder(targetNether);
                deleteFolder(targetEnd);
            }
            target.toFile().delete();
            source.sendFeedback(()-> Text.literal("Deleted save files."),false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendFeedback(()-> Text.literal("ERR: Failed to delete save files! Please manually delete them."),false);
        }
        source.sendFeedback(()-> Text.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
