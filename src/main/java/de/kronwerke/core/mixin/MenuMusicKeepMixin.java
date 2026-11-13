package de.kronwerke.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.kronwerke.core.client.MenuMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The game stops every sound whenever it leaves a connection, and it "leaves" one right
 * before it opens one: pressing the join button cuts the menu's music off. With nothing but
 * the menu's music to stop (there is no world), the stop is left out, and the music plays on
 * through the connecting and under the join picture.
 */
@Mixin(Minecraft.class)
public class MenuMusicKeepMixin {
    @WrapOperation(method = "updateScreenAndTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;stop()V"))
    private void kronwerke$keepMenuMusic(SoundManager sounds, Operation<Void> stop) {
        if (!MenuMusic.keepsPlaying((Minecraft) (Object) this)) stop.call(sounds);
    }
}
