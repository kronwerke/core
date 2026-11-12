package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** A turn for the screen inside long work on the game's thread; see {@link LoadPump}. */
@Mixin(targets = "net.minecraft.world.item.CreativeModeTab$ItemDisplayBuilder")
public abstract class CreativeTabPumpMixin {
    /** Filling the creative tabs (JEI reads all of them, and they are built again after every join): once per item. */
    @Inject(method = "accept", at = @At("HEAD"), require = 0)
    private void kronwerke$accept(CallbackInfo ci) {
        LoadPump.pulse();
    }
}
