package de.kronwerke.core.mixin;

import java.util.Set;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.lock.MachineLocks;
import net.minecraft.world.level.Level;

/**
 * Mekanism keeps its own list of recipes per machine and checks a slot against it before an
 * item goes in. A held back recipe is simply not in that list, so the slot refuses the item
 * without a word. When a player clicks an item into a Mekanism machine and only a held back
 * recipe would take it, they are told which stage it needs, like at every other machine.
 */
@Pseudo
@Mixin(targets = "mekanism.common.recipe.lookup.cache.AbstractInputRecipeCache", remap = false)
public abstract class MekanismInputLocksMixin {

    @Shadow
    @Final
    protected mekanism.common.recipe.MekanismRecipeType<?, ?, ?> recipeType;

    @Inject(method = "containsInput", at = @At("RETURN"), require = 0)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void kronwerke$tellWhy(Level level, Object input, Function extractor, mekanism.common.recipe.lookup.cache.type.IInputCache cache,
                                   Set complex, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        MachineLocks.missedInput(recipeType, level, recipe -> {
            Object ingredient = extractor.apply(recipe);
            return ingredient instanceof mekanism.api.recipes.ingredients.InputIngredient in && in.testType(input);
        });
    }
}
