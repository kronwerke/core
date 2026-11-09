package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.RadiationDose;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Mobs get their radiation dose every fourth round with four times the time step, see RadiationDose. */
@Pseudo
@Mixin(targets = "igentuman.nr.events.EntityExposureEvents", remap = false)
public abstract class RadiationMobDoseMixin {

    @Redirect(method = "onLevelTick", at = @At(value = "INVOKE",
            target = "Ligentuman/nr/radiation/simulation/entity/EntityDoseProcessor;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;JI)V"),
            require = 0)
    private void kronwerke$mobsLessOften(ServerLevel level, LivingEntity entity, long time, int interval) {
        RadiationDose.tick(level, entity, time, interval);
    }
}
