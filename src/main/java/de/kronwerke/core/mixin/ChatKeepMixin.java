package de.kronwerke.core.mixin;

import de.kronwerke.core.client.TravelOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The chat stays while a player moves between the servers of the network: the client empties
 * it at every disconnect, so every move wiped what had just been said.
 */
@Mixin(ChatComponent.class)
public abstract class ChatKeepMixin {
    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void kronwerke$keepOnMove(boolean clearSentMsgHistory, CallbackInfo ci) {
        if (TravelOverlay.moving()) ci.cancel();
    }
}
