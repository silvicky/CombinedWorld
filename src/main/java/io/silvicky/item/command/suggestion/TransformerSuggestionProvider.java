package io.silvicky.item.command.suggestion;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.commands.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

public class TransformerSuggestionProvider implements SuggestionProvider<CommandSourceStack>
{
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> commandContext, SuggestionsBuilder suggestionsBuilder)
    {
        for (String s : VecTransformer.registry.keySet()) suggestionsBuilder.suggest(s);
        return suggestionsBuilder.buildFuture();
    }
}
