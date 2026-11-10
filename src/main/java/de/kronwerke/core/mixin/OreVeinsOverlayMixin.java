package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import de.kronwerke.core.KronwerkeCore;

/**
 * Create Ore Excavation saves its JourneyMap vein markers when JourneyMap stops mapping. On a
 * transfer to another server (the portal to the mining world) JourneyMap stops mapping inside
 * the transfer packet, at a moment when it has no data folder any more; the save then threw a
 * NullPointerException, which broke the transfer packet and ended in "Network Protocol Error".
 * Without a folder there is nothing to save to, so the error is caught and the transfer goes on.
 */
@Pseudo
@Mixin(targets = "com.tom.createores.jm.OreVeinsOverlay", remap = false)
public abstract class OreVeinsOverlayMixin {

    @Unique
    private static boolean kronwerke$warned;

    @WrapMethod(method = "onMappingStopped()V", require = 0)
    private void kronwerke$neverBreakTheTransfer(Operation<Void> original) {
        try {
            original.call();
        } catch (RuntimeException e) {
            if (!kronwerke$warned) {
                kronwerke$warned = true;
                KronwerkeCore.LOGGER.warn("Create Ore Excavation could not save its map veins as JourneyMap stopped: {}", e.toString());
            }
        }
    }
}
