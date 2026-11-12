package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pictures during JEI's start; see {@link LoadPump}. */
@Mixin(Ingredient.class)
public abstract class IngredientPumpMixin {
    /** Plugins unfold every recipe's ingredients while they build their lists: a chance inside their own loops. */
    @Inject(method = "getItems", at = @At("HEAD"), require = 0)
    private void kronwerke$items(CallbackInfoReturnable<ItemStack[]> ci) {
        LoadPump.pulse();
    }
}
