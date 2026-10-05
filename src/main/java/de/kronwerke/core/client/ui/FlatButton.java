package de.kronwerke.core.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A button in the Kronwerke sprites, with an optional item icon on the left and a tooltip.
 * A button with a confirm text needs two clicks: the first one arms it (it turns red and
 * shows the confirm text for a few seconds), the second one runs it.
 */
public class FlatButton extends AbstractButton {
    public enum Style { NORMAL, PRIMARY, DANGER }

    private static final ResourceLocation NORMAL = Ui.sprite("button"), NORMAL_HOVER = Ui.sprite("button_hover");
    private static final ResourceLocation PRIMARY = Ui.sprite("button_primary"), PRIMARY_HOVER = Ui.sprite("button_primary_hover");
    private static final ResourceLocation DANGER = Ui.sprite("button_danger"), DANGER_HOVER = Ui.sprite("button_danger_hover");
    private static final ResourceLocation DISABLED = Ui.sprite("button_disabled");

    private final Runnable action;
    private final Style style;
    private final Component confirm;
    private ItemStack icon = ItemStack.EMPTY;
    private List<Component> tip = List.of();
    private long armedUntil;

    public FlatButton(int x, int y, int w, int h, Component label, Style style, Component confirm, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
        this.style = style;
        this.confirm = confirm;
    }

    public static FlatButton of(int x, int y, int w, String label, Runnable action) {
        return new FlatButton(x, y, w, 18, Component.literal(label), Style.NORMAL, null, action);
    }

    public static FlatButton primary(int x, int y, int w, String label, Runnable action) {
        return new FlatButton(x, y, w, 18, Component.literal(label), Style.PRIMARY, null, action);
    }

    public static FlatButton confirm(int x, int y, int w, String label, String confirm, Style style, Runnable action) {
        return new FlatButton(x, y, w, 18, Component.literal(label), style, Component.literal(confirm), action);
    }

    public FlatButton icon(ItemStack stack) {
        this.icon = stack;
        return this;
    }

    public FlatButton tip(String... lines) {
        this.tip = java.util.Arrays.stream(lines).map(s -> (Component) Component.literal(s)).toList();
        return this;
    }

    public List<Component> tipLines() {
        return tip;
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
        ResourceLocation sprite;
        int fg = Ui.TEXT;
        if (!active) {
            sprite = DISABLED;
            fg = Ui.DIM;
        } else if (armed()) {
            sprite = DANGER_HOVER;
        } else if (style == Style.DANGER) {
            sprite = hover ? DANGER_HOVER : NORMAL;
            fg = hover ? Ui.TEXT : 0xFFff8a8a;
        } else if (style == Style.PRIMARY) {
            sprite = hover ? PRIMARY_HOVER : PRIMARY;
            fg = 0xFF1a1208;
        } else {
            sprite = hover ? NORMAL_HOVER : NORMAL;
            if (hover) fg = Ui.GOLD_LIGHT;
        }
        int x0 = getX(), y0 = getY();
        g.blitSprite(sprite, x0, y0, width, height);
        var font = Minecraft.getInstance().font;
        Component label = armed() ? confirm : getMessage();
        int iconW = icon.isEmpty() ? 0 : 18;
        String text = Ui.fit(font, label.getString(), width - 6 - iconW);
        int tx = x0 + iconW + (width - iconW - font.width(text)) / 2;
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x0 + 2, y0 + (height - 16) / 2.0, 0);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }
        g.drawString(font, text, tx, y0 + (height - 8) / 2, fg, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
