package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.VecTransformer;
import net.minecraft.core.Direction;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin
{

    @Shadow public ServerPlayer player;

    @Shadow private double firstGoodX;

    @Shadow private double firstGoodY;

    @Shadow private double firstGoodZ;


    @Shadow
    private double lastGoodX;

    @Shadow
    private double lastGoodY;

    @Shadow
    private double lastGoodZ;

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
    @ModifyVariable(method = "handleMovePlayer", at = @At(value = "STORE",ordinal = 1),name = "p")
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
            Vec3 dif=target.subtract(last);
            if(dif.y>-0.5||dif.y<0.5)dif=dif.with(Direction.Axis.Y,0);//what is this??
            return dif.lengthSqr();
        }
        catch (Exception ex)
        {
            return 1e9;
        }
    }
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleportSetPosition(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",shift = At.Shift.AFTER))
    private void inject5(PositionMoveRotation positionMoveRotation, Set<Relative> set, CallbackInfo ci)
    {
        VecTransformer.refreshInstance(player);
    }
    @ModifyArg(method = "handleMovePlayer", at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"),index = 1)
    private Vec3 inject6(Vec3 par2,
                         @Local(name = "d")double d,
                         @Local(name = "e")double e,
                         @Local(name = "h")double h)
    {
        try
        {
            VecTransformer transformer = VecTransformer.getInstance(player);
            Vec3 target = transformer.s2cTransform(new Vec3(d, e, h));
            Vec3 last = transformer.s2cTransform(new Vec3(lastGoodX,lastGoodY,lastGoodZ));
            return target.subtract(last);
        }
        catch (Exception ex)
        {
            return par2;
        }
    }
    @ModifyArg(method = "isEntityCollidingWithAnythingNew", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private AABB inject7(AABB value, @Local(argsOnly = true) Entity entity)
    {
        if(!(entity instanceof ServerPlayer player1))return value;
        try
        {
            Vec3 v = VecTransformer.getInstance(player1).s2cTransform(player1.position());
            return entity.getBoundingBox().move(v.x - entity.getX(), v.y - entity.getY(), v.z - entity.getZ()).deflate(1.0e-5F);
        }
        catch (Exception exception)
        {
            return value;
        }
    }
}
