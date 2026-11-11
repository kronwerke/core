package de.kronwerke.core.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import de.kronwerke.core.lock.MachineLocks;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Clients get every recipe, the held back ones too, see {@link MachineLocks#forClients}. */
@Mixin(ClientboundUpdateRecipesPacket.class)
public abstract class RecipesForClientsMixin {

    @ModifyVariable(method = "<init>(Ljava/util/Collection;)V", at = @At("HEAD"), argsOnly = true)
    private static Collection<RecipeHolder<?>> kronwerke$everyRecipe(Collection<RecipeHolder<?>> recipes) {
        return MachineLocks.forClients(recipes);
    }
}
