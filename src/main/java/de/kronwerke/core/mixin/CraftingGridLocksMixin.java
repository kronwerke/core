package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.kronwerke.core.lock.ActingContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/** The crafting table and the inventory's grid: whoever crafts is the one told about a stage lock. */
@Mixin(CraftingMenu.class)
public abstract class CraftingGridLocksMixin {

    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<ServerPlayer> kronwerke$before = new ThreadLocal<>();

    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"))
    private static void kronwerke$crafter(AbstractContainerMenu menu, Level level, Player player, CraftingContainer slots, ResultContainer result,
                                          RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp) kronwerke$before.set(ActingContext.player(sp, true));
    }

    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void kronwerke$done(AbstractContainerMenu menu, Level level, Player player, CraftingContainer slots, ResultContainer result,
                                       RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        if (player instanceof ServerPlayer) {
            ActingContext.restorePlayer(kronwerke$before.get());
            kronwerke$before.remove();
        }
    }
}
