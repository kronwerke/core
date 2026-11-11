package de.kronwerke.core.mixin;

import appeng.api.stacks.AEKey;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.MEStorageMenu;
import de.kronwerke.core.share.ae2.BridgeLayer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player takes something out of a terminal that is still in main's storage (the network is
 * linked over a quantum bridge between servers): the click gives nothing in that moment, the
 * things are fetched (see {@link BridgeLayer}), so it is done once more as soon as they are
 * here. The player clicks once, as always (seen on the real servers before: the first click on
 * the gold gave nothing, the second took it).
 */
@Pseudo
@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class AeTakeReplayMixin {
    @Shadow
    protected abstract void handleNetworkInteraction(ServerPlayer player, AEKey clickedKey, InventoryAction action);

    @Inject(method = "handleNetworkInteraction", at = @At("HEAD"), require = 0)
    private void kronwerke$watch(ServerPlayer player, AEKey clickedKey, InventoryAction action, CallbackInfo ci) {
        BridgeLayer.clickBegins();
    }

    @Inject(method = "handleNetworkInteraction", at = @At("RETURN"), require = 0)
    private void kronwerke$again(ServerPlayer player, AEKey clickedKey, InventoryAction action, CallbackInfo ci) {
        Object self = this;
        BridgeLayer.clickEnds(() -> {
            if (player.containerMenu == self && !player.hasDisconnected()) handleNetworkInteraction(player, clickedKey, action);
        });
    }
}
