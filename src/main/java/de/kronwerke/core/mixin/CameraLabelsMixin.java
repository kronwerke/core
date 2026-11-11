package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.camera.Camera;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

/**
 * The floating labels of the screenshot world are never sent to the camera. Hiding them on the
 * camera's client was not enough: a renderer of another mod draws text displays past the
 * dispatcher's check.
 */
@Mixin(Entity.class)
public abstract class CameraLabelsMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void kronwerke$noLabelsForCamera(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Display.TextDisplay && self.getTags().contains("kw_shot") && Camera.is(player)) cir.setReturnValue(false);
    }
}
