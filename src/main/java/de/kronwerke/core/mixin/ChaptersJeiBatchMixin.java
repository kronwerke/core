package de.kronwerke.core.mixin;

import de.kronwerke.core.compat.JeiBatch;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Set;

/**
 * Chapters applies its stage locks to JEI one item at a time. This routes those calls
 * through {@link JeiBatch}, which sends them to JEI in one go at the end of applyLocked.
 *
 * Chapters also hides every recipe that makes a locked item, fluid or chemical. It finds
 * them by asking each of JEI's four hundred recipe types, once for all items, then once
 * per fluid and chemical, and when a stage opens once more per unlocked item to show them
 * again. With this pack that froze the client for a minute on joining, six more for the
 * fluids, and would have frozen it for far longer at every stage opening. Those lookups
 * are skipped: locked things stay out of JEI's list, and the server still refuses to make
 * them, but a recipe for one can show up under the uses of an open item.
 *
 * Without Chapters or JEI the mixin does nothing.
 */
@Pseudo
@Mixin(targets = "com.gabinx.chapters.compat.jei.ChaptersJeiModPlugin", remap = false)
public abstract class ChaptersJeiBatchMixin {

    @Inject(method = "applyLocked", at = @At("HEAD"), require = 0)
    private static void kronwerke$begin(Set<ResourceLocation> items, Set<ResourceLocation> fluids,
                                        Set<ResourceLocation> chemicals, Set<ResourceLocation> recipes, CallbackInfo ci) {
        JeiBatch.begin();
    }

    @Inject(method = "applyLocked", at = @At("RETURN"), require = 0)
    private static void kronwerke$end(Set<ResourceLocation> items, Set<ResourceLocation> fluids,
                                      Set<ResourceLocation> chemicals, Set<ResourceLocation> recipes, CallbackInfo ci) {
        JeiBatch.end();
    }

    @Inject(method = {"hideOutputRecipesForItemsBatch", "hideOutputRecipesForFluid", "hideOutputRecipesForChemical",
            "ensureOutputRecipesVisibleForItem", "ensureOutputRecipesVisibleForFluid", "ensureOutputRecipesVisibleForChemical"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$skipRecipeLookups(CallbackInfo ci) {
        JeiBatch.skippedLookup();
        ci.cancel();
    }

    @Inject(method = "hideIngredientsForItem", at = @At("HEAD"), require = 0)
    private static void kronwerke$lookingFor(IIngredientManager manager, ResourceLocation item, CallbackInfo ci) {
        JeiBatch.lookingFor(item);
    }

    @Redirect(method = "hideIngredientsForItem",
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/runtime/IIngredientManager;getAllItemStacks()Ljava/util/Collection;"),
            require = 0)
    private static Collection<ItemStack> kronwerke$stacks(IIngredientManager manager) {
        return JeiBatch.itemStacks(manager);
    }

    @Redirect(method = {"hideIngredientsForItem", "hideIngredientsForFluid", "hideIngredientsForChemical"},
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/runtime/IIngredientManager;removeIngredientsAtRuntime(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;)V"),
            require = 0)
    private static void kronwerke$remove(IIngredientManager manager, IIngredientType<?> type, Collection<?> items) {
        JeiBatch.remove(manager, type, items);
    }

    @Redirect(method = {"restoreItemIngredients", "restoreFluidIngredients", "restoreChemicalIngredients"},
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/runtime/IIngredientManager;addIngredientsAtRuntime(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;)V"),
            require = 0)
    private static void kronwerke$add(IIngredientManager manager, IIngredientType<?> type, Collection<?> items) {
        JeiBatch.add(manager, type, items);
    }
}
