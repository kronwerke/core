package de.kronwerke.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.obelisk.ObeliskRite;
import de.kronwerke.core.obelisk.ObeliskTopBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The top of the obelisk: a crystal that floats and turns above the tip, shards that circle
 * it, and the beam. Everything takes its colour from the goal: cyan while the goal runs and
 * warming towards gold as it fills, purple while it waits for the event, gold when it is
 * done, a quiet blue-grey when nothing is active, dim and low while the stone sleeps. The
 * tier (stages done) makes the crystal bigger and gives it more shards; from tier four it
 * leaves afterimages. A deposit makes the crystal flare by the size of the gift. While the
 * rite runs the renderer plays it from the start time the server sent: a freeze, the crystal
 * drawn in and burning white, the burst, and the slow return.
 */
public class ObeliskTopRenderer implements BlockEntityRenderer<ObeliskTopBlockEntity> {
    private static final ResourceLocation CRYSTAL = ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "textures/block/obelisk_crystal.png");
    private static final int FRAMES = 8;
    private static final float[] CYAN = {0.50f, 0.90f, 1.00f};
    private static final float[] GOLD = {1.00f, 0.80f, 0.36f};
    private static final float[] PURPLE = {0.72f, 0.50f, 0.95f};
    private static final float[] IDLE = {0.55f, 0.62f, 0.75f};
    private static final float[] SLEEP = {0.40f, 0.48f, 0.60f};
    private static final float[] AMBER = {1.00f, 0.62f, 0.30f};
    private static final float[] VIOLET = {0.80f, 0.55f, 1.00f};
    private static final float BASE_Y = 22 / 16.0f + 0.8f;

    public ObeliskTopRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** The colour for the state of the goal and the tier of the build. */
    static float[] colour(int percent, int mood, int tier) {
        float[] c;
        switch (mood) {
            case ObeliskTopBlockEntity.MOOD_HELD -> c = PURPLE.clone();
            case ObeliskTopBlockEntity.MOOD_DONE -> c = GOLD.clone();
            case ObeliskTopBlockEntity.MOOD_IDLE -> c = IDLE.clone();
            case ObeliskTopBlockEntity.MOOD_ASLEEP -> c = SLEEP.clone();
            default -> {
                float t = Mth.clamp(percent / 100f, 0f, 1f);
                t = t * t;
                float[] from = tier >= 4 ? VIOLET : tier == 3 ? AMBER : CYAN;
                c = new float[]{Mth.lerp(t, from[0], GOLD[0]), Mth.lerp(t, from[1], GOLD[1]), Mth.lerp(t, from[2], GOLD[2])};
            }
        }
        return c;
    }

    @Override
    public void render(ObeliskTopBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long gameTime = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        float time = gameTime + partialTick;
        int tier = be.tier();
        ObeliskEffects.seen(be, be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + BASE_Y, be.getBlockPos().getZ() + 0.5);
        int mood = be.mood();
        float[] c = colour(be.percent(), mood, tier);

        // the rite: a freeze, the crystal drawn in and burning white, the burst, the slow return
        float riteT = be.rite() > 0 ? gameTime - be.rite() + partialTick : -1;
        boolean frozen = riteT >= 0 && riteT < ObeliskRite.T_INTAKE;
        float riteScale = 1f, riteWhite = 0f, riteSpin = 1f;
        boolean hideBeam = false;
        if (riteT >= 0 && riteT < ObeliskRite.T_END) {
            if (frozen) {
                time = be.rite();
            } else if (riteT < ObeliskRite.T_BURST) {
                float p = (riteT - ObeliskRite.T_INTAKE) / ObeliskRite.INTAKE;
                riteScale = 1f - 0.5f * p;
                riteWhite = p;
                riteSpin = 1f + 6f * p;
                hideBeam = true;
            } else if (riteT < ObeliskRite.T_REFORM) {
                float p = (riteT - ObeliskRite.T_BURST) / ObeliskRite.BURST;
                riteScale = p < 0.15f ? 0.5f + 7.5f * (p / 0.15f) : Math.max(0.05f, 8f * (1 - (p - 0.15f) / 0.85f));
                riteWhite = 1f;
                riteSpin = 8f;
            } else if (riteT < ObeliskRite.T_ROLL) {
                float p = (riteT - ObeliskRite.T_REFORM) / ObeliskRite.REFORM;
                riteScale = 0.05f + 0.95f * (1 - (1 - p) * (1 - p));
                riteWhite = Math.max(0f, 1 - p * 3);
                riteSpin = 1f + 2f * (1 - p);
                c = GOLD.clone();
            } else {
                c = GOLD.clone();
            }
        }
        // the flare after a deposit, scaled by the size of the gift
        float since = gameTime - be.lastDeposit() + partialTick;
        float flare = be.lastDeposit() > 0 && since < 40 ? (float) Math.max(0, 1 - since / 40) * be.flashStrength() : 0;
        boolean asleep = mood == ObeliskTopBlockEntity.MOOD_ASLEEP;
        boolean held = mood == ObeliskTopBlockEntity.MOOD_HELD;
        float breathe = asleep ? 0.6f : 0.85f + 0.15f * Mth.sin(time / 14f);
        float glow = Math.min(1f, breathe + flare * 0.6f + riteWhite);
        // the texture is already coloured, so the tint stays light and only leans the hue
        float[] tint = {Mth.lerp(riteWhite, 0.45f + 0.55f * c[0], 1f), Mth.lerp(riteWhite, 0.45f + 0.55f * c[1], 1f), Mth.lerp(riteWhite, 0.45f + 0.55f * c[2], 1f)};

        float size = (0.75f + 0.08f * tier) * (1f + flare * 0.25f) * riteScale;
        float bob = asleep ? -0.35f : 0.08f * Mth.sin(time / 22f);
        if (held) bob += 0.03f * Mth.sin(time * 2.2f);
        float spin = (asleep ? 0.3f : mood == ObeliskTopBlockEntity.MOOD_DONE ? 2.5f : 1.2f) * riteSpin;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(CRYSTAL));
        int frame = (int) ((gameTime / 3) % FRAMES);

        // afterimages from tier four: the crystal a few frames ago, fainter
        if (tier >= 4 && !asleep) {
            for (int k = 1; k <= 3; k++) {
                float past = time - k * 2.5f;
                pose.pushPose();
                pose.translate(0.5, BASE_Y + 0.08f * Mth.sin(past / 22f), 0.5);
                pose.mulPose(Axis.YP.rotationDegrees(past * spin));
                pose.scale(size, size, size);
                octahedron(pose, vc, 0.72f, 0.32f, tint, glow, (frame + k) % FRAMES, 0.18f / k);
                pose.popPose();
            }
        }

        pose.pushPose();
        pose.translate(0.5, BASE_Y + bob, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * spin));
        pose.scale(size, size, size);
        octahedron(pose, vc, 0.72f, 0.32f, tint, glow, frame, 0.95f);
        // a faint outer shell that breathes against the inner crystal
        float shell = 0.12f + 0.1f * Mth.sin(time / 9f) + flare * 0.3f + riteWhite * 0.3f;
        octahedron(pose, vc, 0.9f, 0.42f, tint, 1f, (frame + 3) % FRAMES, shell);
        pose.popPose();

        // the shards: three, one more from tier two, two more from tier four; frozen in place during the hold
        int shards = 3 + (tier >= 2 ? 1 : 0) + (tier >= 4 ? 2 : 0);
        float orbit = held ? be.rite() : time;
        for (int i = 0; i < shards; i++) {
            float a = orbit * (0.9f + 0.25f * (i % 3)) / 20f * riteSpin + i * (float) (Math.PI * 2 / shards);
            float r = (0.95f + 0.08f * Mth.sin(time / 17f + i)) * (0.7f + 0.3f * riteScale) * (held ? 0.75f : 1f);
            float y = BASE_Y + bob + 0.3f * Mth.sin((held ? be.rite() : time) / 13f + i * 2.1f) + (i >= 4 ? 0.6f : 0f);
            pose.pushPose();
            pose.translate(0.5 + Mth.cos(a) * r, y, 0.5 + Mth.sin(a) * r);
            pose.mulPose(Axis.YP.rotationDegrees(-time * 3 + i * 120));
            pose.mulPose(Axis.ZP.rotationDegrees(held ? 60 : 20));
            octahedron(pose, vc, 0.2f, 0.08f, tint, glow, (frame + i * 2) % FRAMES, 0.9f);
            pose.popPose();
        }

        // the rite's great beam: it comes down from the sky during the intake, and after the
        // burst it wraps the whole trunk in gold and tightens back to the ordinary beam
        if (riteT >= ObeliskRite.T_INTAKE && riteT < ObeliskRite.T_ROLL) {
            pose.pushPose();
            if (riteT < ObeliskRite.T_BURST) {
                float p = (riteT - ObeliskRite.T_INTAKE) / ObeliskRite.INTAKE;
                p = p * p;
                int bottom = (int) (BASE_Y + 1 + 420 * (1 - p));
                float radius = 0.4f + 2.6f * p;
                BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0f, gameTime, bottom, 1024 - bottom, 0xFFFFFF, radius, radius + 0.6f);
            } else {
                float p = riteT < ObeliskRite.T_REFORM ? 0f : (riteT - ObeliskRite.T_REFORM) / ObeliskRite.REFORM;
                float radius = Mth.lerp(p * p, 3.2f, 0.25f);
                int gold = 0xF6D68C, white = 0xFFFFFF;
                BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0f, gameTime, -17, 1024, gold, radius, radius + 1.2f);
                BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 0.5f, gameTime * 3, -17, 1024, white, radius * 0.55f, radius * 0.55f + 0.3f);
            }
            pose.popPose();
        }

        // the beam, in the same colour, from the point of the crystal
        if (!hideBeam) {
            float[] bc = riteT >= ObeliskRite.T_BURST && riteT < ObeliskRite.T_BURST + 20 ? new float[]{1f, 1f, 1f} : c;
            int colour = ((int) (bc[0] * 255) << 16) | ((int) (bc[1] * 255) << 8) | (int) (bc[2] * 255);
            pose.pushPose();
            pose.translate(0.0, BASE_Y + bob + 0.5 * size, 0.0);
            float width = (asleep ? 0.08f : 0.14f + 0.02f * tier) + flare * 0.08f + (riteT >= ObeliskRite.T_BURST && riteT < ObeliskRite.T_BURST + 40 ? 0.3f : 0f);
            BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0f, frozen ? be.rite() : gameTime, 0, 1024, colour, width, width + 0.05f);
            pose.popPose();
        }
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
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose.last(), n.x, n.y, n.z);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ObeliskTopBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX() - 2, p.getY(), p.getZ() - 2, p.getX() + 3, 1024, p.getZ() + 3);
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
