package io.silvicky.item.command.suggestion;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GroupSuggestionProvider implements SuggestionProvider<ServerCommandSource>
{
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> commandContext, SuggestionsBuilder suggestionsBuilder)
    {
        Set<String> groups = new HashSet<>();
        for (ServerWorld world : commandContext.getSource().getServer().getWorlds())
        {
            groups.add(world.getRegistryKey().getValue().getNamespace());
        }
        for (String s : groups) suggestionsBuilder.suggest(s);
        return suggestionsBuilder.buildFuture();
    }
}
