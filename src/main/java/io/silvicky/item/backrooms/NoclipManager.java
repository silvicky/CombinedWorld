package io.silvicky.item.backrooms;

import io.silvicky.item.StateSaver;
import io.silvicky.item.common.WeightedSelector;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.silvicky.item.InventoryManager.loadPos;
import static io.silvicky.item.InventoryManager.removePos;
import static io.silvicky.item.StateSaver.server;

public class NoclipManager
{
    private static final Map<UUID,Boolean> playerStateMap=new HashMap<>();
    public static void init(ServerPlayer player)
    {
        playerStateMap.put(player.getUUID(),true);
    }
    public static void update(ServerPlayer player)
    {
        boolean cur=playerStateMap.computeIfAbsent(player.getUUID(),_->true);
        playerStateMap.put(player.getUUID(),player.isInWall());
        if((!cur)&&player.isInWall())
        {
            StateSaver stateSaver=StateSaver.getServerState(player.level().getServer());
            Identifier source=player.level().dimension.identifier();
            Identifier target=stateSaver
                    .ext
                    .noclip
                    .computeIfAbsent(source,_->new WeightedSelector<>())
                    .select();
            if(target==null)return;
            ServerLevel level=player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,target));
            try{loadPos(server, player, level, stateSaver);removePos(player,stateSaver,source);}
            catch (Exception e){throw new RuntimeException(e);}
        }
    }
}
