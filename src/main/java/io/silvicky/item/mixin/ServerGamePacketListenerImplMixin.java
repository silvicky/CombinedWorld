package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.backrooms.VecTransformer.getChunkPos;
import static io.silvicky.item.backrooms.VecTransformer.isCrossingChunkBorder;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin
{

    @Shadow protected abstract boolean shouldCheckPlayerMovement(boolean elytra);

    @Shadow public ServerPlayer player;

    @Shadow private double firstGoodX;

    @Shadow private double firstGoodZ;

    @Redirect(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 inject1(Vec3 instance, Vec3 vec)
    {
        return VecTransformer.instance.s2cTransform(instance).subtract(VecTransformer.instance.s2cTransform(vec));
    }
    @Inject(method = "isEntityCollidingWithAnythingNew", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelReader;getPreMoveCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/phys/Vec3;)Ljava/lang/Iterable;"), cancellable = true)
    private void inject2(LevelReader world, Entity entity, AABB box, double newX, double newY, double newZ, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 1) AABB box2)
    {
        if(isCrossingChunkBorder(box)||isCrossingChunkBorder(box2)||!entity.chunkPosition().equals(getChunkPos(newX,newZ)))
        {
            cir.setReturnValue(false);
        }
    }
    @Redirect(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;shouldCheckPlayerMovement(Z)Z"))
    private boolean inject3(ServerGamePacketListenerImpl instance, boolean elytra, @Local(argsOnly = true) ServerboundMovePlayerPacket packet)
    {
        ChunkPos cur=player.chunkPosition();
        ChunkPos las=getChunkPos(firstGoodX, firstGoodZ);
        ChunkPos nxt=getChunkPos(packet.x,packet.z);
        if(!(cur.equals(las)&&las.equals(nxt)&&nxt.equals(cur)))
        {
            return false;
        }
        return shouldCheckPlayerMovement(elytra);
    }
    @Redirect(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isInPostImpulseGraceTime()Z"))
    private boolean inject4(ServerPlayer instance, @Local(argsOnly = true) ServerboundMovePlayerPacket packet)
    {
        ChunkPos cur=player.chunkPosition();
        ChunkPos las=getChunkPos(firstGoodX, firstGoodZ);
        ChunkPos nxt=getChunkPos(packet.x,packet.z);
        if(!(cur.equals(las)&&las.equals(nxt)&&nxt.equals(cur)))
        {
            return true;
        }
        return instance.isInPostImpulseGraceTime();
    }
}
