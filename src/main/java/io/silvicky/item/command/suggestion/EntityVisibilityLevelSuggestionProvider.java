package io.silvicky.item.command.suggestion;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.silvicky.item.backrooms.EntityVisibilityLevel;
import net.minecraft.server.command.ServerCommandSource;

import java.util.concurrent.CompletableFuture;

public class EntityVisibilityLevelSuggestionProvider implements SuggestionProvider<ServerCommandSource>
{
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> commandContext, SuggestionsBuilder suggestionsBuilder)
    {
        for(EntityVisibilityLevel level:EntityVisibilityLevel.values())suggestionsBuilder.suggest(level.name().toLowerCase());
        return suggestionsBuilder.buildFuture();
    }
}
