package io.silvicky.item.command.suggestion;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static io.silvicky.item.common.Util.getDimensionId;

public class WorldSuggestionProvider implements SuggestionProvider<CommandSourceStack>
{
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> commandContext, SuggestionsBuilder suggestionsBuilder)
    {
        Set<Identifier> groups=new HashSet<>();
        for(ServerLevel world:commandContext.getSource().getServer().getAllLevels())
        {
            groups.add(getDimensionId(world));
        }
        for(Identifier id:groups)suggestionsBuilder.suggest(id.toString());
        return suggestionsBuilder.buildFuture();
    }
}
