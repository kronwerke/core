package de.kronwerke.core.mixin;

import de.kronwerke.core.client.JeiKeep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEI stays up across a move between the servers of the network; see {@link JeiKeep}. */
@Pseudo
@Mixin(targets = "mezz.jei.neoforge.startup.StartEventObserver", remap = false)
public abstract class JeiKeepMixin {
    /** A stop of JEI that was skipped for a move: JEI still counts as started. */
    @org.spongepowered.asm.mixin.Unique
    private boolean kronwerke$skipped;
    @org.spongepowered.asm.mixin.Unique
    private boolean kronwerke$through;

    @Inject(method = "transitionState", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$keep(@Coerce Enum<?> next, CallbackInfo ci) {
        if (kronwerke$through) return;
        if (next.name().equals("LISTENING")) {
            if (JeiKeep.keep()) {
                kronwerke$skipped = true;
                ci.cancel();
            } else {
                kronwerke$skipped = false;
            }
            return;
        }
        if (kronwerke$skipped && next.name().equals("JEI_STARTED")) {
            // JEI wants to start, so it believes it has stopped, but that stop was skipped: it
            // is stopped now, for real. Without this the start is "from STARTED to STARTED",
            // which JEI throws on, inside the handling of a packet, and the game drops the
            // connection ("Network Protocol Error"). Seen on the real servers when the farm
            // world had one recipe fewer than main: JEI's own restart stops and starts in one
            // go, the stop was skipped, and nobody could have entered the farm world any more.
            kronwerke$skipped = false;
            kronwerke$through = true;
            try {
                for (Object c : next.getDeclaringClass().getEnumConstants()) {
                    if (((Enum<?>) c).name().equals("LISTENING")) {
                        java.lang.reflect.Method m = getClass().getDeclaredMethod("transitionState", next.getDeclaringClass());
                        m.setAccessible(true);
                        m.invoke(this, c);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                de.kronwerke.core.KronwerkeCore.LOGGER.warn("JEI could not be stopped before its restart: {}", e.toString());
            } finally {
                kronwerke$through = false;
            }
        }
        JeiKeep.started();
    }

    @Inject(method = "restart", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$sameRecipes(CallbackInfo ci) {
        if (JeiKeep.same()) ci.cancel();
    }
}
