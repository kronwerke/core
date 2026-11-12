package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** A turn for the screen inside long work on the game's thread; see {@link LoadPump}. */
@Mixin(targets = "net.minecraft.core.Holder$Reference")
public abstract class HolderTagsPumpMixin {
    /** The server's tags being put on every block, item and so on: once per entry. */
    @Inject(method = "bindTags", at = @At("HEAD"), require = 0)
    private void kronwerke$tags(CallbackInfo ci) {
        LoadPump.pulse();
    }
}
