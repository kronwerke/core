package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.DirtyChunks;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts who marks a chunk changed while /kw admin dirty runs, see DirtyChunks. */
@Mixin(ChunkAccess.class)
public abstract class ChunkDirtyMixin {

    @Shadow
    protected volatile boolean unsaved;

    @Inject(method = "setUnsaved", at = @At("HEAD"))
    private void kronwerke$count(boolean value, CallbackInfo ci) {
        if (DirtyChunks.on && value && !this.unsaved) {
            DirtyChunks.record("mark");
        }
    }
}
