package de.kronwerke.core.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mahou Tsukai runs fifteen checks on every living entity every tick. Each one is about a
 * Mahou effect on that entity, apart from one about a player's own spell. A mob without any
 * effect at all has nothing to update, so it is skipped. Players and mobs with effects run
 * as before.
 */
@Pseudo
@Mixin(targets = "stepsword.mahoutsukai.handlers.ServerHandler", remap = false)
public abstract class MahouLivingMixin {

    @Inject(method = "livingEvent(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$skipPlainMobs(EntityTickEvent.Pre event, CallbackInfo ci) {
        if (event.getEntity() instanceof LivingEntity living && !(living instanceof Player) && living.getActiveEffects().isEmpty()) {
            ci.cancel();
        }
    }
}
