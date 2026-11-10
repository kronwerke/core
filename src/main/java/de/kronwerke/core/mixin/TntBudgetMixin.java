package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import de.kronwerke.core.perf.TntBudget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;

/** Spreads TNT explosions over ticks, see {@link TntBudget}. */
@Mixin(PrimedTnt.class)
public abstract class TntBudgetMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kronwerke$waitWhenFull(CallbackInfo ci) {
        PrimedTnt self = (PrimedTnt) (Object) this;
        // a fuse of 1 runs out in this tick; while the budget is used up the TNT holds still
        // with that fuse and tries again next tick
        if (self.getFuse() <= 1 && self.level() instanceof ServerLevel level && TntBudget.full(level.getServer())) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/PrimedTnt;explode()V"))
    private void kronwerke$measure(PrimedTnt self, Operation<Void> original) {
        if (!(self.level() instanceof ServerLevel level)) {
            original.call(self);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(self);
        } finally {
            TntBudget.spent(level.getServer(), System.nanoTime() - start);
        }
    }
}
