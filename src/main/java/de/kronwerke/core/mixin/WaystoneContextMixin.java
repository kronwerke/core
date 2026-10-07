package de.kronwerke.core.mixin;

import com.mojang.datafixers.util.Either;
import de.kronwerke.core.share.ws.WaystoneLayer;
import net.blay09.mods.waystones.InternalMethodsImpl;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.core.WaystoneTeleportContextImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Waystones refuses a jump to a dimension this server does not have before it ever gets to the
 * jump; for a waystone on main (share/ws/WaystoneLayer) the jump is a move home, so it gets its
 * context like any other and WaystoneTravelMixin takes over from there.
 */
@Mixin(value = InternalMethodsImpl.class, remap = false)
public abstract class WaystoneContextMixin {

    @Inject(method = "createUncheckedCustomTeleportContext", at = @At("HEAD"), cancellable = true)
    private void kronwerke$remoteUnchecked(Entity entity, Waystone waystone, CallbackInfoReturnable<Either<WaystoneTeleportContext, WaystoneTeleportError>> cir) {
        if (WaystoneLayer.isRemote(waystone)) cir.setReturnValue(Either.left(new WaystoneTeleportContextImpl(entity, waystone)));
    }

    @Inject(method = "createCustomTeleportContext", at = @At("HEAD"), cancellable = true)
    private void kronwerke$remote(Entity entity, Waystone waystone, CallbackInfoReturnable<Either<WaystoneTeleportContext, WaystoneTeleportError>> cir) {
        if (WaystoneLayer.isRemote(waystone)) cir.setReturnValue(Either.left(new WaystoneTeleportContextImpl(entity, waystone)));
    }
}
