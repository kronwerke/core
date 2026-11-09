package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.kronwerke.core.perf.SpawnGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;

/** Leaves the spawner on ticks where no category may spawn, see {@link de.kronwerke.core.perf.SpawnGate}. */
@Mixin(NaturalSpawner.class)
public abstract class SpawnChunkGateMixin {

    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void kronwerke$onlyWhenDue(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, boolean friendlies, boolean monsters, boolean forced, CallbackInfo ci) {
        if (!SpawnGate.due(level)) ci.cancel();
    }
}
