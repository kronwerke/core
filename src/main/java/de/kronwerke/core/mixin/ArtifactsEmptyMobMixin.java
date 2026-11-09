package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.EmptyCurios;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Artifacts' per tick update of worn artifacts skips mobs that wear nothing, see EmptyCurios. */
@Pseudo
@Mixin(targets = "artifacts.event.ArtifactHooks", remap = false)
public abstract class ArtifactsEmptyMobMixin {

    @Inject(method = "livingUpdate(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$skipEmptyMobs(LivingEntity entity, CallbackInfo ci) {
        if (EmptyCurios.emptyMob(entity)) {
            ci.cancel();
        }
    }
}
