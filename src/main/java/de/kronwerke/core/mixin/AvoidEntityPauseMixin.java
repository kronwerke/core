package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.perf.GoalPause;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;

/** Fleeing asks again a few ticks after a no, see {@link GoalPause}. */
@Mixin(AvoidEntityGoal.class)
public abstract class AvoidEntityPauseMixin {

    @Shadow
    @Final
    protected PathfinderMob mob;

    @Unique
    private long kronwerke$nextCheck;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void kronwerke$wait(CallbackInfoReturnable<Boolean> cir) {
        if (GoalPause.waiting(this.mob, this.kronwerke$nextCheck)) cir.setReturnValue(false);
    }

    @Inject(method = "canUse", at = @At("RETURN"))
    private void kronwerke$afterNo(CallbackInfoReturnable<Boolean> cir) {
        this.kronwerke$nextCheck = cir.getReturnValueZ() ? 0 : GoalPause.next(this.mob);
    }
}
