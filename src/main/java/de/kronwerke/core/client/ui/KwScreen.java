package de.kronwerke.core.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The frame every Kronwerke screen shares: the brass framed panel, the header band with the
 * pixel brand, the name of the screen and a close cross, tooltips for buttons and for whatever the screen marks
 * with hover(). Screen.render draws the background first and the widgets after it, so the
 * panel and its text live in renderBackground and drawContent, and the buttons land on top.
 */
public abstract class KwScreen extends Screen {
    protected static final int HEAD = 26;
    protected int left, top, w, h;
    private final List<Component> hoverTip = new ArrayList<>();
    private boolean closeHover;
    private final long openedAt = net.minecraft.Util.getMillis();

    protected KwScreen(Component title) {
        super(title);
    }

    /** The size of the panel for the current window; called from init(). */
    protected abstract int panelWidth();

    protected abstract int panelHeight();

    /** What the header says next to the brand. */
    protected String heading() {
        return getTitle().getString();
    }

    /** Everything on the panel below the header; widgets are drawn after it. */
    protected abstract void drawContent(GuiGraphics g, int mouseX, int mouseY, float partial);

    /** False for a screen that must be answered, like the language choice. */
    protected boolean closable() {
        return true;
    }

    /** Something at the top right of the header, like a season chip; x1 is the right edge to draw up to. */
    protected void drawHeaderRight(GuiGraphics g, int x1, int y) {
    }

    @Override
    protected void init() {
        w = Math.min(width - 12, panelWidth());
        h = Math.min(height - 12, panelHeight());
        left = (width - w) / 2;
        top = (height - h) / 2;
    }

    /** A line in the player's language: kronwerke.<key> from the lang files, the German text as fallback. */
    protected static String tr(String key, String fallback, Object... args) {
        return de.kronwerke.core.Text.t(key, fallback, args).getString();
    }

    /** Called by drawContent for the thing under the mouse; shown after the widgets. */
    protected void hover(String... lines) {
        hoverTip.clear();
        for (String s : lines) hoverTip.add(Component.literal(s));
    }

    protected void hover(List<Component> lines) {
        hoverTip.clear();
        hoverTip.addAll(lines);
    }

    protected boolean in(int mx, int my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderTransparentBackground(g);
        hoverTip.clear();
        Ui.panel(g, left, top, left + w, top + h);
        Ui.header(g, left + 6, top + 6, left + w - 6, top + HEAD);
        // on a narrow panel the brand gives way to the heading
        boolean brand = w >= 300;
        if (brand) g.blitSprite(Ui.BRAND, left + 9, top + 6, Ui.BRAND_W, Ui.BRAND_H);
        int bx = left + 9 + (brand ? Ui.BRAND_W : 0);
        if (brand) {
            g.fill(bx + 4, top + 11, bx + 5, top + 19, Ui.GOLD_DARK);
            g.fill(bx + 5, top + 12, bx + 6, top + 20, Ui.SHADOW);
        }
        g.drawString(font, Ui.fit(font, heading(), w - (bx + 11 - left) - 110), bx + 11, top + 11, Ui.TEXT, true);
        // close cross, two pixels thick, on a small dark plate
        int cx = left + w - 21, cy = top + 10;
        closeHover = closable() && in(mouseX, mouseY, cx - 3, cy - 3, cx + 11, cy + 11);
        if (closable()) {
            g.fill(cx - 2, cy - 2, cx + 10, cy + 10, Ui.SHADOW);
            g.fill(cx - 1, cy - 1, cx + 9, cy + 9, closeHover ? Ui.RED : Ui.RAISED);
            int cc = closeHover ? Ui.TEXT : Ui.MUTED;
            for (int i = 0; i < 6; i++) {
                g.fill(cx + 1 + i, cy + 1 + i, cx + 2 + i, cy + 2 + i, cc);
                g.fill(cx + 6 - i, cy + 1 + i, cx + 7 - i, cy + 2 + i, cc);
            }
        }
        drawHeaderRight(g, closable() ? cx - 8 : cx + 9, top + 9);
        drawContent(g, mouseX, mouseY, partial);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // the panel grows in over the first moments after opening
        float t = Math.min(1f, (net.minecraft.Util.getMillis() - openedAt) / 160f);
        float ease = 1 - (1 - t) * (1 - t);
        float scale = 0.94f + 0.06f * ease;
        boolean animating = t < 1f;
        if (animating) {
            g.pose().pushPose();
            g.pose().translate(width / 2f, height / 2f, 0);
            g.pose().scale(scale, scale, 1);
            g.pose().translate(-width / 2f, -height / 2f, 0);
        }
        super.render(g, mouseX, mouseY, partial);
        if (animating) g.pose().popPose();
        List<Component> tip = hoverTip;
        for (var child : children()) {
            if (child instanceof FlatButton b && b.isHovered() && !b.tipLines().isEmpty()) tip = b.tipLines();
        }
        if (closeHover) tip = List.of(de.kronwerke.core.Text.t("ui.close", "Schließen (Esc)"));
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closeHover && button == 0) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Removes every widget, for screens that rebuild their buttons on new data. */
    protected void clearButtons() {
        List<AbstractWidget> gone = new ArrayList<>();
        for (var c : children()) if (c instanceof AbstractWidget a) gone.add(a);
        gone.forEach(this::removeWidget);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
