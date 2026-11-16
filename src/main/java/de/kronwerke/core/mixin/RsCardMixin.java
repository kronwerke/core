package de.kronwerke.core.mixin;

import de.kronwerke.core.share.rs.RsBridgeLayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Refined Storage network card bound to a receiver remembers the server it was bound on (RsBridgeLayer). */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.common.networking.NetworkCardItem", remap = false)
public abstract class RsCardMixin {

    @Inject(method = "useOn", at = @At("RETURN"), require = 0)
    private void kronwerke$bound(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (ctx.getLevel().isClientSide() || !cir.getReturnValue().consumesAction()) return;
        RsBridgeLayer.bound(ctx.getItemInHand());
    }
}
