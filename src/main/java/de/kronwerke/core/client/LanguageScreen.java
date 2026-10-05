package de.kronwerke.core.client;

import de.kronwerke.core.config.KronwerkeClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The first thing a new player sees after the first join: Deutsch or English. The choice
 * sets the game language and is remembered in the client config; the screen never comes
 * back unless languageChosen is set to false again.
 */
public final class LanguageScreen extends Screen {
    private final Screen parent;

    private LanguageScreen(Screen parent) {
        super(Component.literal("Sprache"));
        this.parent = parent;
    }

    public static void showIfNeeded() {
        if (KronwerkeClientConfig.LANGUAGE_CHOSEN.get()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new LanguageScreen(mc.screen)));
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        addRenderableWidget(Button.builder(Component.literal("Deutsch"), b -> choose("de_de")).bounds(cx - 105, cy, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("English"), b -> choose("en_us")).bounds(cx + 5, cy, 100, 20).build());
    }

    private void choose(String code) {
        Minecraft mc = Minecraft.getInstance();
        KronwerkeClientConfig.LANGUAGE_CHOSEN.set(true);
        KronwerkeClientConfig.SPEC.save();
        if (!code.equals(mc.options.languageCode)) {
            mc.options.languageCode = code;
            mc.options.save();
            mc.reloadResourcePacks();
        }
        mc.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        int cx = width / 2, cy = height / 2;
        g.fill(cx - 121, cy - 51, cx + 121, cy + 41, 0xFF3a3146);
        g.fill(cx - 120, cy - 50, cx + 120, cy + 40, 0xF0120f18);
        g.drawCenteredString(font, Component.literal("Willkommen auf Kronwerke").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), cx, cy - 40, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal("Welche Sprache soll das Spiel haben?").withStyle(ChatFormatting.GRAY), cx, cy - 26, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal("Which language should the game use?").withStyle(ChatFormatting.DARK_GRAY), cx, cy - 15, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
