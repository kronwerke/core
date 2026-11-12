package de.kronwerke.core.client;

import de.kronwerke.core.Text;
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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * Joining the server: instead of the game's grey "Connecting to the server" and "Loading
 * terrain" screens, the works start up. Three brass gears mesh and turn on a dark ground, a
 * crown stands in the large one, sparks drift up, and the name fades in underneath. It is the
 * same picture whatever world the player lands in, and on purpose nothing like the move
 * between the servers (white flash and stars, see {@link TravelOverlay}).
 * <p>
 * In the top right corner a small gear turns next to a word for what is happening (connecting,
 * loading the world, JEI's plugins while {@link LoadPump} draws). It is the proof that the
 * picture is alive: where it stands still, the game does.
 */
public final class JoinOverlay {
    private static final int GROUND = 0x0e0b12, BRASS = 0xd9a441, SHADE = 0x8f6a26, DIM = 0x6d5226, DARK = 0x3d2e17,
            SPARK = 0xffd591, EMBER = 0xd0661e;
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
        return s instanceof ConnectScreen || s instanceof ReceivingLevelScreen || s instanceof ProgressScreen
                || s instanceof GenericMessageScreen || s instanceof LevelLoadingScreen;
    }

    /** What is happening right now, for the corner. */
    static Component stage(Minecraft mc) {
        if (!note.isEmpty()) return Component.literal(note);
        Screen s = mc.screen;
        if (s == null && mc.level != null) return Text.t("load.world", "Lade Welt");
        if (s instanceof ConnectScreen) return Text.t("load.connect", "Verbinde");
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
        if (screen instanceof DisconnectedScreen || screen instanceof TitleScreen) {
            since = -1;
            fadeFrom = -1;
            holdUntil = -1;
            return;
        }
        if (!loading(screen) || TravelOverlay.active()) return;
        if (since < 0 || now - lastSeen > 3000) since = now;
        lastSeen = now;
        fadeFrom = -1;
        if (holdUntil >= 0) holdSawScreen = true;
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

    /** The whole picture; also called by LoadPump while the game itself draws nothing. */
    static void draw(GuiGraphics g, long now, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        long t = now - Math.max(0, since);
        g.pose().pushPose();
        g.pose().translate(0, 0, 950);
        float cx = w / 2f, cy = h / 2f - 16;
        // the works come up to speed in the first second and a half
        float run = Math.min(1, t / 1500f);
        // (the turn is the integral of a speed that rises from nothing to full, so nothing jumps)
        float a = (float) ((t <= 1500 ? t * t / 3000.0 : t - 750.0) / 1000.0 * 0.6);
        Shape q = new Shape(g);
        q.rect(0, 0, w, h, argb(alpha, GROUND));
        // the glow of the fire behind the works, breathing slowly
        float breath = 0.5f + 0.5f * (float) Math.sin(t / 1300.0);
        q.glow(cx, cy + 6, 20, 120, argb(alpha * (0.16f + 0.05f * breath) * run, EMBER));
        // embers rising behind the gears
        for (int i = 0; i < 34; i++) {
            float speed = 0.09f + 0.13f * hash(i * 7 + 1);
            float life = ((t / 1000f) * speed + hash(i * 7 + 2)) % 1f;
            float x = cx + (hash(i * 7 + 3) - 0.5f) * 230 + (float) Math.sin(t / 900.0 + i * 1.7) * (5 + 6 * life);
            float y = cy + 78 - life * 175;
            float size = 0.6f + 1.1f * hash(i * 7 + 4);
            float flick = 0.7f + 0.3f * (float) Math.sin(t / 110.0 + i * 2.3);
            float fade = Math.min(1, life * 6) * (1 - life) * flick;
            q.diamond(x, y, size, argb(alpha * fade * run, hash(i * 7 + 5) > 0.5f ? SPARK : BRASS));
        }
        // three gears that mesh: 12, 8 and 6 teeth on one module, so the small ones run faster
        float big = 36, module = big * 0.9f / 12;
        float r8 = module * 8 / 0.9f, r6 = module * 6 / 0.9f;
        float at8 = 0.36f, at6 = 2.62f;
        float d8 = module * (12 + 8), d6 = module * (12 + 6);
        q.gear(cx + (float) Math.cos(at8) * d8, cy + (float) Math.sin(at8) * d8, r8, 8, mesh(a, at8, 8), alpha * 0.95f, DIM, DARK, true);
        q.gear(cx + (float) Math.cos(at6) * d6, cy + (float) Math.sin(at6) * d6, r6, 6, mesh(a, at6, 6), alpha * 0.95f, DIM, DARK, true);
        q.gear(cx, cy, big, 12, a, alpha, BRASS, SHADE, false);
        q.crown(cx, cy + 1, 13, argb(alpha, SPARK), argb(alpha, GROUND));
        // the name, and a line that grows out from the middle under it
        Font font = mc.font;
        float in = Math.max(0, Math.min(1, (t - 500) / 900f));
        float grow = 1 - (float) Math.pow(1 - in, 3);
        q.rect(cx - 62 * grow, cy + 73, cx + 62 * grow, cy + 73.6f, argb(alpha * in * 0.8f, DIM));
        q.end();
        if (in * alpha > 0.03f) {
            String name = "K R O N W E R K E";
            text(g, font, Component.literal(name), (int) (cx - font.width(name) / 2f), (int) (cy + 60), argb(in * alpha, BRASS));
            String sub = "S E A S O N   2";
            text(g, font, Component.literal(sub), (int) (cx - font.width(sub) / 2f), (int) (cy + 79), argb(in * alpha * 0.75f, DIM));
        }
        g.pose().popPose();
        indicator(g, now, alpha);
    }

    /** The turn of a gear with the given teeth that meshes with the large one (12 teeth, turned by a) at the direction at. */
    private static float mesh(float a, float at, int teeth) {
        float step = (float) (Math.PI * 2 / teeth), step12 = (float) (Math.PI * 2 / 12);
        return (float) (at + Math.PI - step * (0.5f - at / step12)) - a * 12f / teeth;
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
     * Filled shapes with corners anywhere between the pixels, so that a turning gear does not
     * crawl from pixel to pixel as it would when put together from the GUI's whole rectangles.
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

        void diamond(float x, float y, float r, int c) {
            if ((c >>> 24) == 0) return;
            quad(x, y - r, c, x - r, y, c, x, y + r, c, x + r, y, c);
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

        void glow(float cx, float cy, float r0, float r1, int c) {
            if ((c >>> 24) == 0) return;
            ring(cx, cy, 0, r0, c, c);
            ring(cx, cy, r0, r1, c, c & 0xffffff);
        }

        /** A gear: teeth on a rim, a shaded inner edge, and either spokes with a hub or an open middle. */
        void gear(float cx, float cy, float r, int teeth, float angle, float alpha, int rgb, int shade, boolean spokes) {
            if (alpha <= 0.02f) return;
            int c = argb(alpha, rgb), d = argb(alpha, shade);
            float root = r * 0.80f, inner = r * 0.56f, step = (float) (Math.PI * 2 / teeth);
            for (int i = 0; i < teeth; i++) {
                float at = angle + i * step;
                float rl = at - step * 0.27f, rr = at + step * 0.27f, tl = at - step * 0.15f, tr = at + step * 0.15f;
                float in = root - 0.5f;
                quad(cx + (float) Math.cos(rl) * in, cy + (float) Math.sin(rl) * in, c,
                        cx + (float) Math.cos(tl) * r, cy + (float) Math.sin(tl) * r, c,
                        cx + (float) Math.cos(tr) * r, cy + (float) Math.sin(tr) * r, c,
                        cx + (float) Math.cos(rr) * in, cy + (float) Math.sin(rr) * in, c);
            }
            ring(cx, cy, inner, root, c, c);
            ring(cx, cy, inner, inner + Math.max(0.8f, r * 0.07f), d, d);
            if (!spokes) return;
            float hub = r * 0.26f, half = Math.max(0.7f, r * 0.07f);
            int n = teeth >= 8 ? 4 : 3;
            for (int i = 0; i < n; i++) {
                float at = angle + i * (float) (Math.PI * 2 / n);
                float ux = (float) Math.cos(at), uy = (float) Math.sin(at), px = -uy * half, py = ux * half;
                float r0 = hub - 0.3f, r1 = inner + 0.3f;
                quad(cx + ux * r0 + px, cy + uy * r0 + py, c, cx + ux * r1 + px, cy + uy * r1 + py, c,
                        cx + ux * r1 - px, cy + uy * r1 - py, c, cx + ux * r0 - px, cy + uy * r0 - py, c);
            }
            ring(cx, cy, hub * 0.45f, hub, c, c);
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

        /** A crown of three points with a band, standing still in the open middle of the large gear. */
        void crown(float cx, float cy, float s, int c, int ground) {
            float l = cx - s, r = cx + s, base = cy + s * 0.62f, band = base - s * 0.34f, top = cy - s * 0.80f, low = cy - s * 0.05f;
            rect(l, band, r, base, c);
            // three points; the outer ones lean outwards a little
            float[] tip = {l - s * 0.06f, cx, r + s * 0.06f};
            float[] from = {l, cx - s * 0.42f, r - s * 0.60f};
            float[] to = {l + s * 0.60f, cx + s * 0.42f, r};
            float[] high = {top + s * 0.22f, top, top + s * 0.22f};
            for (int i = 0; i < 3; i++) {
                quad(from[i], band + 0.2f, c, tip[i], high[i], c, tip[i], high[i], c, to[i], band + 0.2f, c);
                ring(tip[i], high[i] - s * 0.04f, 0, s * 0.13f, c, c);
            }
            // the valleys between the points are filled up to the low line
            quad(l, low + s * 0.20f, c, l, band + 0.2f, c, r, band + 0.2f, c, r, low + s * 0.20f, c);
            // a dark line over the band, and three stones in it
            rect(l + s * 0.12f, band + s * 0.07f, r - s * 0.12f, band + s * 0.12f, ground);
            for (int i = -1; i <= 1; i++) diamond(cx + i * s * 0.52f, (band + base) / 2 + s * 0.05f, s * 0.085f, ground);
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
