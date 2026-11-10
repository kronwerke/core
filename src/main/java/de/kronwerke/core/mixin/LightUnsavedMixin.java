package de.kronwerke.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.perf.LightPending;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * Light updates on full chunks only mark them as changed when light really changed nearby, see
 * {@link LightPending}. Chunks still being generated and every client update are untouched.
 */
@Mixin(ChunkHolder.class)
public abstract class LightUnsavedMixin {

    @Shadow @Final private LevelLightEngine lightEngine;

    @WrapOperation(method = "sectionLightChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setUnsaved(Z)V"))
    private void kronwerke$onlyRealLight(ChunkAccess chunk, boolean unsaved, Operation<Void> original) {
        if ((chunk instanceof LevelChunk || chunk instanceof ImposterProtoChunk)
                && this.lightEngine instanceof LightPending pending
                && !pending.kronwerke$recent(chunk.getPos().toLong())
                && KronwerkeConfig.CLEAN_LIGHT_UPDATES.get()) {
            return;
        }
        original.call(chunk, unsaved);
    }
}
