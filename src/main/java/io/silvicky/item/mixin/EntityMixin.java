package io.silvicky.item.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static io.silvicky.item.InventoryManager.*;

@Mixin(Entity.class)
public class EntityMixin {

    @ModifyVariable(method = "tickPortal", at = @At(value = "STORE"),ordinal =0)
	private RegistryKey<World> injected(RegistryKey<World> registryKey) {
		RegistryKey<World> registryKey0=((EntityInvoker)this).invokeGetWorld().getRegistryKey();
		String path=registryKey0.getValue().getPath();

		if(registryKey0.getValue().getPath().endsWith(OVERWORLD))
		{
			return RegistryKey.of(RegistryKey.ofRegistry(registryKey0.getRegistry()),
					Identifier.of(registryKey0.getValue().getNamespace(),
									path.substring(0,path.length()-9)+NETHER));
		}
		else if(registryKey0.getValue().getPath().endsWith(NETHER))
		{
			return RegistryKey.of(RegistryKey.ofRegistry(registryKey0.getRegistry()),
					Identifier.of(registryKey0.getValue().getNamespace(),
							path.substring(0,path.length()-10)+OVERWORLD));
		}
		else
		{
			return registryKey0;
		}
	}
	@ModifyVariable(method = "getTeleportTarget",at=@At("STORE"),ordinal =0)
	public boolean modifyBl1(boolean b, @Local(argsOnly = true) ServerWorld destination)
	{
		return ((EntityInvoker)this).invokeGetWorld().getRegistryKey().getValue().toString().endsWith(END)
				&&destination.getRegistryKey().getValue().toString().endsWith(OVERWORLD);
	}
	@ModifyVariable(method = "getTeleportTarget",at=@At("STORE"),ordinal =1)
	public boolean modifyBl2(boolean b, @Local(argsOnly = true) ServerWorld destination)
	{
		return destination.getRegistryKey().getValue().toString().endsWith(END);
	}
	@ModifyVariable(method = "getTeleportTarget",at=@At("STORE"),ordinal =2)
	public boolean modifyBl3(boolean b, @Local(argsOnly = true) ServerWorld destination)
	{
		return true;
	}
}