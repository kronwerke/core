package de.kronwerke.core.mixin;

import java.util.Map;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.gson.JsonElement;

import de.kronwerke.core.lock.MachineLocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Holds back the recipes of stages that are not open, and tells why one was not found; see {@link MachineLocks}. */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerLocksMixin {

    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Shadow
    public abstract java.util.Collection<RecipeHolder<?>> getRecipes();

    @Shadow
    public abstract void replaceRecipes(Iterable<RecipeHolder<?>> recipes);

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void kronwerke$holdBackLaterStages(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        var all = getRecipes();
        var keep = MachineLocks.filter(all, resourceManager, registries, object);
        if (keep != all) replaceRecipes(keep);
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("RETURN"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void kronwerke$tellWhy(RecipeType<?> type, RecipeInput input, Level level, RecipeHolder<?> last, CallbackInfoReturnable<Optional> cir) {
        if (!cir.getReturnValue().isEmpty()) return;
        RecipeHolder<?> instead = MachineLocks.missed(type, input, level);
        if (instead != null) cir.setReturnValue(Optional.of(instead));
    }
}
