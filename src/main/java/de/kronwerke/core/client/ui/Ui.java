package de.kronwerke.core.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Colours and helpers shared by the Kronwerke screens. Every text that depends on data goes
 * through fit(), so a long name or item never runs out of its column.
 */
public final class Ui {
    public static final int BG = 0xF0141019;
    public static final int BORDER = 0xFF3a3146;
    public static final int RAISED = 0xFF221c2b;
    public static final int HOVER = 0xFF2c2438;
    public static final int GOLD = 0xFFd4a24a;
    public static final int GOLD_DARK = 0xFF8a6420;
    public static final int RED = 0xFF8b2f3a;
    public static final int RED_HOVER = 0xFFa63a47;
    public static final int GREEN = 0xFF4fae5a;
    public static final int PURPLE = 0xFF9a6fd6;
    public static final int TEXT = 0xFFEDE6F5;
    public static final int MUTED = 0xFF9a90a8;
    public static final int DIM = 0xFF5d5468;

    private Ui() {
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

    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BORDER);
        g.fill(x0, y0, x1, y1, BG);
    }

    /** A small rounded looking label with a coloured background. */
    public static int chip(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        int w = font.width(text) + 8;
        g.fill(x + 1, y, x + w - 1, y + 11, colour);
        g.fill(x, y + 1, x + w, y + 10, colour);
        g.drawString(font, text, x + 4, y + 2, 0xFFFFFFFF, false);
        return w;
    }

    public static void bar(GuiGraphics g, int x0, int y, int x1, int percent, int colour) {
        g.fill(x0, y, x1, y + 5, 0xFF2a2435);
        g.fill(x0, y, x0 + (int) ((x1 - x0) * Math.min(100, Math.max(0, percent)) / 100.0), y + 5, colour);
    }

    public static void rule(GuiGraphics g, int x0, int y, int x1) {
        g.fill(x0, y, x1, y + 1, BORDER);
    }
}
