package de.kronwerke.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.kronwerke.core.Text;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.Goal;
import de.kronwerke.core.goal.GoalData;
import de.kronwerke.core.goal.GoalManager;
import de.kronwerke.core.slot.SlotData;
import de.kronwerke.core.slot.SlotManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * /kw ...
 *   invite <player>            streamer: use one of your slots
 *   revoke <player>            streamer: free a slot again
 *   slots                      streamer: show your slots
 *   deposit [all]              everyone: deposit held item (or whole inventory) into the active goal
 *   goals                      everyone: list goals and progress
 *   top [goal]                 everyone: top contributors
 *   admin slots <streamer> <n> op: set a streamer's base slots
 *   admin bonus <streamer> <n> op: add bonus slots (negative to remove)
 *   admin list                 op: all streamers and their invites
 *   admin goal reload|complete|reset|open|rescale|progress ...
 *   admin active            op: how many players count as active for scaling
 *   admin obelisk ...       op: where the obelisk is and its feeders, see ObeliskCommand
 *   admin invite|revoke|grant|ungrant|goals json   for the Discord bot, see BotCommand
 */
public final class KwCommand {

    /** On a side world: /kw only says where things are; season, goals and slots live on main. */
    public static void registerSide(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kw").executes(c -> {
            c.getSource().sendSuccess(() -> de.kronwerke.core.Text.t("network.side", "Du bist in %s. Season, Ziele und Plätze gibt es in der Hauptwelt.",
                    de.kronwerke.core.link.Role.server()).withStyle(net.minecraft.ChatFormatting.GRAY), false);
            return 1;
        }));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kw")
                .executes(KwCommand::hub)
                .then(Commands.literal("help").executes(KwCommand::help))
                .then(Commands.literal("team").requires(s -> s.hasPermission(2)).executes(KwCommand::team))
                .then(Commands.literal("testworld").requires(s -> s.hasPermission(2)).executes(KwCommand::testworld)
                        .then(Commands.literal("back").executes(KwCommand::testworldBack))
                        .then(Commands.literal("rebuild").executes(KwCommand::testworldRebuild)))
                .then(Commands.literal("menu").executes(KwCommand::menu))
                .then(Commands.literal("whitelist").executes(KwCommand::menu))
                .then(Commands.literal("invite")
                        .requires(s -> KronwerkeConfig.STREAMERS_MANAGE_OWN_SLOTS.get() || s.hasPermission(2))
                        .executes(KwCommand::menu)
                        .then(Commands.argument("player", StringArgumentType.word()).executes(KwCommand::invite)))
                .then(Commands.literal("revoke")
                        .requires(s -> KronwerkeConfig.STREAMERS_MANAGE_OWN_SLOTS.get() || s.hasPermission(2))
                        .executes(KwCommand::menu)
                        .then(Commands.argument("player", StringArgumentType.word()).executes(KwCommand::revoke)))
                .then(Commands.literal("slots").executes(KwCommand::slots))
                .then(Commands.literal("deposit")
                        .executes(c -> deposit(c, false))
                        .then(Commands.literal("all").executes(c -> deposit(c, true))))
                .then(Commands.literal("goals").executes(KwCommand::goals))
                .then(Commands.literal("top")
                        .executes(c -> top(c, null))
                        .then(Commands.argument("goal", StringArgumentType.word()).executes(c -> top(c, StringArgumentType.getString(c, "goal")))))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .executes(KwCommand::adminPanel)
                        .then(Commands.literal("season")
                                .executes(KwCommand::seasonInfo)
                                .then(Commands.literal("start").executes(KwCommand::seasonStart))
                                .then(Commands.literal("pause").executes(c -> { de.kronwerke.core.season.Season.get().pause(); ok(c, "Zurück in der Vorbereitung."); return 1; }))
                                .then(Commands.literal("json").executes(KwCommand::seasonJson)))
                        .then(Commands.literal("start-season").executes(KwCommand::seasonStart))
                        .then(Commands.literal("slots")
                                .then(Commands.argument("streamer", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(KwCommand::adminSlots))))
                        .then(Commands.literal("bonus")
                                .then(Commands.argument("streamer", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(KwCommand::adminBonus))))
                        .then(Commands.literal("list").executes(KwCommand::adminList))
                        .then(BotCommand.invite())
                        .then(BotCommand.revoke())
                        .then(BotCommand.grant())
                        .then(BotCommand.ungrant())
                        .then(BotCommand.goals())
                        .then(BotCommand.roster())
                        .then(Commands.literal("goal")
                                .then(Commands.literal("reload").executes(c -> { GoalManager.get().reload(); ok(c, "Goals reloaded: " + GoalManager.get().goalCount()); return 1; }))
                                .then(Commands.literal("complete").then(Commands.argument("goal", StringArgumentType.word()).executes(c -> goalOp(c, "complete"))))
                                .then(Commands.literal("reset").then(Commands.argument("goal", StringArgumentType.word()).executes(c -> goalOp(c, "reset"))))
                                .then(Commands.literal("open").then(Commands.argument("goal", StringArgumentType.word()).executes(c -> goalOp(c, "open"))))
                                .then(Commands.literal("rescale").then(Commands.argument("goal", StringArgumentType.word()).executes(c -> goalOp(c, "rescale"))))
                                .then(Commands.literal("progress").then(Commands.argument("goal", StringArgumentType.word())
                                        .then(Commands.argument("item", StringArgumentType.string())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(c -> goalOp(c, "progress")))))))
                        .then(Commands.literal("active").executes(c -> { ok(c, "Active players (scaling window): " + GoalManager.get().activePlayers()); return 1; }))
                        .then(ObeliskCommand.build()))
                .then(TestCommand.build()));
    }

    // ---- help and menu ----

    private static int help(CommandContext<CommandSourceStack> c) {
        boolean streamer = c.getSource().hasPermission(2) || KronwerkeConfig.STREAMERS_MANAGE_OWN_SLOTS.get();
        c.getSource().sendSuccess(() -> Component.literal("Kronwerke").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        line(c, "/kw goals", "Was der Obelisk gerade braucht und wie weit wir sind");
        line(c, "/kw deposit", "Gibt den Stapel in der Hand am Obelisken ab, /kw deposit all das ganze Inventar");
        line(c, "/kw top", "Die fleißigsten Hände beim laufenden Ziel");
        if (streamer) {
            line(c, "/kw menu", "Deine Whitelist: Plätze, Köpfe, Einladen und Entfernen");
            line(c, "/kw invite <Name>", "Lädt jemanden auf einen deiner Plätze ein");
            line(c, "/kw revoke <Name>", "Nimmt den Platz wieder weg");
        }
        if (c.getSource().hasPermission(2)) {
            line(c, "/kw team", "Plätze aller Streamer, Spieler verschieben oder rauswerfen");
            line(c, "/kw testworld", "Die Screenshot-Welt mit den grauen Boxen, /kw testworld back zum Spawn");
            line(c, "/kw admin", "Das Admin-Menü: Season, Ziele, Team, Obelisk, Spieler");
            line(c, "/kw admin season start", "Setzt alle Ziele zurück und startet die Season");
        }
        return 1;
    }

    private static void line(CommandContext<CommandSourceStack> c, String cmd, String what) {
        c.getSource().sendSuccess(() -> Component.literal(" " + cmd).withStyle(ChatFormatting.YELLOW)
                .withStyle(st -> st.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, cmd.replace(" <Name>", " ").replace(" ...", " ")))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Klicken, um den Befehl in den Chat zu setzen"))))
                .append(Component.literal("  " + what).withStyle(ChatFormatting.GRAY)), false);
    }

    /** /kw without anything: the menu for players, the help for consoles. */
    private static int hub(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return help(c);
        de.kronwerke.core.net.KwNetwork.sendHub(p, "", false);
        return 1;
    }

    /** The screenshot world: a void dimension with one grey box per scene, built on the first visit. */
    private static int testworld(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var server = c.getSource().getServer();
        var level = de.kronwerke.core.world.TestWorld.level(server);
        if (level == null) {
            fail(c, "Die Testwelt kronwerke:testworld gibt es nicht. Das Pack bringt sie mit (kubejs/data/kronwerke/dimension).");
            return 0;
        }
        if (!de.kronwerke.core.world.TestWorld.built(level) && !de.kronwerke.core.world.TestWorld.busy()) {
            if (startBuild(c, false) < 0) return 0;
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "execute in kronwerke:testworld run tp " + p.getGameProfile().getName() + " -3.5 64 -3.5 135 20");
        if (!p.isCreative()) p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        ok(c, "Testwelt. /kw testworld back bringt dich zum Spawn, /kw testworld rebuild baut alles neu.");
        return 1;
    }

    private static int testworldRebuild(CommandContext<CommandSourceStack> c) {
        if (de.kronwerke.core.world.TestWorld.level(c.getSource().getServer()) == null) {
            fail(c, "Die Testwelt kronwerke:testworld gibt es nicht.");
            return 0;
        }
        return startBuild(c, true) < 0 ? 0 : 1;
    }

    private static int startBuild(CommandContext<CommandSourceStack> c, boolean clearMobs) {
        var src = c.getSource();
        int rows = de.kronwerke.core.world.TestWorld.build(src.getServer(), clearMobs,
                msg -> src.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GRAY), false));
        if (rows < 0) {
            fail(c, "kronwerke:shots/rows.json fehlt. Das Pack erzeugt es mit tools/shots/build.py.");
            return -1;
        }
        ok(c, "Die Szenen werden gebaut, eine Reihe pro Tick (" + rows + " Reihen).");
        return rows;
    }

    private static int testworldBack(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var s = c.getSource().getServer().overworld().getSharedSpawnPos();
        c.getSource().getServer().getCommands().performPrefixedCommand(c.getSource().getServer().createCommandSourceStack().withSuppressedOutput(),
                "execute in minecraft:overworld run tp " + p.getGameProfile().getName() + " " + (s.getX() + 0.5) + " " + s.getY() + " " + (s.getZ() + 0.5));
        return 1;
    }

    private static int team(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        de.kronwerke.core.net.KwNetwork.sendAdmin(p, 2, "", false);
        return 1;
    }

    /** /kw admin: the panel for players, the list of admin commands for the console. */
    private static int adminPanel(CommandContext<CommandSourceStack> c) {
        if (c.getSource().getEntity() instanceof ServerPlayer p) {
            de.kronwerke.core.net.KwNetwork.sendAdmin(p, 0, "", false);
            return 1;
        }
        return help(c);
    }

    private static int seasonInfo(CommandContext<CommandSourceStack> c) {
        var s = de.kronwerke.core.season.Season.get();
        ok(c, s.running() ? "Season " + s.number() + " läuft." : "Vorbereitung. /kw admin season start setzt alle Ziele zurück und startet die Season.");
        return 1;
    }

    private static int seasonStart(CommandContext<CommandSourceStack> c) {
        de.kronwerke.core.season.Season.get().start();
        ok(c, "Season " + de.kronwerke.core.season.Season.get().number() + " läuft. Alle Ziele, Ranglisten und Startpakete sind zurückgesetzt.");
        return 1;
    }

    /** For the bot: the season in one line of JSON. */
    private static int seasonJson(CommandContext<CommandSourceStack> c) {
        var s = de.kronwerke.core.season.Season.get();
        var ob = de.kronwerke.core.obelisk.Obelisk.get();
        c.getSource().sendSuccess(() -> Component.literal("OK {\"running\":" + s.running() + ",\"number\":" + s.number() + ",\"startedAt\":" + s.startedAt()
                + ",\"tier\":" + ob.tier() + ",\"slumbering\":" + ob.slumbering() + "}"), false);
        return 1;
    }

    private static int menu(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        de.kronwerke.core.net.KwNetwork.send(p, true, "", false);
        return 1;
    }

    // ---- streamer slot commands ----

    private static int invite(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        SlotData.StreamerEntry me = SlotManager.get().entry(p.getUUID(), p.getGameProfile().getName());
        String target = StringArgumentType.getString(c, "player");
        SlotManager.Result r = SlotManager.get().invite(me, target);
        switch (r) {
            case OK -> ok(c, target + " steht auf der Whitelist. Plätze: " + me.used() + "/" + SlotManager.get().allowance(me));
            case NO_SLOTS_LEFT -> fail(c, "Du hast keinen freien Platz mehr (" + me.used() + "/" + SlotManager.get().allowance(me) + ").");
            case ALREADY_INVITED -> fail(c, target + " hat schon einen Platz.");
            case ALREADY_WHITELISTED -> fail(c, target + " ist schon auf der Whitelist.");
            case UNKNOWN_PLAYER -> fail(c, "Kein Minecraft-Konto mit dem Namen " + target + ". Schreibweise prüfen.");
            default -> fail(c, "Das hat nicht geklappt.");
        }
        return r == SlotManager.Result.OK ? 1 : 0;
    }

    private static int revoke(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        SlotData.StreamerEntry me = SlotManager.get().entry(p.getUUID(), p.getGameProfile().getName());
        String target = StringArgumentType.getString(c, "player");
        SlotManager.Result r = SlotManager.get().revoke(me, target);
        switch (r) {
            case OK -> ok(c, target + " ist von der Whitelist runter. Plätze: " + me.used() + "/" + SlotManager.get().allowance(me));
            case NOT_INVITED_BY_YOU -> fail(c, target + " hat seinen Platz nicht von dir.");
            case UNKNOWN_PLAYER -> fail(c, "Kein Minecraft-Konto mit dem Namen " + target + ".");
            default -> fail(c, "Das hat nicht geklappt.");
        }
        return r == SlotManager.Result.OK ? 1 : 0;
    }

    private static int slots(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        SlotData.StreamerEntry me = SlotManager.get().entry(p.getUUID(), p.getGameProfile().getName());
        c.getSource().sendSuccess(() -> Component.literal("Deine Plätze: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(me.used() + "/" + SlotManager.get().allowance(me)).withStyle(ChatFormatting.WHITE)), false);
        for (String n : me.invited.values()) {
            c.getSource().sendSuccess(() -> Component.literal("  - " + n).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    // ---- goals ----

    private static int deposit(CommandContext<CommandSourceStack> c, boolean all) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        long taken;
        if (all) {
            taken = GoalManager.get().depositAll(p);
        } else {
            ItemStack hand = p.getMainHandItem();
            taken = GoalManager.get().deposit(p, hand);
        }
        if (taken == 0) {
            fail(c, "Nichts in deiner Hand passt zum aktuellen Ziel. /kw goals zeigt, was gebraucht wird.");
            return 0;
        }
        ok(c, "Abgegeben: " + Text.number(taken) + ".");
        return 1;
    }

    private static int goals(CommandContext<CommandSourceStack> c) {
        GoalManager gm = GoalManager.get();
        GoalData d = gm.progressData();
        c.getSource().sendSuccess(() -> Text.t("goals.title", "Gemeinschaftsziele").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Goal g : gm.allGoals()) {
            boolean done = d.isCompleted(g.id());
            boolean active = gm.isActive(g);
            ChatFormatting color = done ? ChatFormatting.GREEN : active ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY;
            String state = done ? "geschafft" : active ? (gm.isHeld(g) ? "wartet auf das Event" : Math.round(gm.fraction(g) * 100) + "%") : "gesperrt";
            c.getSource().sendSuccess(() -> Component.literal(" " + g.title() + " ").withStyle(color)
                    .append(Component.literal("[" + state + "]").withStyle(ChatFormatting.GRAY)), false);
            if (!active) continue;
            if (!g.description().isEmpty()) {
                c.getSource().sendSuccess(() -> Component.literal("   " + g.description()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            }
            for (Goal.Pillar p : g.pillars()) {
                boolean pd = gm.pillarDone(g, p);
                c.getSource().sendSuccess(() -> Component.literal("   " + p.title()).withStyle(pd ? ChatFormatting.GREEN : ChatFormatting.AQUA), false);
                for (Goal.PillarItem it : p.items()) {
                    long have = d.progress(g.id(), it.item()), need = d.target(g.id(), it.item());
                    c.getSource().sendSuccess(() -> Component.literal("     ").append(Text.item(it.item()).withStyle(have >= need ? ChatFormatting.GREEN : ChatFormatting.AQUA))
                            .append(Component.literal("  " + Text.number(have) + " / " + Text.number(need)).withStyle(have >= need ? ChatFormatting.GREEN : ChatFormatting.WHITE)), false);
                }
            }
        }
        return 1;
    }

    private static int top(CommandContext<CommandSourceStack> c, String goalId) {
        GoalManager gm = GoalManager.get();
        Goal g = goalId == null ? (gm.activeGoals().isEmpty() ? null : gm.activeGoals().get(0)) : gm.goal(goalId);
        if (g == null) {
            fail(c, "Dieses Ziel gibt es nicht.");
            return 0;
        }
        c.getSource().sendSuccess(() -> Text.t("goals.top", "Die fleißigsten Hände: %s", g.title()).withStyle(ChatFormatting.GOLD), false);
        int rank = 1;
        for (Map.Entry<UUID, Long> e : gm.leaderboard(g, 10)) {
            String name = c.getSource().getServer().getProfileCache() == null ? e.getKey().toString()
                    : c.getSource().getServer().getProfileCache().get(e.getKey()).map(pr -> pr.getName()).orElse(e.getKey().toString());
            int r = rank++;
            c.getSource().sendSuccess(() -> Component.literal(" " + r + ". " + name + "  " + e.getValue()).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    // ---- admin ----

    private static SlotData.StreamerEntry streamerArg(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "streamer");
        SlotData.StreamerEntry e = SlotManager.get().entryByName(name);
        if (e == null) {
            var profile = SlotManager.get().lookup(name);
            if (profile.isPresent()) e = SlotManager.get().entry(profile.get().getId(), profile.get().getName());
        }
        if (e == null) fail(c, "Unknown player " + name);
        return e;
    }

    private static int adminSlots(CommandContext<CommandSourceStack> c) {
        SlotData.StreamerEntry e = streamerArg(c);
        if (e == null) return 0;
        SlotManager.get().setSlots(e, IntegerArgumentType.getInteger(c, "amount"));
        ok(c, e.name + " now has " + SlotManager.get().allowance(e) + " slots.");
        return 1;
    }

    private static int adminBonus(CommandContext<CommandSourceStack> c) {
        SlotData.StreamerEntry e = streamerArg(c);
        if (e == null) return 0;
        SlotManager.get().addBonus(e, IntegerArgumentType.getInteger(c, "amount"));
        ok(c, e.name + " now has " + SlotManager.get().allowance(e) + " slots (" + e.bonusSlots + " bonus).");
        return 1;
    }

    private static int adminList(CommandContext<CommandSourceStack> c) {
        for (SlotData.StreamerEntry e : SlotManager.get().allStreamers()) {
            String tag = e.granted ? " (own place)" : "";
            c.getSource().sendSuccess(() -> Component.literal(e.name + tag + "  " + e.used() + "/" + SlotManager.get().allowance(e)).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("  " + String.join(", ", e.invited.values())).withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int goalOp(CommandContext<CommandSourceStack> c, String op) {
        GoalManager gm = GoalManager.get();
        Goal g = gm.goal(StringArgumentType.getString(c, "goal"));
        if (g == null) {
            fail(c, "Unknown goal.");
            return 0;
        }
        switch (op) {
            case "complete" -> gm.complete(g);
            case "reset" -> gm.reset(g);
            case "open" -> gm.release(g);
            case "rescale" -> {
                double f = gm.activate(g);
                ok(c, "Goal " + g.id() + " rescaled with factor " + String.format("%.2f", f) + ".");
                return 1;
            }
            case "progress" -> {
                gm.progressData().setProgress(g.id(), StringArgumentType.getString(c, "item"), IntegerArgumentType.getInteger(c, "amount"));
                gm.check(g);
            }
        }
        ok(c, "Goal " + g.id() + ": " + op + " applied.");
        return 1;
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
    }

    private static void fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg).withStyle(ChatFormatting.RED));
    }
}
