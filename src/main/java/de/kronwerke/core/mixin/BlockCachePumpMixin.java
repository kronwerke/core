package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** A turn for the screen inside long work on the game's thread; see {@link LoadPump}. */
@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase")
public abstract class BlockCachePumpMixin {
    /** After the tags every block state works out its shapes again: once per state. */
    @Inject(method = "initCache", at = @At("HEAD"), require = 0)
    private void kronwerke$cache(CallbackInfo ci) {
        LoadPump.pulse();
    }
}
