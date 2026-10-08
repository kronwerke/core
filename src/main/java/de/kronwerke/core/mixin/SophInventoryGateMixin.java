package de.kronwerke.core.mixin;

import de.kronwerke.core.share.soph.Gate;
import de.kronwerke.core.share.soph.SophWrapperAccess;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A linked storage group that is on another server: its inventory here neither takes nor gives. */
@Mixin(value = InventoryHandler.class, remap = false)
public abstract class SophInventoryGateMixin implements SophWrapperAccess {
    @Shadow @Final protected IStorageWrapper storageWrapper;

    @Override
    public IStorageWrapper kronwerke$wrapper() {
        return storageWrapper;
    }

    @Inject(method = "insertItemInternal", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$insertSlot(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (Gate.blocked(storageWrapper)) cir.setReturnValue(stack);
    }

    @Inject(method = "insertItem(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$insert(ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (Gate.blocked(storageWrapper)) cir.setReturnValue(stack);
    }

    @Inject(method = "extractItemInternal", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$extractSlot(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (Gate.blocked(storageWrapper)) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "extractItem(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$extract(ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (Gate.blocked(storageWrapper)) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "setStackInSlot", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$set(int slot, ItemStack stack, CallbackInfo ci) {
        if (Gate.blocked(storageWrapper)) ci.cancel();
    }

    @Inject(method = "isItemValid(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void kronwerke$valid(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Gate.blocked(storageWrapper)) cir.setReturnValue(false);
    }
}
