package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.EmptyCurios;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Curios slot tick skips mobs that wear nothing, see EmptyCurios. */
@Pseudo
@Mixin(targets = "top.theillusivec4.curios.common.event.CuriosEventHandler", remap = false)
public abstract class CuriosTickEmptyMobMixin {

    @Inject(method = "tick(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Post;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$skipEmptyMobs(EntityTickEvent.Post event, CallbackInfo ci) {
        if (event.getEntity() instanceof LivingEntity living && EmptyCurios.emptyMob(living)) {
            ci.cancel();
        }
    }
}
