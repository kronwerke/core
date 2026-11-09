package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.kronwerke.core.perf.JourneyMapOverlay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Only rebuilds the loaded chunk overlay when an operator looks at it, see {@link JourneyMapOverlay}. */
@Pseudo
@Mixin(targets = "journeymap.api.plugins.LoadedChunkOverlayPlugin", remap = false)
public abstract class JourneyMapOverlayMixin {

    @Inject(method = "onTick(Lnet/minecraft/world/level/Level;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$onlyWhenWatched(Level world, CallbackInfo ci) {
        if (world instanceof ServerLevel level && !JourneyMapOverlay.wanted(level)) ci.cancel();
    }
}
