package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.portal.Dimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Modular Routers' Sender Module Mk3 sends items into any dimension. A dimension played on
 * another server only exists here as a copy nobody can visit, so the items would be lost
 * there: the module answers "bad dimension" instead and keeps them in the router.
 */
@Pseudo
@Mixin(targets = "me.desht.modularrouters.item.module.SenderModule3", remap = false)
public abstract class RouterSenderDimensionMixin {
    @Inject(method = "canOperateInDimension", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$notElsewhere(ResourceKey<Level> dim, CallbackInfoReturnable<Boolean> cir) {
        if (!Dimensions.here(dim)) cir.setReturnValue(false);
    }
}
