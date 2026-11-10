package de.kronwerke.core.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.perf.LightPending;
import de.kronwerke.core.perf.QuietLight;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;

/** Records real light changes per chunk, see {@link LightPending}. */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class LightPendingMixin implements LightPending {

    @Unique
    private final Long2LongOpenHashMap kronwerke$changed = new Long2LongOpenHashMap();

    @Unique
    private long kronwerke$lastPrune;

    @Inject(method = "checkBlock", at = @At("HEAD"))
    private void kronwerke$blockChanged(BlockPos pos, CallbackInfo ci) {
        this.kronwerke$markAround(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Inject(method = "lightChunk", at = @At("HEAD"))
    private void kronwerke$firstLight(ChunkAccess chunk, boolean isLighted, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!isLighted) {
            ChunkPos pos = chunk.getPos();
            this.kronwerke$markAround(pos.x, pos.z);
        } else if (chunk instanceof ImposterProtoChunk full && !full.getWrapped().isUnsaved()
                && KronwerkeConfig.CLEAN_CHUNK_LOADS.get()) {
            ((QuietLight) full.getWrapped()).kronwerke$quietLight(); // see QuietLight
        }
    }

    @Override
    public void kronwerke$markAround(int chunkX, int chunkZ) {
        long now = System.currentTimeMillis();
        synchronized (this.kronwerke$changed) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    this.kronwerke$changed.put(ChunkPos.asLong(chunkX + dx, chunkZ + dz), now);
                }
            }
            if (now - this.kronwerke$lastPrune > WINDOW_MS) {
                this.kronwerke$lastPrune = now;
                this.kronwerke$changed.long2LongEntrySet().removeIf(e -> now - e.getLongValue() > WINDOW_MS);
            }
        }
    }

    @Override
    public boolean kronwerke$recent(long chunkPos) {
        synchronized (this.kronwerke$changed) {
            long at = this.kronwerke$changed.getOrDefault(chunkPos, Long.MIN_VALUE);
            return at != Long.MIN_VALUE && System.currentTimeMillis() - at <= WINDOW_MS;
        }
    }
}
