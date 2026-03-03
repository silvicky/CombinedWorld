package io.silvicky.item.mixin;

import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Mixin(PlayerMoveC2SPacket.class)
public class PlayerMoveC2SPacketMixin
{
    @Shadow public double z;

    @Shadow public double x;

    @Shadow @Final protected boolean changePosition;
    @Unique
    private static Set<PlayerMoveC2SPacket> modified= Collections.newSetFromMap(new WeakHashMap<>());
    @Inject(method = "apply(Lnet/minecraft/network/listener/ServerPlayPacketListener;)V",at= @At("HEAD"))
    public void inject2(ServerPlayPacketListener serverPlayPacketListener, CallbackInfo ci)
    {
        if(changePosition)
        {
            if(modified.contains((PlayerMoveC2SPacket) (Object)this))return;
            modified.add((PlayerMoveC2SPacket) (Object)this);
            //System.out.println(this.x+" "+((ServerPlayNetworkHandler)serverPlayPacketListener).player.getX());
            this.x-=16;
            this.z-=16;
        }
    }
}
