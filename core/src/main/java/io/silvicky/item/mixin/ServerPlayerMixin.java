package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.StateSaver;
import io.silvicky.item.backrooms.EntityRemapAccess;
import io.silvicky.item.backrooms.NoclipManager;
import io.silvicky.item.backrooms.VecTransformer;
import io.silvicky.item.common.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Set;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.backrooms.EntityVisibilityManager.updatePlayerVisibility;
import static io.silvicky.item.cfg.JSONConfig.useStorage;
import static io.silvicky.item.common.Util.getDimensionId;
import static io.silvicky.item.common.Util.toOverworld;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin
{
    @Shadow public abstract ServerLevel level();

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getLevelData()Lnet/minecraft/world/level/storage/LevelData;"))
    private void inject1(TeleportTransition teleportTarget, CallbackInfoReturnable<ServerPlayer> cir)
    {
        //TODO Check if warp is allowed
        Identifier source= level().dimension().identifier();
        Identifier target=teleportTarget.newLevel().dimension().identifier();
        ServerPlayer entity=(ServerPlayer) (Object)this;
        StateSaver stateSaver=StateSaver.getServerState(level().getServer());
        if(getDimensionId(source).equals(getDimensionId(target)))return;
        savePos(entity, stateSaver);
        if(!source.getNamespace().equals(target.getNamespace()))
        {
            if(useStorage)saveInventory(entity, stateSaver);
        }
    }
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleportSpectators(Lnet/minecraft/world/level/portal/TeleportTransition;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void inject2(TeleportTransition teleportTarget, CallbackInfoReturnable<ServerPlayer> cir, @Local(name = "oldLevel") ServerLevel serverWorld2)
    {
        Identifier source=serverWorld2.dimension().identifier();
        Identifier target=teleportTarget.newLevel().dimension().identifier();
        ServerPlayer entity=(ServerPlayer) (Object)this;
        StateSaver stateSaver=StateSaver.getServerState(level().getServer());
        if(getDimensionId(source).equals(getDimensionId(target)))return;
        NoclipManager.init(entity);
        if(!source.getNamespace().equals(target.getNamespace()))
        {
            if(useStorage)
            {
                try
                {
                    loadInventory(entity, teleportTarget.newLevel(), stateSaver);
                }
                catch(Exception e)
                {
                    //TODO Any way to rollback?
                    throw new RuntimeException(e);
                }
            }
        }
    }
    @Inject(method = "setRespawnPosition",at= @At(value = "HEAD"),cancellable = true)
    private void inject3(ServerPlayer.RespawnConfig respawn, boolean sendMessage, CallbackInfo ci)
    {
        ServerPlayer player=(ServerPlayer) (Object)this;
        if(respawn==null)
        {
            //what's this??
            player.respawnConfig =null;
            ci.cancel();
            return;
        }
        Identifier id=getDimensionId(respawn.respawnData().dimension().identifier());
        if(id.equals(Level.OVERWORLD.identifier()))
        {
            if(sendMessage&&!respawn.equals(player.respawnConfig))player.sendSystemMessage(ServerPlayer.SPAWN_SET_MESSAGE);
            player.respawnConfig = respawn;
        }
        else
        {
            HashMap<String, ServerPlayer.RespawnConfig> respawnMap=StateSaver.getServerState(level().getServer()).respawn.computeIfAbsent(id, _ -> new HashMap<>());
            if(!respawn.equals(respawnMap.get(player.getStringUUID())))player.sendSystemMessage(ServerPlayer.SPAWN_SET_MESSAGE);
            respawnMap.put(player.getStringUUID(), respawn);
        }
        ci.cancel();
    }
    @Inject(method = "getRespawnConfig",at= @At(value = "HEAD"),cancellable = true)
    private void inject4(CallbackInfoReturnable<ServerPlayer.RespawnConfig> cir)
    {
        ServerPlayer player=(ServerPlayer) (Object)this;
        Identifier id=getDimensionId(level());
        LevelData.RespawnData defaultSpawn= level().getServer().getRespawnData();
        LevelData.RespawnData defaultCurrentSpawn= LevelData.RespawnData.of(ResourceKey.create(Registries.DIMENSION,id),defaultSpawn.pos(),defaultSpawn.yaw(),defaultSpawn.pitch());
        if(id.equals(Level.OVERWORLD.identifier())) cir.setReturnValue(player.respawnConfig);
        else cir.setReturnValue(StateSaver.getServerState(level().getServer()).respawn.computeIfAbsent(id, _ ->new HashMap<>()).getOrDefault(player.getStringUUID(), new ServerPlayer.RespawnConfig(defaultCurrentSpawn,false)));
    }
    @Redirect(method= "findRespawnPositionAndUseSpawnBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/TeleportTransition;missingRespawnBlock(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private TeleportTransition inject5(ServerPlayer player, TeleportTransition.PostTeleportTransition postDimensionTransition)
    {
        ServerLevel serverWorld = toOverworld(player.level().getServer(),player.level());
        LevelData.RespawnData spawnPoint = serverWorld.getRespawnData();
        return new TeleportTransition(
                serverWorld, TeleportTransition.findAdjustedSharedSpawnPos(serverWorld, player), Vec3.ZERO, spawnPoint.yaw(), spawnPoint.pitch(), true, false, Set.of(), postDimensionTransition
        );
    }
    @Redirect(method= "findRespawnPositionAndUseSpawnBlock",at= @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/TeleportTransition;createDefault(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private TeleportTransition inject6(ServerPlayer player, TeleportTransition.PostTeleportTransition postDimensionTransition)
    {
        ServerLevel serverWorld = toOverworld(player.level().getServer(),player.level());
        LevelData.RespawnData spawnPoint = serverWorld.getRespawnData();
        return new TeleportTransition(
                serverWorld, TeleportTransition.findAdjustedSharedSpawnPos(serverWorld, player), Vec3.ZERO, spawnPoint.yaw(), spawnPoint.pitch(), false, false, Set.of(), postDimensionTransition
        );
    }
    /**
 * Hands the connection to the player that replaced the dead one, and drops what was remembered per player.
 *
 * <p><b>Respawn does not teleport the player, and that is the whole problem.</b> {@code PlayerList#respawn}
 * constructs a <em>new</em> {@code ServerPlayer} already sitting in the respawn level and gives it the dead
 * player's connection:
 *
 * <pre>
 *   ServerPlayer player = new ServerPlayer(server, level, ...);
 *   player.connection  = serverPlayer.connection;
 *   player.restoreFrom(serverPlayer, keepAllPlayerData);
 * </pre>
 *
 * So {@code ServerPlayer#teleport} and {@code setServerLevel} are never involved and no dimension-change hook
 * fires. Worse, {@code ServerGamePacketListenerImpl} holds its own {@code player} field, and vanilla leaves it
 * pointing at the <em>dead</em> player - vanilla has no reason to touch it, because there the player object is
 * stable and only the level changes. This mod's per-dimension transformers assume the opposite: the player
 * object changes with the world. So every packet hook, which reaches the player through
 * {@code gamePacketListener.getPlayer()}, would resolve through the dead player and use the outgoing world's
 * transformer. Observed as: die in a shuffled nether, respawn in the plain overworld, and the random
 * transformer is still in force.
 *
 * <p>Repointing the connection is what makes the new player's transformer the one actually used. It is a plain
 * field assignment - same connection object, no packet, no client round trip.
 *
 * <p>Hooked on {@code restoreFrom} rather than on {@code PlayerList#respawn}, because {@code restoreFrom} has
 * exactly one caller - that line - and it hands over both players directly, so no local variable name has to be
 * guessed. It also runs before vanilla sends the respawn position packet, which matters: that packet goes
 * through the outbound hook, which is exactly the code that would otherwise read the dead player.
 *
 * <p>Two things are cleared, each for its own reason: the dead player's transformer, or the known
 * {@code instances} leak becomes a wrong-type lookup; and the connection's remembered entity bases, since the
 * client is about to forget every entity it had - see {@link io.silvicky.item.backrooms.EntityRemapAccess}.
 * The new player needs no transformer built for it here: it is a new key, so the first lookup builds one from
 * the dimension it was born in.
 */
@Inject(method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V",at = @At("TAIL"))
    private void inject7c(ServerPlayer dead, boolean restoreAll, CallbackInfo ci)
    {
        ServerPlayer player=(ServerPlayer) (Object)this;
        if(player==dead)return;
        VecTransformer previous=VecTransformer.peekInstance(dead);
        //Drop the dead entry first, so nothing can resolve through it while the rest is being fixed up.
        VecTransformer.remove(dead);
        if(player.connection instanceof EntityRemapAccess access)access.item_storage$resetEntityRemapper();
        player.connection.player=player;
        Util.LOGGER.debug("[remap] {} | respawned into {}, transformer {} -> {}",
                dead.getGameProfile().name(),
                player.level().dimension().identifier(),
                previous==null?"none":previous.getClass().getSimpleName(),
                VecTransformer.peekInstance(player)==null?"built on first use":
                        VecTransformer.peekInstance(player).getClass().getSimpleName());
    }

    /**
     * Rebuilds the player's transformer when the warp changes dimension.
     *
     * <p>{@code VecTransformer.instances} is keyed by <em>player</em>, so it outlives the dimension. Without
     * this, warping from a shuffled world into a plain one kept using the shuffled world's transformer:
     * {@code getInstance} found an entry and returned it, and {@code getInstanceByDimension} - the only
     * thing that consults the dimension's configured type - was never asked. The player then stood in an
     * unconfigured world with a random mapping applied to it, remapping chunks that were never meant to be
     * remapped, and re-adding loading tickets for the map they had just left.
     *
     * <p><b>Placed at the tail of {@code setServerLevel}, not of {@code triggerDimensionChangeTriggers}.</b>
     * Ordering matters here and the obvious choice is wrong. On the cross-dimension path vanilla does, in
     * order:
     *
     * <pre>
     *   setServerLevel(newLevel)          // level() becomes the new one
     *   connection.teleport(...)          // the position packet, which this mod converts
     *   triggerDimensionChangeTriggers(..)
     * </pre>
     *
     * So a hook on {@code triggerDimensionChangeTriggers} runs <em>after</em> the client has already been
     * sent a position, converted with the outgoing world's transformer. The player would arrive at a
     * coordinate computed by a mapping that is about to be thrown away.
     *
     * <p>{@code setServerLevel} has exactly one caller in the whole game, and it is the line above that
     * position packet, so a tail hook there is the first point at which the new level is current and nothing
     * has been sent yet. It is also unconditional, which is correct: {@code triggerDimensionChangeTriggers}
     * would have needed an old-dimension comparison because it is not the only place a level can change.
     *
     * <p>{@code refreshInstance} rather than {@code getInstance}: an entry exists by this point and reusing it
     * would keep the old type, which is the bug.
     */
    @Inject(method = "setServerLevel",at = @At("TAIL"))
    private void inject7b(ServerLevel level, CallbackInfo ci)
    {
        ServerPlayer player=(ServerPlayer) (Object)this;
        VecTransformer previous=VecTransformer.peekInstance(player);
        //Worth having even when the type does not change: it is a fresh shuffle for a fresh world, so a
        //player cannot carry a map across dimensions and correlate the two layouts. See CONVENTION.md 1.5.
        VecTransformer.refreshInstance(player);
        try
        {
            StateSaver state = StateSaver.getServerState(player.level().getServer());
            if (player.connection != null)
            {
                Integer storedView = state.ext.view.get(level.dimension().identifier());
                int viewValue = (storedView == null)
                        ? player.level().getServer().getPlayerList().getViewDistance()
                        : Mth.clamp(player.requestedViewDistance(), 2, storedView);
                player.connection.send(new ClientboundSetChunkCacheRadiusPacket(viewValue));

                Integer storedSim = state.ext.sim.get(level.dimension().identifier());
                int simValue = (storedSim == null)
                        ? player.level().getServer().getPlayerList().getSimulationDistance()
                        : storedSim;
                player.connection.send(new ClientboundSetSimulationDistancePacket(simValue));
            }
        }
        catch (Exception ignored)
        {
        }
        Util.LOGGER.debug("[remap] {} | warped into {}, transformer {} -> {}",
                player.getGameProfile().name(),
                level.dimension().identifier(),
                previous==null?"none":previous.getClass().getSimpleName(),
                VecTransformer.peekInstance(player).getClass().getSimpleName());
    }
    /**
     * Forgets a player who has left.
     *
     * <p>{@code VecTransformer.instances} is keyed by player and nothing ever removed an entry, so every player
     * who ever joined stayed in the map holding themselves, their transformer, and - for {@code random} and
     * {@code xor} - their whole chunk map. That was logged as a known leak and deliberately left alone as
     * unrequested.
     *
     * <p>It stopped being merely a leak once respawn began removing entries: an entry that outlives its player
     * is now a lookup that can return the wrong <em>type</em> for whoever reuses the connection. A leak that
     * changes behaviour has to go, and this is the natural place - the one call that runs exactly once per
     * player who disconnects.
     *
     * <p>Removals are not idempotent, so a player who reconnects simply gets a fresh entry on first use.
     */
    @Inject(method = "disconnect",at = @At("HEAD"))
    private void inject7d(CallbackInfo ci)
    {
        VecTransformer.remove((ServerPlayer) (Object)this);
    }
    @Inject(method = "die",at = @At("HEAD"))
    private void inject7(DamageSource damageSource, CallbackInfo ci)
    {
        ServerPlayer player=(ServerPlayer) (Object)this;
        updatePlayerVisibility(player.level().getServer(),
                player.level().dimension().identifier().getNamespace(),
                player.getStringUUID());
    }
    @Inject(method = "tick",at = @At("RETURN"))
    private void inject8(CallbackInfo ci)
    {
        ServerPlayer player =((ServerPlayer)(Object)this);
        NoclipManager.update(player);
    }
}
