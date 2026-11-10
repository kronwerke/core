package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.kronwerke.core.perf.QuietLight;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Lets the "already lit" light pass of a loaded chunk leave it unchanged, see {@link QuietLight}. */
@Mixin(ChunkAccess.class)
public abstract class ChunkQuietLightMixin implements QuietLight {

    @Shadow private volatile boolean isLightCorrect;

    @Unique
    private volatile boolean kronwerke$quiet;

    @Override
    public void kronwerke$quietLight() {
        this.kronwerke$quiet = true;
    }

    @Inject(method = "setLightCorrect", at = @At("HEAD"), cancellable = true)
    private void kronwerke$quietFlag(boolean lightCorrect, CallbackInfo ci) {
        if (!this.kronwerke$quiet) return;
        this.isLightCorrect = lightCorrect;
        if (lightCorrect) this.kronwerke$quiet = false; // the pass is over, later changes mark as usual
        ci.cancel();
    }
}
