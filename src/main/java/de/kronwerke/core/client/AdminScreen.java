package de.kronwerke.core.client;

import de.kronwerke.core.client.ui.FlatButton;
import de.kronwerke.core.client.ui.KwScreen;
import de.kronwerke.core.client.ui.Ui;
import de.kronwerke.core.net.KwNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
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
public final class AdminScreen extends KwScreen {
    private static final String[] TABS = {"Übersicht", "Ziele", "Team", "Obelisk", "Spieler"};
    private static final String[] TAB_TIPS = {"Season, Testwelt, du selbst", "Die fünf Stufen steuern", "Streamer und ihre Plätze", "Bauen und die Rangliste", "Wer gerade online ist"};
    private static final ItemStack[] ICONS = {new ItemStack(Items.CLOCK), new ItemStack(Items.BEACON),
            new ItemStack(Items.PLAYER_HEAD), new ItemStack(Items.AMETHYST_CLUSTER), new ItemStack(Items.COMPASS)};
    private static final int SIDE = 104, FOOT = 30, ROW = 24;

    private static int tab;
    private KwNetwork.AdminPayload data;
    private int cx, cw, listTop, listBottom, scroll, contentH;
    private EditBox target;
    private String targetText = "";

    private AdminScreen(KwNetwork.AdminPayload data) {
        super(Component.literal("Admin"));
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

    @Override
    protected int panelWidth() {
        return 540;
    }

    @Override
    protected int panelHeight() {
        return 300;
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
        super.init();
        cx = left + SIDE + 12;
        cw = left + w - 12 - cx;
        listTop = top + HEAD + 34;
        listBottom = top + h - FOOT - 6;
        if (target != null) targetText = target.getValue();
        target = null;
        switch (tab) {
            case 0 -> initOverview();
            case 1 -> initGoals();
            case 2 -> initTeam();
            case 3 -> initObelisk();
            default -> initPlayers();
        }
        addRenderableWidget(FlatButton.of(left + w - 12 - 90, top + h - FOOT + 6, 90, "Aktualisieren", () -> send("refresh", "")).icon(new ItemStack(Items.RECOVERY_COMPASS)).tip("Holt den Stand neu vom Server"));
    }

    // ---- tabs --------------------------------------------------------------------------

    private int y0() {
        return top + HEAD + 36;
    }

    private void initOverview() {
        int y = y0() + 38;
        if (data.seasonRunning()) {
            addRenderableWidget(FlatButton.confirm(cx, y, 170, "Zurück zur Vorbereitung", "Wirklich pausieren?", FlatButton.Style.NORMAL, () -> send("season_pause", ""))
                    .icon(new ItemStack(Items.CLOCK)).tip("Discord und Website zeigen wieder Work in Progress", "Der Fortschritt bleibt"));
        } else {
            addRenderableWidget(FlatButton.confirm(cx, y, 170, "Season starten", "Sicher? Alles auf null", FlatButton.Style.PRIMARY, () -> send("season_start", ""))
                    .icon(new ItemStack(Items.NETHER_STAR)).tip("Setzt jedes Ziel, jede Rangliste und jedes Kit zurück", "Zweiter Klick startet"));
        }
        y = y0() + 80 + 26;
        int bw = (cw - 12) / 3;
        addRenderableWidget(FlatButton.of(cx, y, bw, "Testwelt", () -> send("tw_go", "")).icon(new ItemStack(Items.GRASS_BLOCK)).tip("Teleportiert dich in die Testwelt"));
        addRenderableWidget(FlatButton.confirm(cx + bw + 6, y, bw, "Neu bauen", "Alles neu bauen?", FlatButton.Style.NORMAL, () -> send("tw_rebuild", "")).icon(new ItemStack(Items.STRUCTURE_BLOCK)).tip("Baut jede Szene neu, eine Reihe pro Tick"));
        addRenderableWidget(FlatButton.of(cx + 2 * (bw + 6), y, bw, "Zum Spawn", () -> send("tw_back", "")).icon(new ItemStack(Items.RED_BED)).tip("Zurück in die Oberwelt"));
        y = y0() + 140 + 14;
        KwNetwork.PlayerRow self = data.players().stream().filter(p -> p.name().equals(me())).findFirst().orElse(null);
        addRenderableWidget(FlatButton.of(cx, y, bw, self != null && self.bypass() ? "Bypass aus" : "Bypass an", () -> send("bypass", me())).icon(new ItemStack(Items.TRIPWIRE_HOOK)).tip("Mit Bypass ignorieren dich die Stufen-Sperren"));
        addRenderableWidget(FlatButton.of(cx + bw + 6, y, bw, self != null && self.creative() ? "Überleben" : "Kreativ", () -> send("mode", me())).icon(new ItemStack(Items.COMMAND_BLOCK)).tip("Wechselt deinen Spielmodus"));
        addRenderableWidget(FlatButton.of(cx + 2 * (bw + 6), y, bw, "Ziele neu laden", () -> send("goals_reload", "")).icon(new ItemStack(Items.WRITABLE_BOOK)).tip("Liest config/kronwerke/goals.json neu ein"));
    }

    private void initGoals() {
        int y = listTop - scroll;
        int bw = 54;
        for (KwNetwork.GoalRow gr : data.goals()) {
            if (visible(y)) {
                int bx = cx + cw - 4 * (bw + 4) + 4 - 6;
                addRenderableWidget(FlatButton.of(bx, y + 3, bw, "Öffnen", () -> send("goal_open", gr.id())).tip("Hebt den Halt vor dem Event auf"));
                addRenderableWidget(FlatButton.confirm(bx + bw + 4, y + 3, bw, "Fertig", "Sicher?", FlatButton.Style.NORMAL, () -> send("goal_complete", gr.id())).tip("Schließt die Stufe sofort ab"));
                addRenderableWidget(FlatButton.confirm(bx + 2 * (bw + 4), y + 3, bw, "Reset", "Sicher?", FlatButton.Style.DANGER, () -> send("goal_reset", gr.id())).tip("Löscht den Fortschritt dieser Stufe"));
                addRenderableWidget(FlatButton.of(bx + 3 * (bw + 4), y + 3, bw, "Skalieren", () -> send("goal_rescale", gr.id())).tip("Rechnet die Mengen auf die aktuelle Spielerzahl um"));
            }
            y += ROW + 6;
        }
        contentH = data.goals().size() * (ROW + 6);
    }

    private void initTeam() {
        int y = listTop - scroll;
        for (KwNetwork.StreamerView sv : data.streamers()) {
            if (visible(y)) {
                addRenderableWidget(FlatButton.of(cx + cw - 18 - 4 - 18 - 6, y + 3, 18, "-", () -> sendRaw("admin_slots", sv.name() + "|" + (sv.total() - 1))).tip("Ein Platz weniger"));
                addRenderableWidget(FlatButton.of(cx + cw - 18 - 6, y + 3, 18, "+", () -> sendRaw("admin_slots", sv.name() + "|" + (sv.total() + 1))).tip("Ein Platz mehr"));
            }
            y += ROW;
            for (String p : sv.invited()) {
                if (visible(y)) {
                    addRenderableWidget(FlatButton.confirm(cx + cw - 76 - 4 - 70 - 6, y + 3, 70, "Entfernen", "Sicher?", FlatButton.Style.DANGER, () -> sendRaw("admin_revoke", p)).tip("Nimmt den Spieler von der Whitelist"));
                    addRenderableWidget(FlatButton.of(cx + cw - 76 - 6, y + 3, 76, "Verschieben", () -> sendRaw("admin_move", p + "|" + (target == null ? "" : target.getValue().trim()))).tip("Zu dem Streamer im Feld unten"));
                }
                y += ROW;
            }
        }
        contentH = y + scroll - listTop;
        target = new EditBox(font, cx + 4, top + h - FOOT + 11, 152, 10, Component.literal("Streamer"));
        target.setBordered(false);
        target.setTextColor(Ui.TEXT);
        target.setHint(Component.literal("Verschieben zu Streamer").withStyle(ChatFormatting.DARK_GRAY));
        target.setValue(targetText);
        target.setMaxLength(16);
        addRenderableWidget(target);
    }

    private void initObelisk() {
        int y = y0() + 36;
        addRenderableWidget(FlatButton.confirm(cx, y, 150, "Hier bauen", "An meiner Position?", FlatButton.Style.PRIMARY, () -> send("obelisk_here", "")).icon(new ItemStack(Items.AMETHYST_CLUSTER)).tip("Baut den Obelisken an deiner Position", "Ein alter wird vorher abgebaut"));
        addRenderableWidget(FlatButton.of(cx + 156, y, 130, "Abgaben im Chat", () -> send("obelisk_info", "")).icon(new ItemStack(Items.PAPER)).tip("Schreibt Position und Stand in den Chat"));
        y = y0() + 70 + 36;
        addRenderableWidget(FlatButton.confirm(cx, y, 150, "Rangliste hier", "Wand hier aufstellen?", FlatButton.Style.NORMAL, () -> send("board_here", "")).icon(new ItemStack(Items.CHISELED_DEEPSLATE)).tip("Stellt die Ranglisten-Wand vor dich"));
    }

    private void initPlayers() {
        int y = listTop - scroll;
        int bw = 56;
        for (KwNetwork.PlayerRow pr : data.players()) {
            if (visible(y)) {
                int bx = cx + cw - 4 * (bw + 4) + 4 - 6;
                addRenderableWidget(FlatButton.of(bx, y + 3, bw, "TP", () -> send("tp", pr.name())).tip("Teleportiert dich zu " + pr.name()));
                addRenderableWidget(FlatButton.of(bx + bw + 4, y + 3, bw, pr.bypass() ? "Bypass aus" : "Bypass", () -> send("bypass", pr.name())).tip("Stufen-Sperren für diesen Spieler"));
                addRenderableWidget(FlatButton.of(bx + 2 * (bw + 4), y + 3, bw, pr.creative() ? "Überleben" : "Kreativ", () -> send("mode", pr.name())).tip("Spielmodus wechseln"));
                addRenderableWidget(FlatButton.of(bx + 3 * (bw + 4), y + 3, bw, "Spawn", () -> send("spawn", pr.name())).tip("Schickt den Spieler zum Spawn"));
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
        if (mx >= left + 6 && mx < left + SIDE && my >= top + HEAD + 6) {
            int i = (int) ((my - top - HEAD - 6) / 26);
            if (i >= 0 && i < TABS.length && i != tab) {
                tab = i;
                scroll = 0;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2f));
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
    protected void drawHeaderRight(GuiGraphics g, int x1, int y) {
        String chip = data.seasonRunning() ? "Season " + data.seasonNumber() + " läuft" : "Vorbereitung";
        Ui.chip(g, font, chip, x1 - font.width(chip) - 8, y, data.seasonRunning() ? 0xFF2f6e3a : 0xFF5a3f86);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // the tab rail
        Ui.inset(g, left + 6, top + HEAD + 2, left + SIDE, top + h - 6);
        for (int i = 0; i < TABS.length; i++) {
            int ty = top + HEAD + 6 + i * 26;
            boolean hover = in(mouseX, mouseY, left + 6, ty, left + SIDE, ty + 24);
            if (i == tab) {
                g.blitSprite(Ui.TAB_ACTIVE, left + 8, ty, SIDE - 10, 24);
                g.fill(left + 8, ty + 2, left + 10, ty + 22, Ui.GOLD);
            } else if (hover) {
                g.fill(left + 8, ty, left + SIDE - 2, ty + 24, 0x30FFFFFF);
            }
            g.renderItem(ICONS[i], left + 14, ty + 4);
            g.drawString(font, TABS[i], left + 36, ty + 8, i == tab ? Ui.TEXT : Ui.MUTED, false);
            if (hover && i != tab) hover(TAB_TIPS[i]);
        }
        switch (tab) {
            case 0 -> drawOverview(g);
            case 1 -> drawGoals(g, mouseX, mouseY);
            case 2 -> drawTeam(g);
            case 3 -> drawObelisk(g);
            default -> drawPlayers(g, mouseX, mouseY);
        }
        Ui.rule(g, cx, top + h - FOOT, left + w - 8);
        if (target != null) {
            Ui.inset(g, cx, top + h - FOOT + 6, cx + 160, top + h - FOOT + 24);
            if (target.isFocused()) g.fill(cx + 1, top + h - FOOT + 23, cx + 159, top + h - FOOT + 24, Ui.GOLD);
        }
        if (!data.message().isEmpty()) {
            int msgX = tab == 2 ? cx + 166 : cx;
            int msgW = left + w - 12 - 90 - 8 - msgX;
            g.drawString(font, Ui.fit(font, data.message(), msgW), msgX, top + h - FOOT + 11, data.error() ? 0xFFff6b6b : 0xFF7fd88a, false);
        }
    }

    private void heading(GuiGraphics g, String title, String sub) {
        g.drawString(font, Ui.fit(font, Component.literal(title).withStyle(ChatFormatting.BOLD), cw), cx, top + HEAD + 8, Ui.GOLD, false);
        g.drawString(font, Ui.fit(font, sub, cw), cx, top + HEAD + 19, Ui.MUTED, false);
        Ui.ornament(g, cx, top + HEAD + 31, cx + cw);
    }

    private void section(GuiGraphics g, String title, int y) {
        g.drawString(font, title, cx, y, Ui.GOLD, false);
        Ui.rule(g, cx + font.width(title) + 6, y + 4, cx + cw);
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
        section(g, "Season", y);
        wrapped(g, data.seasonRunning()
                ? "Season " + data.seasonNumber() + " läuft. Discord und Website zeigen den Obelisken live."
                : "Vorbereitung: Discord und Website zeigen Work in Progress. Abgaben sind nur ein Test, der Start setzt alles zurück.",
                cx, y + 12, cw, Ui.MUTED);
        y = y0() + 80;
        section(g, "Testwelt", y);
        g.drawString(font, Ui.fit(font, data.testWorldBuilt() ? "Gebaut, eine Szene pro Screenshot." : "Noch nicht gebaut, der erste Besuch baut sie.", cw), cx, y + 12, Ui.MUTED, false);
        y = y0() + 140;
        section(g, "Für mich", y);
    }

    private void drawGoals(GuiGraphics g, int mouseX, int mouseY) {
        heading(g, "Ziele", "Öffnen, abschließen, zurücksetzen, neu skalieren");
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        int y = listTop - scroll;
        int textW = cw - 4 * 58 - 10;
        int n = 1;
        for (KwNetwork.GoalRow gr : data.goals()) {
            boolean locked = gr.state().equals("gesperrt");
            int colour = gr.percent() >= 100 ? Ui.GREEN : locked ? Ui.DIM : Ui.GOLD;
            Ui.inset(g, cx, y, cx + cw, y + ROW);
            Ui.ring(g, cx + 12, y + ROW / 2, 8, 3, gr.percent(), 0xFF2a2435, colour);
            String num = Integer.toString(n);
            g.drawString(font, num, cx + 12 - font.width(num) / 2 + 1, y + ROW / 2 - 4, Ui.TEXT, false);
            g.drawString(font, Ui.fit(font, gr.title(), textW - 28), cx + 26, y + 4, Ui.TEXT, false);
            String st = gr.state() + (gr.state().equals("läuft") ? ", " + gr.percent() + " %" : "");
            g.drawString(font, Ui.fit(font, st, textW - 28), cx + 26, y + 14, locked ? Ui.DIM : Ui.MUTED, false);
            if (in(mouseX, mouseY, cx, y, cx + textW, y + ROW)) hover(gr.id());
            y += ROW + 6;
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
            Ui.inset(g, cx, y + 1, cx + cw, y + ROW - 1);
            face(g, sv.name(), cx + 4, y + 4, 16);
            String count = sv.used() + " / " + sv.total();
            int countX = cx + cw - 46 - 8 - font.width(count);
            g.drawString(font, Ui.fit(font, sv.name() + (sv.granted() ? "" : "  (eingeladen)"), countX - cx - 30), cx + 24, y + 8, Ui.PURPLE, false);
            g.drawString(font, count, countX, y + 8, Ui.TEXT, false);
            y += ROW;
            for (String p : sv.invited()) {
                g.fill(cx + 11, y, cx + 12, y + ROW, Ui.BORDER);
                face(g, p, cx + 18, y + 4, 16);
                g.drawString(font, Ui.fit(font, p, cw - 180), cx + 38, y + 8, Ui.TEXT, false);
                y += ROW;
            }
        }
        if (data.streamers().isEmpty()) g.drawString(font, "Noch keine Streamer. /kw admin streamer add <Name> <Plätze>", cx, listTop + 4, Ui.MUTED, false);
        g.disableScissor();
    }

    private void drawObelisk(GuiGraphics g) {
        heading(g, "Obelisk", data.obelisk().isEmpty() ? "Noch nicht gesetzt" : "Steht bei " + data.obelisk());
        int y = y0();
        section(g, "Bauen", y);
        wrapped(g, "Setzt den Obelisken mit Sockel, Säulen und Strahl an deine Position. Ein alter Obelisk wird vorher abgebaut.", cx, y + 12, cw, Ui.MUTED);
        y = y0() + 70;
        section(g, "Rangliste", y);
        wrapped(g, "Die Wand zeigt die fleißigsten Hände pro Ziel, eingraviert in Stein. Sie entsteht vor dir, in Blickrichtung.", cx, y + 12, cw, Ui.MUTED);
    }

    private void drawPlayers(GuiGraphics g, int mouseX, int mouseY) {
        heading(g, "Spieler", data.players().size() + " online");
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        int y = listTop - scroll;
        int textW = cw - 4 * 60 - 6;
        for (KwNetwork.PlayerRow pr : data.players()) {
            Ui.inset(g, cx, y + 1, cx + cw, y + ROW - 1);
            face(g, pr.name(), cx + 4, y + 4, 16);
            g.drawString(font, Ui.fit(font, pr.name(), textW - 26), cx + 24, y + 4, Ui.TEXT, false);
            List<String> tags = new ArrayList<>();
            tags.add(pr.where());
            if (pr.bypass()) tags.add("Bypass");
            if (pr.creative()) tags.add("Kreativ");
            g.drawString(font, Ui.fit(font, String.join(", ", tags), textW - 26), cx + 24, y + 13, Ui.MUTED, false);
            y += ROW;
        }
        if (data.players().isEmpty()) g.drawString(font, "Niemand online.", cx, listTop + 4, Ui.MUTED, false);
        g.disableScissor();
    }

    /** The face of a player when the client knows the skin (online players), a frame otherwise. */
    private void face(GuiGraphics g, String name, int x, int y, int size) {
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(name);
        if (info != null) {
            PlayerFaceRenderer.draw(g, info.getSkin(), x, y, size);
        } else {
            g.fill(x, y, x + size, y + size, 0xFF2a2435);
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF1a1620);
        }
    }
}
