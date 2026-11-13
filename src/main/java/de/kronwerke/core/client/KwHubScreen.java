package de.kronwerke.core.client;

import de.kronwerke.core.client.ui.FlatButton;
import de.kronwerke.core.client.ui.Heads;
import de.kronwerke.core.client.ui.KwScreen;
import de.kronwerke.core.client.ui.Ui;
import de.kronwerke.core.config.KronwerkeClientConfig;
import de.kronwerke.core.net.KwNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The /kw menu, four pages behind tabs. Goals: left the five stages as a path of rings in
 * their colours, the running one breathing; right the stage that is selected with its ring,
 * its description and, while it runs, every item with icon, count and bar, grouped by pillar.
 * Ranking: the most diligent hands of the running goal with their heads, the first of every
 * pillar and the player's own part. Options: what a player can set for themselves (language,
 * the goal's bar, softer light, the clouds, the loading picture) and the way to the game's
 * own options. Help: how giving works (by hand, at the obelisk, nowhere else) and the
 * commands, one click puts them into the chat. Below all of it the ways out of the menu: the
 * quest book, the map, Discord, the website, the whitelist for streamers, the admin panel for
 * the team.
 */
public final class KwHubScreen extends KwScreen {
    private static final String DISCORD = "https://discord.gg/DZs3XyyHDj";
    private static final String WEBSITE = "https://kronwerke.com";
    private static final int LEFT_W = 168, FOOT = 30, TABS = 24;
    private static final int[] CLOUDS = {192, 256, 320, 448, 608};
    private static final String[] TAB_SIGNS = {"goal", "crown", "cog", "help"};
    /** The page that was open last, kept while the game runs. */
    private static int tab;

    /** A place on the page that answers a click this frame. */
    private record Hit(int x0, int y0, int x1, int y1, Runnable run) {
    }

    /** Rows of the stage path: 30 high, less on a small window so all five stages fit above the buttons. */
    private int ROW = 30;
    private KwNetwork.HubPayload data;
    private int selected = -1, scroll, itemsH, listTop, listBottom, cTop, cBot;
    private final List<Hit> hits = new ArrayList<>();

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
        return 284;
    }

    @Override
    protected String heading() {
        return data.mine().season().replaceFirst("^Kronwerke ", "");
    }

    private KwNetwork.GoalView goal() {
        if (selected < 0 || selected >= data.goals().size()) return null;
        return data.goals().get(selected);
    }

    private KwNetwork.GoalView running() {
        for (KwNetwork.GoalView gv : data.goals()) if (gv.more().active()) return gv;
        return null;
    }

    @Override
    protected void init() {
        super.init();
        if (selected < 0) {
            for (int i = 0; i < data.goals().size(); i++) if (data.goals().get(i).more().active()) selected = i;
            if (selected < 0) selected = Math.max(0, data.goals().size() - 1);
        }
        cTop = top + HEAD + TABS;
        cBot = top + h - FOOT;
        int y = top + h - 26;
        // right: what only some may open
        int bx = left + w - 10;
        if (data.admin()) {
            bx -= 62;
            addRenderableWidget(FlatButton.of(bx, y, 62, "Admin", () -> send("admin")).sign("shield").tip(tr("hub.admin_tip", "Season, Ziele, Team, Obelisk")));
            bx -= 4;
        }
        if (data.streamer()) {
            bx -= 80;
            addRenderableWidget(FlatButton.of(bx, y, 80, "Whitelist", () -> send("menu")).sign("head").tip(tr("hub.whitelist_tip", "Deine Plätze und wer drin ist")));
            bx -= 4;
        }
        // left: the ways out of the menu, as many as fit
        int x = left + 10;
        String quests = tr("hub.quests", "Questbuch"), map = tr("hub.map", "Karte");
        int[] widths = {font.width(quests) + 27, font.width(map) + 27, 66, 66};
        String[] labels = {quests, map, "Discord", "Website"};
        String[] signs = {"book", "map", "chat", "globe"};
        String[][] tips = {{tr("hub.quests_tip", "Öffnet das Questbuch")}, {tr("hub.map_tip", "Öffnet die große Karte")},
                {tr("hub.discord_tip", "Der Discord von Kronwerke"), DISCORD}, {tr("hub.website_tip", "Status, Stufen und Chronik"), WEBSITE}};
        Runnable[] runs = {this::openQuests, this::openMap, () -> ConfirmLinkScreen.confirmLinkNow(this, DISCORD, true), () -> ConfirmLinkScreen.confirmLinkNow(this, WEBSITE, true)};
        for (int i = 0; i < labels.length; i++) {
            if (x + widths[i] > bx) break;
            addRenderableWidget(FlatButton.of(x, y, widths[i], labels[i], runs[i]).sign(signs[i]).tip(tips[i]));
            x += widths[i] + 4;
        }
        int n = Math.max(1, data.goals().size());
        ROW = Math.max(20, Math.min(30, (cBot - 42 - (cTop + 14)) / n));
        listTop = cTop + 66;
        listBottom = cBot - 6;
        if (tab == 2) {
            int by = cBot - 26;
            addRenderableWidget(FlatButton.of(left + 16, by, 124, tr("hub.opt_game", "Spieloptionen"), () -> minecraft.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(this, minecraft.options))).sign("cog")
                    .tip(tr("hub.opt_game_tip", "Grafik, Ton, Steuerung: die Optionen des Spiels")));
            addRenderableWidget(FlatButton.of(left + 144, by, 124, tr("hub.opt_keys", "Tastenbelegung"), () -> minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this, minecraft.options)))
                    .tip(tr("hub.opt_keys_tip", "Alle Tasten des Packs zum Nachsehen und Ändern")));
        }
    }

    /** The goal's title in the player's language: the pack's lang files, the server's title as fallback. */
    private static String title(KwNetwork.GoalView gv) {
        return tr("goal." + gv.more().id() + ".title", gv.title());
    }

    private static String pillarName(KwNetwork.GoalView gv, String id) {
        for (String p : gv.more().pillars()) {
            int bar = p.indexOf('|');
            if (bar > 0 && p.substring(0, bar).equals(id)) return tr("goal." + gv.more().id() + ".pillar." + id, p.substring(bar + 1));
        }
        return id;
    }

    /** Item names come from the client, so they are in the player's language. */
    private static String itemName(KwNetwork.ItemView it) {
        String n = de.kronwerke.core.Text.item(it.id()).getString();
        return n.isEmpty() || n.equals(it.id()) ? it.name() : n;
    }

    private void send(String action) {
        PacketDistributor.sendToServer(new KwNetwork.ActionPayload(action, ""));
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2f));
    }

    private void openQuests() {
        onClose();
        if (minecraft.player != null) minecraft.player.connection.sendCommand("ftbquests open_book");
    }

    /** The large map of FTB Chunks: its own screen where the mod is there, else its key. */
    private void openMap() {
        onClose();
        try {
            Class.forName("dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen").getMethod("openMap").invoke(null);
            return;
        } catch (ReflectiveOperationException | LinkageError e) {
            // another version of the mod: its key does the same
        }
        for (KeyMapping km : minecraft.options.keyMappings) {
            if (km.getName().equals("key.ftbchunks.map") && !km.isUnbound()) KeyMapping.click(km.getKey());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Hit hit : new ArrayList<>(hits)) {
            if (in((int) mx, (int) my, hit.x0, hit.y0, hit.x1, hit.y1)) {
                click();
                hit.run.run();
                return true;
            }
        }
        if (tab == 0) {
            int x0 = left + 10, y0 = cTop + 14;
            for (int i = 0; i < data.goals().size(); i++) {
                int ry = y0 + i * ROW;
                if (in((int) mx, (int) my, x0, ry, x0 + LEFT_W - 6, ry + ROW)) {
                    if (selected != i) {
                        selected = i;
                        scroll = 0;
                        click();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab != 0) return true;
        int max = Math.max(0, itemsH - (listBottom - listTop));
        scroll = (int) Math.max(0, Math.min(max, scroll - sy * 20));
        return true;
    }

    @Override
    protected void drawHeaderRight(GuiGraphics g, int x1, int y) {
        KwNetwork.GoalView active = running();
        int done = 0;
        for (KwNetwork.GoalView gv : data.goals()) if (gv.percent() >= 100 && !gv.more().active()) done++;
        String chip = active != null ? tr("hub.chip_running", "Stufe %s läuft", data.goals().indexOf(active) + 1) : tr("hub.chip_done", "%s von %s geschafft", done, data.goals().size());
        int cx = x1 - font.width(chip) - 10;
        Ui.chip(g, font, chip, cx, y, active != null ? Ui.stage(data.goals().indexOf(active)) : Ui.GREEN);
        // the works are running: a gear turns next to it, as on the title picture
        if (active != null) g.blitSprite(Ui.sprite("gear_" + (net.minecraft.Util.getMillis() / 150) % 4), cx - 19, y - 2, 16, 16);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        hits.clear();
        drawTabs(g, mouseX, mouseY);
        switch (tab) {
            case 1 -> drawRanks(g, mouseX, mouseY);
            case 2 -> drawOptions(g, mouseX, mouseY);
            case 3 -> drawHelp(g, mouseX, mouseY);
            default -> {
                drawStages(g, mouseX, mouseY);
                drawGoal(g, mouseX, mouseY);
            }
        }
        Ui.rule(g, left + 8, cBot, left + w - 8);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        String[] names = {tr("hub.tab_goals", "Ziele"), tr("hub.tab_ranks", "Rangliste"), tr("hub.tab_options", "Optionen"), tr("hub.tab_help", "Hilfe")};
        int x = left + 10, y = top + HEAD + 4;
        for (int i = 0; i < names.length; i++) {
            int tw = font.width(names[i]) + 25;
            boolean hover = in(mouseX, mouseY, x, y, x + tw, y + 16);
            g.blitSprite(i == tab ? Ui.TAB_ACTIVE : Ui.TAB, x, y, tw, 16);
            int colour = i == tab ? Ui.GOLD_LIGHT : hover ? Ui.TEXT : Ui.MUTED;
            Ui.sign(g, TAB_SIGNS[i], x + 5, y + 3, colour);
            g.drawString(font, names[i], x + 19, y + 4, colour, false);
            if (i != tab) {
                int page = i;
                hits.add(new Hit(x, y, x + tw, y + 16, () -> {
                    tab = page;
                    rebuildWidgets();
                }));
            }
            x += tw + 3;
        }
        // what the last action said, next to the tabs
        if (!data.message().getString().isEmpty()) {
            var line = Ui.fit(font, data.message(), left + w - 12 - x - 8);
            g.drawString(font, line, left + w - 12 - font.width(line), y + 4, data.error() ? 0xFFff8a8a : Ui.GREEN, false);
        }
    }

    // ---- goals ---------------------------------------------------------------------------

    private void drawStages(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left + 10;
        g.drawString(font, tr("hub.stages", "Die Stufen"), x0, cTop + 2, Ui.MUTED, false);
        drawMine(g, x0, cTop + 14 + data.goals().size() * ROW + 6, mouseX, mouseY);
        int y0 = cTop + 14;
        int rx = x0 + 12;
        List<KwNetwork.GoalView> goals = data.goals();
        for (int i = 0; i < goals.size(); i++) {
            KwNetwork.GoalView gv = goals.get(i);
            boolean active = gv.more().active();
            int ry = y0 + i * ROW;
            boolean done = gv.percent() >= 100 && !active;
            boolean sel = i == selected;
            boolean hover = in(mouseX, mouseY, x0, ry, x0 + LEFT_W - 6, ry + ROW);
            if (sel) g.blitSprite(Ui.TAB_ACTIVE, x0, ry, LEFT_W - 6, ROW);
            else if (hover) g.fill(x0, ry, x0 + LEFT_W - 6, ry + ROW, 0x28FFFFFF);
            int colour = done || active ? Ui.stage(i) : Ui.DIM;
            // the path between the rings
            if (i + 1 < goals.size()) {
                g.fill(rx - 1, ry + ROW / 2 + 9, rx + 1, ry + ROW + ROW / 2 - 9, done ? Ui.darken(colour, 90) : Ui.BORDER);
            }
            int cy = ry + ROW / 2;
            if (active) {
                float p = Ui.pulse(1800);
                Ui.disc(g, rx, cy, 12, Ui.withAlpha(colour, (int) (24 + 50 * p)));
            }
            Ui.ring(g, rx, cy, 9, 3, done ? 100 : active ? gv.percent() : 0, Ui.TRACK, colour);
            String n = done ? "✔" : Integer.toString(i + 1);
            g.drawString(font, n, rx - font.width(n) / 2 + 1, cy - 4, done ? Ui.lighten(colour, 90) : active ? Ui.TEXT : Ui.MUTED, false);
            int tx = rx + 16;
            g.drawString(font, Ui.fit(font, title(gv), x0 + LEFT_W - 10 - tx), tx, cy - 9, sel || active ? Ui.TEXT : Ui.MUTED, false);
            String st = done ? tr("goal.state.done", "geschafft") : active ? gv.percent() + " %" : tr("goal.state.locked", "gesperrt");
            g.drawString(font, st, tx, cy + 1, done || active ? colour : Ui.DIM, false);
            if (hover && !sel) hover(tr("hub.stage_tip", "Anklicken zeigt die Stufe rechts"));
        }
    }

    /** "Deine Gaben": the player's points, rank and daily streak at the running goal. */
    private void drawMine(GuiGraphics g, int x0, int y, int mouseX, int mouseY) {
        var m = data.mine();
        if (y + 22 > cBot - 2) return;
        Ui.rule(g, x0, y, x0 + LEFT_W - 6);
        g.drawString(font, tr("hub.mine", "Deine Gaben"), x0, y + 4, Ui.MUTED, false);
        String line = m.points() <= 0 ? tr("hub.mine_none", "Noch nichts gegeben") : tr("hub.mine_line", "%s Punkte, Platz %s von %s", Ui.number(m.points()), m.rank(), m.hands());
        g.drawString(font, Ui.fit(font, line, LEFT_W - 8), x0, y + 15, m.points() > 0 ? Ui.GOLD_LIGHT : Ui.DIM, false);
        boolean streak = m.streak() > 0 && y + 36 <= cBot - 2;
        if (streak) g.drawString(font, tr("hub.streak", "Tagesgabe: %s Tage in Folge", m.streak()), x0, y + 26, Ui.MUTED, false);
        if (in(mouseX, mouseY, x0, y, x0 + LEFT_W - 6, y + (streak ? 36 : 25))) {
            hover(tr("hub.mine_tip", "Punkte: Stück mal Gewicht, wie der Balken zählt"), tr("hub.mine_tip2", "Die Tagesgabe zählt, wenn du jeden Tag etwas abgibst"));
        }
    }

    private void drawGoal(GuiGraphics g, int mouseX, int mouseY) {
        KwNetwork.GoalView gv = goal();
        int x0 = left + 10 + LEFT_W, x1 = left + w - 10;
        int gy = cTop - 4;
        Ui.inset(g, x0, gy + 6, x1, cBot - 4);
        if (gv == null) {
            g.drawString(font, tr("hub.no_goals", "Keine Ziele geladen."), x0 + 8, gy + 14, Ui.MUTED, false);
            return;
        }
        boolean active = gv.more().active();
        boolean done = gv.percent() >= 100 && !active;
        int ringX = x1 - 24, ringY = gy + 28;
        int stage = Ui.stage(selected);
        int colour = done || active ? stage : Ui.DIM;
        Ui.ring(g, ringX, ringY, 15, 4, done ? 100 : gv.percent(), Ui.TRACK, colour);
        String pct = done ? "✔" : gv.percent() + "%";
        g.drawString(font, pct, ringX - font.width(pct) / 2 + 1, ringY - 4, done ? Ui.lighten(colour, 90) : Ui.TEXT, false);
        if (in(mouseX, mouseY, ringX - 16, ringY - 16, ringX + 16, ringY + 16)) {
            hover(done ? tr("hub.ring_done", "Geschafft") : active ? tr("hub.ring_running", "%s %% der Punkte sind im Obelisken", gv.percent()) : tr("hub.ring_locked", "Noch gesperrt"));
        }
        int textW = ringX - 20 - (x0 + 8);
        g.drawString(font, Ui.fit(font, tr("hub.stage", "Stufe %s", selected + 1), textW), x0 + 8, gy + 12, Ui.MUTED, false);
        g.drawString(font, Ui.fit(font, Component.literal(title(gv)).withStyle(ChatFormatting.BOLD), textW), x0 + 8, gy + 22, done || active ? stage : Ui.GOLD, false);
        int y = gy + 34;
        String desc = gv.description().isEmpty() ? "" : tr("goal." + gv.more().id() + ".description", gv.description());
        if (desc.isEmpty()) {
            desc = done ? tr("hub.desc_done", "Diese Stufe ist geschafft, die Welt ist ein Stück weiter.") : active ? tr("hub.desc_running", "Alles unten zählt. Abgegeben wird am Obelisken am Spawn.") : tr("hub.desc_locked", "Öffnet sich, sobald die Stufe davor geschafft ist.");
        }
        if (!gv.more().event().isEmpty()) desc = tr("hub.event", "Das Event %s.", gv.more().event()) + " " + desc;
        for (var line : font.split(Component.literal(desc), textW)) {
            if (y > listTop - 10) break;
            g.drawString(font, line, x0 + 8, y, Ui.MUTED, false);
            y += 10;
        }
        Ui.ornament(g, x0 + 8, listTop - 4, x1 - 8);
        if (done) {
            g.drawString(font, tr("hub.done_top", "Geschafft. Die fleißigsten Hände:"), x0 + 8, listTop + 6, Ui.GOLD, false);
            int ty = listTop + 22, r = 1;
            for (String t : gv.more().top()) {
                int bar = t.lastIndexOf('|');
                String who = bar < 0 ? t : t.substring(0, bar), pts = bar < 0 ? "" : tr("hub.points", "%s Punkte", Ui.number(parse(t.substring(bar + 1))));
                if (ty + 12 > cBot - 6) break;
                medal(g, r++, x0 + 10, ty);
                Heads.draw(g, who, x0 + 28, ty, 10);
                g.drawString(font, who, x0 + 44, ty + 1, Ui.TEXT, false);
                g.drawString(font, pts, x1 - 8 - font.width(pts), ty + 1, Ui.MUTED, false);
                ty += 15;
            }
            if (gv.more().top().isEmpty()) g.drawString(font, tr("hub.no_names", "Ohne Namen, aber nicht ohne Hände."), x0 + 12, ty, Ui.MUTED, false);
            itemsH = 0;
            return;
        }
        if (!active && !gv.more().estimate()) {
            g.drawString(font, Ui.fit(font, tr("hub.locked_list", "Noch gesperrt. Was sie braucht, steht hier, sobald die Stufe davor läuft."), x1 - x0 - 16), x0 + 8, listTop + 6, Ui.MUTED, false);
            itemsH = 0;
            return;
        }
        boolean estimate = gv.more().estimate();
        if (estimate) {
            g.drawString(font, Ui.fit(font, tr("hub.estimate", "Die nächste Stufe, zum Vorsorgen. Mengen geschätzt, sie richten sich nach der Spielerzahl."), x1 - x0 - 16),
                    x0 + 8, listTop + 4, Ui.MUTED, false);
        }
        if (gv.state().equals("wartet auf das Event")) {
            g.drawString(font, Ui.fit(font, tr("hub.held", "Fast voll. Der Rest wird live im Event abgegeben."), x1 - x0 - 16), x0 + 8, listTop + 4, Ui.GOLD_LIGHT, false);
        }
        long total = 0;
        for (KwNetwork.ItemView it : gv.items()) total += it.need() * it.weight();
        // the items, grouped by pillar; in each pillar what moves the bar most comes first
        List<KwNetwork.ItemView> sorted = new ArrayList<>();
        java.util.Map<String, List<KwNetwork.ItemView>> byPillar = new java.util.LinkedHashMap<>();
        for (KwNetwork.ItemView it : gv.items()) byPillar.computeIfAbsent(it.pillar(), k -> new ArrayList<>()).add(it);
        for (List<KwNetwork.ItemView> l : byPillar.values()) {
            l.sort((a, b) -> Long.compare(b.missingPoints(), a.missingPoints()));
            sorted.addAll(l);
        }
        g.enableScissor(x0 + 1, listTop, x1 - 1, listBottom);
        int iy = listTop + 4 - scroll + (gv.state().equals("wartet auf das Event") || estimate ? 12 : 0);
        String pillar = null;
        int rows = 0;
        for (KwNetwork.ItemView it : sorted) {
            if (!it.pillar().equals(pillar)) {
                pillar = it.pillar();
                String pn = pillarName(gv, pillar);
                g.drawString(font, pn, x0 + 8, iy + 2, Ui.PURPLE, false);
                Ui.rule(g, x0 + 10 + font.width(pn), iy + 6, x1 - 8);
                iy += 13;
                rows++;
            }
            boolean full = it.have() >= it.need();
            boolean hover = in(mouseX, mouseY, x0 + 4, Math.max(iy, listTop), x1 - 4, Math.min(iy + 22, listBottom));
            if (hover) g.fill(x0 + 4, iy, x1 - 4, iy + 22, 0x18FFFFFF);
            Ui.slotItem(g, it.id(), x0 + 8, iy + 2);
            String count = estimate ? tr("hub.about", "ca. %s", Ui.number(it.need())) : Ui.number(it.have()) + " / " + Ui.number(it.need());
            int countX = x1 - 8 - font.width(count);
            // what one piece is worth on the bar, where it is more than one point
            String weight = it.weight() > 1 ? "x" + Ui.number(it.weight()) : "";
            int nameW = countX - 6 - (x0 + 30) - (weight.isEmpty() ? 0 : font.width(weight) + 6);
            String name = itemName(it);
            String shown = Ui.fit(font, name, nameW);
            g.drawString(font, shown, x0 + 30, iy + 2, full ? Ui.GREEN : Ui.TEXT, false);
            if (!weight.isEmpty()) g.drawString(font, weight, x0 + 30 + font.width(shown) + 6, iy + 2, Ui.GOLD, false);
            g.drawString(font, count, countX, iy + 2, full ? Ui.GREEN : Ui.MUTED, false);
            int ip = it.need() <= 0 ? 100 : (int) Math.min(100, it.have() * 100 / it.need());
            int share = total <= 0 ? 0 : (int) Math.round(it.need() * it.weight() * 100.0 / total);
            if (!estimate) Ui.bar(g, x0 + 30, iy + 13, x1 - 8, 6, ip, full ? Ui.GREEN : Ui.GOLD, !full);
            if (hover) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(name).withStyle(ChatFormatting.GOLD));
                tip.add(Component.literal(it.weight() > 1 ? tr("hub.item_weight", "Ein Stück zählt %s Punkte", Ui.number(it.weight())) : tr("hub.item_one", "Ein Stück zählt einen Punkt")).withStyle(ChatFormatting.YELLOW));
                tip.add(Component.literal(tr("hub.item_share", "Ganz: %s %% des Ziels", share)).withStyle(ChatFormatting.GRAY));
                if (!estimate) tip.add(Component.literal(full ? tr("hub.item_full", "%s %%, voll", ip)
                        : tr("hub.item_missing", "%s %%, es fehlen noch %s Stück, %s Punkte", ip, Ui.number(it.need() - it.have()), Ui.number(it.missingPoints())))
                        .withStyle(full ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                tip.add(Component.literal(it.id()).withStyle(ChatFormatting.DARK_GRAY));
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
            g.fill(x1 - 4, listTop, x1 - 2, listBottom, Ui.SHADOW);
            g.fill(x1 - 4, thumbY, x1 - 2, thumbY + thumbH, Ui.GOLD_DARK);
        }
    }

    // ---- ranking -------------------------------------------------------------------------

    /** The place as a small plate: gold, silver, bronze, then plain. */
    private void medal(GuiGraphics g, int place, int x, int y) {
        int colour = place == 1 ? Ui.GOLD : place == 2 ? 0xFFc9c6d4 : place == 3 ? 0xFFc07a48 : Ui.BORDER;
        g.fill(x, y - 1, x + 13, y + 11, Ui.SHADOW);
        g.fill(x + 1, y, x + 12, y + 10, place <= 3 ? Ui.darken(colour, 60) : Ui.RAISED);
        if (place <= 3) g.fill(x + 1, y, x + 12, y + 1, colour);
        String s = Integer.toString(place);
        g.drawString(font, s, x + 7 - font.width(s) / 2, y + 1, place <= 3 ? Ui.SHADOW : Ui.MUTED, false);
    }

    private void drawRanks(GuiGraphics g, int mouseX, int mouseY) {
        KwNetwork.GoalView run = running();
        var m = data.mine();
        var board = m.board();
        int x0 = left + 10, lx1 = x0 + LEFT_W - 6;
        // left: the player's own part
        g.drawString(font, tr("hub.mine", "Deine Gaben"), x0, cTop + 2, Ui.MUTED, false);
        Ui.inset(g, x0, cTop + 14, lx1, cTop + 62);
        String me = minecraft.getUser().getName();
        Heads.draw(g, me, x0 + 8, cTop + 22, 16);
        g.drawString(font, Ui.fit(font, me, lx1 - x0 - 40), x0 + 30, cTop + 21, Ui.TEXT, false);
        String pts = m.points() <= 0 ? tr("hub.mine_none", "Noch nichts gegeben") : tr("hub.points", "%s Punkte", Ui.number(m.points()));
        g.drawString(font, Ui.fit(font, pts, lx1 - x0 - 40), x0 + 30, cTop + 31, m.points() > 0 ? Ui.GOLD_LIGHT : Ui.DIM, false);
        String place = m.points() > 0 ? tr("hub.place", "Platz %s von %s", m.rank(), m.hands()) : "";
        String streak = m.streak() > 0 ? tr("hub.streak_short", "%s Tage in Folge", m.streak()) : "";
        g.drawString(font, Ui.fit(font, place.isEmpty() ? streak : streak.isEmpty() ? place : place + ", " + streak, lx1 - x0 - 14), x0 + 8, cTop + 46, Ui.MUTED, false);
        if (in(mouseX, mouseY, x0, cTop + 14, lx1, cTop + 62)) {
            hover(tr("hub.mine_tip", "Punkte: Stück mal Gewicht, wie der Balken zählt"), tr("hub.mine_tip2", "Die Tagesgabe zählt, wenn du jeden Tag etwas abgibst"));
        }
        // the first of every pillar
        int py = cTop + 70;
        if (run != null && py + 12 < cBot) {
            g.drawString(font, tr("hub.champions", "Vorn in den Säulen"), x0, py, Ui.MUTED, false);
            py += 12;
            for (String c : board.champions()) {
                String[] p = c.split("\\|", 3);
                if (p.length < 3 || py + 24 > cBot - 2) continue;
                g.drawString(font, Ui.fit(font, pillarName(run, p[0]), lx1 - x0 - 4), x0 + 2, py, Ui.PURPLE, false);
                Heads.draw(g, p[1], x0 + 3, py + 11, 8);
                String cp = Ui.number(parse(p[2]));
                g.drawString(font, Ui.fit(font, p[1], lx1 - x0 - 24 - font.width(cp)), x0 + 16, py + 11, Ui.TEXT, false);
                g.drawString(font, cp, lx1 - 2 - font.width(cp), py + 11, Ui.GOLD, false);
                py += 26;
            }
            if (board.champions().isEmpty()) g.drawString(font, tr("hub.champions_none", "Noch ist jeder Platz frei."), x0 + 2, py, Ui.DIM, false);
        }
        // right: the ten most diligent hands
        int rx0 = left + 10 + LEFT_W, rx1 = left + w - 10;
        Ui.inset(g, rx0, cTop + 2, rx1, cBot - 4);
        List<String[]> rows = new ArrayList<>();
        String head;
        if (run != null) {
            head = tr("hub.ranks_running", "Die fleißigsten Hände: %s", title(run));
            for (String t : board.top()) {
                String[] p = t.split("\\|", 3);
                if (p.length == 3) rows.add(new String[]{p[1], p[2]});
            }
        } else {
            // nothing runs: the last stage that is done keeps its names
            KwNetwork.GoalView last = null;
            for (KwNetwork.GoalView gv : data.goals()) if (!gv.more().top().isEmpty()) last = gv;
            head = last == null ? tr("hub.ranks_none", "Gerade läuft kein Ziel.") : tr("hub.ranks_done", "Die fleißigsten Hände: %s (geschafft)", title(last));
            if (last != null) {
                for (String t : last.more().top()) {
                    int bar = t.lastIndexOf('|');
                    if (bar > 0) rows.add(new String[]{t.substring(0, bar), t.substring(bar + 1)});
                }
            }
        }
        g.drawString(font, Ui.fit(font, head, rx1 - rx0 - 16), rx0 + 8, cTop + 10, Ui.GOLD, false);
        Ui.ornament(g, rx0 + 8, cTop + 23, rx1 - 8);
        if (rows.isEmpty()) {
            g.drawString(font, Ui.fit(font, run != null ? tr("hub.ranks_empty", "Noch hat niemand etwas gegeben. Der erste Platz ist frei.") : "", rx1 - rx0 - 16), rx0 + 8, cTop + 32, Ui.MUTED, false);
            return;
        }
        long best = Math.max(1, parse(rows.get(0)[1]));
        int rowH = Math.max(13, Math.min(17, (cBot - 8 - (cTop + 28)) / Math.max(1, rows.size())));
        int y = cTop + 29;
        for (int i = 0; i < rows.size(); i++) {
            if (y + rowH > cBot - 5) break;
            String who = rows.get(i)[0];
            long points = parse(rows.get(i)[1]);
            boolean mine = who.equals(me);
            if (mine) g.fill(rx0 + 4, y - 1, rx1 - 4, y + rowH - 2, Ui.withAlpha(Ui.GOLD, 34));
            medal(g, i + 1, rx0 + 8, y + 1);
            Heads.draw(g, who, rx0 + 27, y + 1, 10);
            String ps = Ui.number(points);
            g.drawString(font, Ui.fit(font, who, 110), rx0 + 43, y + 2, mine ? Ui.GOLD_LIGHT : Ui.TEXT, false);
            g.drawString(font, ps, rx1 - 8 - font.width(ps), y + 2, i == 0 ? Ui.GOLD : Ui.MUTED, false);
            // how far behind the first: a thin gauge between name and points
            int bx0 = rx0 + 160, bx1 = rx1 - 62;
            if (bx1 - bx0 > 20) Ui.bar(g, bx0, y + 3, bx1, 6, (int) (points * 100 / best), i == 0 ? Ui.GOLD : Ui.GOLD_DARK, false);
            y += rowH;
        }
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- options -------------------------------------------------------------------------

    private void drawOptions(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left + 10, x1 = left + w - 10;
        Ui.inset(g, x0, cTop + 2, x1, cBot - 4);
        int rows = 6;
        int rowH = Math.max(15, Math.min(27, (cBot - 32 - (cTop + 6)) / rows));
        boolean roomy = rowH >= 23;
        int y = cTop + 6;
        boolean german = minecraft.options.languageCode.startsWith("de");
        // 1: the language, two plates
        optionText(g, y, rowH, roomy, 146, tr("hub.opt_lang", "Sprache"), tr("hub.opt_lang_tip", "Menüs, Hinweise, Quests. Wechselt sofort."));
        int px = x1 - 10 - 124, py = y + (rowH - 16) / 2;
        plate(g, px, py, 60, "Deutsch", german, mouseX, mouseY, () -> language("de_de"));
        plate(g, px + 64, py, 60, "English", !german, mouseX, mouseY, () -> language("en_us"));
        y += rowH;
        // 2: the goal's bar, kept by the server
        optionText(g, y, rowH, roomy, 46, tr("hub.opt_bar", "Leiste des Ziels"), tr("hub.opt_bar_tip", "Der Balken oben am Bildschirm, der zeigt, wie weit die Stufe ist."));
        lever(g, x1 - 34, y + (rowH - 12) / 2, data.mine().board().bar(), mouseX, mouseY, () -> send("bar"));
        y += rowH;
        // 3: softer light
        optionText(g, y, rowH, roomy, 46, tr("hub.opt_soft", "Sanftes Licht"), tr("hub.opt_soft_tip", "Weniger Blitze, Wackeln und Flackern beim Ritus des Obelisken."));
        lever(g, x1 - 34, y + (rowH - 12) / 2, KronwerkeClientConfig.fewerFlashes(), mouseX, mouseY, () -> {
            KronwerkeClientConfig.FEWER_FLASHES.set(!KronwerkeClientConfig.FEWER_FLASHES.get());
            KronwerkeClientConfig.SPEC.save();
        });
        y += rowH;
        // 4: the clouds
        int clouds = (int) KronwerkeClientConfig.cloudHeight();
        optionText(g, y, rowH, roomy, 100, tr("hub.opt_clouds", "Höhe der Wolken"), tr("hub.opt_clouds_tip", "Vanilla: 192. Hier wird bis 608 gebaut, darum 448."));
        int sy = y + (rowH - 16) / 2;
        plate(g, x1 - 10 - 16, sy, 16, "+", false, mouseX, mouseY, () -> clouds(clouds, 1));
        plate(g, x1 - 10 - 78, sy, 16, "-", false, mouseX, mouseY, () -> clouds(clouds, -1));
        String cs = Integer.toString(clouds);
        g.drawString(font, cs, x1 - 10 - 39 - font.width(cs) / 2, sy + 4, Ui.GOLD_LIGHT, false);
        y += rowH;
        // 5: the loading picture
        optionText(g, y, rowH, roomy, 46, tr("hub.opt_load", "Bewegtes Ladebild"), tr("hub.opt_load_tip", "Das Bild bewegt sich beim Betreten. Aus, falls das Spiel dabei hakt."));
        lever(g, x1 - 34, y + (rowH - 12) / 2, KronwerkeClientConfig.loadPictures(), mouseX, mouseY, () -> {
            KronwerkeClientConfig.LOAD_PICTURES.set(!KronwerkeClientConfig.LOAD_PICTURES.get());
            KronwerkeClientConfig.SPEC.save();
        });
        y += rowH;
        // 6: the music of the menus
        optionText(g, y, rowH, roomy, 46, tr("hub.opt_music", "Menümusik"), tr("hub.opt_music_tip", "Kronwerkes ruhige Musik im Menü und beim Verbinden."));
        lever(g, x1 - 34, y + (rowH - 12) / 2, KronwerkeClientConfig.menuMusic(), mouseX, mouseY, () -> {
            KronwerkeClientConfig.MENU_MUSIC.set(!KronwerkeClientConfig.MENU_MUSIC.get());
            KronwerkeClientConfig.SPEC.save();
        });
    }

    /** The language changes at once; the buttons are built again so their labels follow. */
    private void language(String code) {
        LanguageScreen.apply(code);
        rebuildWidgets();
    }

    private void clouds(int now, int dir) {
        int i = 0;
        for (int k = 0; k < CLOUDS.length; k++) if (Math.abs(CLOUDS[k] - now) < Math.abs(CLOUDS[i] - now)) i = k;
        i = Math.max(0, Math.min(CLOUDS.length - 1, i + dir));
        KronwerkeClientConfig.CLOUD_HEIGHT.set(CLOUDS[i]);
        KronwerkeClientConfig.SPEC.save();
    }

    /** The name of an option and, where the rows are high enough, a line about it; reserve is the width of its control on the right. */
    private void optionText(GuiGraphics g, int y, int rowH, boolean roomy, int reserve, String title, String tip) {
        int x0 = left + 18, x1 = left + w - 10;
        if (roomy) {
            g.drawString(font, title, x0, y + (rowH - 19) / 2, Ui.TEXT, false);
            g.drawString(font, Ui.fit(font, tip, x1 - reserve - x0), x0, y + (rowH - 19) / 2 + 10, Ui.MUTED, false);
        } else {
            g.drawString(font, title, x0, y + (rowH - 8) / 2, Ui.TEXT, false);
        }
        g.fill(x0 - 2, y + rowH - 1, x1 - 6, y + rowH, Ui.RAISED);
    }

    private void lever(GuiGraphics g, int x, int y, boolean on, int mouseX, int mouseY, Runnable run) {
        Ui.lever(g, x, y, on);
        if (in(mouseX, mouseY, x - 2, y - 2, x + 26, y + 14)) hover(on ? tr("hub.on", "An") : tr("hub.off", "Aus"));
        hits.add(new Hit(x - 2, y - 2, x + 26, y + 14, run));
    }

    /** A small plate to click, framed in brass while it is the chosen one. */
    private void plate(GuiGraphics g, int x, int y, int width, String label, boolean chosen, int mouseX, int mouseY, Runnable run) {
        boolean hover = in(mouseX, mouseY, x, y, x + width, y + 16);
        g.blitSprite(chosen ? Ui.TAB_ACTIVE : Ui.TAB, x, y, width, 16);
        g.drawString(font, label, x + (width - font.width(label)) / 2 + (width % 2), y + 4, chosen ? Ui.GOLD_LIGHT : hover ? Ui.TEXT : Ui.MUTED, false);
        if (!chosen) hits.add(new Hit(x, y, x + width, y + 16, run));
    }

    // ---- help ----------------------------------------------------------------------------

    private void drawHelp(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left + 10, x1 = left + w - 10;
        Ui.inset(g, x0, cTop + 2, x1, cBot - 4);
        int y = cTop + 7;
        g.drawString(font, tr("hub.help_give", "So gibst du ab"), x0 + 8, y, Ui.GOLD, false);
        y += 11;
        String[] how = {tr("hub.help_give1", "Rechtsklick auf den Obelisken am Spawn, mit dem Stapel in der Hand. Schleichen gibt alles Passende aus dem Inventar."),
                tr("hub.help_give2", "Nur von Hand: kein Befehl, kein Menüknopf und keine Maschine gibt für dich ab."),
                tr("hub.help_give3", "/kw undo holt eine Gabe zehn Sekunden lang zurück.")};
        for (String s : how) {
            g.fill(x0 + 10, y + 3, x0 + 12, y + 5, Ui.GOLD_DARK);
            for (var line : font.split(Component.literal(s), x1 - x0 - 30)) {
                if (y + 10 > cBot - 6) return;
                g.drawString(font, line, x0 + 17, y, Ui.TEXT, false);
                y += 10;
            }
            y += 1;
        }
        y += 3;
        Ui.ornament(g, x0 + 8, y, x1 - 8);
        y += 7;
        if (y + 12 > cBot - 6) return;
        g.drawString(font, tr("hub.help_cmds", "Befehle"), x0 + 8, y, Ui.GOLD, false);
        String note = Ui.fit(font, tr("help.click", "Klicken, um den Befehl in den Chat zu setzen"), x1 - x0 - 90);
        g.drawString(font, note, x1 - 8 - font.width(note), y, Ui.DIM, false);
        y += 12;
        List<String[]> cmds = new ArrayList<>();
        cmds.add(new String[]{"/kw goals", tr("help.goals", "Was der Obelisk gerade braucht und wie weit wir sind")});
        cmds.add(new String[]{"/kw top", tr("help.top", "Die fleißigsten Hände beim laufenden Ziel")});
        cmds.add(new String[]{"/kw undo", tr("help.undo", "Gibt die Gabe der letzten zehn Sekunden zurück")});
        cmds.add(new String[]{"/kw bar", tr("help.bar", "Blendet die Leiste des Ziels aus oder wieder ein")});
        cmds.add(new String[]{"/kw ignore ", tr("help.ignore", "Blendet den Chat eines Spielers aus, auf allen Servern")});
        if (data.streamer()) cmds.add(new String[]{"/kw menu", tr("help.menu", "Deine Whitelist: Plätze, Köpfe, Einladen und Entfernen")});
        cmds.add(new String[]{"/kw help", tr("hub.help_all", "Alle Befehle im Chat")});
        int step = cBot - 5 - y >= cmds.size() * 13 ? 13 : 11;
        for (String[] c : cmds) {
            if (y + 10 > cBot - 5) break;
            boolean hover = in(mouseX, mouseY, x0 + 4, y - 1, x1 - 4, y + step - 1);
            if (hover) g.fill(x0 + 4, y - 1, x1 - 4, y + step - 1, 0x18FFFFFF);
            String shown = c[0].endsWith(" ") ? c[0] + "<Name>" : c[0];
            g.drawString(font, shown, x0 + 10, y, hover ? Ui.GOLD_LIGHT : Ui.GOLD, false);
            g.drawString(font, Ui.fit(font, c[1], x1 - x0 - 124), x0 + 112, y, Ui.MUTED, false);
            hits.add(new Hit(x0 + 4, y - 1, x1 - 4, y + step - 1, () -> minecraft.setScreen(new ChatScreen(c[0]))));
            y += step;
        }
    }
}
