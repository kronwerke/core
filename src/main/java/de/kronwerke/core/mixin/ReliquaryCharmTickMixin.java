package de.kronwerke.core.mixin;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reliquary looks at every mob's target and memories every tick to drop players who carry a
 * mob charm. Four times a second is enough for that, spread over the ticks by entity id.
 */
@Pseudo
@Mixin(targets = "reliquary.item.MobCharmItem", remap = false)
public abstract class ReliquaryCharmTickMixin {

    @Inject(method = "onLivingUpdate(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$fourTimesASecond(EntityTickEvent.Pre event, CallbackInfo ci) {
        Entity entity = event.getEntity();
        if (((entity.tickCount + entity.getId()) & 3) != 0) {
            ci.cancel();
        }
    }
}
