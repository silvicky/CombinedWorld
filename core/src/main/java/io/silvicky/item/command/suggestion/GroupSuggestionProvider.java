package io.silvicky.item.command.suggestion;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GroupSuggestionProvider implements SuggestionProvider<CommandSourceStack>
{
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> commandContext, SuggestionsBuilder suggestionsBuilder)
    {
        Set<String> groups = new HashSet<>();
        for (ServerLevel world : commandContext.getSource().getServer().getAllLevels())
        {
            groups.add(world.dimension().identifier().getNamespace());
        }
        for (String s : groups) suggestionsBuilder.suggest(s);
        return suggestionsBuilder.buildFuture();
    }
}
