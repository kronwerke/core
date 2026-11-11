package de.kronwerke.core.mixin;

import de.kronwerke.core.share.mek.QioLayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player takes something out of a QIO window that only another server has: the click gives
 * nothing in that moment (the things are asked for, see {@link QioLayer}), so it is done once
 * more as soon as they are here, a split second later. The player clicks once, as always.
 */
@Pseudo
@Mixin(targets = {"mekanism.common.network.to_server.qio.PacketQIOItemViewerSlotTake",
        "mekanism.common.network.to_server.qio.PacketQIOItemViewerSlotShiftTake"}, remap = false)
public abstract class QioTakeReplayMixin {
    @Shadow
    public abstract void handle(IPayloadContext context);

    @Inject(method = "handle", at = @At("HEAD"), require = 0)
    private void kronwerke$watch(IPayloadContext context, CallbackInfo ci) {
        QioLayer.clickBegins();
    }

    @Inject(method = "handle", at = @At("RETURN"), require = 0)
    private void kronwerke$again(IPayloadContext context, CallbackInfo ci) {
        QioLayer.clickEnds(() -> handle(context));
    }
}
