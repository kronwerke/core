package de.kronwerke.core.mixin;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draconic Evolution walks the armor slots of every mob every tick and rebuilds attribute
 * modifiers from modular items, which only players wear. Mobs are skipped; players as before.
 */
@Pseudo
@Mixin(targets = "com.brandon3055.draconicevolution.handlers.ModularArmorEventHandler", remap = false)
public abstract class DraconicArmorTickMixin {

    @Inject(method = "livingTick(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$playersOnly(EntityTickEvent.Pre event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof Player)) {
            ci.cancel();
        }
    }
}
