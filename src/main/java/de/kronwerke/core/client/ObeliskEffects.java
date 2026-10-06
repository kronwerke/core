package de.kronwerke.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.obelisk.ObeliskTopBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import de.kronwerke.core.net.KwNetwork;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.List;

/**
 * What the obelisk does to the whole picture, beyond its own blocks. The renderer of the top
 * block tells this class where the obelisk is and what state it is in; from that come
 * <ul>
 * <li>the shockwave: a post-processing pass (shaders/post/shockwave.json) that pushes the
 * picture outward in a ring from the crystal, for a huge gift and for the burst of the rite,</li>
 * <li>the fog: near the obelisk the air takes the colour of its tier, amber from the third
 * stage, violet from the fourth, gold when everything is done,</li>
 * <li>the aurora: from the fourth stage, curtains of light hang in the sky above the
 * obelisk, drawn as additive ribbons that wave with time,</li>
 * <li>the torn sky: when a stage completes the server tells every player, and for a time
 * that grows with the stage the sky is a galaxy (shaders/core/galaxy), opening from the
 * zenith and closing again,</li>
 * <li>the camera shake of the burst and of a huge gift.</li>
 * </ul>
 */
public final class ObeliskEffects {
    private static final ResourceLocation SHOCKWAVE = ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "shaders/post/shockwave.json");
    private static final ResourceLocation VEIL = ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "shaders/post/veil.json");

    /** where the obelisk's crystal is, as last drawn */
    static Vec3 crystal;
    static int tier, mood, percent;
    static long lastSeen;
    private static long lastDepositSeen, lastRiteSeen;

    private static PostChain chain, veil;
    private static boolean chainFailed, veilFailed;
    private static int chainW, chainH, veilW, veilH;
    private static long waveStart;
    private static float waveStrength;
    private static int waveTicks;
    private static Vec3 waveAt;

    private ObeliskEffects() {
    }

    /** Called by the top renderer every frame it draws. */
    static void seen(ObeliskTopBlockEntity be, double x, double y, double z) {
        crystal = new Vec3(x, y, z);
        tier = be.tier();
        mood = be.mood();
        percent = be.percent();
        long now = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        lastSeen = now;
        if (be.lastDeposit() != lastDepositSeen) {
            lastDepositSeen = be.lastDeposit();
            // only a gift the server marked as large or huge shakes the air, and only when it just happened
            if (be.flashStrength() >= 0.85f && now - be.lastDeposit() < 20) {
                shockwave(crystal, be.flashStrength() >= 1f ? 1f : 0.5f, 30);
                if (be.flashStrength() >= 1f) shake(0.4f, 15);
            }
        }
        if (be.rite() != lastRiteSeen) {
            lastRiteSeen = be.rite();
        }
        if (be.rite() > 0) {
            long t = now - be.rite();
            if (t >= de.kronwerke.core.obelisk.ObeliskRite.T_BURST && t < de.kronwerke.core.obelisk.ObeliskRite.T_BURST + 2 && waveStart != be.rite()) {
                waveStart = be.rite();
                shockwave(crystal, 1.4f, 45);
            }
        }
    }

    public static void shockwave(Vec3 at, float strength, int ticks) {
        waveAt = at;
        waveStrength = strength;
        waveTicks = ticks;
        waveStart = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        waveStartMillis = System.currentTimeMillis();
    }

    private static long waveStartMillis;

    private static ShaderInstance galaxy;
    private static KwNetwork.SkyPayload sky;
    private static long shakeUntil;
    private static float shakeStrength;

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ObeliskEffects::onRenderStage);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ObeliskEffects::onFog);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ObeliskEffects::onCamera);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((ViewportEvent.RenderFog e) -> {
            float open = skyOpen();
            if (open <= 0 || e.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) return;
            float far = e.getFarPlaneDistance();
            e.setNearPlaneDistance(Mth.lerp(open, e.getNearPlaneDistance(), far * 0.85f));
            e.setCanceled(true);
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterShadersEvent e) -> {
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "galaxy"), DefaultVertexFormat.POSITION), sh -> galaxy = sh);
            } catch (Exception ex) {
                KronwerkeCore.LOGGER.warn("The galaxy shader did not load, the sky stays as it is", ex);
            }
        });
    }

    /** The server says the sky is open. */
    public static void sky(KwNetwork.SkyPayload payload) {
        sky = payload;
        shake(1.2f, 40);
    }

    public static void shake(float strength, int ticks) {
        shakeStrength = Math.max(shakeStrength, strength);
        shakeUntil = System.currentTimeMillis() + ticks * 50L;
    }

    private static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        long left = shakeUntil - System.currentTimeMillis();
        if (left <= 0) {
            shakeStrength = 0;
            return;
        }
        float t = System.currentTimeMillis() / 1000f;
        float amp = shakeStrength * Math.min(1f, left / 800f);
        event.setPitch(event.getPitch() + amp * 1.6f * (float) Math.sin(t * 61.3) * (float) Math.cos(t * 17.1));
        event.setYaw(event.getYaw() + amp * 1.3f * (float) Math.sin(t * 47.7 + 1.3));
        event.setRoll(event.getRoll() + amp * 0.5f * (float) Math.sin(t * 29.0 + 0.7));
    }

    // ---- shockwave ----

    private static PostChain chain() {
        if (chainFailed) return null;
        Minecraft mc = Minecraft.getInstance();
        if (chain == null) {
            chain = load(SHOCKWAVE);
            if (chain == null) chainFailed = true;
            chainW = chainH = 0;
        }
        if (chain != null && (chainW != mc.getWindow().getWidth() || chainH != mc.getWindow().getHeight())) {
            chainW = mc.getWindow().getWidth();
            chainH = mc.getWindow().getHeight();
            chain.resize(chainW, chainH);
        }
        return chain;
    }

    private static PostChain veil() {
        if (veilFailed) return null;
        Minecraft mc = Minecraft.getInstance();
        if (veil == null) {
            veil = load(VEIL);
            if (veil == null) veilFailed = true;
            veilW = veilH = 0;
        }
        if (veil != null && (veilW != mc.getWindow().getWidth() || veilH != mc.getWindow().getHeight())) {
            veilW = mc.getWindow().getWidth();
            veilH = mc.getWindow().getHeight();
            veil.resize(veilW, veilH);
        }
        return veil;
    }

    private static PostChain load(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        try {
            return new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), id);
        } catch (Exception e) {
            KronwerkeCore.LOGGER.warn("The post shader {} did not load, the obelisk goes without it", id, e);
            return null;
        }
    }

    /** Resource reloads drop the chain, so it is read again with the new shaders. */
    public static void onReload() {
        if (chain != null) chain.close();
        if (veil != null) veil.close();
        chain = null;
        veil = null;
        chainFailed = false;
        veilFailed = false;
    }

    @SuppressWarnings("unchecked")
    private static List<PostPass> passes(PostChain c) {
        try {
            Field f = PostChain.class.getDeclaredField("passes");
            f.setAccessible(true);
            return (List<PostPass>) f.get(c);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            drawAurora(event);
            drawVeil(event);
            drawShockwave(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            drawSky(event);
        }
    }

    // ---- the torn sky ----

    private static void drawSky(RenderLevelStageEvent event) {
        if (sky == null || galaxy == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = mc.level.getGameTime() + partial - sky.start();
        if (t < 0) return;
        if (t > sky.duration()) {
            sky = null;
            return;
        }
        float in = Math.min(1f, t / 70f);
        float out = Math.min(1f, (sky.duration() - t) / 80f);
        float tear = in * in * (3 - 2 * in);
        float fade = Math.min(1f, t / 25f) * out;
        if (fade <= 0.002f) return;

        galaxy.safeGetUniform("Time").set(t / 20f);
        galaxy.safeGetUniform("Fade").set(fade);
        galaxy.safeGetUniform("Tear").set(tear);
        galaxy.safeGetUniform("Tier").set((float) sky.tier());
        RenderSystem.setShader(() -> galaxy);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        // the camera's rotation sits in the event's matrix, so it goes on RenderSystem's stack and the
        // vertex positions stay plain world directions for the shader
        org.joml.Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        Matrix4f m = new Matrix4f();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        float r = 60f;
        // a cube around the eye; the shader only uses the direction of each point
        float[][] faces = {
                {-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1},
                {1, -1, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1},
                {-1, -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, 1},
                {1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1},
                {-1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1},
                {-1, -1, 1, 1, -1, 1, 1, -1, -1, -1, -1, -1}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) b.addVertex(m, f[i * 3] * r, f[i * 3 + 1] * r, f[i * 3 + 2] * r);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** How open the sky is right now, 0 to 1, for the renderer of the beam. */
    static float skyOpen() {
        if (sky == null || Minecraft.getInstance().level == null) return 0f;
        float t = Minecraft.getInstance().level.getGameTime() - sky.start();
        if (t < 0 || t > sky.duration()) return 0f;
        return Math.min(1f, t / 70f) * Math.min(1f, (sky.duration() - t) / 80f);
    }

    private static void drawShockwave(RenderLevelStageEvent event) {
        if (waveAt == null || waveTicks <= 0) return;
        float t = (System.currentTimeMillis() - waveStartMillis) / (waveTicks * 50f);
        if (t >= 1f) {
            waveAt = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PostChain c = chain();
        if (c == null) return;
        // the crystal on the screen
        Vec3 cam = event.getCamera().getPosition();
        Vector4f p = new Vector4f((float) (waveAt.x - cam.x), (float) (waveAt.y - cam.y), (float) (waveAt.z - cam.z), 1f);
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix());
        event.getProjectionMatrix().mul(mv, mv);
        mv.transform(p);
        if (p.w <= 0.01f) return;
        float sx = p.x / p.w * 0.5f + 0.5f, sy = p.y / p.w * 0.5f + 0.5f;
        float aspect = mc.getWindow().getWidth() / (float) mc.getWindow().getHeight();
        float radius = 0.1f + 1.6f * t;
        float strength = waveStrength * (1 - t) * (1 - t);
        for (PostPass pass : passes(c)) {
            var eff = pass.getEffect();
            eff.safeGetUniform("Center").set(sx, sy);
            eff.safeGetUniform("Radius").set(radius);
            eff.safeGetUniform("Strength").set(strength);
            eff.safeGetUniform("Aspect").set(aspect);
        }
        c.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        mc.getMainRenderTarget().bindWrite(false);
    }

    /** The grading of the whole picture while the sky is open. */
    private static void drawVeil(RenderLevelStageEvent event) {
        float open = skyOpen();
        if (open <= 0.01f) return;
        PostChain c = veil();
        if (c == null) return;
        Minecraft mc = Minecraft.getInstance();
        float strength = 0.75f * open;
        for (PostPass pass : passes(c)) {
            var eff = pass.getEffect();
            eff.safeGetUniform("Strength").set(strength);
            eff.safeGetUniform("Time").set((System.currentTimeMillis() % 100000) / 1000f);
        }
        c.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        mc.getMainRenderTarget().bindWrite(false);
    }

    // ---- fog ----

    private static void onFog(ViewportEvent.ComputeFogColor event) {
        // while the sky is open the air darkens with it, so the horizon and the far beam do not stay daylight blue
        float open = skyOpen();
        if (open > 0) {
            event.setRed(Mth.lerp(open, event.getRed(), 0.012f));
            event.setGreen(Mth.lerp(open, event.getGreen(), 0.01f));
            event.setBlue(Mth.lerp(open, event.getBlue(), 0.03f));
        }
        if (crystal == null || tier < 3) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() - lastSeen > 100) return;
        double dist = event.getCamera().getPosition().distanceTo(crystal);
        if (dist > 56) return;
        float near = (float) Mth.clamp(1 - (dist - 12) / 44, 0, 1);
        float[] c = mood == ObeliskTopBlockEntity.MOOD_DONE ? new float[]{0.85f, 0.65f, 0.3f}
                : tier >= 4 ? new float[]{0.45f, 0.3f, 0.65f} : new float[]{0.6f, 0.35f, 0.2f};
        float mix = 0.35f * near;
        event.setRed(Mth.lerp(mix, event.getRed(), c[0]));
        event.setGreen(Mth.lerp(mix, event.getGreen(), c[1]));
        event.setBlue(Mth.lerp(mix, event.getBlue(), c[2]));
    }

    // ---- aurora ----

    private static void drawAurora(RenderLevelStageEvent event) {
        if (crystal == null || tier < 4) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() - lastSeen > 100) return;
        Vec3 cam = event.getCamera().getPosition();
        if (cam.distanceToSqr(crystal) > 200 * 200) return;
        float time = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // only at night and dusk, like the real thing
        float visibility = Mth.clamp(1f - skyBrightness(mc, event), 0f, 1f);
        if (visibility <= 0.02f) return;
        boolean gold = mood == ObeliskTopBlockEntity.MOOD_DONE || tier >= 5;
        float[] lo = gold ? new float[]{1.0f, 0.75f, 0.3f} : new float[]{0.3f, 0.95f, 0.7f};
        float[] hi = gold ? new float[]{1.0f, 0.95f, 0.7f} : new float[]{0.6f, 0.35f, 0.95f};

        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(crystal.x - cam.x, crystal.y - cam.y, crystal.z - cam.z);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        // the curtains hang far out; without this the distance fog would swallow them
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Matrix4f m = pose.last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int curtains = 3;
        for (int k = 0; k < curtains; k++) {
            float baseY = 38 + k * 9;
            float height = 22 + k * 4;
            float radius = 26 + k * 14;
            int segs = 48;
            float phase = k * 2.1f;
            for (int i = 0; i < segs; i++) {
                float a0 = i / (float) segs * Mth.TWO_PI, a1 = (i + 1) / (float) segs * Mth.TWO_PI;
                float w0 = Mth.sin(a0 * 3 + time / 40f + phase) * 4 + Mth.sin(a0 * 7 - time / 23f) * 1.5f;
                float w1 = Mth.sin(a1 * 3 + time / 40f + phase) * 4 + Mth.sin(a1 * 7 - time / 23f) * 1.5f;
                float x0 = Mth.cos(a0) * (radius + w0), z0 = Mth.sin(a0) * (radius + w0);
                float x1 = Mth.cos(a1) * (radius + w1), z1 = Mth.sin(a1) * (radius + w1);
                float bright0 = 0.5f + 0.5f * Mth.sin(a0 * 5 + time / 17f + phase);
                float bright1 = 0.5f + 0.5f * Mth.sin(a1 * 5 + time / 17f + phase);
                // the curtains rise and fall along the ring, so they read as folds, not as a bowl
                float h0 = height * (0.55f + 0.45f * Mth.sin(a0 * 4 - time / 31f + phase));
                float h1 = height * (0.55f + 0.45f * Mth.sin(a1 * 4 - time / 31f + phase));
                float alpha = 0.2f * visibility;
                // bottom edge bright in the low colour, the top thins out in the high colour
                vertex(b, m, x0, baseY, z0, lo, alpha * (0.4f + 0.6f * bright0));
                vertex(b, m, x1, baseY, z1, lo, alpha * (0.4f + 0.6f * bright1));
                vertex(b, m, x1, baseY + h1 + w1, z1, hi, alpha * 0.25f * bright1);
                vertex(b, m, x0, baseY + h0 + w0, z0, hi, alpha * 0.25f * bright0);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** 1 in full daylight, 0 at night; the sun stands high at 0.0 and 1.0 of the day, midnight is 0.5. */
    private static float skyBrightness(Minecraft mc, RenderLevelStageEvent event) {
        float angle = mc.level.getTimeOfDay(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        float day = Mth.cos(angle * Mth.TWO_PI);
        return Mth.clamp(day * 0.9f + 0.6f, 0f, 1f);
    }

    private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, float z, float[] c, float a) {
        b.addVertex(m, x, y, z).setColor(c[0], c[1], c[2], a);
    }

    static BlockPos crystalPos() {
        return crystal == null ? null : BlockPos.containing(crystal);
    }
}
