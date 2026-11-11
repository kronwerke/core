package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.portal.Crossing;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;

/**
 * An animal, item, cart or any other entity that a mod sends into a dimension played on
 * another server stays where it is. This server still has its own copy of that dimension,
 * which nobody can visit: whatever went there would be lost. Players are moved to the right
 * server by {@link PlayerCrossingMixin}; portals already keep everything but players here.
 */
@Mixin(Entity.class)
public class EntityCrossingMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void kronwerke$stay(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (Crossing.refuseEntity((Entity) (Object) this, transition)) cir.setReturnValue(null);
    }
}
