package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.TickMemo;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iron's Spellbooks asks whether an entity wears one of its curios (the invisibility ring)
 * every time anything checks whether that entity is invisible to someone: mob targeting, the
 * JourneyMap radar, entity tracking. Each answer searches every curio slot. The answer per
 * item and entity is kept for the rest of the server tick.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.item.curios.CurioBaseItem", remap = false)
public abstract class IronsCurioCacheMixin {

    @Inject(method = "isEquippedBy(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$cached(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        Boolean known = TickMemo.get(this, entity);
        if (known != null) {
            cir.setReturnValue(known);
        }
    }

    @Inject(method = "isEquippedBy(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"), require = 0)
    private void kronwerke$remember(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        TickMemo.put(this, entity, cir.getReturnValue());
    }
}
