package de.kronwerke.core.client.ui;

import de.kronwerke.core.KronwerkeCore;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * The look shared by every Kronwerke screen: the sprites under textures/gui/sprites (drawn
 * by tools/textures/gui.py), the colours of the pack's menus (brass, night violet, cream),
 * and the small pieces every screen needs: panels, cards, bars that shimmer while a goal runs, rings for the stages, item icons
 * from an id. Every text that depends on data goes through fit(), so a long name never runs
 * out of its column.
 */
public final class Ui {
    public static final int BORDER = 0xFF3a3046;
    public static final int RAISED = 0xFF231d2c;
    public static final int HOVER = 0xFF2d2538;
    public static final int GOLD = 0xFFe5b451;
    public static final int GOLD_LIGHT = 0xFFf7dc86;
    public static final int GOLD_DARK = 0xFF8a5a24;
    public static final int RED = 0xFF86303e;
    public static final int RED_HOVER = 0xFFa8404a;
    public static final int GREEN = 0xFF7fc46a;
    public static final int CYAN = 0xFF8bb6dc;
    public static final int PURPLE = 0xFFbb98f4;
    public static final int TEXT = 0xFFf2ede3;
    public static final int MUTED = 0xFFa79db4;
    public static final int DIM = 0xFF655b72;
    public static final int SHADOW = 0xFF15121f;
    /** The field of a ring or a bar that is not filled yet. */
    public static final int TRACK = 0xFF2d2538;
    /** The colours of the five stages, as the website and the menus use them. */
    public static final int[] STAGE = {0xFFa8b596, 0xFFe5b451, 0xFF8bb6dc, 0xFFbb98f4, 0xFFf06579};

    public static final ResourceLocation PANEL = sprite("panel");
    public static final ResourceLocation INSET = sprite("inset");
    public static final ResourceLocation HEADER = sprite("header");
    public static final ResourceLocation SLOT = sprite("slot");
    public static final ResourceLocation BAR = sprite("bar");
    public static final ResourceLocation TAB = sprite("tab");
    public static final ResourceLocation TAB_ACTIVE = sprite("tab_active");
    public static final ResourceLocation BRAND = sprite("brand");
    public static final ResourceLocation SWITCH_ON = sprite("switch_on");
    public static final ResourceLocation SWITCH_OFF = sprite("switch_off");
    /** The brand sprite's size in GUI pixels. */
    public static final int BRAND_W = 130, BRAND_H = 18;

    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private Ui() {
    }

    public static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, name);
    }

    /** The text cut to width, with three dots when it had to be cut. */
    public static String fit(Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        String dots = "...";
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width(dots))) + dots;
    }

    public static FormattedCharSequence fit(Font font, Component c, int width) {
        if (font.width(c) <= width) return c.getVisualOrderText();
        FormattedText cut = font.substrByWidth(c, Math.max(0, width - font.width("...")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("...")));
    }

    /** Thousands separated by a thin space, the way the chat prints them. */
    public static String number(long n) {
        String s = Long.toString(n);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    // ---- surfaces ------------------------------------------------------------------------

    /** The framed slate panel every screen sits on. */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.blitSprite(PANEL, x0, y0, x1 - x0, y1 - y0);
    }

    /** The lighter band at the top of a panel with the brass rule under it. */
    public static void header(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.blitSprite(HEADER, x0, y0, x1 - x0, y1 - y0);
    }

    /** A darker field inside a panel, for lists. */
    public static void inset(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.blitSprite(INSET, x0, y0, x1 - x0, y1 - y0);
    }

    /** An item slot frame, 18 px like the inventory's. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.blitSprite(SLOT, x, y, 18, 18);
    }

    /** A small tag in the given colour, darkened so the cream writing reads on it, the corners cut. */
    public static int chip(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        int w = font.width(text) + 10;
        g.fill(x + 1, y, x + w - 1, y + 12, SHADOW);
        g.fill(x, y + 1, x + w, y + 11, SHADOW);
        g.fill(x + 1, y + 1, x + w - 1, y + 11, darken(colour, 110));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, colour);
        g.fill(x + 1, y + 10, x + w - 1, y + 11, darken(colour, 160));
        g.drawString(font, text, x + 5, y + 2, TEXT, false);
        return w;
    }

    public static void rule(GuiGraphics g, int x0, int y, int x1) {
        g.fill(x0, y, x1, y + 1, BORDER);
    }

    /** A brass rule with a small diamond in the middle, under headings. */
    public static void ornament(GuiGraphics g, int x0, int y, int x1) {
        g.fill(x0, y, x1, y + 1, GOLD_DARK);
        int cx = (x0 + x1) / 2;
        g.fill(cx - 1, y - 1, cx + 2, y + 2, GOLD);
        g.fill(cx, y - 2, cx + 1, y + 3, GOLD);
        g.fill(cx - 2, y, cx + 3, y + 1, GOLD);
    }

    // ---- progress ------------------------------------------------------------------------

    /**
     * A bar in the slot frame. While it is running a light sweeps over the filled part, so
     * the eye sees that something is happening.
     */
    public static void bar(GuiGraphics g, int x0, int y, int x1, int h, int percent, int colour, boolean running) {
        g.blitSprite(BAR, x0, y, x1 - x0, h);
        int fill = (int) ((x1 - x0 - 2) * Math.min(100, Math.max(0, percent)) / 100.0);
        if (fill <= 0) return;
        int fx0 = x0 + 1, fx1 = x0 + 1 + fill;
        g.fill(fx0, y + 1, fx1, y + h - 1, colour);
        g.fill(fx0, y + 1, fx1, y + 2, lighten(colour, 60));
        g.fill(fx0, y + h - 2, fx1, y + h - 1, darken(colour, 60));
        // cut like a brass gauge: a dark notch every six pixels
        int notch = darken(colour, 70);
        for (int nx = x0 + 6; nx < fx1 - 1; nx += 6) g.fill(nx, y + 2, nx + 1, y + h - 1, notch);
        if (running && fill > 6) {
            long t = Util.getMillis() % 2400;
            int sweep = (int) ((fx1 - fx0 + 12) * t / 2400.0) - 6;
            int sx0 = Math.max(fx0, fx0 + sweep - 3), sx1 = Math.min(fx1, fx0 + sweep + 3);
            if (sx1 > sx0) g.fill(sx0, y + 1, sx1, y + h - 1, 0x55FFFFFF);
        }
    }

    public static void bar(GuiGraphics g, int x0, int y, int x1, int percent, int colour) {
        bar(g, x0, y, x1, 7, percent, colour, false);
    }

    /**
     * A ring that fills clockwise from the top, drawn pixel by pixel; r is the outer radius
     * and t the thickness. Small rings for the stage list, a big one for the active goal.
     */
    public static void ring(GuiGraphics g, int cx, int cy, int r, int t, int percent, int track, int colour) {
        double fill = Math.min(100, Math.max(0, percent)) / 100.0;
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                double d = Math.sqrt(x * x + y * y);
                if (d > r + 0.5 || d < r - t + 0.5) continue;
                double a = Math.atan2(x, -y);
                if (a < 0) a += Math.PI * 2;
                int c = a / (Math.PI * 2) <= fill && fill > 0 ? colour : track;
                g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, c);
            }
        }
    }

    /** The colour of a stage by its place in the season (0 is the first); brass for anything beyond the five. */
    public static int stage(int index) {
        return index >= 0 && index < STAGE.length ? STAGE[index] : GOLD;
    }

    /** One of the 10 px signs (icon_<name>), tinted. */
    public static void sign(GuiGraphics g, String name, int x, int y, int colour) {
        g.setColor(((colour >> 16) & 255) / 255f, ((colour >> 8) & 255) / 255f, (colour & 255) / 255f, 1f);
        g.blitSprite(sprite("icon_" + name), x, y, 10, 10);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** A yes or no lever, 24 by 12. */
    public static void lever(GuiGraphics g, int x, int y, boolean on) {
        g.blitSprite(on ? SWITCH_ON : SWITCH_OFF, x, y, 24, 12);
    }

    /** A filled disc, row by row, for a glow behind a ring. */
    public static void disc(GuiGraphics g, int cx, int cy, int r, int colour) {
        for (int y = -r; y <= r; y++) {
            int half = (int) Math.round(Math.sqrt(r * r + r - y * y));
            g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, colour);
        }
    }

    /** A soft pulse between 0 and 1 for things that should breathe. */
    public static float pulse(int periodMs) {
        long t = Util.getMillis() % periodMs;
        return (float) (0.5 + 0.5 * Math.sin(t / (double) periodMs * Math.PI * 2));
    }

    public static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static int lighten(int colour, int by) {
        return mix(colour, 0xFFFFFFFF, by / 255.0);
    }

    public static int darken(int colour, int by) {
        return mix(colour, 0xFF000000, by / 255.0);
    }

    public static int mix(int a, int b, double t) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (aa << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    // ---- items ---------------------------------------------------------------------------

    /** The stack to show for an item id or a #tag; a tag shows its first item. */
    public static ItemStack icon(String id) {
        return ICONS.computeIfAbsent(id, k -> {
            try {
                if (k.startsWith("#")) {
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(k.substring(1)));
                    var holders = BuiltInRegistries.ITEM.getTag(tag);
                    if (holders.isPresent()) {
                        for (var h : holders.get()) return new ItemStack(h.value());
                    }
                    return new ItemStack(Items.CHEST);
                }
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(k));
                return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
            } catch (Exception e) {
                return new ItemStack(Items.BARRIER);
            }
        });
    }

    /** An item in a slot frame. */
    public static void slotItem(GuiGraphics g, String id, int x, int y) {
        slot(g, x, y);
        g.renderItem(icon(id), x + 1, y + 1);
    }
}
