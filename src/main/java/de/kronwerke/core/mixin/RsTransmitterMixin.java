package de.kronwerke.core.mixin;

import com.refinedmods.refinedstorage.common.networking.NetworkTransmitterBlockEntity;
import com.refinedmods.refinedstorage.common.networking.NetworkTransmitterData;
import com.refinedmods.refinedstorage.common.networking.NetworkTransmitterState;
import de.kronwerke.core.share.rs.RsBridgeLayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Refined Storage network transmitter whose card names a receiver on main (RsBridgeLayer): the
 * layer learns of it while it works, and while it is linked it shows as transmitting instead of
 * "receiver unreachable" and does not keep rebuilding its network looking for the receiver here.
 * Nothing without Refined Storage.
 */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.common.networking.NetworkTransmitterBlockEntity", remap = false)
public abstract class RsTransmitterMixin {

    @Inject(method = "doWork", at = @At("TAIL"), require = 0)
    private void kronwerke$seen(CallbackInfo ci) {
        RsBridgeLayer.seen((NetworkTransmitterBlockEntity) (Object) this);
    }

    @Inject(method = "tryReconnectingWithReceiver", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$noRebuild(CallbackInfo ci) {
        if (RsBridgeLayer.linked((NetworkTransmitterBlockEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "calculateState", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$state(CallbackInfoReturnable<NetworkTransmitterState> cir) {
        if (RsBridgeLayer.linked((NetworkTransmitterBlockEntity) (Object) this)) cir.setReturnValue(NetworkTransmitterState.ACTIVE);
    }

    @Inject(method = "getStatus", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$status(CallbackInfoReturnable<NetworkTransmitterData> cir) {
        if (RsBridgeLayer.linked((NetworkTransmitterBlockEntity) (Object) this)) {
            cir.setReturnValue(new NetworkTransmitterData(false, true,
                    Component.translatableWithFallback("kronwerke.rs.linked", "Verbunden mit %s", RsBridgeLayer.labelText())));
        }
    }
}
