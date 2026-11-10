package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import de.kronwerke.core.lock.ActingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Which machine ticks right now, so the players next to it hear about a stage lock. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BlockEntityContextMixin {

    @Shadow
    @Final
    private BlockEntity blockEntity;

    @WrapMethod(method = "tick")
    private void kronwerke$machine(Operation<Void> original) {
        BlockPos before = ActingContext.blockEntity(this.blockEntity.getBlockPos());
        try {
            original.call();
        } finally {
            ActingContext.blockEntity(before);
        }
    }
}
