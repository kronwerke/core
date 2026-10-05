package de.kronwerke.core.client;

import de.kronwerke.core.net.KwNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import de.kronwerke.core.client.ui.FlatButton;
import de.kronwerke.core.client.ui.Ui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * The /kw menu: the five goals with their bars, the running one with every item, and the
 * things a player can do from here: deposit, the whitelist for streamers, the admin screen
 * for the team.
 */
public final class KwHubScreen extends Screen {
    private static final int W = 340;
    private KwNetwork.HubPayload data;
    private int left, top, panelH, bodyH, scroll;

    private KwHubScreen(KwNetwork.HubPayload data) {
        super(Component.literal("Kronwerke"));
        this.data = data;
    }

    public static void receive(KwNetwork.HubPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof KwHubScreen s) {
            s.data = payload;
            s.rebuildWidgets();
        } else {
            mc.setScreen(new KwHubScreen(payload));
        }
    }

    private static String fmt(long n) {
        String s = Long.toString(n);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    private int bodyHeight() {
        int h = 30;
        for (KwNetwork.GoalView g : data.goals()) {
            h += 14;
            if (g.active()) h += 4 + g.items().size() * 11 + 4;
        }
        return h;
    }

    @Override
    protected void init() {
        // the body scrolls when five goals and a long item list do not fit the screen
        bodyH = Math.min(bodyHeight(), height - 20 - 30 - 44);
        panelH = bodyH + 30 + 44;
        left = (width - W) / 2;
        top = Math.max(10, (height - panelH) / 2);
        int y = top + bodyH + 8;
        int x = left + 8;
        addRenderableWidget(FlatButton.primary(x, y, 90, "Hand abgeben", () -> send("deposit")));
        addRenderableWidget(FlatButton.of(x + 96, y, 90, "Alles abgeben", () -> send("deposit_all")));
        int bx = left + W - 8;
        if (data.admin()) {
            bx -= 56;
            addRenderableWidget(FlatButton.of(bx, y, 56, "Admin", () -> send("admin")));
            bx -= 6;
        }
        if (data.streamer()) {
            bx -= 66;
            addRenderableWidget(FlatButton.of(bx, y, 66, "Whitelist", () -> send("menu")));
        }
        addRenderableWidget(FlatButton.of(left + W - 8 - 70, y + 22, 70, "Schließen", this::onClose));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, bodyHeight() - bodyH);
        scroll = (int) Math.max(0, Math.min(max, scroll - sy * 11));
        return true;
    }

    private void send(String action) {
        PacketDistributor.sendToServer(new KwNetwork.ActionPayload(action, ""));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Screen.render draws this first and the buttons after it, so the panel lives here
        renderTransparentBackground(g);
        drawPanel(g);
    }

    private void drawPanel(GuiGraphics g) {
        Ui.panel(g, left, top, left + W, top + panelH);
        g.drawString(font, Component.literal("Kronwerke Season 2").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + 8, top + 8, 0xFFFFFF);
        g.drawString(font, Component.literal("Die fünf Stufen und was der Obelisk braucht").withStyle(ChatFormatting.GRAY), left + 8, top + 19, 0xFFFFFF);
        g.enableScissor(left, top + 30, left + W, top + bodyH + 4);
        int y = top + 32 - scroll;
        int n = 1;
        for (KwNetwork.GoalView gv : data.goals()) {
            int colour = gv.percent() >= 100 ? 0xFF55ff55 : gv.active() ? 0xFFd4a24a : 0xFF555555;
            // the bar, the state left of it, the title in what is left
            int bx0 = left + W - 8 - 110, bx1 = left + W - 8;
            g.fill(bx0, y + 1, bx1, y + 8, 0xFF2a2435);
            g.fill(bx0, y + 1, bx0 + (int) ((bx1 - bx0) * Math.min(100, gv.percent()) / 100.0), y + 8, colour);
            String st = gv.active() && !gv.state().equals("wartet auf das Event") ? gv.percent() + "%" : gv.state();
            int stX = bx0 - 6 - font.width(st);
            g.drawString(font, Component.literal(st).withStyle(ChatFormatting.GRAY), stX, y, 0xFFFFFF);
            g.drawString(font, Ui.fit(font, n + ". " + gv.title(), stX - 8 - left - 8), left + 8, y, colour);
            y += 14;
            if (gv.active()) {
                y += 2;
                for (KwNetwork.ItemView it : gv.items()) {
                    boolean done = it.have() >= it.need();
                    String c = fmt(it.have()) + " / " + fmt(it.need());
                    Component line = Component.literal("   " + it.pillar() + ": ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal(it.name()).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.AQUA));
                    g.drawString(font, Ui.fit(font, line, W - 16 - font.width(c) - 8), left + 8, y, 0xFFFFFF);
                    g.drawString(font, Component.literal(c).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE), left + W - 8 - font.width(c), y, 0xFFFFFF);
                    y += 11;
                }
                y += 6;
            }
            n++;
        }
        g.disableScissor();
        if (!data.message().isEmpty()) {
            g.drawString(font, Ui.fit(font, data.message(), W - 16 - 80), left + 8, top + bodyH + 8 + 26, data.error() ? 0xFFff6b6b : 0xFF7fd88a, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
