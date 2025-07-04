package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.WorldBorderCommand;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldBorderCommand.class)
public class WorldBorderCommandMixin
{
    @Redirect(method = "executeBuffer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getWorldBorder()Lnet/minecraft/world/border/WorldBorder;"))
    private static WorldBorder inject1(ServerWorld instance, @Local(argsOnly = true)ServerCommandSource source)
    {
        return source.getWorld().getWorldBorder();
    }
    @Redirect(method = "executeDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getWorldBorder()Lnet/minecraft/world/border/WorldBorder;"))
    private static WorldBorder inject2(ServerWorld instance, @Local(argsOnly = true)ServerCommandSource source)
    {
        return source.getWorld().getWorldBorder();
    }
    @Redirect(method = "executeWarningTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getWorldBorder()Lnet/minecraft/world/border/WorldBorder;"))
    private static WorldBorder inject3(ServerWorld instance, @Local(argsOnly = true)ServerCommandSource source)
    {
        return source.getWorld().getWorldBorder();
    }
    @Redirect(method = "executeWarningDistance", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getWorldBorder()Lnet/minecraft/world/border/WorldBorder;"))
    private static WorldBorder inject4(ServerWorld instance, @Local(argsOnly = true)ServerCommandSource source)
    {
        return source.getWorld().getWorldBorder();
    }
    @Redirect(method = "executeCenter", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getWorldBorder()Lnet/minecraft/world/border/WorldBorder;"))
    private static WorldBorder inject5(ServerWorld instance, @Local(argsOnly = true)ServerCommandSource source)
    {
        return source.getWorld().getWorldBorder();
    }
    @Redirect(method = "executeSet", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getWorldBorder()Lnet/minecraft/world/border/WorldBorder;"))
    private static WorldBorder inject6(ServerWorld instance, @Local(argsOnly = true)ServerCommandSource source)
    {
        return source.getWorld().getWorldBorder();
    }
    @Redirect(method = "executeGet", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getWorldBorder()Lnet/minecraft/world/border/WorldBorder;"))
    private static WorldBorder inject7(ServerWorld instance, @Local(argsOnly = true)ServerCommandSource source)
    {
        return source.getWorld().getWorldBorder();
    }
}
