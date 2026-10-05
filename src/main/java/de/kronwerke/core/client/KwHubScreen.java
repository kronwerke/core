package de.kronwerke.core.client;

import de.kronwerke.core.client.ui.FlatButton;
import de.kronwerke.core.client.ui.KwScreen;
import de.kronwerke.core.client.ui.Ui;
import de.kronwerke.core.net.KwNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The /kw menu. Left the five stages as a path of rings, the running one breathing; right
 * the stage that is selected: its progress ring, its description and, while it runs, every
 * item with icon, count and bar, grouped by pillar. Below it what a player can do from here:
 * deposit, the whitelist for streamers, the admin panel for the team.
 */
public final class KwHubScreen extends KwScreen {
    private static final int LEFT_W = 168, ROW = 30, FOOT = 46;
    private KwNetwork.HubPayload data;
    private int selected = -1, scroll, itemsH, listTop, listBottom;

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

    @Override
    protected int panelWidth() {
        return 464;
    }

    @Override
    protected int panelHeight() {
        return 280;
    }

    @Override
    protected String heading() {
        return "Season 2";
    }

    private KwNetwork.GoalView goal() {
        if (selected < 0 || selected >= data.goals().size()) return null;
        return data.goals().get(selected);
    }

    @Override
    protected void init() {
        super.init();
        if (selected < 0) {
            for (int i = 0; i < data.goals().size(); i++) if (data.goals().get(i).active()) selected = i;
            if (selected < 0) selected = Math.max(0, data.goals().size() - 1);
        }
        int y = top + h - FOOT + 8;
        int x = left + 10;
        addRenderableWidget(FlatButton.primary(x, y, 104, "Hand abgeben", () -> send("deposit")).icon(new ItemStack(Items.GOLD_INGOT))
                .tip("Gibt ab, was du in der Hand hältst", "Nur was das laufende Ziel braucht"));
        addRenderableWidget(FlatButton.of(x + 110, y, 104, "Alles abgeben", () -> send("deposit_all")).icon(new ItemStack(Items.CHEST))
                .tip("Leert dein Inventar in den Obelisken", "Alles, was das Ziel braucht, Rest bleibt"));
        int bx = left + w - 10;
        if (data.admin()) {
            bx -= 70;
            addRenderableWidget(FlatButton.of(bx, y, 70, "Admin", () -> send("admin")).icon(new ItemStack(Items.COMMAND_BLOCK)).tip("Season, Ziele, Team, Obelisk"));
            bx -= 6;
        }
        if (data.streamer()) {
            bx -= 84;
            addRenderableWidget(FlatButton.of(bx, y, 84, "Whitelist", () -> send("menu")).icon(new ItemStack(Items.PLAYER_HEAD)).tip("Deine Plätze und wer drin ist"));
        }
        listTop = top + HEAD + 70;
        listBottom = top + h - FOOT - 6;
    }

    private void send(String action) {
        PacketDistributor.sendToServer(new KwNetwork.ActionPayload(action, ""));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x0 = left + 10, y0 = top + HEAD + 22;
        for (int i = 0; i < data.goals().size(); i++) {
            int ry = y0 + i * ROW;
            if (in((int) mx, (int) my, x0, ry, x0 + LEFT_W - 6, ry + ROW)) {
                if (selected != i) {
                    selected = i;
                    scroll = 0;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2f));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, itemsH - (listBottom - listTop));
        scroll = (int) Math.max(0, Math.min(max, scroll - sy * 20));
        return true;
    }

    @Override
    protected void drawHeaderRight(GuiGraphics g, int x1, int y) {
        KwNetwork.GoalView active = null;
        int done = 0;
        for (KwNetwork.GoalView gv : data.goals()) {
            if (gv.active()) active = gv;
            if (gv.percent() >= 100) done++;
        }
        String chip = active != null ? "Stufe " + (data.goals().indexOf(active) + 1) + " läuft" : done + " von " + data.goals().size() + " geschafft";
        Ui.chip(g, font, chip, x1 - font.width(chip) - 8, y, active != null ? 0xFF8a6420 : 0xFF2f6e3a);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawStages(g, mouseX, mouseY);
        drawGoal(g, mouseX, mouseY);
        Ui.rule(g, left + 8, top + h - FOOT, left + w - 8);
        if (!data.message().isEmpty()) {
            g.drawString(font, Ui.fit(font, data.message(), w - 20), left + 10, top + h - FOOT + 30, data.error() ? 0xFFff6b6b : 0xFF7fd88a, false);
        }
    }

    private void drawStages(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left + 10;
        g.drawString(font, "Die fünf Stufen", x0, top + HEAD + 8, Ui.MUTED, false);
        int y0 = top + HEAD + 22;
        int rx = x0 + 12;
        List<KwNetwork.GoalView> goals = data.goals();
        for (int i = 0; i < goals.size(); i++) {
            KwNetwork.GoalView gv = goals.get(i);
            int ry = y0 + i * ROW;
            boolean done = gv.percent() >= 100 && !gv.active();
            boolean sel = i == selected;
            boolean hover = in(mouseX, mouseY, x0, ry, x0 + LEFT_W - 6, ry + ROW);
            if (sel) Ui.inset(g, x0, ry, x0 + LEFT_W - 6, ry + ROW);
            else if (hover) g.fill(x0, ry, x0 + LEFT_W - 6, ry + ROW, 0x30FFFFFF);
            // the path between the rings
            if (i + 1 < goals.size()) {
                g.fill(rx - 1, ry + ROW / 2 + 10, rx + 1, ry + ROW + ROW / 2 - 10, done ? Ui.GOLD_DARK : Ui.BORDER);
            }
            int colour = done ? Ui.GOLD : gv.active() ? Ui.GOLD : Ui.DIM;
            int cy = ry + ROW / 2;
            if (gv.active()) {
                float p = Ui.pulse(1800);
                g.fill(rx - 12, cy - 12, rx + 12, cy + 12, Ui.withAlpha(Ui.GOLD, (int) (20 + 40 * p)));
            }
            Ui.ring(g, rx, cy, 9, 3, done ? 100 : gv.active() ? gv.percent() : 0, 0xFF2a2435, colour);
            String n = done ? "✔" : Integer.toString(i + 1);
            g.drawString(font, n, rx - font.width(n) / 2 + 1, cy - 4, done ? Ui.GOLD_LIGHT : gv.active() ? Ui.TEXT : Ui.MUTED, false);
            int tx = rx + 16;
            g.drawString(font, Ui.fit(font, gv.title(), x0 + LEFT_W - 10 - tx), tx, cy - 9, sel || gv.active() ? Ui.TEXT : Ui.MUTED, false);
            String st = done ? "geschafft" : gv.active() ? gv.percent() + " %" : "gesperrt";
            g.drawString(font, st, tx, cy + 1, done ? Ui.GOLD : gv.active() ? Ui.GOLD_LIGHT : Ui.DIM, false);
            if (hover && !sel) hover("Anklicken zeigt die Stufe rechts");
        }
    }

    private void drawGoal(GuiGraphics g, int mouseX, int mouseY) {
        KwNetwork.GoalView gv = goal();
        int x0 = left + 10 + LEFT_W, x1 = left + w - 10;
        Ui.inset(g, x0, top + HEAD + 6, x1, top + h - FOOT - 4);
        if (gv == null) {
            g.drawString(font, "Keine Ziele geladen.", x0 + 8, top + HEAD + 14, Ui.MUTED, false);
            return;
        }
        boolean done = gv.percent() >= 100 && !gv.active();
        int ringX = x1 - 24, ringY = top + HEAD + 28;
        int colour = done || gv.active() ? Ui.GOLD : Ui.DIM;
        Ui.ring(g, ringX, ringY, 15, 4, done ? 100 : gv.percent(), 0xFF2a2435, colour);
        String pct = done ? "✔" : gv.percent() + "%";
        g.drawString(font, pct, ringX - font.width(pct) / 2 + 1, ringY - 4, done ? Ui.GOLD_LIGHT : Ui.TEXT, false);
        if (in(mouseX, mouseY, ringX - 16, ringY - 16, ringX + 16, ringY + 16)) {
            hover(done ? "Geschafft" : gv.active() ? gv.percent() + " % der Punkte sind im Obelisken" : "Noch gesperrt");
        }
        int textW = ringX - 20 - (x0 + 8);
        g.drawString(font, Ui.fit(font, "Stufe " + (selected + 1), textW), x0 + 8, top + HEAD + 12, Ui.MUTED, false);
        g.drawString(font, Ui.fit(font, Component.literal(gv.title()).withStyle(ChatFormatting.BOLD), textW), x0 + 8, top + HEAD + 22, Ui.GOLD, false);
        int y = top + HEAD + 34;
        String desc = gv.description();
        if (desc.isEmpty()) {
            desc = done ? "Diese Stufe ist geschafft, die Welt ist ein Stück weiter." : gv.active() ? "Alles unten zählt. Abgeben am Obelisken oder hier aus der Hand." : "Öffnet sich, sobald die Stufe davor geschafft ist.";
        }
        for (var line : font.split(Component.literal(desc), textW)) {
            if (y > listTop - 10) break;
            g.drawString(font, line, x0 + 8, y, Ui.MUTED, false);
            y += 10;
        }
        Ui.ornament(g, x0 + 8, listTop - 4, x1 - 8);
        if (!gv.active()) {
            String s = done ? "Geschafft. Danke an alle Hände." : gv.state().equals("wartet auf das Event") ? "Wartet auf das Event." : "Noch gesperrt.";
            g.drawString(font, s, x0 + 8, listTop + 6, done ? Ui.GOLD : Ui.MUTED, false);
            itemsH = 0;
            return;
        }
        if (gv.state().equals("wartet auf das Event")) {
            g.drawString(font, "Fast voll. Der Rest wird live im Event abgegeben.", x0 + 8, listTop + 4, Ui.GOLD_LIGHT, false);
        }
        // the items, grouped by pillar
        g.enableScissor(x0 + 1, listTop, x1 - 1, listBottom);
        int iy = listTop + 4 - scroll + (gv.state().equals("wartet auf das Event") ? 12 : 0);
        String pillar = null;
        int rows = 0;
        for (KwNetwork.ItemView it : gv.items()) {
            if (!it.pillar().equals(pillar)) {
                pillar = it.pillar();
                g.drawString(font, pillar, x0 + 8, iy + 2, Ui.PURPLE, false);
                Ui.rule(g, x0 + 10 + font.width(pillar), iy + 6, x1 - 8);
                iy += 13;
                rows++;
            }
            boolean full = it.have() >= it.need();
            boolean hover = in(mouseX, mouseY, x0 + 4, Math.max(iy, listTop), x1 - 4, Math.min(iy + 22, listBottom));
            if (hover) g.fill(x0 + 4, iy, x1 - 4, iy + 22, 0x18FFFFFF);
            Ui.slotItem(g, it.id(), x0 + 8, iy + 2);
            String count = Ui.number(it.have()) + " / " + Ui.number(it.need());
            int countX = x1 - 8 - font.width(count);
            g.drawString(font, Ui.fit(font, it.name(), countX - 6 - (x0 + 30)), x0 + 30, iy + 2, full ? Ui.GREEN : Ui.TEXT, false);
            g.drawString(font, count, countX, iy + 2, full ? Ui.GREEN : Ui.MUTED, false);
            int ip = it.need() <= 0 ? 100 : (int) Math.min(100, it.have() * 100 / it.need());
            Ui.bar(g, x0 + 30, iy + 13, x1 - 8, 6, ip, full ? Ui.GREEN : Ui.CYAN, !full);
            if (hover) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(it.name()).withStyle(ChatFormatting.GOLD));
                tip.add(Component.literal(it.id()).withStyle(ChatFormatting.DARK_GRAY));
                tip.add(Component.literal(ip + " %, " + (full ? "voll" : "es fehlen noch " + Ui.number(it.need() - it.have()))).withStyle(full ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                hover(tip);
            }
            iy += 24;
        }
        g.disableScissor();
        itemsH = gv.items().size() * 24 + rows * 13 + 8;
        int max = Math.max(0, itemsH - (listBottom - listTop));
        if (max > 0) {
            // a thin scroll mark on the right
            int trackH = listBottom - listTop;
            int thumbH = Math.max(10, trackH * trackH / itemsH);
            int thumbY = listTop + (trackH - thumbH) * scroll / max;
            g.fill(x1 - 4, listTop, x1 - 2, listBottom, 0xFF1a1620);
            g.fill(x1 - 4, thumbY, x1 - 2, thumbY + thumbH, Ui.GOLD_DARK);
        }
    }
}
