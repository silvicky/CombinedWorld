package io.silvicky.item.command.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import io.silvicky.item.StateSaver;
import io.silvicky.item.command.suggestion.WorldSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static io.silvicky.item.command.world.DeleteWorld.notifyEvacuation;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
public class ExportWorld {
    private static Identifier id;
    private static boolean firstType=true;
    private static StateSaver stateSaver;
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("exportworld")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.OWNERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION_ID, DimensionArgument.dimension())
                                .suggests(new WorldSuggestionProvider())
                                .executes(context -> exportWorld(context.getSource(), DimensionArgument.getDimension(context, DIMENSION_ID)))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /exportworld <id>"),false);
        source.sendSuccess(()-> Component.literal("Export world of <id> into /exported."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int exportWorld(CommandSourceStack source, ServerLevel world)
    {
        if(firstType)
        {
            firstType=false;
            source.sendSuccess(()-> Component.literal("Hello, admin! This command can export a world. It is still strongly suggested that you backup your save first. Also you need to read the result carefully. Type this command without arguments to see the help. Type this command again if you already understand what you are doing."),false);
            return Command.SINGLE_SUCCESS;
        }
        id=getDimensionId(world);
        final boolean isSinglet= !id.getPath().endsWith(OVERWORLD);
        Identifier idNether=null;
        Identifier idEnd=null;
        if(!isSinglet)
        {
            String tmp1 = id.getPath().substring(0, id.getPath().length() - OVERWORLD.length());
            idNether = Identifier.fromNamespaceAndPath(id.getNamespace(), tmp1 + NETHER);
            idEnd = Identifier.fromNamespaceAndPath(id.getNamespace(), tmp1 + END);
        }
        MinecraftServer server=source.getServer();
        if(notifyEvacuation(source,id))return Command.SINGLE_SUCCESS;
        //TODO
        return Command.SINGLE_SUCCESS;
    }
}
