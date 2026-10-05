package de.kronwerke.core.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The frame every Kronwerke screen shares: the slate panel, the header band with the name
 * of the screen and a close cross, tooltips for buttons and for whatever the screen marks
 * with hover(). Screen.render draws the background first and the widgets after it, so the
 * panel and its text live in renderBackground and drawContent, and the buttons land on top.
 */
public abstract class KwScreen extends Screen {
    protected static final int HEAD = 24;
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
        Ui.header(g, left + 5, top + 5, left + w - 5, top + HEAD);
        Component brand = Component.literal("KRONWERKE").withStyle(net.minecraft.ChatFormatting.BOLD);
        g.drawString(font, brand, left + 12, top + 10, Ui.GOLD, true);
        int bx = left + 12 + font.width(brand);
        g.fill(bx + 5, top + 12, bx + 6, top + 16, Ui.GOLD_DARK);
        g.drawString(font, Ui.fit(font, heading(), w - (bx + 11 - left) - 110), bx + 11, top + 10, Ui.TEXT, false);
        // close cross
        int cx = left + w - 19, cy = top + 9;
        closeHover = closable() && in(mouseX, mouseY, cx - 3, cy - 3, cx + 11, cy + 11);
        if (closable()) {
            int cc = closeHover ? Ui.GOLD_LIGHT : Ui.MUTED;
            for (int i = 0; i < 7; i++) {
                g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, cc);
                g.fill(cx + 6 - i, cy + i, cx + 7 - i, cy + i + 1, cc);
            }
        }
        drawHeaderRight(g, closable() ? cx - 8 : cx + 7, top + 8);
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
        if (closeHover) tip = List.of(Component.literal("Schließen (Esc)"));
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
