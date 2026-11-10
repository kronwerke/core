package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import de.kronwerke.core.net.JoinGrace;

/** The client waits two minutes for a silent server instead of 30 seconds, see {@link JoinGrace}. */
@Mixin(targets = "net.minecraft.network.Connection$1")
public abstract class ClientReadTimeoutMixin {

    @ModifyConstant(method = "initChannel", require = 0, constant = @Constant(intValue = 30))
    private int kronwerke$longer(int original) {
        return JoinGrace.READ_TIMEOUT_S;
    }
}
