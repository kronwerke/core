package de.kronwerke.core.mixin;

import de.kronwerke.core.portal.Travel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Between the snapshot that travels to another server and the transfer, the player's hands
 * and inventory do nothing: no drop, no click in a menu, no use of an item or a block, no
 * attack. Without this, what a player dropped or stored in that second existed on both
 * servers. Checked after the packet has moved to the server thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TravelFreezeMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = {"handlePlayerAction", "handleContainerClick", "handleUseItem", "handleUseItemOn", "handleInteract",
            "handleSetCreativeModeSlot", "handlePlaceRecipe", "handleContainerButtonClick", "handleSelectTrade", "handlePickItem",
            "handleRenameItem"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void kronwerke$frozen(CallbackInfo ci) {
        if (Travel.frozen(player)) ci.cancel();
    }
}
