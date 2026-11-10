package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;

/**
 * Iron Furnaces turns an exploding million furnace into rainbow coal. It listens for the
 * knockback event, which comes once for every entity the explosion pushes, and each time looks
 * up the block entity of every block the explosion breaks. With a few thousand TNT around that
 * was thousands of entities times hundreds of blocks per explosion, 38 % of the whole tick in
 * the 29,000 TNT test. After the first entity the list holds no million furnace any more (the
 * handler takes them out), so the handler now runs once per explosion: the same rainbow coal,
 * without the repeats.
 */
@Pseudo
@Mixin(targets = "ironfurnaces.util.EventHandler", remap = false)
public abstract class IronFurnacesExplosionMixin {

    @Unique
    private static Object kronwerke$lastExplosion;

    @Inject(method = "explosionEvent(Lnet/neoforged/neoforge/event/level/ExplosionKnockbackEvent;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$oncePerExplosion(ExplosionKnockbackEvent event, CallbackInfo ci) {
        Object explosion = event.getExplosion();
        if (explosion == kronwerke$lastExplosion) {
            ci.cancel();
            return;
        }
        kronwerke$lastExplosion = explosion;
    }
}
