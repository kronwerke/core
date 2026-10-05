package de.kronwerke.core.client;

import de.kronwerke.core.net.KwNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private int left, top, panelH;

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
        panelH = bodyHeight() + 30 + 20;
        left = (width - W) / 2;
        top = Math.max(10, (height - panelH) / 2);
        int y = top + bodyHeight() + 6;
        int x = left + 8;
        addRenderableWidget(Button.builder(Component.literal("Hand abgeben"), b -> send("deposit")).bounds(x, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Alles abgeben"), b -> send("deposit_all")).bounds(x + 96, y, 90, 20).build());
        int bx = left + W - 8;
        if (data.admin()) {
            bx -= 60;
            addRenderableWidget(Button.builder(Component.literal("Team"), b -> send("admin")).bounds(bx, y, 56, 20).build());
            bx -= 4;
        }
        if (data.streamer()) {
            bx -= 70;
            addRenderableWidget(Button.builder(Component.literal("Whitelist"), b -> send("menu")).bounds(bx, y, 66, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose()).bounds(left + W - 8 - 70, y + 24, 70, 20).build());
    }

    private void send(String action) {
        PacketDistributor.sendToServer(new KwNetwork.ActionPayload(action, ""));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        g.fill(left - 1, top - 1, left + W + 1, top + panelH + 1, 0xFF3a3146);
        g.fill(left, top, left + W, top + panelH, 0xF0120f18);
        g.drawString(font, Component.literal("Kronwerke Season 2").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + 8, top + 8, 0xFFFFFF);
        g.drawString(font, Component.literal("Die fünf Stufen und was der Obelisk braucht").withStyle(ChatFormatting.GRAY), left + 8, top + 19, 0xFFFFFF);
        int y = top + 32;
        int n = 1;
        for (KwNetwork.GoalView gv : data.goals()) {
            int colour = gv.percent() >= 100 ? 0xFF55ff55 : gv.active() ? 0xFFd4a24a : 0xFF555555;
            g.drawString(font, Component.literal(n + ". " + gv.title()), left + 8, y, colour);
            // the bar
            int bx0 = left + W - 8 - 110, bx1 = left + W - 8;
            g.fill(bx0, y + 1, bx1, y + 8, 0xFF2a2435);
            g.fill(bx0, y + 1, bx0 + (int) ((bx1 - bx0) * Math.min(100, gv.percent()) / 100.0), y + 8, colour);
            String st = gv.active() && !gv.state().equals("wartet auf das Event") ? gv.percent() + "%" : gv.state();
            g.drawString(font, Component.literal(st).withStyle(ChatFormatting.GRAY), bx0 - 6 - font.width(st), y, 0xFFFFFF);
            y += 14;
            if (gv.active()) {
                y += 2;
                for (KwNetwork.ItemView it : gv.items()) {
                    boolean done = it.have() >= it.need();
                    g.drawString(font, Component.literal("   " + it.pillar() + ": ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal(it.name()).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.AQUA)), left + 8, y, 0xFFFFFF);
                    String c = fmt(it.have()) + " / " + fmt(it.need());
                    g.drawString(font, Component.literal(c).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE), left + W - 8 - font.width(c), y, 0xFFFFFF);
                    y += 11;
                }
                y += 6;
            }
            n++;
        }
        if (!data.message().isEmpty()) {
            g.drawString(font, Component.literal(data.message()).withStyle(data.error() ? ChatFormatting.RED : ChatFormatting.GREEN), left + 8, top + bodyHeight() + 6 + 30, 0xFFFFFF);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
