package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pictures during JEI's start; see {@link LoadPump}. */
@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeManagerInternal", remap = false)
public abstract class JeiPumpRecipeMixin {
    /** Once per recipe that a plugin hands in. */
    @Inject(method = "addRecipe", at = @At("HEAD"), require = 0)
    private void kronwerke$recipe(CallbackInfoReturnable<?> ci) {
        LoadPump.pulse();
    }
}
