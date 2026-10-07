package de.kronwerke.core.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The obelisk's own beam, in place of the beacon's. A ribbon that always turns to face the
 * camera, drawn with shaders/core/beam: a white core, a glow in the beam's colour, threads of
 * energy streaming up, a spiral and rings that travel along it. Everything is timed in real
 * seconds, so it stays smooth while the server runs the burst in slow motion, and it is drawn
 * after the world, where shader packs leave it alone.
 * <p>
 * Calls go between {@link #begin} and {@link #end}; each {@link #column} adds one beam.
 */
public final class ObeliskBeam {
    private static ShaderInstance shader;
    private static BufferBuilder buffer;
    private static Matrix4f view;
    private static Vec3 cam;

    private ObeliskBeam() {
    }

    static void shader(ShaderInstance s) {
        shader = s;
    }

    static boolean ready() {
        return shader != null;
    }

    /** Starts a batch of beams with the camera's view matrix and position. */
    static void begin(Matrix4f modelView, Vec3 camera) {
        view = modelView;
        cam = camera;
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    /**
     * A vertical beam from bottom up by height blocks, radius blocks wide on each side of its
     * axis. alpha scales the whole beam; the bottom two blocks and the top fifth fade out.
     */
    static void column(Vec3 bottom, float height, float radius, float[] c, float alpha) {
        if (buffer == null || height <= 0 || radius <= 0 || alpha <= 0.002f) return;
        double dx = cam.x - bottom.x, dz = cam.z - bottom.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        // straight underneath, any side will do
        float sx = len < 1e-3 ? 1f : (float) (-dz / len), sz = len < 1e-3 ? 0f : (float) (dx / len);
        float x = (float) (bottom.x - cam.x), y = (float) (bottom.y - cam.y), z = (float) (bottom.z - cam.z);
        float fadeIn = Math.min(2f, height * 0.1f), fadeOut = height * 0.2f;
        float[] ys = {0, fadeIn, height - fadeOut, height};
        float[] as = {0, 1, 1, 0};
        for (int i = 0; i < 3; i++) {
            float y0 = ys[i], y1 = ys[i + 1];
            float a0 = as[i] * alpha, a1 = as[i + 1] * alpha;
            vertex(x - sx * radius, y + y0, z - sz * radius, -1, y0, c, a0);
            vertex(x + sx * radius, y + y0, z + sz * radius, 1, y0, c, a0);
            vertex(x + sx * radius, y + y1, z + sz * radius, 1, y1, c, a1);
            vertex(x - sx * radius, y + y1, z - sz * radius, -1, y1, c, a1);
        }
    }

    private static void vertex(float x, float y, float z, float u, float v, float[] c, float a) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(c[0], c[1], c[2], Math.max(0f, Math.min(1f, a)));
    }

    /** Draws the batch: additive, tested against the world's depth so the stone hides what is behind it. */
    static void end(float seconds, float flow, float rings) {
        if (buffer == null) return;
        BufferBuilder b = buffer;
        buffer = null;
        var mesh = b.build();
        if (mesh == null || shader == null) return;
        shader.safeGetUniform("Time").set(seconds);
        shader.safeGetUniform("Flow").set(flow);
        shader.safeGetUniform("Rings").set(rings);
        RenderSystem.setShader(() -> shader);
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.mul(view);
        RenderSystem.applyModelViewMatrix();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
