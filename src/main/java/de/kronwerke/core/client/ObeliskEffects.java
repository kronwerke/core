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
 * <li>the camera shake of the burst and of a huge gift,</li>
 * <li>the signal: from the first stage, ribbons of light wind up from the crystal into the
 * sky, higher and wider with every stage, visible from far beyond the render distance,</li>
 * <li>the ground rings: two circles of runes shine on the pavement around the plinth and
 * turn against each other, bright at night, flaring with every gift.</li>
 * </ul>
 * The server sends where the obelisk stands and how it feels (StatePayload), so the far
 * effects do not depend on its blocks being rendered.
 */
public final class ObeliskEffects {
    private static final ResourceLocation SHOCKWAVE = ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "shaders/post/shockwave.json");
    private static final ResourceLocation VEIL = ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "shaders/post/veil.json");
    private static final ResourceLocation RING_OUTER = ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "textures/effect/rune_ring_outer.png");
    private static final ResourceLocation RING_INNER = ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "textures/effect/rune_ring_inner.png");

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
            if (now - be.lastDeposit() < 20) {
                giftAt = System.currentTimeMillis();
                giftStrength = be.flashStrength();
            }
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
                flashAt = System.currentTimeMillis();
                shake(1.2f, 40);
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
    private static long giftAt;
    private static float giftStrength;

    /** what the server last said about the obelisk, for the effects seen from far away */
    private static KwNetwork.StatePayload far;

    public static void state(KwNetwork.StatePayload payload) {
        far = payload.tier() < 0 ? null : payload;
    }

    /** True when the server's word about the obelisk applies to the level the player is in. */
    private static boolean farHere(Minecraft mc) {
        return far != null && mc.level != null && mc.level.dimension().location().toString().equals(far.dimension());
    }

    /** Where the crystal is: as last drawn when its blocks are in range, else as the server said. */
    private static Vec3 where(Minecraft mc) {
        if (crystal != null && mc.level != null && mc.level.getGameTime() - lastSeen <= 100) return crystal;
        return farHere(mc) ? new Vec3(far.x(), far.y(), far.z()) : null;
    }

    private static int tierNow(Minecraft mc) {
        if (crystal != null && mc.level != null && mc.level.getGameTime() - lastSeen <= 100) return tier;
        return farHere(mc) ? far.tier() : 0;
    }

    private static int moodNow(Minecraft mc) {
        if (crystal != null && mc.level != null && mc.level.getGameTime() - lastSeen <= 100) return mood;
        return farHere(mc) ? far.mood() : ObeliskTopBlockEntity.MOOD_IDLE;
    }

    private static int percentNow(Minecraft mc) {
        if (crystal != null && mc.level != null && mc.level.getGameTime() - lastSeen <= 100) return percent;
        return farHere(mc) ? far.percent() : 0;
    }

    private static ShaderInstance galaxy;
    private static KwNetwork.SkyPayload sky;
    private static long shakeUntil;
    private static float shakeStrength;
    private static long flashAt;

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
        shake(0.3f, 60);
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
            drawRings(event);
            drawSignal(event);
            drawFarBeam(event);
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
        // the flash lasts a second and a half of real time, the burst itself runs in slow motion
        float sinceFlash = (System.currentTimeMillis() - flashAt) / 1500f;
        float flash = flashAt > 0 && sinceFlash < 1f ? (1 - sinceFlash) * (1 - sinceFlash) : 0f;
        for (PostPass pass : passes(c)) {
            var eff = pass.getEffect();
            eff.safeGetUniform("Strength").set(strength);
            eff.safeGetUniform("Time").set((System.currentTimeMillis() % 100000) / 1000f);
            eff.safeGetUniform("Flash").set(flash);
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
        Minecraft mc = Minecraft.getInstance();
        Vec3 at = where(mc);
        int tier = tierNow(mc), mood = moodNow(mc);
        if (at == null || tier < 3) return;
        double dist = event.getCamera().getPosition().distanceTo(at);
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
        Minecraft mc = Minecraft.getInstance();
        Vec3 crystal = where(mc);
        int tier = tierNow(mc), mood = moodNow(mc);
        if (crystal == null || tier < 4) return;
        Vec3 cam = event.getCamera().getPosition();
        if (cam.distanceToSqr(crystal) > 400 * 400) return;
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

    // ---- the signal ----

    /**
     * Ribbons of light that wind up from the crystal into the sky. Their height and width grow
     * with the stage, their colour is the obelisk's, they thin out towards the top and a slow
     * pulse travels up them. By day they are faint, at night they are the thing one steers by.
     */
    private static void drawSignal(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 crystal = where(mc);
        int tier = tierNow(mc), mood = moodNow(mc);
        if (crystal == null || tier < 1 || mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        double dist = cam.distanceTo(crystal);
        if (dist > 600) return;
        float time = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float day = skyBrightness(mc, event);
        float strength = mood == ObeliskTopBlockEntity.MOOD_ASLEEP ? 0.3f : 1f;
        // the open sky has its own light, the signal steps back while the galaxy shows
        strength *= 1f - 0.7f * skyOpen();
        // up close the crystal and the beam already carry the picture; the signal fades in with distance
        strength *= (float) Mth.clamp((dist - 10) / 30, 0.15, 1);
        if (strength <= 0.01f) return;
        float[] colour = ObeliskTopRenderer.colour(percentNow(mc), mood, tier);

        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(crystal.x - cam.x, crystal.y - cam.y, crystal.z - cam.z);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Matrix4f m = pose.last().pose();
        // by day the light has to stand against a bright sky, so it is painted over it; by night it adds to the dark
        if (day > 0.03f) {
            RenderSystem.defaultBlendFunc();
            signal(m, time, tier, colour, strength * day * 1.6f);
        }
        if (day < 0.97f) {
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            signal(m, time, tier, colour, strength * (1 - day));
        }
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** The ribbons and the haze of the signal, around the origin, with the given overall strength. */
    private static void signal(Matrix4f m, float time, int tier, float[] colour, float strength) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float height = 50 + 26 * tier;
        float radius = 0.7f + 0.3f * tier;
        int ribbons = 2 + Math.min(3, tier);
        int steps = 48;
        float width = 0.45f + 0.1f * tier;
        for (int k = 0; k < ribbons; k++) {
            float phase = k * Mth.TWO_PI / ribbons;
            for (int i = 0; i < steps; i++) {
                float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
                float y0 = t0 * height, y1 = t1 * height;
                // the ribbons widen as they rise and turn slowly, each at its own pace
                float r0 = radius * (1 + 1.4f * t0), r1 = radius * (1 + 1.4f * t1);
                float a0 = phase + t0 * 5.5f + time * 0.012f * (1 + k * 0.15f), a1 = phase + t1 * 5.5f + time * 0.012f * (1 + k * 0.15f);
                float pulse0 = 0.55f + 0.45f * Mth.sin(t0 * 14f - time * 0.09f + phase);
                float pulse1 = 0.55f + 0.45f * Mth.sin(t1 * 14f - time * 0.09f + phase);
                float fade0 = (1 - t0) * (1 - t0) * Math.min(1f, t0 * 8), fade1 = (1 - t1) * (1 - t1) * Math.min(1f, t1 * 8);
                float alpha0 = 0.55f * strength * fade0 * pulse0, alpha1 = 0.55f * strength * fade1 * pulse1;
                float cx0 = Mth.cos(a0) * r0, cz0 = Mth.sin(a0) * r0, cx1 = Mth.cos(a1) * r1, cz1 = Mth.sin(a1) * r1;
                // the ribbon has a width across the radius, bright in the middle and soft at the edges
                float nx0 = Mth.cos(a0) * width, nz0 = Mth.sin(a0) * width, nx1 = Mth.cos(a1) * width, nz1 = Mth.sin(a1) * width;
                vertex(b, m, cx0 - nx0, y0, cz0 - nz0, colour, 0);
                vertex(b, m, cx1 - nx1, y1, cz1 - nz1, colour, 0);
                vertex(b, m, cx1, y1, cz1, colour, alpha1);
                vertex(b, m, cx0, y0, cz0, colour, alpha0);
                vertex(b, m, cx0, y0, cz0, colour, alpha0);
                vertex(b, m, cx1, y1, cz1, colour, alpha1);
                vertex(b, m, cx1 + nx1, y1, cz1 + nz1, colour, 0);
                vertex(b, m, cx0 + nx0, y0, cz0 + nz0, colour, 0);
            }
        }
        // a soft column of haze inside the ribbons
        int sides = 16;
        float haze = 0.06f * strength;
        for (int i = 0; i < sides; i++) {
            float a0 = i / (float) sides * Mth.TWO_PI, a1 = (i + 1) / (float) sides * Mth.TWO_PI;
            float r = radius * 0.9f;
            vertex(b, m, Mth.cos(a0) * r, 0, Mth.sin(a0) * r, colour, haze);
            vertex(b, m, Mth.cos(a1) * r, 0, Mth.sin(a1) * r, colour, haze);
            vertex(b, m, Mth.cos(a1) * r * 2.4f, height * 0.8f, Mth.sin(a1) * r * 2.4f, colour, 0);
            vertex(b, m, Mth.cos(a0) * r * 2.4f, height * 0.8f, Mth.sin(a0) * r * 2.4f, colour, 0);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    // ---- the rite's beam, seen from far ----

    /**
     * The great beam of the rite for players too far away for the obelisk's blocks to be
     * drawn: the column that comes down from the tear and the gold pillar that follows the
     * burst, timed from the sky payload like the renderer times them from the block entity.
     */
    private static void drawFarBeam(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (sky == null || mc.level == null) return;
        // in range the renderer of the top block draws the real thing
        if (crystal != null && mc.level.getGameTime() - lastSeen <= 100) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // the sky opened 20 ticks before the intake began
        float riteT = mc.level.getGameTime() + partial - sky.start() + (de.kronwerke.core.obelisk.ObeliskRite.T_INTAKE - 20);
        if (riteT < de.kronwerke.core.obelisk.ObeliskRite.T_INTAKE || riteT >= de.kronwerke.core.obelisk.ObeliskRite.T_ROLL) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(sky.x() - cam.x, sky.y() - cam.y, sky.z() - cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        float[] white = {1f, 1f, 1f};
        float[] gold = {1f, 0.84f, 0.5f};
        // the beam faces the camera as a sheet with soft edges, so it reads as light and not as a pipe
        Vec3 toCam = new Vec3(cam.x - sky.x(), 0, cam.z - sky.z());
        float side = (float) Math.atan2(toCam.z, toCam.x) + Mth.HALF_PI;
        if (riteT < de.kronwerke.core.obelisk.ObeliskRite.T_BURST) {
            float p = (riteT - de.kronwerke.core.obelisk.ObeliskRite.T_INTAKE) / de.kronwerke.core.obelisk.ObeliskRite.INTAKE;
            p = p * p;
            float bottom = 1 + 420 * (1 - p);
            float radius = 0.4f + 2.6f * p;
            sheet(m, side, bottom, 1024, radius * 2.5f, white, 0.35f);
            sheet(m, side, bottom, 1024, radius, white, 0.9f);
        } else {
            float p = riteT < de.kronwerke.core.obelisk.ObeliskRite.T_REFORM ? 0f : (riteT - de.kronwerke.core.obelisk.ObeliskRite.T_REFORM) / de.kronwerke.core.obelisk.ObeliskRite.REFORM;
            float radius = Mth.lerp(p * p, 3.2f, 0.25f);
            sheet(m, side, -17, 1024, radius * 3f, gold, 0.3f);
            sheet(m, side, -17, 1024, radius * 1.2f, gold, 0.7f);
            sheet(m, side, -17, 1024, radius * 0.5f, white, 0.9f);
        }
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** A vertical sheet of light turned to face the camera, bright along its middle and clear at the edges. */
    private static void sheet(Matrix4f m, float side, float y0, float y1, float halfWidth, float[] c, float alpha) {
        float dx = Mth.cos(side) * halfWidth, dz = Mth.sin(side) * halfWidth;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(b, m, -dx, y0, -dz, c, 0);
        vertex(b, m, -dx, y1, -dz, c, 0);
        vertex(b, m, 0, y1, 0, c, alpha);
        vertex(b, m, 0, y0, 0, c, alpha);
        vertex(b, m, 0, y0, 0, c, alpha);
        vertex(b, m, 0, y1, 0, c, alpha);
        vertex(b, m, dx, y1, dz, c, 0);
        vertex(b, m, dx, y0, dz, c, 0);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    // ---- the ground rings ----

    /**
     * Two circles of runes on the pavement around the plinth, turning against each other in
     * the obelisk's colour. They are bright at night and faint by day, and every gift makes
     * them flare for a moment by its size.
     */
    private static void drawRings(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!farHere(mc) || far.tier() < 1 || mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        double cx = far.x(), cz = far.z(), cy = far.baseY() + 0.03;
        double dist = cam.distanceTo(new Vec3(cx, cy, cz));
        if (dist > 64) return;
        float time = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float night = Mth.clamp(1f - skyBrightness(mc, event), 0f, 1f);
        int mood = moodNow(mc);
        float strength = (0.35f + 0.65f * night) * (mood == ObeliskTopBlockEntity.MOOD_ASLEEP ? 0.35f : 1f) * (float) Mth.clamp(1 - (dist - 40) / 24, 0, 1);
        float sinceGift = (System.currentTimeMillis() - giftAt) / 1200f;
        float gift = giftAt > 0 && sinceGift < 1f ? (1 - sinceGift) * (1 - sinceGift) * giftStrength : 0f;
        strength = Math.min(1f, strength + gift);
        if (strength <= 0.01f) return;
        float[] colour = ObeliskTopRenderer.colour(percentNow(mc), mood, tierNow(mc));
        // the rings breathe with the stone
        float breathe = 0.85f + 0.15f * Mth.sin(time / 14f);

        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(cx - cam.x, cy - cam.y, cz - cam.z);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        // kept below full strength on purpose: added light saturates to white and the colour is the point
        ring(pose, RING_OUTER, time * 0.0025f, colour, strength * breathe * 0.75f);
        ring(pose, RING_INNER, -time * 0.004f, colour, strength * breathe * 0.65f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void ring(PoseStack pose, ResourceLocation texture, float angle, float[] c, float alpha) {
        RenderSystem.setShaderTexture(0, texture);
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotation(angle));
        Matrix4f m = pose.last().pose();
        float r = 8f;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(m, -r, 0, -r).setUv(0, 0).setColor(c[0], c[1], c[2], alpha);
        b.addVertex(m, -r, 0, r).setUv(0, 1).setColor(c[0], c[1], c[2], alpha);
        b.addVertex(m, r, 0, r).setUv(1, 1).setColor(c[0], c[1], c[2], alpha);
        b.addVertex(m, r, 0, -r).setUv(1, 0).setColor(c[0], c[1], c[2], alpha);
        BufferUploader.drawWithShader(b.buildOrThrow());
        pose.popPose();
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
