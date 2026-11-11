package de.kronwerke.core.mixin;

import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.link.Role;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * One server of the network can run with smaller mob caps than the others (mobs.capScale, by
 * the launcher's role): the farm world is a place to dig, and fewer monsters and animals there
 * leave the machine to the worlds people live in. After ServerCore's own cap, so its settings
 * and its lowering under load still count.
 */
@Mixin(value = MobCategory.class, priority = 1500)
public abstract class MobCapScaleMixin {
    @Unique
    private static double kronwerke$scale = -1;

    @Inject(method = "getMaxInstancesPerChunk", at = @At("RETURN"), cancellable = true)
    private void kronwerke$scaled(CallbackInfoReturnable<Integer> cir) {
        double s = kronwerke$scale;
        if (s < 0) {
            s = 1;
            try {
                for (String e : KronwerkeConfig.MOB_CAP_SCALE.get()) {
                    int eq = e.indexOf('=');
                    if (eq > 0 && e.substring(0, eq).trim().equals(Role.role())) s = Math.max(0, Math.min(1, Double.parseDouble(e.substring(eq + 1).trim())));
                }
            } catch (RuntimeException ex) {
                return; // the config is not loaded yet (or the number is none): asked again next time
            }
            kronwerke$scale = s;
        }
        if (s < 1) cir.setReturnValue((int) Math.ceil(cir.getReturnValueI() * s));
    }
}
