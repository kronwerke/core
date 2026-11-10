package de.kronwerke.core.mixin;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.perf.SpreadAutosave;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;

/** Queues the autosave's chunks and writes them a few per tick, see {@link SpreadAutosave}. */
@Mixin(ChunkMap.class)
public abstract class SpreadAutosaveMixin {

    @Shadow @Final ServerLevel level;
    @Shadow private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Shadow
    protected abstract boolean saveChunkIfNeeded(ChunkHolder holder);

    @Unique
    private final ArrayDeque<ChunkHolder> kronwerke$toSave = new ArrayDeque<>();
    @Unique
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet kronwerke$queued = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    @Inject(method = "saveAllChunks", at = @At("HEAD"), cancellable = true)
    private void kronwerke$queue(boolean flush, CallbackInfo ci) {
        if (flush) {
            this.kronwerke$toSave.clear(); // everything is saved right now anyway
            this.kronwerke$queued.clear();
            SpreadAutosave.draining(this.level, false);
            return;
        }
        if (!SpreadAutosave.autosaving || !KronwerkeConfig.SPREAD_AUTOSAVE.get()) return;
        // a queue that did not drain since the last autosave goes on where it was: chunks late in
        // the order are not pushed back to the start again (they were only written on unload)
        for (ChunkHolder h : this.visibleChunkMap.values()) {
            if (this.kronwerke$queued.add(h.getPos().toLong())) this.kronwerke$toSave.add(h);
        }
        if (!this.kronwerke$toSave.isEmpty()) SpreadAutosave.draining(this.level, true);
        KronwerkeCore.LOGGER.debug("Autosave of {} spread out: {} chunks to look at", this.level.dimension().location(), this.kronwerke$toSave.size());
        ci.cancel();
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void kronwerke$drain(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        if (this.kronwerke$toSave.isEmpty() || this.level.noSave()) return; // save-off pauses it too
        long budget = (long) (KronwerkeConfig.AUTOSAVE_MS_PER_TICK.get() * 1_000_000L);
        long start = System.nanoTime();
        boolean wrote = false;
        ChunkHolder holder;
        while ((holder = this.kronwerke$toSave.poll()) != null) {
            this.kronwerke$queued.remove(holder.getPos().toLong());
            if (this.visibleChunkMap.get(holder.getPos().toLong()) != holder) continue; // unloaded since, saved on unload
            if (this.saveChunkIfNeeded(holder)) wrote = true;
            if (wrote && System.nanoTime() - start >= budget) break;
        }
        if (this.kronwerke$toSave.isEmpty()) {
            KronwerkeCore.LOGGER.debug("Autosave of {} done", this.level.dimension().location());
            SpreadAutosave.draining(this.level, false);
        }
    }
}
