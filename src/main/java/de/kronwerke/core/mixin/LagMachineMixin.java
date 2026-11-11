package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import de.kronwerke.core.perf.LagScan;
import net.minecraft.world.level.block.entity.BlockEntity;

/** /kw admin lag: times every machine (block entity) tick while a scan runs. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class LagMachineMixin {

    @Shadow
    @Final
    private BlockEntity blockEntity;

    @WrapMethod(method = "tick")
    private void kronwerke$timeMachine(Operation<Void> original) {
        if (!LagScan.on) {
            original.call();
            return;
        }
        long t0 = System.nanoTime();
        try {
            original.call();
        } finally {
            LagScan.machine(this.blockEntity, System.nanoTime() - t0);
        }
    }
}
