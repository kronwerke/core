package de.kronwerke.core.client;

import de.kronwerke.core.client.ui.KwScreen;
import de.kronwerke.core.client.ui.Ui;
import de.kronwerke.core.config.KronwerkeClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The first thing a new player sees after the first join: Deutsch or English, as two big
 * cards. The choice sets the game language and is remembered in the client config; the
 * screen never comes back unless languageChosen is set to false again.
 */
public final class LanguageScreen extends KwScreen {
    private final Screen parent;
    private int hoverCard = -1;

    private LanguageScreen(Screen parent) {
        super(Component.literal("Willkommen"));
        this.parent = parent;
    }

    public static void showIfNeeded() {
        if (KronwerkeClientConfig.LANGUAGE_CHOSEN.get()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new LanguageScreen(mc.screen)));
    }

    @Override
    protected int panelWidth() {
        return 340;
    }

    @Override
    protected int panelHeight() {
        return 184;
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

    private int cardX(int i) {
        int cw = (w - 36) / 2;
        return left + 12 + i * (cw + 12);
    }

    private int cardW() {
        return (w - 36) / 2;
    }

    private int cardY() {
        return top + HEAD + 46;
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.drawCenteredString(font, Component.literal("Willkommen auf Kronwerke").withStyle(ChatFormatting.BOLD), left + w / 2, top + HEAD + 10, Ui.GOLD);
        g.drawCenteredString(font, "Welche Sprache soll das Spiel haben?", left + w / 2, top + HEAD + 22, Ui.MUTED);
        g.drawCenteredString(font, "Which language should the game use?", left + w / 2, top + HEAD + 32, Ui.DIM);
        String[][] cards = {{"Deutsch", "Quests, Menüs und Hinweise auf Deutsch", "Jederzeit in den Optionen änderbar"},
                {"English", "Quests, menus and hints in English", "Can be changed in the options any time"}};
        hoverCard = -1;
        for (int i = 0; i < 2; i++) {
            int x = cardX(i), y = cardY(), cw = cardW(), ch = h - HEAD - 46 - 14;
            boolean hover = in(mouseX, mouseY, x, y, x + cw, y + ch);
            if (hover) hoverCard = i;
            g.blitSprite(hover ? Ui.TAB_ACTIVE : Ui.INSET, x, y, cw, ch);
            if (hover) {
                float p = Ui.pulse(1600);
                g.fill(x + 1, y + 1, x + cw - 1, y + ch - 1, Ui.withAlpha(Ui.GOLD, (int) (10 + 20 * p)));
            }
            g.drawCenteredString(font, Component.literal(cards[i][0]).withStyle(ChatFormatting.BOLD), x + cw / 2, y + 14, hover ? Ui.GOLD_LIGHT : Ui.TEXT);
            Ui.ornament(g, x + 16, y + 28, x + cw - 16);
            int ty = y + 38;
            for (var line : font.split(Component.literal(cards[i][1]), cw - 16)) {
                g.drawCenteredString(font, line, x + cw / 2, ty, Ui.MUTED);
                ty += 10;
            }
            ty += 4;
            for (var line : font.split(Component.literal(cards[i][2]), cw - 16)) {
                g.drawCenteredString(font, line, x + cw / 2, ty, Ui.DIM);
                ty += 10;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hoverCard >= 0 && button == 0) {
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
            choose(hoverCard == 0 ? "de_de" : "en_us");
            return true;
        }
        return false;
    }

    @Override
    protected boolean closable() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
