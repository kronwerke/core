package de.kronwerke.core.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.kronwerke.core.portal.Crossing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player who changes into a dimension on another server (a mod's teleport, /tp, an End
 * gateway of a mod) moves there instead; a respawn at a bed or anchor elsewhere moves on too.
 */
@Mixin(ServerPlayer.class)
public class PlayerCrossingMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void kronwerke$cross(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (Crossing.transition((ServerPlayer) (Object) this, transition)) cir.setReturnValue(null);
    }

    @WrapMethod(method = "findRespawnPositionAndUseSpawnBlock")
    private DimensionTransition kronwerke$respawn(boolean keepInventory, DimensionTransition.PostDimensionTransition post,
                                                  Operation<DimensionTransition> original) {
        return Crossing.respawn((ServerPlayer) (Object) this, keepInventory, () -> original.call(keepInventory, post));
    }
}
