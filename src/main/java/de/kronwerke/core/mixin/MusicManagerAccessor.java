package de.kronwerke.core.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How long the game waits before its next piece of music: set when the menu's music hands over to the world. */
@Mixin(MusicManager.class)
public interface MusicManagerAccessor {
    @Accessor("nextSongDelay")
    void kronwerke$nextSongDelay(int ticks);
}
