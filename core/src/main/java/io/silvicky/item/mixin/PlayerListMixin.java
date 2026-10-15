package io.silvicky.item.mixin;

import io.silvicky.item.StateSaver;
import io.silvicky.item.common.Util;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin
{
    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    private int viewDistance;

    @Shadow
    private int simulationDistance;

    @Redirect(method = "setViewDistance(I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;setViewDistance(I)V"))
    private void item_storage$propagateView(ServerChunkCache instance, int newDistance, @com.llamalad7.mixinextras.sugar.Local(name = "level") ServerLevel level)
    {
        //Levels whose distance is pinned per-dimension keep that value; a global change does not touch them.
        if (StateSaver.getServerState(server).ext.view.containsKey(level.dimension().identifier())) return;
        instance.setViewDistance(newDistance);
    }

    @Redirect(method = "setSimulationDistance", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;setSimulationDistance(I)V"))
    private void item_storage$propagateSim(ServerChunkCache instance, int newDistance, @com.llamalad7.mixinextras.sugar.Local(name = "level") ServerLevel level)
    {
        if (StateSaver.getServerState(server).ext.sim.containsKey(level.dimension().identifier())) return;
        instance.setSimulationDistance(newDistance);
    }

    /**
     * The vanilla broadcasts in {@code setViewDistance} / {@code setSimulationDistance} send one global value to
     * everyone. When per-dimension overrides are in play that is wrong: a player in an overridden dimension
     * still receives the global number, so their client never sees the overridden radius. Re-run the same
     * updates ourselves, then send each player the value their current level calls for.
     *
     * <p>A level with no stored override follows the global value; an overridden one keeps its own.
     */
    @Inject(method = "setViewDistance(I)V", at = @At("TAIL"))
    private void item_storage$sendPerPlayerView(int newDistance, CallbackInfo ci)
    {
        try
        {
            StateSaver state = StateSaver.getServerState(server);
            //Broadcast per-player.
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers()))
            {
                if (player.connection == null) continue;
                Integer stored = state.ext.view.get(player.level().dimension().identifier());
                int value;
                if (stored == null) value = newDistance;
                else value = Mth.clamp(player.requestedViewDistance(), 2, stored);
                player.connection.send(new ClientboundSetChunkCacheRadiusPacket(value));
            }
        }
        catch (Exception ignored)
        {
            //Keep vanilla's global broadcast already done before this tail runs; nothing further to do.
        }
    }

    @Inject(method = "setSimulationDistance", at = @At("TAIL"))
    private void item_storage$sendPerPlayerSim(int newDistance, CallbackInfo ci)
    {
        try
        {
            StateSaver state = StateSaver.getServerState(server);
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers()))
            {
                if (player.connection == null) continue;
                Integer stored = state.ext.sim.get(player.level().dimension().identifier());
                int value = (stored == null) ? newDistance : stored;
                player.connection.send(new ClientboundSetSimulationDistancePacket(value));
            }
        }
        catch (Exception ignored)
        {
        }
    }

    /**
     * A newly placed player is logged in with the global radius in the login packet; if their spawn dimension
     * actually carries an override, overwrite it before they start interacting.
     */
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void item_storage$placeNewPlayer(net.minecraft.network.Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci)
    {
        item_storage$sendView(player);
        item_storage$sendSim(player);
    }

    @Unique
    private void item_storage$sendView(ServerPlayer player)
    {
        if (player == null || player.connection == null) return;
        try
        {
            StateSaver state = StateSaver.getServerState(server);
            Integer stored = state.ext.view.get(player.level().dimension().identifier());
            int value;
            if (stored == null) value = this.viewDistance;
            else value = Mth.clamp(player.requestedViewDistance(), 2, stored);
            player.connection.send(new ClientboundSetChunkCacheRadiusPacket(value));
        }
        catch (Exception ignored)
        {
        }
    }

    @Unique
    private void item_storage$sendSim(ServerPlayer player)
    {
        if (player == null || player.connection == null) return;
        try
        {
            StateSaver state = StateSaver.getServerState(server);
            Integer stored = state.ext.sim.get(player.level().dimension().identifier());
            int value = (stored == null) ? this.simulationDistance : stored;
            player.connection.send(new ClientboundSetSimulationDistancePacket(value));
        }
        catch (Exception ignored)
        {
        }
    }
}
