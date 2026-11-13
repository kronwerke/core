package de.kronwerke.core.client;

import de.kronwerke.core.Text;
import de.kronwerke.core.mixin.DisconnectedScreenAccessor;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * Joining the server: instead of the game's grey "Connecting to the server" and "Loading
 * terrain" screens, an old works that still ticks. Three brass gears mesh on a timber frame
 * next to a crooked little house with light in the window, on a piece of ground that floats
 * in the night: moss on the roof and the beams, vines over the wheels, a broken wheel in the
 * grass. The crown stands in the large gear, a pendulum swings under the middle one, smoke
 * rises from the chimney, fireflies drift, and the name fades in underneath. It is pixel art
 * on the grid of the pack's menus, painted by tools/art/join_art.py; this class only puts
 * the layers together and moves what moves. It is the same picture whatever world the
 * player lands in, and on purpose nothing like the move between the servers (white flash and
 * stars, see {@link TravelOverlay}).
 * <p>
 * In the top right corner a small gear turns next to a word for what is happening (connecting,
 * loading the world, JEI's plugins while {@link LoadPump} draws). It is the proof that the
 * picture is alive: where it stands still, the game does.
 */
public final class JoinOverlay {
    private static final int GROUND = 0x0d0b10, BRASS = 0xe5b451, DIM = 0x8a5a24;
    /** The picture's grid, and what join_art.py prints for the parts that move: where they sit, how big a frame is. */
    private static final int ART_W = 320, ART_H = 200, PIC_H = 232, TOP = 22;
    private static final int BIG_X = 154, BIG_Y = 54, BIG_S = 84, MID_X = 212, MID_Y = 32, MID_S = 58, SMALL_X = 128, SMALL_Y = 98, SMALL_S = 46;
    private static final int PEND_X = 222, PEND_Y = 65, PEND_W = 40, PEND_H = 58, PEND_N = 13;
    private static final int LOGO_W = 112, LOGO_H = 34;
    /** How many teeth pass a second once the works run. */
    private static final double TEETH = 1.05;
    private static final ResourceLocation BACK = art("back"), SCENE = art("scene"), FRONT = art("front"), LIGHT = art("light"),
            GEAR_BIG = art("gear_big"), GEAR_MID = art("gear_mid"), GEAR_SMALL = art("gear_small"), PENDULUM = art("pendulum"), LOGO = art("logo");

    private static ResourceLocation art(String name) {
        return ResourceLocation.fromNamespaceAndPath(de.kronwerke.core.KronwerkeCore.MOD_ID, "textures/gui/join/" + name + ".png");
    }
    private static final long FADE_MS = 700, STAY_MS = 250;

    private static long since = -1, lastSeen, fadeFrom = -1;
    /** Sent on at the login: until when the picture stays, whether the other server's screens came, and when it was. */
    private static long holdUntil = -1, forwardedAt = -1;
    private static boolean holdSawScreen;
    private static String note = "";

    private JoinOverlay() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post e) -> onScreen(e.getScreen(), e.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> onHud(e.getGuiGraphics()));
    }

    /** The screens of joining a server, not of moving between two (that is TravelOverlay's). */
    static boolean loading(Screen s) {
        return transfer(s) || s instanceof ConnectScreen || s instanceof ReceivingLevelScreen || s instanceof ProgressScreen
                || s instanceof GenericMessageScreen || s instanceof LevelLoadingScreen;
    }

    /**
     * The game's "Connection Lost: Transferred to another server". It is drawn for one frame in
     * the middle of every move between two servers, before the new connection starts, and that
     * frame stays on the screen for as long as the old world takes to come down. It is part of
     * the move, not the end of it, and the pictures go over it.
     */
    static boolean transfer(Screen s) {
        if (!(s instanceof DisconnectedScreen d)) return false;
        try {
            return ((DisconnectedScreenAccessor) d).kronwerke$details().reason().getContents() instanceof TranslatableContents t
                    && t.getKey().equals("disconnect.transfer");
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** What is happening right now, for the corner. */
    static Component stage(Minecraft mc) {
        if (!note.isEmpty()) return Component.literal(note);
        Screen s = mc.screen;
        if (s == null && mc.level != null) return Text.t("load.world", "Lade Welt");
        if (s instanceof ConnectScreen || transfer(s)) return Text.t("load.connect", "Verbinde");
        if (s instanceof ReceivingLevelScreen || s instanceof LevelLoadingScreen) return Text.t("load.world", "Lade Welt");
        return Text.t("load.prepare", "Bereite vor");
    }

    /** LoadPump: what the game is busy with while it cannot draw itself ("JEI: Mekanism"), or "" when done. */
    static void note(String what) {
        note = what == null ? "" : what;
    }

    /**
     * Whether a move that starts now still belongs to the login: who logged out in the Nether
     * comes in on main and is sent on at once. That is one arrival for the player, so it gets
     * this one picture all the way and not the stars of a move in between.
     */
    static boolean joining(long now) {
        return since >= 0 || now - lastSeen < 5000;
    }

    /** TravelOverlay: the server sends this client on while it is still coming in. */
    static void forward(long now) {
        if (since < 0) since = now - 3000;
        fadeFrom = -1;
        holdUntil = now + 60_000;
        holdSawScreen = false;
        forwardedAt = now;
    }

    /** TravelOverlay: whether the arrival that is reported now is the end of a login sent on (asked once). */
    static boolean forwarded(long now) {
        boolean was = forwardedAt >= 0 && now - forwardedAt < 120_000;
        forwardedAt = -1;
        return was;
    }

    private static void onScreen(Screen screen, GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (!loading(screen)) {
            // a menu (title, server list, "Connection Lost"): the next join starts the works anew
            if (Minecraft.getInstance().level == null) {
                since = -1;
                fadeFrom = -1;
                holdUntil = -1;
                TravelOverlay.failed();
            }
            return;
        }
        if (TravelOverlay.active()) return;
        if (since < 0) {
            // the works start with a connection; "Saving world" on the way out is no join
            if (!(screen instanceof ConnectScreen)) return;
            since = now;
        }
        lastSeen = now;
        fadeFrom = -1;
        if (holdUntil >= 0) holdSawScreen = true;
        LoadPump.covers();
        draw(g, now, 1);
    }

    /** In the world: the picture clears once nothing loads any more. */
    private static void onHud(GuiGraphics g) {
        if (since < 0 || TravelOverlay.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && loading(mc.screen)) return; // the screen draws it
        long now = System.currentTimeMillis();
        if (holdUntil >= 0) {
            // on main for a moment before the move: the picture stays whole until the other world stands
            if (!holdSawScreen && now < holdUntil) {
                lastSeen = now;
                LoadPump.covers();
                draw(g, now, 1);
                return;
            }
            holdUntil = -1;
        }
        if (fadeFrom < 0) fadeFrom = now + STAY_MS;
        float k = Math.max(0, (now - fadeFrom) / (float) FADE_MS);
        if (k >= 1) {
            since = -1;
            fadeFrom = -1;
            return;
        }
        draw(g, now, 1 - k);
    }

    /** LoadPump: whether the last picture the game drew itself was this one, whole. */
    static boolean whole() {
        return since >= 0 && fadeFrom < 0;
    }

    /** LoadPump: the picture, drawn while the game's own loop stands still. */
    static void pumped(GuiGraphics g, long now) {
        lastSeen = now;
        draw(g, now, 1);
    }

    /** LoadPump over the world: only the corner, on a dark plate that also covers the gear of the picture before. */
    static void plate(GuiGraphics g, long now) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int tw = mc.font.width(stage(mc));
        g.pose().pushPose();
        g.pose().translate(0, 0, 955);
        Shape q = new Shape(g);
        q.rect(w - 36 - tw, 3, w - 3, 24, argb(1, GROUND));
        q.rect(w - 36 - tw, 23, w - 3, 24, argb(1, DIM));
        q.end();
        g.pose().popPose();
        indicator(g, now, 1);
    }

    /** The whole picture; also called by LoadPump while the game itself draws nothing. */
    static void draw(GuiGraphics g, long now, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        long t = now - Math.max(0, since);
        g.pose().pushPose();
        g.pose().translate(0, 0, 950);
        Shape ground = new Shape(g);
        ground.rect(0, 0, w, h, argb(alpha, GROUND));
        ground.end();
        // as large as the window allows, in steps that keep one texel a whole number of real
        // pixels (the GUI scale times k), so the pixels stay square; the rows above TOP hold
        // only stars and may run off the top
        float k = Math.min(w / (ART_W + 8f), h / (PIC_H - TOP + 6f));
        float gui = (float) mc.getWindow().getGuiScale();
        if (k * gui >= 1) k = (float) Math.floor(k * gui) / gui;
        float ox = Math.round((w - ART_W * k) / 2f * gui) / gui, oy = Math.round(((h - (PIC_H - TOP) * k) / 2f - TOP * k) * gui) / gui;
        g.pose().translate(ox, oy, 0);
        g.pose().scale(k, k, 1);
        // the picture comes out of the dark in the first half second
        float show = alpha * Math.min(1, t / 500f);
        // the works come up to speed in the first second and a half (the turn is the integral of a
        // speed that rises from nothing to full, so nothing jumps), and then they tick: every
        // tooth goes through fast and slow, like a clock's
        double s = (t <= 1500 ? t * t / 3000.0 : t - 750.0) / 1000.0;
        double teeth = TEETH * s + 0.115 * Math.sin(Math.PI * 2 * TEETH * s);
        long f = (long) Math.floor(teeth * 8);
        float swing = (float) Math.sin(Math.PI * TEETH * s);

        tex(g, BACK, 0, 0, ART_W, ART_H, 0, 0, ART_W, ART_H, show);
        tex(g, SCENE, 0, 0, ART_W, ART_H, 0, 0, ART_W, ART_H, show);
        // smoke from the chimney: small squares that rise, drift and thin out
        Shape q = new Shape(g);
        for (int i = 0; i < 9; i++) {
            float life = ((t / 5200f) + i / 9f) % 1f;
            int x = (int) Math.floor(70 + life * 22 + Math.sin(t / 1400.0 + i * 2.1) * (1.5 + 4 * life));
            int y = (int) Math.floor(58 - life * 34);
            int size = life < 0.25f ? 2 : life < 0.7f ? 3 : 2;
            float fade = Math.min(1, life * 5) * (1 - life) * 0.9f;
            int c = argb(show * fade, i % 2 == 0 ? 0x655a73 : 0x4b4159);
            q.rect(x, y, x + size, y + size, c);
            if (size == 3) q.rect(x + 1, y - 1, x + 2, y, c);
        }
        q.end();
        int pf = Math.round((swing * 0.5f + 0.5f) * (PEND_N - 1));
        tex(g, PENDULUM, PEND_X, PEND_Y, PEND_W, PEND_H, pf * PEND_W, 0, PEND_W * PEND_N, PEND_H, show);
        gear(g, GEAR_SMALL, SMALL_X, SMALL_Y, SMALL_S, 8, 48, f, show);
        gear(g, GEAR_MID, MID_X, MID_Y, MID_S, 8, 64, f, show);
        gear(g, GEAR_BIG, BIG_X, BIG_Y, BIG_S, 12, 96, f, show);
        tex(g, FRONT, 0, 0, ART_W, ART_H, 0, 0, ART_W, ART_H, show);
        // the lamps flicker: the warm layer comes and goes a little, in steps
        float flick = 0.80f + 0.12f * (float) Math.sin(t / 173.0) + 0.08f * (float) Math.sin(t / 61.0 + 1.3);
        tex(g, LIGHT, 0, 0, ART_W, ART_H, 0, 0, ART_W, ART_H, show * Math.round(flick * 8) / 8f);
        // fireflies over the grass, each a texel that wanders and blinks
        q = new Shape(g);
        for (int i = 0; i < 11; i++) {
            float bx = 44 + hash(i * 5 + 1) * 240, by = 112 + hash(i * 5 + 2) * 34;
            int x = (int) Math.floor(bx + Math.sin(t / (1900.0 + 700 * hash(i * 5 + 3)) + i) * 9);
            int y = (int) Math.floor(by + Math.sin(t / (1300.0 + 500 * hash(i * 5 + 4)) + i * 1.7) * 5);
            float blink = (float) Math.sin(t / (520.0 + 260 * hash(i * 5 + 5)) + i * 2.3);
            if (blink < -0.2f) continue;
            q.rect(x, y, x + 1, y + 1, argb(show * (0.55f + 0.45f * blink), blink > 0.6f ? 0xfff1b8 : 0xc6dc6e));
        }
        q.end();
        // the name, a moment after the rest
        float in = Math.max(0, Math.min(1, (t - 600) / 900f));
        tex(g, LOGO, (ART_W - LOGO_W) / 2, ART_H - 3, LOGO_W, LOGO_H, 0, 0, LOGO_W, LOGO_H, alpha * in);
        g.pose().popPose();
        indicator(g, now, alpha);
    }

    /** One gear from its sheet: frame f of a whole turn, the sheet has the given number of columns. */
    private static void gear(GuiGraphics g, ResourceLocation sheet, int x, int y, int size, int columns, int frames, long f, float alpha) {
        int i = (int) Math.floorMod(f, (long) frames);
        tex(g, sheet, x, y, size, size, (i % columns) * size, (i / columns) * size, columns * size, frames / columns * size, alpha);
    }

    /**
     * A piece of a texture, drawn at once and over everything: no depth test, so nothing of
     * the HUD shows through, and in the order it is called in, between the buffered shapes.
     */
    private static void tex(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int u, int v, int texW, int texH, float alpha) {
        if (alpha <= 0.01f) return;
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        Matrix4f m = g.pose().last().pose();
        float u0 = u / (float) texW, u1 = (u + w) / (float) texW, v0 = v / (float) texH, v1 = (v + h) / (float) texH;
        int c = argb(Math.min(1, alpha), 0xffffff);
        com.mojang.blaze3d.vertex.BufferBuilder b = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(m, x, y, 0).setUv(u0, v0).setColor(c);
        b.addVertex(m, x, y + h, 0).setUv(u0, v1).setColor(c);
        b.addVertex(m, x + w, y + h, 0).setUv(u1, v1).setColor(c);
        b.addVertex(m, x + w, y, 0).setUv(u1, v0).setColor(c);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(b.buildOrThrow());
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    /** The corner: a small gear that turns as long as pictures are drawn, and what is going on. */
    static void indicator(GuiGraphics g, long now, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        g.pose().pushPose();
        g.pose().translate(0, 0, 960);
        Shape q = new Shape(g);
        q.cog(w - 14.5f, 13.5f, 7.5f, (float) ((now / 1000.0 * 2.2) % (Math.PI * 2)), argb(alpha, BRASS));
        q.end();
        Component what = stage(mc);
        text(g, mc.font, what, w - 28 - mc.font.width(what), 10, argb(alpha * 0.85f, 0xe8dcc0));
        g.pose().popPose();
    }

    /**
     * Text that nothing of the HUD covers: the HUD's layers lie at rising depths, and the boss
     * bar, the chat and the tab list would otherwise show through the picture.
     */
    static void text(GuiGraphics g, Font font, Component text, int x, int y, int argb) {
        if ((argb >>> 24) < 8) return;
        font.drawInBatch(text, x, y, argb, false, g.pose().last().pose(), g.bufferSource(), Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        g.flush();
    }

    /**
     * Filled shapes over everything: the dark ground, the texels of smoke and fireflies, and
     * the small gear of the corner, whose corners lie anywhere between the pixels.
     */
    private static final class Shape {
        private final GuiGraphics g;
        private final Matrix4f m;
        private final VertexConsumer vc;

        Shape(GuiGraphics g) {
            this.g = g;
            this.m = g.pose().last().pose();
            this.vc = g.bufferSource().getBuffer(RenderType.guiOverlay());
        }

        void end() {
            g.flush();
        }

        /** One quad; the corners are put in the order the GUI's own rectangles have, whichever way they come. */
        void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
            float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0) + (x2 - x0) * (y3 - y0) - (x3 - x0) * (y2 - y0);
            if (area < 0) {
                vc.addVertex(m, x0, y0, 0).setColor(c0);
                vc.addVertex(m, x1, y1, 0).setColor(c1);
                vc.addVertex(m, x2, y2, 0).setColor(c2);
                vc.addVertex(m, x3, y3, 0).setColor(c3);
            } else {
                vc.addVertex(m, x3, y3, 0).setColor(c3);
                vc.addVertex(m, x2, y2, 0).setColor(c2);
                vc.addVertex(m, x1, y1, 0).setColor(c1);
                vc.addVertex(m, x0, y0, 0).setColor(c0);
            }
        }

        void rect(float x0, float y0, float x1, float y1, int c) {
            if ((c >>> 24) == 0) return;
            quad(x0, y0, c, x0, y1, c, x1, y1, c, x1, y0, c);
        }

        /** A ring between two radii, each with its own colour (0 alpha outside gives a glow). */
        void ring(float cx, float cy, float r0, float r1, int c0, int c1) {
            int n = Math.max(12, (int) (r1 * 1.6f));
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                float ca = (float) Math.cos(a0), sa = (float) Math.sin(a0), cb = (float) Math.cos(a1), sb = (float) Math.sin(a1);
                quad(cx + ca * r0, cy + sa * r0, c0, cx + ca * r1, cy + sa * r1, c1, cx + cb * r1, cy + sb * r1, c1, cx + cb * r0, cy + sb * r0, c0);
            }
        }

        /** The small gear of the corner: at this size it needs coarse teeth and a plain rim to read as a gear. */
        void cog(float cx, float cy, float r, float angle, int c) {
            if ((c >>> 24) == 0) return;
            float root = r * 0.70f, step = (float) (Math.PI * 2 / 6);
            for (int i = 0; i < 6; i++) {
                float at = angle + i * step;
                float ux = (float) Math.cos(at), uy = (float) Math.sin(at), px = -uy * r * 0.24f, py = ux * r * 0.24f;
                float r0 = root - 0.5f;
                quad(cx + ux * r0 + px, cy + uy * r0 + py, c, cx + ux * r + px * 0.8f, cy + uy * r + py * 0.8f, c,
                        cx + ux * r - px * 0.8f, cy + uy * r - py * 0.8f, c, cx + ux * r0 - px, cy + uy * r0 - py, c);
            }
            ring(cx, cy, r * 0.32f, root, c, c);
        }
    }

    private static float hash(int i) {
        int x = i * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA77;
        x ^= x >>> 13;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    private static int argb(float a, int rgb) {
        int al = Math.max(0, Math.min(255, (int) (a * 255)));
        return (al << 24) | (rgb & 0xffffff);
    }
}
