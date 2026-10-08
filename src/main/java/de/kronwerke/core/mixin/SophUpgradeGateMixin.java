package de.kronwerke.core.mixin;

import de.kronwerke.core.share.soph.Gate;
import de.kronwerke.core.share.soph.SophWrapperAccess;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A linked storage group that is on another server: no upgrade goes in or out here. */
@Mixin(value = UpgradeHandler.class, remap = false)
public abstract class SophUpgradeGateMixin implements SophWrapperAccess {
    @Shadow @Final private IStorageWrapper storageWrapper;

    @Override
    public IStorageWrapper kronwerke$wrapper() {
        return storageWrapper;
    }

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$insert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (Gate.blocked(storageWrapper)) cir.setReturnValue(stack);
    }

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$extract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (Gate.blocked(storageWrapper)) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "setStackInSlot", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$set(int slot, ItemStack stack, CallbackInfo ci) {
        if (Gate.blocked(storageWrapper)) ci.cancel();
    }

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$valid(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Gate.blocked(storageWrapper)) cir.setReturnValue(false);
    }
}
