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
 * turn against each other, bright at night, flaring with every gift,</li>
 * <li>the thin sky: from the second stage the veil above the obelisk is worn, and a faint
 * patch of the galaxy shows through it day and night, growing with the stage,</li>
 * <li>the rite's own extras: during the intake the pylons fire their light into the crystal,
 * and at the burst cracks of light run out from the plinth across the ground,</li>
 * <li>the gaze: a player who looks straight at the crystal for two seconds is noticed, the
 * crystal flares towards them and the stone whispers once,</li>
 * <li>the crowd: the more players stand around the plinth, the livelier the shards, the rings
 * and the signal,</li>
 * <li>the gift: the item given flies from the giver's hand to the crystal and is taken in, a
 * ripple runs out over the pavement and the amount rises from the crystal in the pillar's
 * colour.</li>
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
        depositTick = be.lastDeposit();
        depositStrength = be.flashStrength();
        if (be.rite() > 0) {
            long t = now - be.rite();
            if (t >= de.kronwerke.core.obelisk.ObeliskRite.T_BURST && t < de.kronwerke.core.obelisk.ObeliskRite.T_BURST + 2 && waveStart != be.rite()) {
                waveStart = be.rite();
                boolean soft = de.kronwerke.core.config.KronwerkeClientConfig.fewerFlashes();
                shockwave(crystal, soft ? 0.6f : 1.6f, 60);
                flashAt = System.currentTimeMillis();
                shake(soft ? 0.3f : 1.4f, 50);
                RiteFx.burst();
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
    static KwNetwork.StatePayload far;

    public static void state(KwNetwork.StatePayload payload) {
        far = payload.tier() < 0 ? null : payload;
    }

    /** True when the server's word about the obelisk applies to the level the player is in. */
    static boolean farHere(Minecraft mc) {
        return far != null && mc.level != null && mc.level.dimension().location().toString().equals(far.dimension());
    }

    /** Where the crystal is: as last drawn when its blocks are in range, else as the server said. */
    static Vec3 where(Minecraft mc) {
        if (crystal != null && mc.level != null && mc.level.getGameTime() - lastSeen <= 100) return crystal;
        return farHere(mc) ? new Vec3(far.x(), far.y(), far.z()) : null;
    }

    static int tierNow(Minecraft mc) {
        if (crystal != null && mc.level != null && mc.level.getGameTime() - lastSeen <= 100) return tier;
        return farHere(mc) ? far.tier() : 0;
    }

    static int moodNow(Minecraft mc) {
        if (crystal != null && mc.level != null && mc.level.getGameTime() - lastSeen <= 100) return mood;
        return farHere(mc) ? far.mood() : ObeliskTopBlockEntity.MOOD_IDLE;
    }

    /** How many players the server counts around the plinth. */
    static int crowd() {
        return far == null ? 0 : far.crowd();
    }

    static int percentNow(Minecraft mc) {
        if (crystal != null && mc.level != null && mc.level.getGameTime() - lastSeen <= 100) return percent;
        return farHere(mc) ? far.percent() : 0;
    }

    private static ShaderInstance galaxy;
    private static KwNetwork.SkyPayload sky;
    private static long shakeUntil;
    private static float shakeStrength;
    static long flashAt;
    /** the last gift the top block showed, for the flare of the beam */
    private static long depositTick;
    private static float depositStrength;

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        RiteFx.register();
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
                e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "beam"), DefaultVertexFormat.POSITION_TEX_COLOR), ObeliskBeam::shader);
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
        RiteFx.onReload();
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
            // a shader pack paints its own sky over anything drawn with the sky, so then the galaxy
            // comes first after the world, while the depth buffer still holds the world
            if (shaderPack()) drawSky(event, true);
            gaze(event);
            drawRings(event);
            drawGifts(event);
            drawSignal(event);
            RiteFx.drawWorld(event);
            drawBeams(event);
            drawPylonBeams(event);
            drawCracks(event);
            drawAurora(event);
            // the post effects last: after a post pass the depth buffer is no longer the world's
            RiteFx.drawPost(event);
            drawVeil(event);
            drawShockwave(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            if (!shaderPack()) drawSky(event, false);
        }
    }

    private static java.lang.reflect.Method irisInUse;
    private static Object irisApi;
    private static boolean irisLooked;

    /** True while an Iris shader pack is in use; Iris is optional, so it is asked through reflection. */
    static boolean shaderPack() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                irisApi = null;
            }
        }
        if (irisApi == null) return false;
        try {
            return (Boolean) irisInUse.invoke(irisApi);
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Where the rite stands right now in ticks from its start, or -1 when none runs: from the
     * top block entity when it is in view, else from the sky payload (sent 20 ticks before the
     * intake).
     */
    static float riteTime(Minecraft mc, float partial) {
        if (mc.level == null) return -1;
        long now = mc.level.getGameTime();
        if (crystal != null && now - lastSeen <= 100) {
            if (lastRiteSeen <= 0) return -1;
            float t = now - lastRiteSeen + partial;
            return t < de.kronwerke.core.obelisk.ObeliskRite.T_END ? t : -1;
        }
        if (sky == null) return -1;
        float t = now + partial - sky.start() + de.kronwerke.core.obelisk.ObeliskRite.T_PULL;
        return t >= 0 && t < de.kronwerke.core.obelisk.ObeliskRite.T_END ? t : -1;
    }

    // ---- the torn sky ----

    private static void drawSky(RenderLevelStageEvent event, boolean afterWorld) {
        if (galaxy == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade, tear, tier;
        float cxd = 0, cyd = 1, czd = 0;
        if (sky != null && mc.level.getGameTime() + partial - sky.start() > sky.duration()) sky = null;
        if (sky != null) {
            float t = mc.level.getGameTime() + partial - sky.start();
            if (t < 0) return;
            float in = Math.min(1f, t / 70f);
            float out = Math.min(1f, (sky.duration() - t) / 80f);
            tear = in * in * (3 - 2 * in);
            fade = Math.min(1f, t / 25f) * out;
            tier = sky.tier();
        } else {
            // between rites: the thin sky above the obelisk, from the second stage
            Vec3 at = where(mc);
            int tierNow = tierNow(mc);
            if (at == null || tierNow < 2) return;
            Vec3 cam = event.getCamera().getPosition();
            double dist = cam.distanceTo(at);
            if (dist > 160) return;
            float near = (float) Mth.clamp(1 - (dist - 100) / 60, 0, 1);
            float breathe = 0.8f + 0.2f * Mth.sin((mc.level.getGameTime() + partial) / 90f);
            fade = (0.14f + 0.07f * (tierNow - 2)) * near * breathe;
            if (moodNow(mc) == ObeliskTopBlockEntity.MOOD_ASLEEP) fade *= 0.4f;
            tear = 0.16f + 0.03f * (tierNow - 2);
            tier = tierNow;
            // the patch hangs high above the obelisk, seen from where the player stands
            Vec3 c = new Vec3(at.x - cam.x, at.y + 120 - cam.y, at.z - cam.z).normalize();
            cxd = (float) c.x;
            cyd = (float) c.y;
            czd = (float) c.z;
        }
        if (fade <= 0.002f) return;
        float time = (mc.level.getGameTime() + partial) / 20f;

        galaxy.safeGetUniform("Time").set(time);
        galaxy.safeGetUniform("Fade").set(fade);
        galaxy.safeGetUniform("Tear").set(tear);
        galaxy.safeGetUniform("Tier").set(tier);
        galaxy.safeGetUniform("Center").set(cxd, cyd, czd);
        RenderSystem.setShader(() -> galaxy);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        if (afterWorld) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
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
        // the stillness before the tear darkens the picture too
        Minecraft mcv = Minecraft.getInstance();
        float riteV = riteTime(mcv, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        float still = riteV >= 0 && riteV < de.kronwerke.core.obelisk.ObeliskRite.T_PULL + 60 ? Math.min(1f, riteV / 40f) * 0.8f : 0f;
        if (open <= 0.01f && still <= 0.01f) return;
        PostChain c = veil();
        if (c == null) return;
        Minecraft mc = Minecraft.getInstance();
        float strength = Math.max(0.75f * open, still);
        // the flash lasts a second and a half of real time, the burst itself runs in slow motion
        float sinceFlash = (System.currentTimeMillis() - flashAt) / 1500f;
        float flash = flashAt > 0 && sinceFlash < 1f ? (1 - sinceFlash) * (1 - sinceFlash) : 0f;
        if (de.kronwerke.core.config.KronwerkeClientConfig.fewerFlashes()) flash *= 0.25f;
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
        strength *= 1f + 0.08f * Math.min(crowd(), 6);
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

    // ---- the beams ----

    /**
     * Every beam of the obelisk, near or far, drawn with {@link ObeliskBeam}: the everyday beam
     * from the point of the crystal in its colour, and through the rite the great beam that
     * comes down out of the tear, swells white at the burst and narrows back in gold.
     */
    private static void drawBeams(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ObeliskBeam.ready() || mc.level == null) return;
        Vec3 c = where(mc);
        if (c == null) return;
        Vec3 cam = event.getCamera().getPosition();
        if (cam.distanceToSqr(c) > 700 * 700) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float riteT = riteTime(mc, partial);
        int tier = tierNow(mc), mood = moodNow(mc);
        boolean asleep = mood == ObeliskTopBlockEntity.MOOD_ASLEEP;
        float[] colour = ObeliskTopRenderer.colour(percentNow(mc), mood, tier);
        float[] white = {1f, 1f, 1f};
        float[] gold = {1f, 0.82f, 0.45f};
        float[] cold = {0.75f, 0.92f, 1f};
        long now = mc.level.getGameTime();
        float since = now - depositTick + partial;
        float flare = depositTick > 0 && since < 40 ? (1 - since / 40f) * depositStrength : 0f;
        boolean soft = de.kronwerke.core.config.KronwerkeClientConfig.fewerFlashes();
        double ground = farHere(mc) ? far.baseY() : c.y - 18;
        Vec3 tip = c.add(0, 0.6, 0);
        float seconds = (System.currentTimeMillis() % 1_000_000L) / 1000f;
        float flow = 1f, rings = soft ? 0.3f : 0.7f;

        ObeliskBeam.begin(event.getModelViewMatrix(), cam);
        if (riteT < 0 || riteT >= de.kronwerke.core.obelisk.ObeliskRite.T_END) {
            float r = (asleep ? 0.2f : 0.38f + 0.04f * tier) + flare * 0.35f;
            ObeliskBeam.column(tip, 900, r, colour, asleep ? 0.35f : 0.85f + flare * 0.3f);
            flow = asleep ? 0.3f : 1f + flare;
        } else if (riteT < de.kronwerke.core.obelisk.ObeliskRite.T_PULL) {
            // the stillness: the beam goes out
            float fade = 1f - riteT / de.kronwerke.core.obelisk.ObeliskRite.T_PULL;
            ObeliskBeam.column(tip, 900, 0.38f + 0.04f * tier, colour, 0.85f * fade * fade);
            flow = 0.2f;
        } else if (riteT < de.kronwerke.core.obelisk.ObeliskRite.T_BURST) {
            // the pull: the great beam comes down out of the tear, then swells and throbs
            float p = (riteT - de.kronwerke.core.obelisk.ObeliskRite.T_PULL) / de.kronwerke.core.obelisk.ObeliskRite.PULL;
            float down = Math.min(1f, (riteT - de.kronwerke.core.obelisk.ObeliskRite.T_PULL) / 80f);
            down = 1 - (1 - down) * (1 - down) * (1 - down);
            double bottom = c.y + 480 * (1 - down);
            float throb = 1f + 0.12f * Mth.sin(riteT * 0.35f) * p;
            float r = (0.6f + 1.8f * p * p) * throb;
            float[] mix = {Mth.lerp(p, cold[0], 1f), Mth.lerp(p, cold[1], 1f), Mth.lerp(p, cold[2], 1f)};
            ObeliskBeam.column(new Vec3(c.x, bottom, c.z), (float) (900 - (bottom - c.y)), r, mix, 1f);
            ObeliskBeam.column(new Vec3(c.x, bottom, c.z), (float) (900 - (bottom - c.y)), r * 3.2f, mix, 0.18f + 0.15f * p);
            flow = 1f + p;
            rings = soft ? 0.3f : 0.6f + 0.4f * p;
        } else if (riteT < de.kronwerke.core.obelisk.ObeliskRite.T_REFORM) {
            // the burst: a white pillar from the ground to the sky, wider than the obelisk
            float bt = riteT - de.kronwerke.core.obelisk.ObeliskRite.T_BURST;
            float swell = bt < 6 ? bt / 6f : Math.max(0f, 1f - (bt - 6) / (de.kronwerke.core.obelisk.ObeliskRite.BURST - 6f));
            float r = 2.4f + 6f * swell;
            Vec3 base = new Vec3(c.x, ground, c.z);
            ObeliskBeam.column(base, 960, r, white, 1f);
            ObeliskBeam.column(base, 960, r * 2.5f, gold, 0.35f * swell + 0.1f);
            flow = 2f;
            rings = soft ? 0.3f : 1f;
        } else if (riteT < de.kronwerke.core.obelisk.ObeliskRite.T_ROLL) {
            // the return: the gold pillar narrows back to the everyday beam
            float p = (riteT - de.kronwerke.core.obelisk.ObeliskRite.T_REFORM) / de.kronwerke.core.obelisk.ObeliskRite.REFORM;
            float e = p * p * (3 - 2 * p);
            float r = Mth.lerp(e, 2.6f, 0.5f);
            Vec3 base = new Vec3(c.x, Mth.lerp(e, (float) ground, (float) tip.y), c.z);
            ObeliskBeam.column(base, 960, r, gold, 1f);
            ObeliskBeam.column(base, 960, r * 0.4f, white, 0.8f);
            flow = 1.5f - 0.5f * p;
        } else {
            ObeliskBeam.column(tip, 900, 0.55f, gold, 1f);
            flow = 1.2f;
        }
        ObeliskBeam.end(seconds, flow, rings);
    }

    // ---- the gift ----

    private static final int[] PILLAR_COLOURS = {0x9aa0a8, 0xd4a24a, 0x9a6fd6};
    private static final int GIFT_FLIGHT = 24, GIFT_AFTER = 50;

    /** One gift in flight or just arrived: where from, what, when it left (real time), how big. */
    private record Gift(Vec3 from, net.minecraft.world.item.ItemStack stack, long startMillis, int size, int pillar, long amount) {
    }

    private static final List<Gift> gifts = new java.util.ArrayList<>();

    public static void gift(KwNetwork.GiftPayload payload) {
        gifts.add(new Gift(new Vec3(payload.x(), payload.y(), payload.z()), payload.stack(), System.currentTimeMillis(), payload.size(), payload.pillar(), payload.amount()));
        if (gifts.size() > 24) gifts.remove(0);
    }

    /** The flight of the gift and what follows it, drawn every frame from real time so it is smooth whatever the tick rate. */
    private static void drawGifts(RenderLevelStageEvent event) {
        if (gifts.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 crystal = where(mc);
        if (crystal == null || mc.level == null) {
            gifts.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Vec3 cam = event.getCamera().getPosition();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        java.util.Iterator<Gift> it = gifts.iterator();
        while (it.hasNext()) {
            Gift g = it.next();
            float t = (now - g.startMillis()) / 50f;
            if (t > GIFT_FLIGHT + GIFT_AFTER) {
                it.remove();
                continue;
            }
            float[] colour = pillarColour(g.pillar());
            if (t < GIFT_FLIGHT) {
                // the flight: an arc that leans up and in, quick at the end, the item spinning
                float p = t / GIFT_FLIGHT;
                float ease = p * p * (3 - 2 * p);
                Vec3 target = crystal;
                Vec3 at = flightPoint(g, p, target);
                // the giver's own gift leaves from right in front of the camera: it grows out of the hand
                boolean own = g.from().distanceToSqr(cam) < 4;
                float grow = own ? Mth.clamp(p / 0.25f, 0.05f, 1f) : 1f;
                PoseStack pose = new PoseStack();
                pose.mulPose(event.getModelViewMatrix());
                pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
                // a trail of light behind the item
                if (!own || p > 0.2f) drawTrail(pose, g, p, colour, cam, target);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(t * 18f));
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(t * 7f));
                float scale = (0.6f + 0.15f * g.size()) * (1f - 0.6f * p * p) * grow;
                pose.scale(scale, scale, scale);
                mc.getItemRenderer().renderStatic(g.stack(), net.minecraft.world.item.ItemDisplayContext.GROUND, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
            } else {
                float a = (t - GIFT_FLIGHT) / GIFT_AFTER;
                drawRipple(event, mc, cam, a, g.size(), colour);
                drawRisingNumber(event, mc, cam, crystal, a, g, colour, buffers);
            }
        }
        buffers.endBatch();
    }

    /** Where the gift is at p of its flight: an arc from the giver to the crystal that leans up and in. */
    private static Vec3 flightPoint(Gift g, float p, Vec3 target) {
        float ease = p * p * (3 - 2 * p);
        double lift = Math.sin(p * Math.PI) * Math.min(4.0, 1.0 + 0.1 * g.from().distanceTo(target));
        return g.from().lerp(target, ease).add(0, lift, 0);
    }

    private static float[] pillarColour(int pillar) {
        int c = pillar >= 0 && pillar < 3 ? PILLAR_COLOURS[pillar] : 0xf6d68c;
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    /** A few fading quads along the path the item has flown. */
    private static void drawTrail(PoseStack pose, Gift g, float p, float[] colour, Vec3 cam, Vec3 target) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Matrix4f m = pose.last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vec3 here = null;
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            float q = Math.max(0f, p - i * 0.025f);
            Vec3 at = flightPoint(g, q, target);
            if (here == null) {
                here = at;
                continue;
            }
            // nothing right in front of the camera, it would fill the screen
            if (at.distanceToSqr(cam) < 2.5) break;
            // positions relative to the item, which sits at the pose's origin
            Vec3 a0 = at.subtract(here), a1 = i == 1 ? Vec3.ZERO : prevRel;
            float w = 0.12f * (1 - i / (float) steps) * (0.7f + 0.2f * g.size());
            float alpha = 0.5f * (1 - i / (float) steps);
            vertex(b, m, (float) a1.x - w, (float) a1.y, (float) a1.z, colour, alpha);
            vertex(b, m, (float) a1.x + w, (float) a1.y, (float) a1.z, colour, alpha);
            vertex(b, m, (float) a0.x + w, (float) a0.y, (float) a0.z, colour, 0);
            vertex(b, m, (float) a0.x - w, (float) a0.y, (float) a0.z, colour, 0);
            vertex(b, m, (float) a1.x, (float) a1.y - w, (float) a1.z, colour, alpha);
            vertex(b, m, (float) a1.x, (float) a1.y + w, (float) a1.z, colour, alpha);
            vertex(b, m, (float) a0.x, (float) a0.y + w, (float) a0.z, colour, 0);
            vertex(b, m, (float) a0.x, (float) a0.y - w, (float) a0.z, colour, 0);
            prevRel = a0;
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static Vec3 prevRel = Vec3.ZERO;

    /** A ring of light that runs out over the pavement from the plinth, wider for a bigger gift. */
    private static void drawRipple(RenderLevelStageEvent event, Minecraft mc, Vec3 cam, float a, int size, float[] colour) {
        if (!farHere(mc) || a > 0.6f) return;
        float q = a / 0.6f;
        float r = 3.5f + (4f + 3f * size) * q;
        float alpha = (1 - q) * (0.6f + 0.25f * size);
        float width = 1.0f + 0.4f * size;
        float y = far.baseY() + 0.05f;
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(far.x() - cam.x, y - cam.y, far.z() - cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int sides = 48;
        for (int i = 0; i < sides; i++) {
            float a0 = i / (float) sides * Mth.TWO_PI, a1 = (i + 1) / (float) sides * Mth.TWO_PI;
            vertex(b, m, Mth.cos(a0) * (r - width), 0, Mth.sin(a0) * (r - width), colour, 0);
            vertex(b, m, Mth.cos(a1) * (r - width), 0, Mth.sin(a1) * (r - width), colour, 0);
            vertex(b, m, Mth.cos(a1) * r, 0, Mth.sin(a1) * r, colour, alpha);
            vertex(b, m, Mth.cos(a0) * r, 0, Mth.sin(a0) * r, colour, alpha);
            vertex(b, m, Mth.cos(a0) * r, 0, Mth.sin(a0) * r, colour, alpha);
            vertex(b, m, Mth.cos(a1) * r, 0, Mth.sin(a1) * r, colour, alpha);
            vertex(b, m, Mth.cos(a1) * (r + width * 0.5f), 0, Mth.sin(a1) * (r + width * 0.5f), colour, 0);
            vertex(b, m, Mth.cos(a0) * (r + width * 0.5f), 0, Mth.sin(a0) * (r + width * 0.5f), colour, 0);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** The amount, and for a bigger gift the item's name, rising from the crystal and fading. */
    private static void drawRisingNumber(RenderLevelStageEvent event, Minecraft mc, Vec3 cam, Vec3 crystal, float a, Gift g, float[] colour,
                                         net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers) {
        float rise = 1.2f + 3.0f * (1 - (1 - a) * (1 - a));
        float alpha = a < 0.15f ? a / 0.15f : a > 0.7f ? (1 - a) / 0.3f : 1f;
        if (alpha <= 0.02f) return;
        Vec3 at = crystal.add(0, rise, 0);
        double dist = cam.distanceTo(at);
        if (dist > 48) return;
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(event.getCamera().rotation());
        // bigger with distance so it stays readable, bigger for a bigger gift
        float scale = (0.07f + 0.025f * g.size()) * (float) Math.max(1, dist / 10);
        pose.scale(scale, -scale, scale);
        net.minecraft.client.gui.Font font = mc.font;
        String line = "+" + de.kronwerke.core.Text.number(g.amount());
        int rgb = ((int) (colour[0] * 255) << 16) | ((int) (colour[1] * 255) << 8) | (int) (colour[2] * 255);
        int argb = ((int) (alpha * 255) << 24) | rgb;
        float x = -font.width(line) / 2f;
        Matrix4f m = pose.last().pose();
        font.drawInBatch(line, x, -font.lineHeight, argb, true, m, buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        if (g.size() >= 1) {
            String name = g.stack().getHoverName().getString();
            float sx = 0.7f;
            pose.scale(sx, sx, sx);
            font.drawInBatch(name, -font.width(name) / 2f, 4, ((int) (alpha * 220) << 24) | 0xffffff, true, pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        }
    }

    // ---- the gaze ----

    private static int gazeTicks;
    private static long gazeLastTick;
    private static float gazeGlow;
    private static boolean gazeNoticed;

    /**
     * Whoever looks straight at the crystal from within thirty blocks for two seconds is
     * noticed: the crystal flares towards them with a soft billboard of light and the stone
     * whispers once. Looking away lets it fade.
     */
    private static void gaze(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 at = where(mc);
        if (at == null || mc.level == null || mc.player == null) return;
        long tick = mc.level.getGameTime();
        boolean newTick = tick != gazeLastTick;
        // counted in game ticks, not frames, so a slow client is noticed as soon as a fast one
        int passed = (int) Mth.clamp(tick - gazeLastTick, 1, 10);
        gazeLastTick = tick;
        Vec3 cam = event.getCamera().getPosition();
        Vec3 to = at.subtract(cam);
        double dist = to.length();
        Vec3 look = mc.player.getViewVector(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        boolean looking = dist < 30 && dist > 2 && look.dot(to.normalize()) > 0.9985;
        if (newTick) {
            if (looking) gazeTicks = Math.min(gazeTicks + passed, 80);
            else gazeTicks = Math.max(gazeTicks - 3 * passed, 0);
            if (gazeTicks >= 40 && !gazeNoticed) {
                gazeNoticed = true;
                mc.level.playLocalSound(at.x, at.y, at.z, de.kronwerke.core.obelisk.KwSounds.WHISPER.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1f, false);
                for (int i = 0; i < 6; i++) {
                    mc.level.addParticle(de.kronwerke.core.obelisk.KwParticles.RUNE.get(), at.x + (mc.level.random.nextDouble() - 0.5), at.y + (mc.level.random.nextDouble() - 0.5), at.z + (mc.level.random.nextDouble() - 0.5),
                            -to.x * 0.01, -to.y * 0.01, -to.z * 0.01);
                }
            }
            if (gazeTicks == 0) gazeNoticed = false;
        }
        float target = gazeTicks >= 40 ? Mth.clamp((gazeTicks - 40) / 20f, 0f, 1f) : 0f;
        gazeGlow += (target - gazeGlow) * 0.08f;
        if (gazeGlow <= 0.01f) return;
        float[] colour = ObeliskTopRenderer.colour(percentNow(mc), moodNow(mc), tierNow(mc));
        float time = tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float size = (0.8f + 1.6f * gazeGlow) * (1f + 0.08f * Mth.sin(time / 3f));
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        // the billboard faces the camera
        pose.mulPose(event.getCamera().rotation());
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int rays = 24;
        float alpha = 0.55f * gazeGlow;
        for (int i = 0; i < rays; i++) {
            float a0 = i / (float) rays * Mth.TWO_PI, a1 = (i + 1) / (float) rays * Mth.TWO_PI;
            // a soft disc, bright at the centre, with a slow ripple along its edge
            float r0 = size * (0.8f + 0.2f * Mth.sin(a0 * 5 + time / 4f)), r1 = size * (0.8f + 0.2f * Mth.sin(a1 * 5 + time / 4f));
            b.addVertex(m, 0, 0, 0).setColor(1f, 1f, 1f, alpha);
            b.addVertex(m, Mth.cos(a0) * r0, Mth.sin(a0) * r0, 0).setColor(colour[0], colour[1], colour[2], 0f);
            b.addVertex(m, Mth.cos(a1) * r1, Mth.sin(a1) * r1, 0).setColor(colour[0], colour[1], colour[2], 0f);
        }
        // four long rays, like a lens catching the light
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI + Mth.PI / 4 + time / 60f;
            float len = size * 3.5f, w = size * 0.12f;
            float dx = Mth.cos(a), dy = Mth.sin(a);
            b.addVertex(m, -dy * w, dx * w, 0).setColor(1f, 1f, 1f, alpha * 0.6f);
            b.addVertex(m, dy * w, -dx * w, 0).setColor(1f, 1f, 1f, alpha * 0.6f);
            b.addVertex(m, dx * len, dy * len, 0).setColor(colour[0], colour[1], colour[2], 0f);
            b.addVertex(m, dy * w, -dx * w, 0).setColor(1f, 1f, 1f, alpha * 0.6f);
            b.addVertex(m, -dy * w, dx * w, 0).setColor(1f, 1f, 1f, alpha * 0.6f);
            b.addVertex(m, -dx * len, -dy * len, 0).setColor(colour[0], colour[1], colour[2], 0f);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ---- the pylons' light and the cracks of the burst ----

    /**
     * During the intake the four pylons fire their light into the crystal: thin sheets from each
     * lantern to the point of the crystal, brighter as the intake nears the burst. From the
     * second stage, when the pylons exist.
     */
    private static void drawPylonBeams(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!farHere(mc) || far.tier() < 2 || mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float riteT = riteTime(mc, partial);
        if (riteT < de.kronwerke.core.obelisk.ObeliskRite.T_INTAKE || riteT >= de.kronwerke.core.obelisk.ObeliskRite.T_BURST + 6) return;
        float p = Mth.clamp((riteT - de.kronwerke.core.obelisk.ObeliskRite.T_INTAKE) / de.kronwerke.core.obelisk.ObeliskRite.INTAKE, 0f, 1f);
        float after = riteT >= de.kronwerke.core.obelisk.ObeliskRite.T_BURST ? 1 - (riteT - de.kronwerke.core.obelisk.ObeliskRite.T_BURST) / 6f : 1f;
        Vec3 cam = event.getCamera().getPosition();
        Vec3 crystal = new Vec3(far.x(), far.y(), far.z());
        if (cam.distanceToSqr(crystal) > 300 * 300) return;
        float time = mc.level.getGameTime() + partial;
        float[] colour = ObeliskTopRenderer.colour(far.percent(), far.mood(), far.tier());
        float[] white = {1f, 1f, 1f};
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // the crystal is drawn in during the intake, the beams aim at where it is
        Vec3 target = crystal.add(0, 0.2, 0);
        int[][] dirs = {{6, 0}, {-6, 0}, {0, 6}, {0, -6}};
        for (int i = 0; i < 4; i++) {
            Vec3 from = new Vec3(far.x() + dirs[i][0], far.baseY() + 2.5, far.z() + dirs[i][1]);
            float flicker = 0.75f + 0.25f * Mth.sin(time * 1.7f + i * 2.1f);
            float strength = (0.15f + 0.85f * p * p) * after * flicker;
            beam(m, from, target, cam, 0.7f + 0.6f * p, colour, 0.5f * strength);
            beam(m, from, target, cam, 0.15f + 0.12f * p, white, 1.0f * strength);
        }
        BufferUploader.drawWithShader(beamBuffer.buildOrThrow());
        beamBuffer = null;
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static BufferBuilder beamBuffer;

    /** A soft sheet of light from one point to another, turned to face the camera. */
    private static void beam(Matrix4f m, Vec3 from, Vec3 to, Vec3 cam, float halfWidth, float[] c, float alpha) {
        if (beamBuffer == null) beamBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vec3 axis = to.subtract(from);
        Vec3 mid = from.add(axis.scale(0.5));
        Vec3 side = axis.cross(mid.subtract(cam)).normalize().scale(halfWidth);
        if (side.lengthSqr() < 1e-6) return;
        BufferBuilder b = beamBuffer;
        vertex(b, m, (float) (from.x - side.x), (float) (from.y - side.y), (float) (from.z - side.z), c, 0);
        vertex(b, m, (float) (to.x - side.x), (float) (to.y - side.y), (float) (to.z - side.z), c, 0);
        vertex(b, m, (float) to.x, (float) to.y, (float) to.z, c, alpha);
        vertex(b, m, (float) from.x, (float) from.y, (float) from.z, c, alpha);
        vertex(b, m, (float) from.x, (float) from.y, (float) from.z, c, alpha);
        vertex(b, m, (float) to.x, (float) to.y, (float) to.z, c, alpha);
        vertex(b, m, (float) (to.x + side.x), (float) (to.y + side.y), (float) (to.z + side.z), c, 0);
        vertex(b, m, (float) (from.x + side.x), (float) (from.y + side.y), (float) (from.z + side.z), c, 0);
    }

    /**
     * At the burst, cracks of light run out from the plinth across the ground, jagged lines that
     * follow the terrain, reach thirty blocks in the slow seconds of the burst and fade while
     * the obelisk reforms.
     */
    private static void drawCracks(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!farHere(mc) || mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float riteT = riteTime(mc, partial);
        int start = de.kronwerke.core.obelisk.ObeliskRite.T_BURST, end = de.kronwerke.core.obelisk.ObeliskRite.T_REFORM + 60;
        if (riteT < start || riteT >= end) return;
        Vec3 cam = event.getCamera().getPosition();
        Vec3 base = new Vec3(far.x(), far.baseY(), far.z());
        if (cam.distanceToSqr(base) > 120 * 120) return;
        float grow = Mth.clamp((riteT - start) / 30f, 0f, 1f);
        grow = 1 - (1 - grow) * (1 - grow);
        float fade = riteT < de.kronwerke.core.obelisk.ObeliskRite.T_REFORM ? 1f : 1 - (riteT - de.kronwerke.core.obelisk.ObeliskRite.T_REFORM) / 60f;
        float time = mc.level.getGameTime() + partial;
        float[] gold = {1f, 0.85f, 0.5f};
        float[] white = {1f, 0.97f, 0.9f};
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int cracks = 12, segments = 14;
        java.util.Random rnd = new java.util.Random(far.baseY() * 31L + 7);
        for (int k = 0; k < cracks; k++) {
            float angle = k / (float) cracks * Mth.TWO_PI + (rnd.nextFloat() - 0.5f) * 0.4f;
            float reach = (22 + rnd.nextFloat() * 10) * grow;
            float px = (float) far.x(), pz = (float) far.z();
            float py = far.baseY() + 0.06f;
            float width = 0.35f;
            // the crack wanders a little from its line and is pulled back to it, so it stays radial
            float drift = 0;
            for (int i = 0; i < segments; i++) {
                float t0 = i / (float) segments, t1 = (i + 1) / (float) segments;
                float r1 = 4 + reach * t1;
                drift = drift * 0.6f + (rnd.nextFloat() - 0.5f) * 0.5f;
                float a1 = angle + drift * 0.35f;
                float nx = (float) far.x() + Mth.cos(a1) * r1, nz = (float) far.z() + Mth.sin(a1) * r1;
                float ny = groundAt(mc, nx, nz, far.baseY()) + 0.06f;
                if (i == 0) {
                    px = (float) far.x() + Mth.cos(angle) * 4;
                    pz = (float) far.z() + Mth.sin(angle) * 4;
                    py = groundAt(mc, px, pz, far.baseY()) + 0.06f;
                }
                // the light runs along the crack as a pulse and dies out towards the tip
                float pulse = 0.6f + 0.4f * Mth.sin(t0 * 9f - time * 0.5f + k);
                float tip = 1 - t1 * t1;
                float alpha = fade * tip * pulse;
                float w0 = width * (1 - t0 * 0.7f), w1 = width * (1 - t1 * 0.7f);
                // across the crack, perpendicular on the ground
                float dx = nx - px, dz = nz - pz;
                float len = Math.max(0.001f, Mth.sqrt(dx * dx + dz * dz));
                float ox = -dz / len, oz = dx / len;
                vertex(b, m, px + ox * w0, py, pz + oz * w0, gold, 0);
                vertex(b, m, nx + ox * w1, ny, nz + oz * w1, gold, 0);
                vertex(b, m, nx, ny, nz, white, alpha);
                vertex(b, m, px, py, pz, white, alpha);
                vertex(b, m, px, py, pz, white, alpha);
                vertex(b, m, nx, ny, nz, white, alpha);
                vertex(b, m, nx - ox * w1, ny, nz - oz * w1, gold, 0);
                vertex(b, m, px - ox * w0, py, pz - oz * w0, gold, 0);
                // a wider, fainter glow under the crack
                vertex(b, m, px + ox * w0 * 3, py - 0.01f, pz + oz * w0 * 3, gold, 0);
                vertex(b, m, nx + ox * w1 * 3, ny - 0.01f, nz + oz * w1 * 3, gold, 0);
                vertex(b, m, nx - ox * w1 * 3, ny - 0.01f, nz - oz * w1 * 3, gold, 0);
                vertex(b, m, px - ox * w0 * 3, py - 0.01f, pz - oz * w0 * 3, gold, 0);
                px = nx;
                pz = nz;
                py = ny;
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** The ground height at a point, the top of the highest solid block, or the plinth's floor when the chunk is not there. */
    static float groundAt(Minecraft mc, float x, float z, int fallback) {
        BlockPos p = BlockPos.containing(x, 0, z);
        if (!mc.level.hasChunkAt(p)) return fallback;
        int y = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
        // the pavement sits one below the plinth; inside it the floor is the pavement's top
        return Math.min(y, fallback + 3);
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
        // a gathering wakes the rings
        strength *= 1f + 0.12f * Math.min(far.crowd(), 6);
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

    static void vertex(BufferBuilder b, Matrix4f m, float x, float y, float z, float[] c, float a) {
        b.addVertex(m, x, y, z).setColor(c[0], c[1], c[2], a);
    }

    static BlockPos crystalPos() {
        return crystal == null ? null : BlockPos.containing(crystal);
    }
}
