package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.RadiationScan;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets Nuclear Radiation's chunk load scan skip sections without radioactive blocks, see RadiationScan. */
@Pseudo
@Mixin(targets = "igentuman.nr.events.RadSourceEvents", remap = false)
public abstract class RadiationScanMixin {

    @Redirect(method = "scanBlocksAndFluids", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z"),
            require = 0)
    private boolean kronwerke$skipQuietSections(LevelChunkSection section) {
        return RadiationScan.skip(section);
    }
}
