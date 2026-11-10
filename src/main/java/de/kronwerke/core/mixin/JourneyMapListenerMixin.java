package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * JourneyMap tells its plugins that mapping started or stopped. On a server transfer it stops
 * mapping inside the transfer packet, so one plugin that threw there ended the transfer with
 * "Network Protocol Error" (Create Ore Excavation did, see OreVeinsOverlayMixin). An error in a
 * plugin's mapping listener is now logged and the transfer, and JourneyMap, go on.
 */
@Pseudo
@Mixin(targets = "journeymap.api.client.impl.ClientEventManager", remap = false)
public abstract class JourneyMapListenerMixin {

    @Unique
    private static boolean kronwerke$warned;

    @WrapMethod(method = "fireMappingEvent(ZLnet/minecraft/resources/ResourceKey;)V", require = 0)
    private void kronwerke$neverBreakTheCaller(boolean started, ResourceKey<Level> dimension, Operation<Void> original) {
        try {
            original.call(started, dimension);
        } catch (RuntimeException e) {
            if (!kronwerke$warned) {
                kronwerke$warned = true;
                KronwerkeCore.LOGGER.warn("A JourneyMap plugin failed as mapping {}; skipped: {}", started ? "started" : "stopped", e.toString());
            }
        }
    }
}
