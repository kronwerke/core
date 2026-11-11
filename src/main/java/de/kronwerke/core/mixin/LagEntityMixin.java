package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import de.kronwerke.core.perf.LagScan;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** /kw admin lag: times entity ticks (with their passengers) and chunk ticks while a scan runs. */
@Mixin(ServerLevel.class)
public abstract class LagEntityMixin {

    @WrapMethod(method = "tickNonPassenger")
    private void kronwerke$timeEntity(Entity entity, Operation<Void> original) {
        if (!LagScan.on) {
            original.call(entity);
            return;
        }
        long t0 = System.nanoTime();
        try {
            original.call(entity);
        } finally {
            LagScan.entity(entity, System.nanoTime() - t0);
        }
    }

    @WrapMethod(method = "tickChunk")
    private void kronwerke$timeChunk(LevelChunk chunk, int randomTickSpeed, Operation<Void> original) {
        if (!LagScan.on) {
            original.call(chunk, randomTickSpeed);
            return;
        }
        long t0 = System.nanoTime();
        try {
            original.call(chunk, randomTickSpeed);
        } finally {
            ChunkPos pos = chunk.getPos();
            LagScan.chunk((ServerLevel) (Object) this, pos, System.nanoTime() - t0);
        }
    }
}
