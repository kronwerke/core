package de.kronwerke.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import de.kronwerke.core.portal.TravelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * What a player sees while the portal moves them to another server, drawn over the world and
 * over every screen in between (connecting, loading terrain), so the reconnect disappears:
 * <ol>
 * <li>Rising: light streams upward around them, the picture brightens.</li>
 * <li>Between: white, then flying through stars toward the target's name in its colour, for as
 * long as the reconnect takes.</li>
 * <li>Arriving: once the new world is drawn, a white flash that clears from the middle out.</li>
 * </ol>
 * The state lives in this class, so it survives the disconnect; nothing about the server.
 */
public final class TravelOverlay {
    private static final int CHARGE = 0, GO = 1, ARRIVING = 2, FADE = 3, OFF = -1;
    private static final long CHARGE_MS = 2500, WHITE_MS = 700, ARRIVE_MS = 1700, FADE_MS = 500, GIVE_UP_MS = 45_000;

    private static int phase = OFF;
    private static long start, arrivedAt;
    private static boolean arrived;
    private static String label = "";
    private static int color = 0x8bb6dc;
    private static float fadeFrom = 1;

    private TravelOverlay() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> draw(e.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post e) -> draw(e.getGuiGraphics()));
    }

    public static void receive(TravelPayload p) {
        long now = System.currentTimeMillis();
        switch (p.phase()) {
            case TravelPayload.CHARGE -> {
                phase = CHARGE;
                start = now;
                label = p.label();
                color = p.color();
                arrived = false;
            }
            case TravelPayload.GO -> {
                if (phase != CHARGE) start = now - CHARGE_MS;
                phase = GO;
                start = now;
                label = p.label().isEmpty() ? label : p.label();
                color = p.color();
                arrived = false;
            }
            case TravelPayload.CANCEL -> {
                if (phase == OFF) return;
                fadeFrom = phase == GO ? 1 : Math.min(1, (now - start) / (float) CHARGE_MS);
                phase = FADE;
                start = now;
            }
            case TravelPayload.ARRIVE -> {
                if (phase == OFF) {
                    // arrived without seeing the start (a move ordered by the server): a short arrival
                    phase = GO;
                    start = now - WHITE_MS - 1000;
                }
                arrived = true;
                arrivedAt = now;
            }
            default -> {
            }
        }
    }

    private static void draw(GuiGraphics g) {
        if (phase == OFF) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        long t = now - start;
        int w = g.guiWidth(), h = g.guiHeight();
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(0, 0, 900);
        switch (phase) {
            case CHARGE -> {
                float k = Math.min(1, t / (float) CHARGE_MS);
                streams(g, w, h, now, k, true);
                g.fill(0, 0, w, h, argb(k * k * 0.55f, 0xffffff));
            }
            case GO -> {
                if (t < WHITE_MS) {
                    g.fill(0, 0, w, h, argb(0.55f + 0.45f * t / (float) WHITE_MS, 0xffffff));
                } else {
                    space(g, mc.font, w, h, now, 1);
                    long since = t - WHITE_MS;
                    if (since < 500) g.fill(0, 0, w, h, argb(1 - since / 500f, 0xffffff));
                }
                boolean ready = arrived && mc.level != null && mc.player != null && mc.screen == null;
                if (ready && now - arrivedAt > 400) {
                    phase = ARRIVING;
                    start = now;
                } else if (!arrived && t > GIVE_UP_MS) {
                    fadeFrom = 1;
                    phase = FADE;
                    start = now;
                }
            }
            case ARRIVING -> {
                float k = Math.min(1, t / (float) ARRIVE_MS);
                float spaceAlpha = Math.max(0, 1 - k * 3);
                if (spaceAlpha > 0) space(g, mc.font, w, h, now, spaceAlpha);
                float white = k < 0.25f ? k / 0.25f : Math.max(0, 1 - (k - 0.25f) / 0.75f);
                streams(g, w, h, now, Math.max(0, 1 - k), false);
                g.fill(0, 0, w, h, argb(white * 0.9f, 0xffffff));
                if (k >= 1) phase = OFF;
            }
            case FADE -> {
                float k = Math.min(1, t / (float) FADE_MS);
                g.fill(0, 0, w, h, argb(fadeFrom * (1 - k) * 0.6f, 0xffffff));
                if (k >= 1) phase = OFF;
            }
            default -> {
            }
        }
        g.pose().popPose();
    }

    /** Light streaming up (rising) or down (arriving), thicker and brighter with k; a beam in the middle; gold sparks. */
    private static void streams(GuiGraphics g, int w, int h, long now, float k, boolean up) {
        if (k <= 0) return;
        float secs = (now % 600_000L) / 1000f;
        // the beam that comes down on the player, widening as it charges
        int cx = w / 2, beam = (int) (w * (0.02f + 0.10f * k * k));
        int bc = blend(color, 0xffffff, 0.45f);
        g.fillGradient(cx - beam * 3, 0, cx + beam * 3, h, argb(0.10f * k, bc), argb(0.22f * k, bc));
        g.fillGradient(cx - beam, 0, cx + beam, h, argb(0.35f * k, bc), argb(0.55f * k, 0xffffff));
        int n = 54;
        for (int i = 0; i < n; i++) {
            float seed = hash(i);
            // more streams near the middle, around the player
            float side = (seed - 0.5f);
            int x = (int) (cx + side * Math.abs(side) * 2 * w);
            float speed = 0.35f + hash(i + 97) * 0.65f;
            float phaseY = (secs * speed + hash(i + 31)) % 1f;
            int len = (int) (h * (0.10f + 0.22f * hash(i + 7)) * (0.6f + k));
            int y = up ? (int) (h - phaseY * (h + len)) : (int) (phaseY * (h + len)) - len;
            int core = 1 + (int) (k * 2 * hash(i + 3));
            int c = blend(color, 0xffffff, 0.25f + 0.55f * hash(i + 11));
            float a = Math.min(1, k * (0.6f + 0.6f * hash(i + 5)));
            int head = up ? 0 : 1;
            g.fillGradient(x - core * 2, y, x + core * 3, y + len, argb(head == 0 ? 0.25f * a : 0, c), argb(head == 0 ? 0 : 0.25f * a, c));
            g.fillGradient(x, y, x + core, y + len, argb(up ? a : 0, 0xffffff), argb(up ? 0 : a, c));
        }
        // gold sparks drifting with the light
        for (int i = 0; i < 40; i++) {
            float p = (secs * (0.2f + 0.3f * hash(i + 300)) + hash(i + 301)) % 1f;
            int x = (int) (hash(i + 302) * w + Math.sin(secs * 2 + i) * 6);
            int y = up ? (int) (h * (1 - p)) : (int) (h * p);
            float a = k * (1 - Math.abs(p - 0.5f) * 2) * 0.9f;
            g.fill(x, y, x + 2, y + 2, argb(a, 0xffcf6e));
        }
        // a glow from below that grows
        g.fillGradient(0, (int) (h * (1 - 0.6f * k)), w, h, argb(0, color), argb(0.45f * k, blend(color, 0xffffff, 0.4f)));
    }

    /** Stars rushing past toward the name of where the player goes. */
    private static void space(GuiGraphics g, Font font, int w, int h, long now, float alpha) {
        g.fillGradient(0, 0, w, h, argb(alpha, 0x070912), argb(alpha, blend(0x101a33, color, 0.18f)));
        float cx = w / 2f, cy = h / 2f, max = (float) Math.hypot(cx, cy);
        float time = (now % 600_000L) / 1000f; // small, so a float still has its fractions
        for (int i = 0; i < 220; i++) {
            float a = hash(i) * 6.2831855f;
            float z = (hash(i + 500) + time * (0.18f + 0.22f * hash(i + 900))) % 1f;
            float r = z * z * max;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            float tail = Math.max(1, z * z * 22);
            float x0 = cx + (float) Math.cos(a) * Math.max(0, r - tail), y0 = cy + (float) Math.sin(a) * Math.max(0, r - tail);
            int c = i % 7 == 0 ? blend(color, 0xffffff, 0.3f) : 0xf2ede3;
            float sa = alpha * Math.min(1, z * 2.2f);
            line(g, x0, y0, x, y, 1 + (int) (z * 2), argb(sa, c));
        }
        if (!label.isEmpty()) {
            g.pose().pushPose();
            float scale = Math.max(2, Math.min(4, w / 160f));
            g.pose().translate(cx, cy - 10 * scale / 2, 0);
            g.pose().scale(scale, scale, 1);
            Component name = Component.literal(label);
            int tw = font.width(name);
            float pulse = 0.85f + 0.15f * (float) Math.sin(time * 2.4);
            g.drawString(font, name, -tw / 2, 0, argb(alpha * pulse, blend(color, 0xffffff, 0.2f)), false);
            g.pose().popPose();
        }
    }

    /** A straight line of little squares; enough for star tails. */
    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int size, int c) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = Math.max(1, (int) (Math.hypot(dx, dy) / Math.max(1, size)));
        for (int s = 0; s <= steps; s++) {
            int x = (int) (x0 + dx * s / steps), y = (int) (y0 + dy * s / steps);
            g.fill(x, y, x + size, y + size, c);
        }
    }

    private static float hash(int i) {
        int x = i * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA77;
        x ^= x >>> 13;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    private static int blend(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int gg = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (gg << 8) | bl;
    }

    private static int argb(float a, int rgb) {
        int al = Math.max(0, Math.min(255, (int) (a * 255)));
        return (al << 24) | (rgb & 0xffffff);
    }
}
