package de.kronwerke.core.mixin;

import de.kronwerke.core.lock.LockedItems;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chapters drops every locked item out of the inventory once a second. Core only keeps
 * locked items out of hands and armour slots, see {@link LockedItems}.
 */
@Pseudo
@Mixin(targets = "com.gabinx.chapters.event.InventoryAuditor", remap = false)
public abstract class ChaptersAuditMixin {

    @Inject(method = "auditNow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$audit(ServerPlayer player, CallbackInfo ci) {
        LockedItems.audit(player);
        ci.cancel();
    }
}
