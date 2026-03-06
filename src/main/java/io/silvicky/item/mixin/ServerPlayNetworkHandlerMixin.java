package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.silvicky.item.backrooms.VecTransformer.getChunkPos;
import static io.silvicky.item.backrooms.VecTransformer.isCrossingChunkBorder;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin
{

    @Shadow protected abstract boolean shouldCheckMovement(boolean elytra);

    @Shadow public ServerPlayerEntity player;

    @Shadow private double lastTickX;

    @Shadow private double lastTickZ;

    @Redirect(method = "onPlayerInteractBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;subtract(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d inject1(Vec3d instance, Vec3d vec)
    {
        return VecTransformer.instance.s2cTransform(instance).subtract(VecTransformer.instance.s2cTransform(vec));
    }
    @Inject(method = "isEntityNotCollidingWithBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldView;getCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/math/Vec3d;)Ljava/lang/Iterable;"), cancellable = true)
    private void inject2(WorldView world, Entity entity, Box box, double newX, double newY, double newZ, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 1) Box box2)
    {
        if(isCrossingChunkBorder(box)||isCrossingChunkBorder(box2)||!entity.getChunkPos().equals(getChunkPos(newX,newZ)))
        {
            cir.setReturnValue(false);
        }
    }
    @Redirect(method = "onPlayerMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;shouldCheckMovement(Z)Z"))
    private boolean inject3(ServerPlayNetworkHandler instance, boolean elytra, @Local(argsOnly = true) PlayerMoveC2SPacket packet)
    {
        ChunkPos cur=player.getChunkPos();
        ChunkPos las=getChunkPos(lastTickX,lastTickZ);
        ChunkPos nxt=getChunkPos(packet.x,packet.z);
        if(!(cur.equals(las)&&las.equals(nxt)&&nxt.equals(cur)))
        {
            return false;
        }
        return shouldCheckMovement(elytra);
    }
    @Redirect(method = "onPlayerMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isInCurrentExplosionResetGraceTime()Z"))
    private boolean inject4(ServerPlayerEntity instance, @Local(argsOnly = true) PlayerMoveC2SPacket packet)
    {
        ChunkPos cur=player.getChunkPos();
        ChunkPos las=getChunkPos(lastTickX,lastTickZ);
        ChunkPos nxt=getChunkPos(packet.x,packet.z);
        if(!(cur.equals(las)&&las.equals(nxt)&&nxt.equals(cur)))
        {
            return true;
        }
        return instance.isInCurrentExplosionResetGraceTime();
    }
}
