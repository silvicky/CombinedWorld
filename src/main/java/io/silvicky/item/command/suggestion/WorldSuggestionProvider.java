package io.silvicky.item.command.suggestion;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static io.silvicky.item.common.Util.getDimensionId;

public class WorldSuggestionProvider implements SuggestionProvider<ServerCommandSource>
{
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> commandContext, SuggestionsBuilder suggestionsBuilder)
    {
        Set<Identifier> groups=new HashSet<>();
        for(ServerWorld world:commandContext.getSource().getServer().getWorlds())
        {
            groups.add(getDimensionId(world));
        }
        for(Identifier id:groups)suggestionsBuilder.suggest(id.toString());
        return suggestionsBuilder.buildFuture();
    }
}
