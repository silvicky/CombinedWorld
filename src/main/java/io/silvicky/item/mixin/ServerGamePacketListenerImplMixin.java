package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

import static io.silvicky.item.common.Util.getChunkPos;
import static io.silvicky.item.common.Util.isCrossingChunkBorder;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin
{

    @Shadow public ServerPlayer player;

    @Shadow private double firstGoodX;

    @Shadow private double firstGoodY;

    @Shadow private double firstGoodZ;


    @Shadow
    protected abstract boolean shouldCheckPlayerMovement(boolean bl);

    @Redirect(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 inject1(Vec3 instance, Vec3 vec)
    {
        try
        {
            VecTransformer vecTransformer = VecTransformer.getInstance(player);
            return vecTransformer.s2cTransform(instance).subtract(vecTransformer.s2cTransform(vec));
        }
        catch (ChunkUnusedException e){return VecTransformer.INF;}
    }
    @Inject(method = "isEntityCollidingWithAnythingNew", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelReader;getPreMoveCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/phys/Vec3;)Ljava/lang/Iterable;"), cancellable = true)
    private void inject2(LevelReader world, Entity entity, AABB box, double newX, double newY, double newZ, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 1) AABB box2)
    {
        //TODO all of these
        if(isCrossingChunkBorder(box)||isCrossingChunkBorder(box2)||!entity.chunkPosition().equals(getChunkPos(newX,newZ)))
        {
            cir.setReturnValue(false);
        }
    }
    @ModifyVariable(method = "handleMovePlayer", at = @At(value = "STORE",ordinal = 0),name="p")
    private double inject3(double value,
                           @Local(name = "d")double d,
                           @Local(name = "e")double e,
                           @Local(name = "h")double h)
    {
        try
        {
            VecTransformer transformer = VecTransformer.getInstance(player);
            Vec3 target = transformer.s2cTransform(new Vec3(d, e, h));
            Vec3 last = transformer.s2cTransform(new Vec3(firstGoodX, firstGoodY, firstGoodZ));
            return target.subtract(last).lengthSqr();
        }
        catch (Exception ex)
        {
            return 1e9;
        }
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
        //TODO the correct impl. below would expose more underlying problems
    }
    /*@ModifyVariable(method = "handleMovePlayer", at = @At(value = "STORE",ordinal = 1),name = "p")
    private double inject4(double value,
                           @Local(name = "d")double d,
                           @Local(name = "e")double e,
                           @Local(name = "h")double h)
    {
        try
        {
            VecTransformer transformer = VecTransformer.getInstance(player);
            Vec3 target = transformer.s2cTransform(new Vec3(d, e, h));
            Vec3 last = transformer.s2cTransform(player.position());
            Vec3 dif=last.subtract(target);
            if(dif.y>-0.5||dif.y<0.5)dif=dif.with(Direction.Axis.Y,0);//what is this??
            return dif.lengthSqr();
        }
        catch (Exception ex)
        {
            return 1e9;
        }
    }*/
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleportSetPosition(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",shift = At.Shift.AFTER))
    private void inject5(PositionMoveRotation positionMoveRotation, Set<Relative> set, CallbackInfo ci)
    {
        VecTransformer.refreshInstance(player);
    }
}
