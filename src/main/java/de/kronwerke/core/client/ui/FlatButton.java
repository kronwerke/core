package de.kronwerke.core.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A flat button in the Kronwerke colours. A button with a confirm text needs two clicks:
 * the first one arms it (it turns red and shows the confirm text for a few seconds), the
 * second one runs it.
 */
public class FlatButton extends AbstractButton {
    public enum Style { NORMAL, PRIMARY, DANGER }

    private final Runnable action;
    private final Style style;
    private final Component confirm;
    private long armedUntil;

    public FlatButton(int x, int y, int w, int h, Component label, Style style, Component confirm, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
        this.style = style;
        this.confirm = confirm;
    }

    public static FlatButton of(int x, int y, int w, String label, Runnable action) {
        return new FlatButton(x, y, w, 16, Component.literal(label), Style.NORMAL, null, action);
    }

    public static FlatButton primary(int x, int y, int w, String label, Runnable action) {
        return new FlatButton(x, y, w, 16, Component.literal(label), Style.PRIMARY, null, action);
    }

    public static FlatButton confirm(int x, int y, int w, String label, String confirm, Style style, Runnable action) {
        return new FlatButton(x, y, w, 16, Component.literal(label), style, Component.literal(confirm), action);
    }

    private boolean armed() {
        return confirm != null && System.currentTimeMillis() < armedUntil;
    }

    @Override
    public void onPress() {
        if (confirm != null && !armed()) {
            armedUntil = System.currentTimeMillis() + 4000;
            return;
        }
        armedUntil = 0;
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        boolean hover = isHoveredOrFocused() && active;
        int bg, fg = Ui.TEXT;
        if (!active) {
            bg = 0xFF1a1620;
            fg = Ui.DIM;
        } else if (armed()) {
            bg = Ui.RED_HOVER;
        } else if (style == Style.DANGER) {
            bg = hover ? Ui.RED : Ui.RAISED;
            fg = hover ? Ui.TEXT : 0xFFff8a8a;
        } else if (style == Style.PRIMARY) {
            bg = hover ? 0xFFe2b25a : Ui.GOLD;
            fg = 0xFF1a1208;
        } else {
            bg = hover ? Ui.HOVER : Ui.RAISED;
        }
        int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
        g.fill(x0 + 1, y0, x1 - 1, y1, bg);
        g.fill(x0, y0 + 1, x1, y1 - 1, bg);
        if (active && style != Style.PRIMARY && !armed()) g.fill(x0 + 1, y0, x1 - 1, y0 + 1, 0x22FFFFFF);
        var font = Minecraft.getInstance().font;
        Component label = armed() ? confirm : getMessage();
        String text = Ui.fit(font, label.getString(), width - 6);
        g.drawString(font, text, x0 + (width - font.width(text)) / 2, y0 + (height - 8) / 2, fg, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
