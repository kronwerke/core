package de.kronwerke.core.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.config.KronwerkeClientConfig;
import de.kronwerke.core.obelisk.ObeliskPartBlock;
import de.kronwerke.core.obelisk.ObeliskRite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What the rite does to the world on the clients, beyond the sky and the beam:
 * <ul>
 * <li>the vortex: copies of the blocks lying around the plinth are torn up, spiral round the
 * beam, higher and faster through the pull, and are flung out at the burst; the ground they
 * came from stays as it is,</li>
 * <li>the breaking: at the burst the obelisk's blocks hide (the server sets them hidden) and
 * their copies fly apart, hang in the air and come back together,</li>
 * <li>the arcs: during the return arcs of light leap from the pylons to the players and the
 * crystal,</li>
 * <li>the lens: through the pull the air around the crystal bends like light around a heavy
 * star (shaders/post/lens),</li>
 * <li>black bars like a film from the stillness to the roll call, the view drawn in before the
 * burst and punched out by it.</li>
 * </ul>
 * Every copy is chosen from the rite's start time, so all clients see the same spiral.
 */
public final class RiteFx {
    private static final ResourceLocation LENS = ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "shaders/post/lens.json");
    private static final int VORTEX_FROM = ObeliskRite.T_PULL + 20;

    /** a block of the ground in the vortex */
    private record Debris(Vec3 home, BlockState state, float r0, float theta0, int start, float top, Vector3f axis, float spin, float scale) {
    }

    /** a block of the obelisk in the breaking */
    private record Piece(Vec3 home, BlockState state, float dx, float dz, float lift, float turn, Vector3f axis, BlockPos light) {
    }

    private static long vortexFor = -1, piecesFor = -1;
    private static final List<Debris> debris = new ArrayList<>();
    private static final List<Piece> pieces = new ArrayList<>();
    private static long burstMillis;
    private static PostChain lens;
    private static boolean lensFailed;
    private static int lensW, lensH;

    private RiteFx() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(RiteFx::onFov);
        NeoForge.EVENT_BUS.addListener(RiteFx::onGui);
    }

    /** Called when the burst is seen. */
    static void burst() {
        burstMillis = System.currentTimeMillis();
    }

    static void onReload() {
        if (lens != null) lens.close();
        lens = null;
        lensFailed = false;
    }

    /** The rite's start in game time, or -1. */
    private static long riteStart(Minecraft mc, float partial) {
        float t = ObeliskEffects.riteTime(mc, partial);
        if (t < 0 || mc.level == null) return -1;
        return Math.round(mc.level.getGameTime() + partial - t);
    }

    static void drawWorld(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ObeliskEffects.farHere(mc)) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = ObeliskEffects.riteTime(mc, partial);
        if (t < 0) return;
        Vec3 cam = event.getCamera().getPosition();
        Vec3 core = new Vec3(ObeliskEffects.far.x(), ObeliskEffects.far.baseY(), ObeliskEffects.far.z());
        if (cam.distanceToSqr(core) > 260 * 260) return;
        long start = riteStart(mc, partial);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (t >= VORTEX_FROM && t < ObeliskRite.T_BURST + 80) {
            if (vortexFor != start) pickDebris(mc, core, start);
            drawVortex(event, mc, cam, core, t, buffers);
        }
        if (t >= ObeliskRite.T_BURST - 80 && piecesFor != start) pickPieces(mc, core, start);
        if (t >= ObeliskRite.T_BURST && t < ObeliskRite.T_RETURN + 2) drawPieces(event, mc, cam, core, t, buffers);
        buffers.endBatch();
        if (t >= ObeliskRite.T_REFORM + 20 && t < ObeliskRite.T_ROLL) drawArcs(event, mc, cam, core, t, start);
    }

    /** The post effects of the rite, after every piece of geometry, since a post pass leaves no usable depth behind. */
    static void drawPost(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ObeliskEffects.farHere(mc)) return;
        float t = ObeliskEffects.riteTime(mc, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (t >= ObeliskRite.T_PULL && t < ObeliskRite.T_BURST) drawLens(event, mc, t);
    }

    // ---- the vortex ----

    private static void pickDebris(Minecraft mc, Vec3 core, long start) {
        vortexFor = start;
        debris.clear();
        Random rnd = new Random(start * 31 + (long) core.x * 7 + (long) core.z);
        int want = 260;
        for (int tries = 0; tries < 1400 && debris.size() < want; tries++) {
            float r = 8.5f + rnd.nextFloat() * (ObeliskRite.LIFT_RADIUS - 2);
            float a = rnd.nextFloat() * Mth.TWO_PI;
            int x = Mth.floor(core.x + Mth.cos(a) * r), z = Mth.floor(core.z + Mth.sin(a) * r);
            BlockPos column = new BlockPos(x, 0, z);
            if (!mc.level.hasChunkAt(column)) continue;
            int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            BlockPos pos = new BlockPos(x, y, z);
            BlockState s = mc.level.getBlockState(pos);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.getRenderShape() != RenderShape.MODEL || s.hasProperty(ObeliskPartBlock.HIDDEN)) continue;
            // two out of three come from the surface, the rest a block deeper, so the spiral shows earth and stone too
            if (rnd.nextInt(3) == 0) {
                BlockState below = mc.level.getBlockState(pos.below());
                if (!below.isAir() && below.getFluidState().isEmpty() && below.getRenderShape() == RenderShape.MODEL) s = below;
            }
            Vector3f axis = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalize();
            int begin = VORTEX_FROM + (int) (debris.size() * 230f / want) + rnd.nextInt(12);
            debris.add(new Debris(Vec3.atCenterOf(pos), s, r, a, begin, 14 + rnd.nextFloat() * 34, axis, 2f + rnd.nextFloat() * 6f, 0.55f + rnd.nextFloat() * 0.45f));
        }
    }

    /** Where a piece of debris is at rite time t; null while it still lies on the ground. */
    private static Vec3 debrisAt(Debris d, Vec3 core, float t) {
        if (t < d.start()) return null;
        float tau = t - d.start();
        float tear = Math.min(1f, tau / 30f);
        float shake = tau < 30 ? (1 - tear) * 0.08f * Mth.sin(tau * 2.7f + d.theta0() * 10) : 0f;
        Vec3 torn = d.home().add(shake, 1.6f * tear * tear * (3 - 2 * tear), shake);
        if (tau < 30) return torn;
        float t2 = Math.min(t, ObeliskRite.T_BURST) - d.start() - 30;
        float span = Math.max(40f, ObeliskRite.T_BURST - d.start() - 30f);
        float q = Mth.clamp(t2 / span, 0f, 1f);
        float ease = q * q * (3 - 2 * q);
        float radius = Mth.lerp(ease, d.r0(), 4.5f + (d.r0() - 8.5f) * 0.3f);
        // the angular speed grows from slow to fast; this is its integral
        float angle = d.theta0() + 0.02f * t2 + 0.13f * t2 * t2 * t2 / (3f * span * span);
        float height = (float) d.home().y + 1.6f + (float) Math.pow(q, 1.3) * d.top();
        Vec3 orbit = new Vec3(core.x + Mth.cos(angle) * radius, height, core.z + Mth.sin(angle) * radius);
        float blend = Math.min(1f, t2 / 25f);
        blend = blend * blend * (3 - 2 * blend);
        Vec3 at = torn.lerp(orbit, blend);
        if (t >= ObeliskRite.T_BURST) {
            // flung out from where the burst caught it
            float bt = t - ObeliskRite.T_BURST;
            double ox = at.x - core.x, oz = at.z - core.z;
            double len = Math.max(0.5, Math.sqrt(ox * ox + oz * oz));
            float speed = 0.9f + (d.theta0() % 1f) * 0.8f;
            at = at.add(ox / len * speed * bt, (0.45f + d.scale() * 0.3f) * bt - 0.02f * bt * bt, oz / len * speed * bt);
        }
        return at;
    }

    private static void drawVortex(RenderLevelStageEvent event, Minecraft mc, Vec3 cam, Vec3 core, float t, MultiBufferSource buffers) {
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        var blocks = mc.getBlockRenderer();
        for (Debris d : debris) {
            Vec3 at = debrisAt(d, core, t);
            if (at == null) continue;
            float scale = d.scale();
            if (t >= ObeliskRite.T_BURST) scale *= Math.max(0f, 1f - (t - ObeliskRite.T_BURST) / 70f);
            // a block that flies right through the camera would fill the screen: it shrinks away near the eye
            double eye = at.distanceTo(cam);
            if (eye < 4) scale *= (float) Math.max(0, (eye - 1.5) / 2.5);
            if (scale <= 0.02f) continue;
            float tau = t - d.start();
            float spin = tau * d.spin() * (t >= ObeliskRite.T_BURST ? 3f : 1f);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(Axis.of(d.axis()).rotationDegrees(spin));
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, -0.5, -0.5);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
            light = LightTexture.pack(Math.max(LightTexture.block(light), 7), LightTexture.sky(light));
            blocks.renderSingleBlock(d.state(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    // ---- the breaking ----

    private static void pickPieces(Minecraft mc, Vec3 core, long start) {
        piecesFor = start;
        pieces.clear();
        Random rnd = new Random(start * 17 + 3);
        BlockPos c = BlockPos.containing(core.x, core.y, core.z);
        int r = 9;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 20; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = mc.level.getBlockState(p);
                    if (!s.hasProperty(ObeliskPartBlock.HIDDEN)) continue;
                    s = s.setValue(ObeliskPartBlock.HIDDEN, false);
                    float hx = dx, hz = dz;
                    float len = Mth.sqrt(hx * hx + hz * hz);
                    if (len < 0.5f) {
                        float a = rnd.nextFloat() * Mth.TWO_PI;
                        hx = Mth.cos(a);
                        hz = Mth.sin(a);
                        len = 1f;
                    }
                    // the further out and the higher up, the further a piece flies
                    float out = 2.5f + len * 0.9f + dy * 0.12f + rnd.nextFloat() * 2f;
                    Vector3f axis = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalize();
                    pieces.add(new Piece(Vec3.atLowerCornerOf(p), s, hx / len * out, hz / len * out, 1.5f + dy * 0.35f + rnd.nextFloat() * 3f,
                            (rnd.nextFloat() - 0.5f) * 140f, axis, p));
                }
            }
        }
    }

    private static void drawPieces(RenderLevelStageEvent event, Minecraft mc, Vec3 cam, Vec3 core, float t, MultiBufferSource buffers) {
        float bt = t - ObeliskRite.T_BURST;
        float back = ObeliskRite.T_RETURN - ObeliskRite.T_BURST;
        // out fast, hang and drift, then come back and close
        float e;
        if (bt < 30) {
            float p = bt / 30f;
            e = 1 - (1 - p) * (1 - p) * (1 - p);
        } else if (bt < 90) {
            e = 1f + (bt - 30) / 60f * 0.15f;
        } else {
            float p = Mth.clamp((bt - 90) / (back - 90), 0f, 1f);
            float s = p * p * (3 - 2 * p);
            e = 1.15f * (1 - s);
        }
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        var blocks = mc.getBlockRenderer();
        for (Piece p : pieces) {
            double x = p.home().x + p.dx() * e, y = p.home().y + p.lift() * e, z = p.home().z + p.dz() * e;
            pose.pushPose();
            pose.translate(x - cam.x + 0.5, y - cam.y + 0.5, z - cam.z + 0.5);
            pose.mulPose(Axis.of(p.axis()).rotationDegrees(p.turn() * e));
            pose.translate(-0.5, -0.5, -0.5);
            int light = LevelRenderer.getLightColor(mc.level, p.light());
            blocks.renderSingleBlock(p.state(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    // ---- the arcs ----

    private static void drawArcs(RenderLevelStageEvent event, Minecraft mc, Vec3 cam, Vec3 core, float t, long start) {
        Vec3 crystal = ObeliskEffects.where(mc);
        if (crystal == null) return;
        boolean soft = KronwerkeClientConfig.fewerFlashes();
        List<Vec3> from = new ArrayList<>();
        if (ObeliskEffects.tierNow(mc) >= 2) {
            int[][] d = {{6, 0}, {-6, 0}, {0, 6}, {0, -6}};
            for (int[] o : d) from.add(new Vec3(core.x + o[0], core.y + 3.4, core.z + o[1]));
        } else {
            from.add(crystal);
        }
        List<Vec3> to = new ArrayList<>();
        for (var p : mc.level.players()) {
            // the viewer is struck at the feet, seen from above, never into the eye
            if (p == mc.player && !mc.options.getCameraType().isFirstPerson()) to.add(p.position().add(0, 1.2, 0));
            else if (p == mc.player) to.add(p.position().add(0, 0.05, 0));
            else if (p.distanceToSqr(core) < 30 * 30) to.add(p.position().add(0, 1.2, 0));
        }
        int step = (int) (t / 3);
        Random rnd = new Random(start * 13 + step);
        Matrix4f m = new Matrix4f(event.getModelViewMatrix());
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] cyan = {0.55f, 0.9f, 1f};
        float[] white = {1f, 1f, 1f};
        float fade = Mth.clamp((ObeliskRite.T_ROLL - t) / 60f, 0f, 1f);
        float alpha = (soft ? 0.35f : 0.8f) * fade;
        for (Vec3 a : from) {
            // each source strikes the crystal, and every other tick a player or the ground
            arc(b, m, cam, a, crystal, rnd, cyan, white, alpha);
            if (!to.isEmpty() && rnd.nextBoolean()) {
                arc(b, m, cam, a, to.get(rnd.nextInt(to.size())), rnd, cyan, white, alpha);
            } else {
                float ang = rnd.nextFloat() * Mth.TWO_PI, r = 8 + rnd.nextFloat() * 14;
                float gx = (float) (core.x + Mth.cos(ang) * r), gz = (float) (core.z + Mth.sin(ang) * r);
                arc(b, m, cam, a, new Vec3(gx, ObeliskEffects.groundAt(mc, gx, gz, (int) core.y) + 0.1, gz), rnd, cyan, white, alpha * 0.7f);
            }
        }
        var mesh = b.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** A jagged line of light from a to b, a bright thin core in a wider cold glow. */
    private static void arc(BufferBuilder b, Matrix4f m, Vec3 cam, Vec3 a, Vec3 to, Random rnd, float[] glow, float[] core, float alpha) {
        int segs = 10;
        Vec3 dir = to.subtract(a);
        double len = dir.length();
        if (len < 0.5) return;
        Vec3 prev = a;
        for (int i = 1; i <= segs; i++) {
            double f = i / (double) segs;
            double jitter = i == segs ? 0 : Math.sin(f * Math.PI) * len * 0.08;
            Vec3 next = a.add(dir.scale(f)).add((rnd.nextDouble() - 0.5) * 2 * jitter, (rnd.nextDouble() - 0.5) * 2 * jitter, (rnd.nextDouble() - 0.5) * 2 * jitter);
            segment(b, m, cam, prev, next, 0.35f, glow, alpha * 0.35f);
            segment(b, m, cam, prev, next, 0.07f, core, alpha);
            prev = next;
        }
    }

    private static void segment(BufferBuilder b, Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, float w, float[] c, float a) {
        Vec3 mid = p0.add(p1).scale(0.5);
        // nothing right in front of the eye
        if (mid.distanceToSqr(cam) < 6.0) return;
        Vec3 side = p1.subtract(p0).cross(mid.subtract(cam));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(w);
        float x0 = (float) (p0.x - cam.x), y0 = (float) (p0.y - cam.y), z0 = (float) (p0.z - cam.z);
        float x1 = (float) (p1.x - cam.x), y1 = (float) (p1.y - cam.y), z1 = (float) (p1.z - cam.z);
        float sx = (float) side.x, sy = (float) side.y, sz = (float) side.z;
        ObeliskEffects.vertex(b, m, x0 - sx, y0 - sy, z0 - sz, c, 0);
        ObeliskEffects.vertex(b, m, x1 - sx, y1 - sy, z1 - sz, c, 0);
        ObeliskEffects.vertex(b, m, x1, y1, z1, c, a);
        ObeliskEffects.vertex(b, m, x0, y0, z0, c, a);
        ObeliskEffects.vertex(b, m, x0, y0, z0, c, a);
        ObeliskEffects.vertex(b, m, x1, y1, z1, c, a);
        ObeliskEffects.vertex(b, m, x1 + sx, y1 + sy, z1 + sz, c, 0);
        ObeliskEffects.vertex(b, m, x0 + sx, y0 + sy, z0 + sz, c, 0);
    }

    // ---- the lens ----

    private static PostChain lens() {
        if (lensFailed) return null;
        Minecraft mc = Minecraft.getInstance();
        if (lens == null) {
            try {
                lens = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), LENS);
            } catch (Exception e) {
                KronwerkeCore.LOGGER.warn("The lens shader did not load, the rite goes without it", e);
                lensFailed = true;
                return null;
            }
            lensW = lensH = 0;
        }
        if (lensW != mc.getWindow().getWidth() || lensH != mc.getWindow().getHeight()) {
            lensW = mc.getWindow().getWidth();
            lensH = mc.getWindow().getHeight();
            lens.resize(lensW, lensH);
        }
        return lens;
    }

    @SuppressWarnings("unchecked")
    private static List<PostPass> passes(PostChain c) {
        try {
            var f = PostChain.class.getDeclaredField("passes");
            f.setAccessible(true);
            return (List<PostPass>) f.get(c);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** The air around the crystal bends through the pull, more and more, and turns. */
    private static void drawLens(RenderLevelStageEvent event, Minecraft mc, float t) {
        Vec3 crystal = ObeliskEffects.where(mc);
        if (crystal == null) return;
        float p = (t - ObeliskRite.T_PULL) / ObeliskRite.PULL;
        float strength = Mth.clamp(p * 1.3f, 0f, 1f);
        float late = Math.max(0f, (t - (ObeliskRite.T_BURST - 40)) / 40f);
        strength = Math.min(1.6f, strength + late * 0.6f);
        if (KronwerkeClientConfig.fewerFlashes()) strength *= 0.5f;
        if (strength <= 0.01f) return;
        Vec3 cam = event.getCamera().getPosition();
        Vector4f pos = new Vector4f((float) (crystal.x - cam.x), (float) (crystal.y - cam.y), (float) (crystal.z - cam.z), 1f);
        Matrix4f mvp = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        mvp.transform(pos);
        if (pos.w <= 0.1f) return;
        float sx = pos.x / pos.w * 0.5f + 0.5f, sy = pos.y / pos.w * 0.5f + 0.5f;
        if (sx < -0.5f || sx > 1.5f || sy < -0.5f || sy > 1.5f) return;
        // the bent region keeps its size in the world: some fifteen blocks around the crystal
        float radius = Mth.clamp(15f / pos.w, 0.04f, 0.6f);
        PostChain c = lens();
        if (c == null) return;
        float aspect = mc.getWindow().getWidth() / (float) mc.getWindow().getHeight();
        for (PostPass pass : passes(c)) {
            var eff = pass.getEffect();
            eff.safeGetUniform("Center").set(sx, sy);
            eff.safeGetUniform("Radius").set(radius);
            eff.safeGetUniform("Strength").set(strength);
            eff.safeGetUniform("Aspect").set(aspect);
            eff.safeGetUniform("Time").set((System.currentTimeMillis() % 100000) / 1000f);
        }
        c.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        mc.getMainRenderTarget().bindWrite(false);
    }

    // ---- the frame: bars and the field of view ----

    /** How far the black bars are in, 0 to 1. */
    private static float bars(Minecraft mc, float partial) {
        float t = ObeliskEffects.riteTime(mc, partial);
        if (t < 0 || !ObeliskEffects.farHere(mc) || mc.player == null) return 0f;
        if (mc.player.distanceToSqr(ObeliskEffects.far.x(), ObeliskEffects.far.y(), ObeliskEffects.far.z()) > 220 * 220) return 0f;
        float in = Mth.clamp(t / 40f, 0f, 1f);
        float out = Mth.clamp((ObeliskRite.T_ROLL + 60 - t) / 60f, 0f, 1f);
        float f = Math.min(in, out);
        return f * f * (3 - 2 * f);
    }

    private static void onGui(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        float f = bars(mc, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (f <= 0.001f) return;
        var g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = Math.round(h * 0.1f * f);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }

    private static void onFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        float partial = (float) event.getPartialTick();
        float t = ObeliskEffects.riteTime(mc, partial);
        if (t < 0 || bars(mc, partial) <= 0f) return;
        double fov = event.getFOV();
        // drawn in over the last three seconds of the pull
        float late = Mth.clamp((t - (ObeliskRite.T_BURST - 60)) / 60f, 0f, 1f);
        if (t < ObeliskRite.T_BURST) fov *= 1 - 0.14 * late * late;
        // and punched out by the burst, in real time
        float since = (System.currentTimeMillis() - burstMillis) / 1800f;
        if (burstMillis > 0 && since < 1f) {
            float punch = (1 - since) * (1 - since) * (KronwerkeClientConfig.fewerFlashes() ? 0.08f : 0.3f);
            fov *= 1 + punch;
        }
        event.setFOV(fov);
    }
}
