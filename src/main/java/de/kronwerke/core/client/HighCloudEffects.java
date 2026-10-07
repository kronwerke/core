package de.kronwerke.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.kronwerke.core.config.KronwerkeClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.joml.Matrix4f;

/**
 * The overworld's sky as vanilla draws it, with the clouds at the height from the client
 * config. Kronwerke's overworld reaches up to 608, and clouds at 192 would hang in the
 * middle of the mountains.
 * <p>
 * Vanilla draws the clouds with the terrain's fog, which ends at the render distance
 * measured flat across the ground, and with the terrain's far plane. That is fine for clouds
 * at 192; clouds at 448 would shrink to a small patch straight overhead. So the clouds are
 * drawn here with their own reach: the fog ends where the cloud layer itself ends (vanilla
 * builds it some 380 blocks out), and the far plane lies beyond the farthest cloud. The
 * change in the far plane moves the depth of a point by far less than one part in a
 * thousand, so the clouds still sit correctly behind mountains.
 */
public class HighCloudEffects extends DimensionSpecialEffects.OverworldEffects {
    private static final float NEAR = 0.05f;
    private static boolean drawing;

    @Override
    public float getCloudHeight() {
        return KronwerkeClientConfig.cloudHeight();
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ,
                                Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        // the second pass is vanilla's own drawing, called from here
        if (drawing) return false;
        Minecraft mc = Minecraft.getInstance();
        float reach = 380f;
        float height = Math.abs(getCloudHeight() - (float) camY);
        float far = (float) Math.sqrt(reach * reach + height * height) + 64f;
        float oldFar = mc.gameRenderer.getDepthFar();
        Matrix4f projection = new Matrix4f(projectionMatrix);
        if (far > oldFar) {
            // a perspective matrix keeps near and far in these two entries; set them for the new far plane
            projection.m22((far + NEAR) / (NEAR - far));
            projection.m32(2f * far * NEAR / (NEAR - far));
        }
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        var fogShape = RenderSystem.getShaderFogShape();
        RenderSystem.setShaderFogStart(reach * 0.55f);
        RenderSystem.setShaderFogEnd(reach);
        RenderSystem.setShaderFogShape(com.mojang.blaze3d.shaders.FogShape.CYLINDER);
        drawing = true;
        try {
            mc.levelRenderer.renderClouds(poseStack, modelViewMatrix, projection, partialTick, camX, camY, camZ);
        } finally {
            drawing = false;
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
        }
        return true;
    }
}
