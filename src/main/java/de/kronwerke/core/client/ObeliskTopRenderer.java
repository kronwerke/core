package de.kronwerke.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.obelisk.ObeliskTopBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The top of the obelisk: a crystal that floats and turns above the tip, three shards that
 * circle it, and the beam. Everything takes its colour from the goal: cyan while the goal
 * runs and warming towards gold as it fills, purple while it waits for the event, gold
 * when it is done, a quiet grey-blue when nothing is active. A deposit makes the crystal
 * flare for a moment.
 */
public class ObeliskTopRenderer implements BlockEntityRenderer<ObeliskTopBlockEntity> {
    private static final ResourceLocation CRYSTAL = ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "textures/block/obelisk_crystal.png");
    private static final int FRAMES = 8;
    private static final float[] CYAN = {0.50f, 0.90f, 1.00f};
    private static final float[] GOLD = {1.00f, 0.80f, 0.36f};
    private static final float[] PURPLE = {0.72f, 0.50f, 0.95f};
    private static final float[] IDLE = {0.55f, 0.62f, 0.75f};

    public ObeliskTopRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** The colour for the state of the goal. */
    static float[] colour(int percent, int mood, float time) {
        float[] c;
        switch (mood) {
            case 1 -> c = PURPLE.clone();
            case 2 -> c = GOLD.clone();
            case 3 -> c = IDLE.clone();
            default -> {
                float t = Mth.clamp(percent / 100f, 0f, 1f);
                t = t * t;
                c = new float[]{Mth.lerp(t, CYAN[0], GOLD[0]), Mth.lerp(t, CYAN[1], GOLD[1]), Mth.lerp(t, CYAN[2], GOLD[2])};
            }
        }
        return c;
    }

    @Override
    public void render(ObeliskTopBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long gameTime = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        float time = gameTime + partialTick;
        float[] c = colour(be.percent(), be.mood(), time);
        // the texture is already coloured, so the tint stays light and only leans the hue
        float[] tint = {0.45f + 0.55f * c[0], 0.45f + 0.55f * c[1], 0.45f + 0.55f * c[2]};
        // the flare after a deposit: bright for half a second, fading over two
        float since = gameTime - be.lastDeposit() + partialTick;
        float flare = be.lastDeposit() > 0 && since < 40 ? (float) Math.max(0, 1 - since / 40) : 0;
        float breathe = 0.85f + 0.15f * Mth.sin(time / 14f);
        float glow = Math.min(1f, breathe + flare * 0.6f);

        pose.pushPose();
        pose.translate(0.5, 22 / 16.0 + 0.8 + 0.08 * Mth.sin(time / 22f), 0.5);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * (be.mood() == 2 ? 2.5f : 1.2f)));
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(CRYSTAL));
        int frame = (int) ((gameTime / 3) % FRAMES);
        float scale = 1f + flare * 0.25f;
        pose.pushPose();
        pose.scale(scale, scale, scale);
        octahedron(pose, vc, 0.72f, 0.32f, tint, glow, frame, 0.95f);
        // a faint outer shell that breathes against the inner crystal
        float shell = 0.12f + 0.1f * Mth.sin(time / 9f) + flare * 0.3f;
        octahedron(pose, vc, 0.9f, 0.42f, tint, 1f, (frame + 3) % FRAMES, shell);
        pose.popPose();
        pose.popPose();

        // three shards circling at different heights and speeds
        for (int i = 0; i < 3; i++) {
            float a = time * (0.9f + 0.25f * i) / 20f + i * (float) (Math.PI * 2 / 3);
            float r = 0.95f + 0.08f * Mth.sin(time / 17f + i);
            float y = 22 / 16.0f + 0.8f + 0.3f * Mth.sin(time / 13f + i * 2.1f);
            pose.pushPose();
            pose.translate(0.5 + Mth.cos(a) * r, y, 0.5 + Mth.sin(a) * r);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-time * 3 + i * 120));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(20));
            octahedron(pose, vc, 0.2f, 0.08f, tint, glow, (frame + i * 2) % FRAMES, 0.9f);
            pose.popPose();
        }

        // the beam, in the same colour
        int colour = ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
        pose.pushPose();
        pose.translate(0.0, 22 / 16.0 + 0.8 + 0.5, 0.0);
        float width = 0.16f + flare * 0.08f;
        BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0f, gameTime, 0, 1024, colour, width, width + 0.05f);
        pose.popPose();
    }

    /**
     * A double pyramid with its tip up and down, h half the height, r the radius of the
     * middle, drawn with the crystal texture's frame and tinted.
     */
    private static void octahedron(PoseStack pose, VertexConsumer vc, float h, float r, float[] c, float glow, int frame, float alpha) {
        Matrix4f m = pose.last().pose();
        float v0 = frame / (float) FRAMES, v1 = (frame + 1) / (float) FRAMES;
        int light = LightTexture.FULL_BRIGHT;
        float[][] ring = {{r, 0, 0}, {0, 0, r}, {-r, 0, 0}, {0, 0, -r}};
        for (int i = 0; i < 4; i++) {
            float[] p = ring[i], q = ring[(i + 1) % 4];
            // each side face is a little darker on the lower half, so the shape reads as solid
            tri(m, pose, vc, 0, h, 0, p[0], p[1], p[2], q[0], q[1], q[2], c, glow, v0, v1, alpha, light);
            tri(m, pose, vc, 0, -h, 0, q[0], q[1], q[2], p[0], p[1], p[2], c, glow * 0.75f, v0, v1, alpha, light);
        }
    }

    private static void tri(Matrix4f m, PoseStack pose, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float[] c, float glow, float v0, float v1, float alpha, int light) {
        Vector3f n = new Vector3f(bx - ax, by - ay, bz - az).cross(cx - ax, cy - ay, cz - az).normalize();
        float r = Math.min(1f, c[0] * glow), g = Math.min(1f, c[1] * glow), b = Math.min(1f, c[2] * glow);
        vertex(m, pose, vc, ax, ay, az, 0.5f, v0, r, g, b, alpha, light, n);
        vertex(m, pose, vc, bx, by, bz, 0f, v1, r, g, b, alpha, light, n);
        vertex(m, pose, vc, cx, cy, cz, 1f, v1, r, g, b, alpha, light, n);
        vertex(m, pose, vc, cx, cy, cz, 1f, v1, r, g, b, alpha, light, n);
    }

    private static void vertex(Matrix4f m, PoseStack pose, VertexConsumer vc, float x, float y, float z, float u, float v,
                               float r, float g, float b, float a, int light, Vector3f n) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose.last(), n.x, n.y, n.z);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ObeliskTopBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, 1024, p.getZ() + 2);
    }

    @Override
    public boolean shouldRenderOffScreen(ObeliskTopBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRender(ObeliskTopBlockEntity be, Vec3 cameraPos) {
        BlockPos p = be.getBlockPos();
        return cameraPos.distanceToSqr(p.getX(), cameraPos.y, p.getZ()) < 256 * 256;
    }
}
