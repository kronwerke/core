package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.camera.CameraClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

/** The camera does not draw the floating labels of the screenshot world. */
@Mixin(EntityRenderDispatcher.class)
public class CameraHideMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void kronwerke$noLabels(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (CameraClient.ON && entity instanceof Display.TextDisplay) cir.setReturnValue(false);
    }
}
