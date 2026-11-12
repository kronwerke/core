package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** A turn for the screen inside long work on the game's thread; see {@link LoadPump}. */
@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.jei.tags.TagInfoRecipeMaker", remap = false)
public abstract class JeiPumpTagsMixin {
    /** JEI's pages for tags: once per tag. */
    @Inject(method = "getIngredients", at = @At("HEAD"), require = 0)
    private static void kronwerke$tag(CallbackInfoReturnable<?> ci) {
        LoadPump.pulse();
    }
}
