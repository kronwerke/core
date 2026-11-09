package de.kronwerke.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import de.kronwerke.core.perf.SpawnGate;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;

/** Skips the per tick mob count on ticks where no category may spawn, see {@link SpawnGate}. */
@Mixin(ServerChunkCache.class)
public abstract class SpawnCountGateMixin {

    @Shadow @Final ServerLevel level;
    @Shadow private NaturalSpawner.SpawnState lastSpawnState;

    @WrapOperation(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"))
    private NaturalSpawner.SpawnState kronwerke$countWhenDue(int chunks, Iterable<Entity> entities, NaturalSpawner.ChunkGetter getter, LocalMobCapCalculator caps, Operation<NaturalSpawner.SpawnState> original) {
        if (this.lastSpawnState != null && !SpawnGate.due(this.level)) return this.lastSpawnState;
        return original.call(chunks, entities, getter, caps);
    }
}
