package de.kronwerke.core.mixin;

import de.kronwerke.core.share.ws.WaystoneLayer;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystoneTeleportResult;
import net.blay09.mods.waystones.core.WaystoneTeleportManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * A waystone that stands on main, chosen on a side world: no jump inside this world, the player
 * moves home and stands at it there (share/ws/WaystoneLayer). Free, like the portal.
 */
@Mixin(value = WaystoneTeleportManager.class, remap = false)
public abstract class WaystoneTravelMixin {

    @Inject(method = "tryTeleport", at = @At("HEAD"), cancellable = true)
    private static void kronwerke$toMain(WaystoneTeleportContext ctx, CallbackInfoReturnable<WaystoneTeleportResult> cir) {
        if (ctx.getTargetWaystone() == null || !WaystoneLayer.isRemote(ctx.getTargetWaystone())) return;
        if (ctx.getEntity() instanceof ServerPlayer p) WaystoneLayer.travel(p, ctx.getTargetWaystone());
        cir.setReturnValue(new WaystoneTeleportResult(new ArrayList<>()));
    }

    /** The waystone menu goes this way. */
    @Inject(method = "tryTeleportAsync", at = @At("HEAD"), cancellable = true)
    private static void kronwerke$toMainAsync(WaystoneTeleportContext ctx, CallbackInfoReturnable<CompletableFuture<WaystoneTeleportResult>> cir) {
        if (ctx.getTargetWaystone() == null || !WaystoneLayer.isRemote(ctx.getTargetWaystone())) return;
        if (ctx.getEntity() instanceof ServerPlayer p) WaystoneLayer.travel(p, ctx.getTargetWaystone());
        cir.setReturnValue(CompletableFuture.completedFuture(new WaystoneTeleportResult(new ArrayList<>())));
    }
}
