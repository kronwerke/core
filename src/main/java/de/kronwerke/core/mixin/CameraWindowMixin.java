package de.kronwerke.core.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.camera.CameraClient;

/**
 * The camera's window: an OSMesa context on GLFW's null platform, see {@link CameraGlfwMixin}.
 * Without NeoForge's loading screen (the camera turns it off), NeoForge creates Minecraft's
 * window here, after Minecraft has set its hints: OSMesa wants no forward-compatible context.
 */
@Mixin(targets = "net.neoforged.neoforge.client.loading.NoVizFallback", remap = false)
public class CameraWindowMixin {
    @Inject(method = "lambda$windowHandoff$0", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"), require = 1)
    private static void kronwerke$osmesa(CallbackInfoReturnable<Long> cir) {
        if (!CameraClient.HEADLESS) return;
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_OSMESA_CONTEXT_API);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_FALSE);
    }
}
