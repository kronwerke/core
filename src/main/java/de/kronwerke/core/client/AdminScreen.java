package de.kronwerke.core.client;

import de.kronwerke.core.client.ui.FlatButton;
import de.kronwerke.core.client.ui.Ui;
import de.kronwerke.core.net.KwNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The admin panel, /kw admin. Five tabs down the left side: the season and the worlds, the
 * goals, the streamers with their slots, the obelisk, and the players online. Every button
 * sends one operation to the server and the panel comes back with the result in the line at
 * the bottom. Buttons that cannot be undone need a second click.
 */
public final class AdminScreen extends Screen {
    private static final String[] TABS = {"Übersicht", "Ziele", "Team", "Obelisk", "Spieler"};
    private static final ItemStack[] ICONS = {new ItemStack(Items.CLOCK), new ItemStack(Items.BEACON),
            new ItemStack(Items.PLAYER_HEAD), new ItemStack(Items.AMETHYST_CLUSTER), new ItemStack(Items.COMPASS)};
    private static final int SIDE = 92, HEAD = 26, FOOT = 24, ROW = 22;

    private static int tab;
    private KwNetwork.AdminPayload data;
    private int left, top, w, h, cx, cw, listTop, listBottom, scroll, contentH;
    private EditBox target;
    private String targetText = "";

    private AdminScreen(KwNetwork.AdminPayload data) {
        super(Component.literal("Kronwerke Admin"));
        this.data = data;
    }

    public static void receive(KwNetwork.AdminPayload payload) {
        if (payload.tab() >= 0) tab = payload.tab();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AdminScreen s) {
            s.data = payload;
            s.rebuildWidgets();
        } else {
            mc.setScreen(new AdminScreen(payload));
        }
    }

    private void send(String op, String arg) {
        PacketDistributor.sendToServer(new KwNetwork.ActionPayload("adm", arg.isEmpty() ? op : op + "|" + arg));
    }

    private void sendRaw(String action, String arg) {
        PacketDistributor.sendToServer(new KwNetwork.ActionPayload(action, arg));
    }

    private String me() {
        return Minecraft.getInstance().getUser().getName();
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 460);
        h = Math.min(height - 16, 290);
        left = (width - w) / 2;
        top = (height - h) / 2;
        cx = left + SIDE + 10;
        cw = left + w - 10 - cx;
        listTop = top + HEAD + 34;
        listBottom = top + h - FOOT - 4;
        if (target != null) targetText = target.getValue();
        target = null;
        switch (tab) {
            case 0 -> initOverview();
            case 1 -> initGoals();
            case 2 -> initTeam();
            case 3 -> initObelisk();
            default -> initPlayers();
        }
        addRenderableWidget(FlatButton.of(left + w - 10 - 70, top + h - FOOT + 4, 70, "Schließen", this::onClose));
        addRenderableWidget(FlatButton.of(left + w - 10 - 70 - 6 - 70, top + h - FOOT + 4, 70, "Aktualisieren", () -> send("refresh", "")));
    }

    // ---- tabs --------------------------------------------------------------------------

    private int y0() {
        return top + HEAD + 36;
    }

    private void initOverview() {
        int y = y0() + 36;
        if (data.seasonRunning()) {
            addRenderableWidget(FlatButton.confirm(cx, y, 150, "Zurück zur Vorbereitung", "Wirklich pausieren?", FlatButton.Style.NORMAL, () -> send("season_pause", "")));
        } else {
            addRenderableWidget(FlatButton.confirm(cx, y, 150, "Season starten", "Sicher? Alles auf null", FlatButton.Style.PRIMARY, () -> send("season_start", "")));
        }
        y = y0() + 64 + 12;
        int bw = (cw - 12) / 3;
        addRenderableWidget(FlatButton.of(cx, y, bw, "Testwelt", () -> send("tw_go", "")));
        addRenderableWidget(FlatButton.confirm(cx + bw + 6, y, bw, "Neu bauen", "Alles neu bauen?", FlatButton.Style.NORMAL, () -> send("tw_rebuild", "")));
        addRenderableWidget(FlatButton.of(cx + 2 * (bw + 6), y, bw, "Zum Spawn", () -> send("tw_back", "")));
        y = y0() + 104 + 12;
        KwNetwork.PlayerRow self = data.players().stream().filter(p -> p.name().equals(me())).findFirst().orElse(null);
        addRenderableWidget(FlatButton.of(cx, y, bw, self != null && self.bypass() ? "Bypass aus" : "Bypass an", () -> send("bypass", me())));
        addRenderableWidget(FlatButton.of(cx + bw + 6, y, bw, self != null && self.creative() ? "Überleben" : "Kreativ", () -> send("mode", me())));
        addRenderableWidget(FlatButton.of(cx + 2 * (bw + 6), y, bw, "Ziele neu laden", () -> send("goals_reload", "")));
    }

    private void initGoals() {
        int y = listTop - scroll;
        int bw = 50;
        for (KwNetwork.GoalRow gr : data.goals()) {
            if (visible(y)) {
                int bx = cx + cw - 4 * (bw + 4) + 4;
                addRenderableWidget(FlatButton.of(bx, y + 3, bw, "Öffnen", () -> send("goal_open", gr.id())));
                addRenderableWidget(FlatButton.confirm(bx + bw + 4, y + 3, bw, "Fertig", "Sicher?", FlatButton.Style.NORMAL, () -> send("goal_complete", gr.id())));
                addRenderableWidget(FlatButton.confirm(bx + 2 * (bw + 4), y + 3, bw, "Reset", "Sicher?", FlatButton.Style.DANGER, () -> send("goal_reset", gr.id())));
                addRenderableWidget(FlatButton.of(bx + 3 * (bw + 4), y + 3, bw, "Skalieren", () -> send("goal_rescale", gr.id())));
            }
            y += ROW + 4;
        }
        contentH = data.goals().size() * (ROW + 4);
    }

    private void initTeam() {
        int y = listTop - scroll;
        for (KwNetwork.StreamerView sv : data.streamers()) {
            if (visible(y)) {
                addRenderableWidget(FlatButton.of(cx + cw - 16 - 4 - 16, y + 3, 16, "-", () -> sendRaw("admin_slots", sv.name() + "|" + (sv.total() - 1))));
                addRenderableWidget(FlatButton.of(cx + cw - 16, y + 3, 16, "+", () -> sendRaw("admin_slots", sv.name() + "|" + (sv.total() + 1))));
            }
            y += ROW;
            for (String p : sv.invited()) {
                if (visible(y)) {
                    addRenderableWidget(FlatButton.confirm(cx + cw - 72 - 4 - 64, y + 3, 64, "Entfernen", "Sicher?", FlatButton.Style.DANGER, () -> sendRaw("admin_revoke", p)));
                    addRenderableWidget(FlatButton.of(cx + cw - 72, y + 3, 72, "Verschieben", () -> sendRaw("admin_move", p + "|" + (target == null ? "" : target.getValue().trim()))));
                }
                y += ROW;
            }
        }
        contentH = y + scroll - listTop;
        target = new EditBox(font, cx, top + h - FOOT + 4, 150, 16, Component.literal("Streamer"));
        target.setHint(Component.literal("Verschieben zu Streamer").withStyle(ChatFormatting.DARK_GRAY));
        target.setValue(targetText);
        target.setMaxLength(16);
        addRenderableWidget(target);
    }

    private void initObelisk() {
        int y = y0() + 34;
        addRenderableWidget(FlatButton.confirm(cx, y, 140, "Hier bauen", "An meiner Position?", FlatButton.Style.PRIMARY, () -> send("obelisk_here", "")));
        addRenderableWidget(FlatButton.of(cx + 146, y, 110, "Abgaben im Chat", () -> send("obelisk_info", "")));
        y = y0() + 64 + 34;
        addRenderableWidget(FlatButton.confirm(cx, y, 140, "Rangliste hier", "Wand hier aufstellen?", FlatButton.Style.NORMAL, () -> send("board_here", "")));
    }

    private void initPlayers() {
        int y = listTop - scroll;
        int bw = 58;
        for (KwNetwork.PlayerRow pr : data.players()) {
            if (visible(y)) {
                int bx = cx + cw - 4 * (bw + 4) + 4;
                addRenderableWidget(FlatButton.of(bx, y + 3, bw, "TP", () -> send("tp", pr.name())));
                addRenderableWidget(FlatButton.of(bx + bw + 4, y + 3, bw, pr.bypass() ? "Bypass aus" : "Bypass", () -> send("bypass", pr.name())));
                addRenderableWidget(FlatButton.of(bx + 2 * (bw + 4), y + 3, bw, pr.creative() ? "Überleben" : "Kreativ", () -> send("mode", pr.name())));
                addRenderableWidget(FlatButton.of(bx + 3 * (bw + 4), y + 3, bw, "Spawn", () -> send("spawn", pr.name())));
            }
            y += ROW;
        }
        contentH = data.players().size() * ROW;
    }

    private boolean visible(int y) {
        return y >= listTop && y + ROW <= listBottom;
    }

    // ---- input -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= left && mx < left + SIDE && my >= top + HEAD) {
            int i = (int) ((my - top - HEAD - 6) / 24);
            if (i >= 0 && i < TABS.length && i != tab) {
                tab = i;
                scroll = 0;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, contentH - (listBottom - listTop));
        int before = scroll;
        scroll = (int) Math.max(0, Math.min(max, scroll - sy * ROW));
        if (scroll != before) rebuildWidgets();
        return true;
    }

    // ---- drawing -----------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Screen.render draws the background first, so the panel and its text live here and the buttons land on top
        renderTransparentBackground(g);
        Ui.panel(g, left, top, left + w, top + h);
        g.fill(left, top, left + w, top + HEAD, 0xFF1b1622);
        Ui.rule(g, left, top + HEAD, left + w);
        Component brand = Component.literal("Kronwerke").withStyle(ChatFormatting.BOLD);
        g.drawString(font, brand, left + 10, top + 9, Ui.GOLD, false);
        g.drawString(font, "Admin", left + 10 + font.width(brand) + 5, top + 9, Ui.MUTED, false);
        String chip = data.seasonRunning() ? "Season " + data.seasonNumber() + " läuft" : "Vorbereitung";
        Ui.chip(g, font, chip, left + w - 10 - font.width(chip) - 8, top + 7, data.seasonRunning() ? 0xFF2f6e3a : 0xFF5a3f86);
        g.fill(left, top + HEAD + 1, left + SIDE, top + h, 0xFF17121d);
        g.fill(left + SIDE, top + HEAD + 1, left + SIDE + 1, top + h, Ui.BORDER);
        for (int i = 0; i < TABS.length; i++) {
            int ty = top + HEAD + 6 + i * 24;
            boolean hover = mouseX >= left && mouseX < left + SIDE && mouseY >= ty && mouseY < ty + 22;
            if (i == tab) {
                g.fill(left, ty, left + SIDE, ty + 22, Ui.RAISED);
                g.fill(left, ty, left + 2, ty + 22, Ui.GOLD);
            } else if (hover) {
                g.fill(left, ty, left + SIDE, ty + 22, 0xFF1e1826);
            }
            g.renderItem(ICONS[i], left + 8, ty + 3);
            g.drawString(font, TABS[i], left + 30, ty + 7, i == tab ? Ui.TEXT : Ui.MUTED, false);
        }
        switch (tab) {
            case 0 -> drawOverview(g);
            case 1 -> drawGoals(g);
            case 2 -> drawTeam(g);
            case 3 -> drawObelisk(g);
            default -> drawPlayers(g);
        }
        Ui.rule(g, left + SIDE + 1, top + h - FOOT, left + w);
        if (!data.message().isEmpty()) {
            int msgX = tab == 2 ? cx + 156 : cx;
            int msgW = left + w - 10 - 146 - 8 - msgX;
            g.drawString(font, Ui.fit(font, data.message(), msgW), msgX, top + h - FOOT + 8, data.error() ? 0xFFff6b6b : 0xFF7fd88a, false);
        }
    }

    private void heading(GuiGraphics g, String title, String sub) {
        g.drawString(font, Ui.fit(font, title, cw), cx, top + HEAD + 8, Ui.TEXT, false);
        g.drawString(font, Ui.fit(font, sub, cw), cx, top + HEAD + 18, Ui.MUTED, false);
    }

    private void wrapped(GuiGraphics g, String text, int x, int y, int width, int colour) {
        for (var line : font.split(Component.literal(text), width)) {
            g.drawString(font, line, x, y, colour, false);
            y += 10;
        }
    }

    private void drawOverview(GuiGraphics g) {
        heading(g, "Übersicht", data.players().size() == 1 ? "1 Spieler online" : data.players().size() + " Spieler online");
        int y = y0();
        g.drawString(font, "Season", cx, y, Ui.GOLD, false);
        wrapped(g, data.seasonRunning()
                ? "Season " + data.seasonNumber() + " läuft. Discord und Website zeigen den Obelisken live."
                : "Vorbereitung: Discord und Website zeigen Work in Progress. Abgaben sind nur ein Test, der Start setzt alles zurück.",
                cx, y + 11, cw, Ui.MUTED);
        y = y0() + 64;
        g.drawString(font, "Testwelt", cx, y, Ui.GOLD, false);
        g.drawString(font, Ui.fit(font, data.testWorldBuilt() ? "gebaut" : "noch nicht gebaut, der erste Besuch baut sie", cw - 60), cx + 60, y, Ui.MUTED, false);
        y = y0() + 104;
        g.drawString(font, "Für mich", cx, y, Ui.GOLD, false);
    }

    private void drawGoals(GuiGraphics g) {
        heading(g, "Ziele", "Öffnen, abschließen, zurücksetzen, neu skalieren");
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        int y = listTop - scroll;
        int textW = cw - 4 * 54 - 4;
        int n = 1;
        for (KwNetwork.GoalRow gr : data.goals()) {
            int colour = gr.percent() >= 100 ? 0xFF55ff55 : gr.state().equals("gesperrt") ? Ui.DIM : Ui.GOLD;
            g.fill(cx, y, cx + cw, y + ROW, 0xFF19141f);
            g.drawString(font, Ui.fit(font, n + ". " + gr.title(), textW - 4), cx + 4, y + 3, Ui.TEXT, false);
            String st = gr.state() + (gr.state().equals("läuft") ? "  " + gr.percent() + "%" : "");
            g.drawString(font, Ui.fit(font, st, textW - 70), cx + 4, y + 13, Ui.MUTED, false);
            Ui.bar(g, cx + textW - 64, y + 15, cx + textW - 4, gr.percent(), colour);
            y += ROW + 4;
            n++;
        }
        g.disableScissor();
    }

    private void drawTeam(GuiGraphics g) {
        int slots = 0, used = 0;
        for (KwNetwork.StreamerView sv : data.streamers()) {
            slots += sv.total();
            used += sv.used();
        }
        heading(g, "Team", data.streamers().size() + " Streamer, " + used + " von " + slots + " Plätzen vergeben");
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        int y = listTop - scroll;
        for (KwNetwork.StreamerView sv : data.streamers()) {
            g.fill(cx, y + 1, cx + cw, y + ROW - 1, 0xFF19141f);
            String count = sv.used() + " / " + sv.total();
            int countX = cx + cw - 40 - 6 - font.width(count);
            g.drawString(font, Ui.fit(font, sv.name() + (sv.granted() ? "" : "  (eingeladen)"), countX - cx - 10), cx + 4, y + 7, Ui.PURPLE, false);
            g.drawString(font, count, countX, y + 7, Ui.TEXT, false);
            y += ROW;
            for (String p : sv.invited()) {
                g.drawString(font, Ui.fit(font, p, cw - 160), cx + 16, y + 7, Ui.TEXT, false);
                y += ROW;
            }
        }
        g.disableScissor();
    }

    private void drawObelisk(GuiGraphics g) {
        heading(g, "Obelisk", data.obelisk().isEmpty() ? "Noch nicht gesetzt" : "Steht bei " + data.obelisk());
        int y = y0();
        wrapped(g, "Hier bauen stellt den Obelisken mit Sockel, Säulen und Strahl an deine Position. Ein alter Obelisk wird vorher abgebaut.", cx, y, cw, Ui.MUTED);
        y = y0() + 64;
        wrapped(g, "Die Ranglisten-Wand zeigt die fleißigsten Hände pro Ziel, eingraviert in Stein. Sie entsteht vor dir, in Blickrichtung.", cx, y, cw, Ui.MUTED);
    }

    private void drawPlayers(GuiGraphics g) {
        heading(g, "Spieler", data.players().size() + " online");
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        int y = listTop - scroll;
        int textW = cw - 4 * 62;
        for (KwNetwork.PlayerRow pr : data.players()) {
            g.fill(cx, y + 1, cx + cw, y + ROW - 1, 0xFF19141f);
            g.drawString(font, Ui.fit(font, pr.name(), textW - 6), cx + 4, y + 3, Ui.TEXT, false);
            List<String> tags = new ArrayList<>();
            tags.add(pr.where());
            if (pr.bypass()) tags.add("Bypass");
            if (pr.creative()) tags.add("Kreativ");
            g.drawString(font, Ui.fit(font, String.join(", ", tags), textW - 6), cx + 4, y + 12, Ui.MUTED, false);
            y += ROW;
        }
        if (data.players().isEmpty()) g.drawString(font, "Niemand online.", cx, listTop + 4, Ui.MUTED, false);
        g.disableScissor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
