package io.silvicky.item.command.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import io.silvicky.item.StateSaver;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.command.world.DeleteWorld.notifyEvacuation;
import static io.silvicky.item.command.world.ImportWorld.*;
import static io.silvicky.item.command.list.ListWorldPlayers.getListOfPlayers;
import static io.silvicky.item.command.list.ListWorldPlayers.listToString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
public class ExportWorld {
    private static Identifier id;
    private static boolean firstType=true;
    private static StateSaver stateSaver;
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("exportworld")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION_ID, DimensionArgumentType.dimension())
                                .executes(context -> exportWorld(context.getSource(),DimensionArgumentType.getDimensionArgument(context,DIMENSION_ID)))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /exportworld <id>"),false);
        source.sendFeedback(()-> Text.literal("Export world of <id> into /exported."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int exportWorld(ServerCommandSource source, ServerWorld world)
    {
        if(firstType)
        {
            firstType=false;
            source.sendFeedback(()-> Text.literal("Hello, admin! This command can export a world. It is still strongly suggested that you backup your save first. Also you need to read the result carefully. Type this command without arguments to see the help. Type this command again if you already understand what you are doing."),false);
            return Command.SINGLE_SUCCESS;
        }
        id=Identifier.of(getDimensionId(world));
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
        if(notifyEvacuation(source,id))return Command.SINGLE_SUCCESS;
        return Command.SINGLE_SUCCESS;
    }
}
