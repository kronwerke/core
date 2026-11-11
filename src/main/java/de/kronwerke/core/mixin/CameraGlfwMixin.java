package de.kronwerke.core.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.GLX;

import de.kronwerke.core.camera.CameraClient;

/**
 * The camera client (-Dkronwerke.camera=true) runs without a screen: GLFW's null platform,
 * so no X server is needed, with an OSMesa context that renders on the CPU (llvmpipe).
 */
@Mixin(GLX.class)
public class CameraGlfwMixin {
    @Inject(method = "_initGlfw", at = @At("HEAD"), remap = false)
    private static void kronwerke$nullPlatform(CallbackInfoReturnable<?> cir) {
        if (CameraClient.HEADLESS) GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_NULL);
    }
}
