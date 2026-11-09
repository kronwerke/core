package de.kronwerke.core.mixin;

import java.util.Optional;

import de.kronwerke.core.perf.EmptyCurios;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/** Searches in the curio slots of a mob that wears nothing find nothing at once, see EmptyCurios. */
@Pseudo
@Mixin(targets = "top.theillusivec4.curios.common.capability.CurioInventoryCapability", remap = false)
public abstract class CuriosEmptyMobMixin {

    @Shadow
    public abstract LivingEntity getWearer();

    @Inject(method = {
            "findFirstCurio(Lnet/minecraft/world/item/Item;)Ljava/util/Optional;",
            "findFirstCurio(Ljava/util/function/Predicate;)Ljava/util/Optional;",
            "findFirstCurio(Ljava/util/function/Predicate;Ljava/lang/String;)Ljava/util/Optional;",
            "findFirstCurio(Ljava/util/function/Predicate;ZLjava/lang/String;)Ljava/util/Optional;"
    }, at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$nothingOnEmptyMobs(CallbackInfoReturnable<Optional<?>> cir) {
        if (EmptyCurios.emptyMob(getWearer())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
