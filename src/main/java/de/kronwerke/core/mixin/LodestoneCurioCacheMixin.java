package de.kronwerke.core.mixin;

import de.kronwerke.core.perf.TickMemo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lodestone's "does this entity wear item X" (Malum asks it in every visibility check, for
 * the necklace that hides players from mobs) searches every curio slot each time. The answer
 * per entity and item is kept for the rest of the server tick.
 */
@Pseudo
@Mixin(targets = "team.lodestar.lodestone.helpers.CurioHelper", remap = false)
public abstract class LodestoneCurioCacheMixin {

    @Inject(method = "hasCurioEquipped(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/Item;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$cached(LivingEntity entity, Item item, CallbackInfoReturnable<Boolean> cir) {
        Boolean known = TickMemo.get(item, entity);
        if (known != null) {
            cir.setReturnValue(known);
        }
    }

    @Inject(method = "hasCurioEquipped(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/Item;)Z", at = @At("RETURN"), require = 0)
    private static void kronwerke$remember(LivingEntity entity, Item item, CallbackInfoReturnable<Boolean> cir) {
        TickMemo.put(item, entity, cir.getReturnValue());
    }
}
