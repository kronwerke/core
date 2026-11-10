package de.kronwerke.core.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Ars Additions writes "no local weather" into every chunk each time a player starts watching it,
 * even when the chunk already says so (in a release build its local weather is always reset to
 * none). Writing the attachment marks the chunk as changed, so every chunk anyone looked at was
 * written to disk again on autosave and unload: 2 700 of them in two minutes of the load test.
 * A chunk without the attachment already counts as "none" everywhere in the mod, so the write is
 * skipped when the chunk has no local weather or already has none. A chunk with real local
 * weather is reset as before.
 */
@Pseudo
@Mixin(targets = "com.github.jarva.arsadditions.common.data.WeatherStatus", remap = false)
public abstract class ArsWeatherDirtyMixin {

    @Unique
    private static AttachmentType<?> kronwerke$type;

    @Inject(method = "setWeatherStatus(Lnet/neoforged/neoforge/event/level/ChunkWatchEvent$Watch;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$skipNoop(ChunkWatchEvent.Watch event, CallbackInfo ci) {
        if (kronwerke$type == null) {
            kronwerke$type = NeoForgeRegistries.ATTACHMENT_TYPES.get(ResourceLocation.fromNamespaceAndPath("ars_additions", "local_weather_status"));
            if (kronwerke$type == null) return;
        }
        LevelChunk chunk = event.getChunk();
        Optional<?> status = chunk.getExistingData(kronwerke$type);
        if (status.isEmpty() || (status.get() instanceof Enum<?> e && e.name().equals("NONE"))) ci.cancel();
    }
}
