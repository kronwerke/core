package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Map;

/**
 * Nuclear Radiation hands out a fresh copy of its tag bindings to whoever asks, and its lookup
 * of an item's radiation asks once per item: its JEI page goes through every item of the game
 * that way, which copied the whole table tens of thousands of times while the client stood
 * still at every login (the largest single part of JEI's start on the test client). One copy
 * is good for a second; the table only changes when data packs are loaded.
 */
@Pseudo
@Mixin(targets = "igentuman.nr.api.binding.Bindings", remap = false)
public abstract class RadiationTagsCopyMixin {
    @Unique
    private static volatile Map<?, ?> kronwerke$items, kronwerke$blocks, kronwerke$fluids;
    @Unique
    private static volatile long kronwerke$itemsAt, kronwerke$blocksAt, kronwerke$fluidsAt;

    @Inject(method = "itemTags", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$itemTagsCached(CallbackInfoReturnable<Map<?, ?>> cir) {
        Map<?, ?> m = kronwerke$items;
        if (m != null && System.nanoTime() - kronwerke$itemsAt < 1_000_000_000L) cir.setReturnValue(m);
    }

    @Inject(method = "itemTags", at = @At("RETURN"), cancellable = true, require = 0)
    private static void kronwerke$itemTagsKeep(CallbackInfoReturnable<Map<?, ?>> cir) {
        if (cir.getReturnValue() == kronwerke$items) return;
        kronwerke$items = Collections.unmodifiableMap(cir.getReturnValue());
        kronwerke$itemsAt = System.nanoTime();
        cir.setReturnValue(kronwerke$items);
    }

    @Inject(method = "blockTags", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$blockTagsCached(CallbackInfoReturnable<Map<?, ?>> cir) {
        Map<?, ?> m = kronwerke$blocks;
        if (m != null && System.nanoTime() - kronwerke$blocksAt < 1_000_000_000L) cir.setReturnValue(m);
    }

    @Inject(method = "blockTags", at = @At("RETURN"), cancellable = true, require = 0)
    private static void kronwerke$blockTagsKeep(CallbackInfoReturnable<Map<?, ?>> cir) {
        if (cir.getReturnValue() == kronwerke$blocks) return;
        kronwerke$blocks = Collections.unmodifiableMap(cir.getReturnValue());
        kronwerke$blocksAt = System.nanoTime();
        cir.setReturnValue(kronwerke$blocks);
    }

    @Inject(method = "fluidTags", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$fluidTagsCached(CallbackInfoReturnable<Map<?, ?>> cir) {
        Map<?, ?> m = kronwerke$fluids;
        if (m != null && System.nanoTime() - kronwerke$fluidsAt < 1_000_000_000L) cir.setReturnValue(m);
    }

    @Inject(method = "fluidTags", at = @At("RETURN"), cancellable = true, require = 0)
    private static void kronwerke$fluidTagsKeep(CallbackInfoReturnable<Map<?, ?>> cir) {
        if (cir.getReturnValue() == kronwerke$fluids) return;
        kronwerke$fluids = Collections.unmodifiableMap(cir.getReturnValue());
        kronwerke$fluidsAt = System.nanoTime();
        cir.setReturnValue(kronwerke$fluids);
    }
}
