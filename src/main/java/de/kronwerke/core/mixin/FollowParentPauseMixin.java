package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.perf.GoalPause;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.animal.Animal;

/** A baby looks for its parent again a few ticks after a no, see {@link GoalPause}. */
@Mixin(FollowParentGoal.class)
public abstract class FollowParentPauseMixin {

    @Shadow
    @Final
    private Animal animal;

    @Unique
    private long kronwerke$nextCheck;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void kronwerke$wait(CallbackInfoReturnable<Boolean> cir) {
        // adults answer no at once without searching; only babies are worth pausing
        if (this.animal.getAge() < 0 && GoalPause.waiting(this.animal, this.kronwerke$nextCheck)) cir.setReturnValue(false);
    }

    @Inject(method = "canUse", at = @At("RETURN"))
    private void kronwerke$afterNo(CallbackInfoReturnable<Boolean> cir) {
        this.kronwerke$nextCheck = cir.getReturnValueZ() || this.animal.getAge() >= 0 ? 0 : GoalPause.next(this.animal);
    }
}
