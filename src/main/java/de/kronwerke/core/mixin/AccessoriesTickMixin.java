package de.kronwerke.core.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Accessories (bundled with the Aether) walks every slot of every living entity every tick
 * and builds four maps for it, on mobs too, which in this pack never wear accessories. On
 * the server, mobs now get that walk once a second instead, spread over the ticks by their
 * id, so anything a mob might wear still applies. Players are untouched.
 */
@Pseudo
@Mixin(targets = "io.wispforest.accessories.impl.AccessoriesEventHandler", remap = false)
public abstract class AccessoriesTickMixin {

    @Inject(method = "onLivingEntityTick(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$mobsOncePerSecond(LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof Player || entity.level().isClientSide()) {
            return;
        }
        if ((entity.tickCount + entity.getId()) % 20 != 0) {
            ci.cancel();
        }
    }
}
