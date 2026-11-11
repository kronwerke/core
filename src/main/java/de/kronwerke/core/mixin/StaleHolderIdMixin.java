package de.kronwerke.core.mixin;

import de.kronwerke.core.client.JeiKeep;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An object of a registry of the connection before a move (an enchantment in a stack JEI
 * kept) gets the number of the object with the same name in this connection's registry,
 * instead of none; see {@link JeiKeep}.
 */
@Mixin(MappedRegistry.class)
public abstract class StaleHolderIdMixin<T> {
    @Inject(method = "getId(Ljava/lang/Object;)I", at = @At("RETURN"), cancellable = true)
    private void kronwerke$staleId(T value, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() != -1 || value == null) return;
        ResourceLocation name = JeiKeep.staleName(value);
        if (name == null) return;
        @SuppressWarnings("unchecked") MappedRegistry<T> self = (MappedRegistry<T>) (Object) this;
        T now = self.get(name);
        if (now != null && now != value) cir.setReturnValue(self.getId(now));
    }
}
