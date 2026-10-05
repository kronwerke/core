package de.kronwerke.core.client;

import de.kronwerke.core.net.KwNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The team's screen: every streamer with their slots (plus and minus change the base
 * allowance), the players in those slots, and for each player a button to take the place
 * away or to move them to the streamer typed in the field at the bottom. Scrolls with the
 * mouse wheel.
 */
public final class AdminScreen extends Screen {
    private static final int W = 360;
    private KwNetwork.AdminPayload data;
    private EditBox target;
    private int left, top, panelH, scroll, contentH;

    private AdminScreen(KwNetwork.AdminPayload data) {
        super(Component.literal("Team"));
        this.data = data;
    }

    public static void receive(KwNetwork.AdminPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AdminScreen s) {
            s.data = payload;
            s.rebuildWidgets();
        } else {
            mc.setScreen(new AdminScreen(payload));
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        panelH = Math.min(height - 20, 80 + Math.max(1, rows()) * 14);
        top = (height - panelH) / 2;
        int y = top + 34;
        int listBottom = top + panelH - 40;
        String keep = target == null ? "" : target.getValue();
        for (KwNetwork.StreamerView sv : data.streamers()) {
            int ry = y - scroll;
            if (ry >= top + 30 && ry + 12 <= listBottom) {
                addRenderableWidget(Button.builder(Component.literal("-"), b -> send("admin_slots", sv.name() + "|" + (sv.total() - 1))).bounds(left + W - 8 - 50, ry - 1, 14, 12).build());
                addRenderableWidget(Button.builder(Component.literal("+"), b -> send("admin_slots", sv.name() + "|" + (sv.total() + 1))).bounds(left + W - 8 - 14, ry - 1, 14, 12).build());
            }
            y += 14;
            for (String p : sv.invited()) {
                ry = y - scroll;
                if (ry >= top + 30 && ry + 12 <= listBottom) {
                    addRenderableWidget(Button.builder(Component.literal("Weg"), b -> send("admin_revoke", p)).bounds(left + W - 8 - 100, ry - 1, 40, 12).build());
                    addRenderableWidget(Button.builder(Component.literal("Zu ->"), b -> send("admin_move", p + "|" + target.getValue().trim())).bounds(left + W - 8 - 56, ry - 1, 56, 12).build());
                }
                y += 14;
            }
        }
        contentH = y - (top + 34);
        target = new EditBox(font, left + 8, top + panelH - 30, 150, 20, Component.literal("Streamer"));
        target.setHint(Component.literal("Zu welchem Streamer").withStyle(ChatFormatting.DARK_GRAY));
        target.setValue(keep);
        addRenderableWidget(target);
        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose()).bounds(left + W - 8 - 70, top + panelH - 30, 70, 20).build());
    }

    private int rows() {
        int n = 0;
        for (KwNetwork.StreamerView sv : data.streamers()) n += 1 + sv.invited().size();
        return n;
    }

    private void send(String action, String arg) {
        PacketDistributor.sendToServer(new KwNetwork.ActionPayload(action, arg));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, contentH - (panelH - 74));
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY * 14));
        rebuildWidgets();
        return true;
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
        g.drawString(font, Component.literal("Plätze und Spieler").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + 8, top + 8, 0xFFFFFF);
        g.drawString(font, Component.literal("Streamer mit Plätzen, darunter wer den Platz hat").withStyle(ChatFormatting.GRAY), left + 8, top + 19, 0xFFFFFF);
        int listTop = top + 30, listBottom = top + panelH - 40;
        g.enableScissor(left, listTop, left + W, listBottom);
        int y = top + 34 - scroll;
        for (KwNetwork.StreamerView sv : data.streamers()) {
            g.drawString(font, Component.literal(sv.name()).withStyle(ChatFormatting.LIGHT_PURPLE).append(Component.literal(sv.granted() ? "" : "  (eingeladen)").withStyle(ChatFormatting.DARK_GRAY)), left + 8, y, 0xFFFFFF);
            String slots = sv.used() + " / " + sv.total();
            g.drawString(font, slots, left + W - 8 - 56 - font.width(slots), y, 0xFFFFFF);
            y += 14;
            for (String p : sv.invited()) {
                g.drawString(font, Component.literal("    " + p).withStyle(ChatFormatting.WHITE), left + 8, y, 0xFFFFFF);
                y += 14;
            }
        }
        g.disableScissor();
        if (!data.message().isEmpty()) {
            g.drawString(font, Component.literal(data.message()).withStyle(data.error() ? ChatFormatting.RED : ChatFormatting.GREEN), left + 8, top + panelH - 40, 0xFFFFFF);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
