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

import java.util.List;
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
 *   share                   op: the shared network between the servers, see share/ShareCommand
 *   move <players>          op: to the portal's other side without the portal, see portal/MoveCommand
 */
public final class KwCommand {

    /** On a side world: /kw only says where things are; season, goals and slots live on main. */
    public static void registerSide(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kw").executes(c -> {
            c.getSource().sendSuccess(() -> de.kronwerke.core.Text.t("network.side", "Du bist in %s. Season, Ziele und Plätze gibt es in der Hauptwelt.",
                    de.kronwerke.core.link.NetworkSync.ownLabel()).withStyle(net.minecraft.ChatFormatting.GRAY), false);
            return 1;
        }).then(Commands.literal("admin").requires(s -> s.hasPermission(2)).then(loadtest())));
    }

    /** The load test, on main and on every side world (bots in the side world's own dimension). */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> loadtest() {
        // only on a test server (testing.testCommands, for a live test in overrides.properties): the
        // bots run every join handler and generate terrain in the world they are started in
        return Commands.literal("loadtest").requires(s -> s.hasPermission(2) && KronwerkeConfig.TEST_COMMANDS.get())
                .executes(KwCommand::loadtestInfo)
                .then(Commands.literal("stop").executes(c -> { ok(c, de.kronwerke.core.loadtest.LoadTest.stop(c.getSource().getServer()) + " Testspieler entfernt."); return 1; }))
                .then(Commands.literal("start")
                        .then(Commands.argument("settlers", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                .then(Commands.argument("travellers", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                        .executes(c -> loadtestStart(c, 3000, 1.5))
                                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(64, 20000))
                                                .executes(c -> loadtestStart(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"), 1.5))
                                                .then(Commands.argument("speed", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.05, 10))
                                                        .executes(c -> loadtestStart(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"),
                                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "speed"))))))));
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
                .then(Commands.literal("ignore").then(Commands.argument("player", StringArgumentType.word()).executes(c -> {
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    String name = StringArgumentType.getString(c, "player");
                    // players of the whole network: here, or in the tab list from another server
                    java.util.UUID id = null;
                    ServerPlayer here = c.getSource().getServer().getPlayerList().getPlayerByName(name);
                    if (here != null) id = here.getUUID();
                    if (id == null) id = de.kronwerke.core.link.NetworkSync.remoteId(name);
                    if (id == null) {
                        fail(c, name + " ist gerade nicht online.");
                        return 0;
                    }
                    boolean on = de.kronwerke.core.link.NetworkSync.toggleIgnore(me, id, name);
                    ok(c, on ? "Du siehst den Chat von " + name + " nicht mehr, auf keinem Server. Nochmal /kw ignore " + name + " hebt das auf."
                            : "Du siehst den Chat von " + name + " wieder.");
                    return 1;
                })))
                .then(Commands.literal("bar").executes(c -> {
                    boolean shown = GoalManager.get().toggleBar(c.getSource().getPlayerOrException());
                    ok(c, shown ? "Die Leiste des Ziels ist wieder da." : "Die Leiste des Ziels ist ausgeblendet. /kw bar holt sie zurück.");
                    return 1;
                }))
                .then(Commands.literal("undo").executes(c -> {
                    long back = GoalManager.get().undo(c.getSource().getPlayerOrException());
                    if (back > 0) ok(c, "Zurückgegeben: " + Text.number(back) + ".");
                    else fail(c, "Nichts zum Zurückgeben: nur Gaben der letzten zehn Sekunden, und nicht, wenn das Ziel damit voll oder angehalten ist.");
                    return back > 0 ? 1 : 0;
                }))
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
                                .then(Commands.literal("start").executes(c -> seasonStart(c, false))
                                        .then(Commands.literal("confirm").executes(c -> seasonStart(c, true))))
                                .then(Commands.literal("pause").executes(c -> { de.kronwerke.core.season.Season.get().pause(); ok(c, "Zurück in der Vorbereitung."); return 1; }))
                                .then(Commands.literal("json").executes(KwCommand::seasonJson)))
                        .then(Commands.literal("gate")
                                .executes(KwCommand::gateInfo)
                                .then(Commands.literal("on").executes(c -> gateSet(c, true)))
                                .then(Commands.literal("off").executes(c -> gateSet(c, false)))
                                .then(Commands.literal("beta")
                                        .then(Commands.literal("on").executes(c -> gateBeta(c, true)))
                                        .then(Commands.literal("off").executes(c -> gateBeta(c, false))))
                                .then(Commands.literal("allow").then(Commands.argument("player", StringArgumentType.word()).executes(c -> gateAllow(c, true))))
                                .then(Commands.literal("deny").then(Commands.argument("player", StringArgumentType.word()).executes(c -> gateAllow(c, false)))))
                        .then(loadtest())
                        .then(Commands.literal("dumprecipes").requires(src -> src.hasPermission(4)).executes(c -> {
                            // every recipe as the server has it (jars, KubeJS, removals), for the pack's tools/check_progression.py
                            var server = c.getSource().getServer();
                            var ops = server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                            com.google.gson.JsonArray list = new com.google.gson.JsonArray();
                            int failed = 0;
                            for (var holder : server.getRecipeManager().getRecipes()) {
                                var enc = net.minecraft.world.item.crafting.Recipe.CODEC.encodeStart(ops, holder.value());
                                if (enc.result().isEmpty()) {
                                    failed++;
                                    continue;
                                }
                                com.google.gson.JsonObject e = new com.google.gson.JsonObject();
                                e.addProperty("id", holder.id().toString());
                                e.add("json", enc.result().get());
                                try {
                                    // the result a machine shows and makes first; the machine stage locks go by it
                                    var res = holder.value().getResultItem(server.registryAccess());
                                    if (!res.isEmpty()) e.addProperty("result", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(res.getItem()).toString());
                                } catch (RuntimeException ignored) {
                                    // some recipes only know their result with an input
                                }
                                list.add(e);
                            }
                            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
                            root.add("recipes", list);
                            java.nio.file.Path out = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("kronwerke-dump").resolve("recipes.json");
                            try {
                                java.nio.file.Files.createDirectories(out.getParent());
                                java.nio.file.Files.writeString(out, root.toString());
                            } catch (java.io.IOException ex) {
                                c.getSource().sendFailure(net.minecraft.network.chat.Component.literal(ex.toString()));
                                return 0;
                            }
                            int n = list.size(), f = failed;
                            c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(n + " recipes written to " + out + ", " + f + " could not be written"), false);
                            return n;
                        }))
                        .then(Commands.literal("locks")
                                .executes(c -> {
                                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            de.kronwerke.core.lock.MachineLocks.on()
                                                    ? "Stufensperre für Maschinen an: Stufe " + de.kronwerke.core.lock.MachineLocks.appliedStage() + " offen, " + de.kronwerke.core.lock.MachineLocks.heldCount() + " Rezepte zurückgehalten"
                                                    : "Stufensperre für Maschinen aus"), false);
                                    return 1;
                                })
                                .then(Commands.literal("on").executes(c -> setLocks(c, true)))
                                .then(Commands.literal("off").executes(c -> setLocks(c, false))))
                        .then(Commands.literal("queue")
                                .executes(c -> {
                                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(de.kronwerke.core.queue.JoinQueue.status()), false);
                                    return 1;
                                })
                                .then(Commands.literal("limit").then(Commands.argument("players", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 999))
                                        .executes(c -> {
                                            int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "players");
                                            de.kronwerke.core.queue.JoinQueue.setLimit(n);
                                            c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Spielerlimit ist jetzt " + n + ". " + de.kronwerke.core.queue.JoinQueue.status()), true);
                                            return 1;
                                        }))))
                        .then(Commands.literal("tabs").executes(c -> {
                            // times how long each creative tab takes to fill, to find what freezes the creative menu
                            var params = new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                                    c.getSource().getServer().getWorldData().enabledFeatures(), true, c.getSource().getServer().registryAccess());
                            java.util.List<String> lines = new java.util.ArrayList<>();
                            long total = 0;
                            for (var e : net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
                                long t0 = System.nanoTime();
                                try {
                                    e.getValue().buildContents(params);
                                } catch (RuntimeException ex) {
                                    lines.add("FAIL " + e.getKey().location() + " " + ex);
                                    continue;
                                }
                                long ms = (System.nanoTime() - t0) / 1_000_000;
                                total += ms;
                                lines.add(String.format(java.util.Locale.ROOT, "%6d ms %6d items  %s", ms, e.getValue().getDisplayItems().size(), e.getKey().location()));
                            }
                            lines.sort(java.util.Comparator.reverseOrder());
                            de.kronwerke.core.KronwerkeCore.LOGGER.info("Creative tabs, {} ms in total:\n{}", total, String.join("\n", lines));
                            ok(c, "Creative tabs: " + total + " ms in total, slowest:\n" + String.join("\n", lines.subList(0, Math.min(12, lines.size()))));
                            return 1;
                        }))
                        .then(Commands.literal("dirty")
                                .then(Commands.literal("start").executes(c -> { de.kronwerke.core.perf.DirtyChunks.start(); ok(c, "Zähle, wer Chunks als geändert markiert."); return 1; }))
                                .then(Commands.literal("stop").executes(c -> { de.kronwerke.core.perf.DirtyChunks.stop(); ok(c, "Zählen gestoppt."); return 1; }))
                                .then(Commands.literal("show").executes(c -> {
                                    java.util.List<String> top = de.kronwerke.core.perf.DirtyChunks.top(25);
                                    ok(c, top.isEmpty() ? "Noch nichts gezählt." : String.join("\n", top));
                                    return 1;
                                })))
                        .then(Commands.literal("start-season").executes(c -> seasonStart(c, false))
                                .then(Commands.literal("confirm").executes(c -> seasonStart(c, true))))
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
                                .then(Commands.literal("reload").executes(c -> {
                                    List<String> problems = GoalManager.get().reloadChecked();
                                    if (problems.isEmpty()) {
                                        ok(c, "Ziele neu geladen: " + GoalManager.get().goalCount());
                                        return 1;
                                    }
                                    for (String pr : problems) c.getSource().sendFailure(Component.literal(pr));
                                    return 0;
                                }))
                                .then(Commands.literal("complete").then(Commands.argument("goal", StringArgumentType.word()).suggests(KwCommand::goalIds).executes(c -> goalOp(c, "complete"))))
                                .then(Commands.literal("reset").then(Commands.argument("goal", StringArgumentType.word()).suggests(KwCommand::goalIds).executes(c -> goalOp(c, "reset"))))
                                .then(Commands.literal("open").then(Commands.argument("goal", StringArgumentType.word()).suggests(KwCommand::goalIds).executes(c -> goalOp(c, "open"))))
                                .then(Commands.literal("event").then(Commands.argument("goal", StringArgumentType.word()).suggests(KwCommand::goalIds)
                                        .then(Commands.literal("none").executes(c -> goalEvent(c, null)))
                                        .then(Commands.argument("when", StringArgumentType.greedyString()).executes(c -> goalEvent(c, StringArgumentType.getString(c, "when"))))))
                                .then(Commands.literal("rescale").then(Commands.argument("goal", StringArgumentType.word()).suggests(KwCommand::goalIds)
                                        .executes(c -> goalOp(c, "rescale_preview"))
                                        .then(Commands.literal("confirm").executes(c -> goalOp(c, "rescale")))))
                                .then(Commands.literal("progress").then(Commands.argument("goal", StringArgumentType.word()).suggests(KwCommand::goalIds)
                                        .then(Commands.argument("item", StringArgumentType.string())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(c -> goalOp(c, "progress")))))))
                        .then(Commands.literal("quests").then(Commands.literal("reset").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(c -> {
                                    try {
                                        ok(c, de.kronwerke.core.share.ftb.QuestLayer.reset(c.getSource().getServer(), net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player")));
                                    } catch (LinkageError e) {
                                        fail(c, "FTB Quests ist nicht da.");
                                    }
                                    return 1;
                                }))))
                        .then(Commands.literal("active").executes(c -> { ok(c, "Active players (scaling window): " + GoalManager.get().activePlayers()); return 1; }))
                        .then(ObeliskCommand.build()))
                .then(TestCommand.build()));
    }

    // ---- help and menu ----

    private static int help(CommandContext<CommandSourceStack> c) {
        boolean streamer = c.getSource().hasPermission(2) || (c.getSource().getPlayer() != null && SlotManager.get().ownSlots(c.getSource().getPlayer().getUUID()) != null);
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
            line(c, "/kw admin gate", "Startsperre: bis zum Start nur Ops und die Liste (on, off, beta on|off, allow, deny)");
            line(c, "/kw admin loadtest", "Lasttest mit Testspielern (start, stop)");
            line(c, "/kw admin dirty", "Wer Chunks als geändert markiert (start, show, stop)");
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

    /**
     * A running season is only started over with confirm: it resets every goal, leaderboard,
     * kit and streak, and one mistyped command must not end a season. The launcher makes a
     * backup first either way.
     */
    private static int seasonStart(CommandContext<CommandSourceStack> c, boolean confirmed) {
        var s = de.kronwerke.core.season.Season.get();
        if (s.running() && !confirmed) {
            fail(c, "Season " + s.number() + " läuft. Ein Neustart setzt alle Ziele, Ranglisten, Startpakete und Serien zurück. "
                    + "Wenn das wirklich gewollt ist: /kw admin season start confirm");
            return 0;
        }
        s.start();
        ok(c, "Season " + de.kronwerke.core.season.Season.get().number() + " läuft. Alle Ziele, Ranglisten und Startpakete sind zurückgesetzt.");
        return 1;
    }

    private static int gateInfo(CommandContext<CommandSourceStack> c) {
        var s = de.kronwerke.core.season.Season.get();
        String list = s.gateList().isEmpty() ? "niemand" : String.join(", ", s.gateList());
        if (s.running()) ok(c, "Die Season läuft, die Startsperre ist offen.");
        else if (s.gateClosed()) ok(c, "Startsperre zu: nur Ops" + (s.beta() ? ", Beta-Tester mit eigenem Platz" : "") + " und die Liste kommen rein. Liste: " + list + ".");
        else ok(c, "Startsperre aus: jeder auf der Whitelist kommt rein. Liste: " + list + ".");
        return 1;
    }

    private static int gateSet(CommandContext<CommandSourceStack> c, boolean on) {
        de.kronwerke.core.season.Season.get().setGate(on);
        ok(c, on ? "Startsperre an. Bis zum Season-Start kommen nur Ops und die Liste rein." : "Startsperre aus. Jeder auf der Whitelist kommt rein.");
        return 1;
    }

    private static int gateBeta(CommandContext<CommandSourceStack> c, boolean on) {
        de.kronwerke.core.season.Season.get().setBeta(on);
        ok(c, on ? "Beta an: Streamer und Season-1-Spieler mit eigenem Platz kommen durch die Startsperre, ihre Leute noch nicht."
                : "Beta aus: durch die Startsperre kommen nur Ops und die Liste.");
        return 1;
    }

    private static int gateAllow(CommandContext<CommandSourceStack> c, boolean add) {
        String name = StringArgumentType.getString(c, "player");
        boolean changed = de.kronwerke.core.season.Season.get().allow(name, add);
        if (add) ok(c, changed ? name + " kommt jetzt durch die Startsperre." : name + " stand schon auf der Liste.");
        else ok(c, changed ? name + " ist von der Liste." : name + " stand nicht auf der Liste.");
        return 1;
    }

    private static int loadtestInfo(CommandContext<CommandSourceStack> c) {
        int n = de.kronwerke.core.loadtest.LoadTest.count();
        ok(c, n == 0 ? "Kein Lasttest. /kw admin loadtest start <stehend> <fliegend> [radius] [blöcke pro tick]"
                : n + " Testspieler, davon " + de.kronwerke.core.loadtest.LoadTest.travellers() + " fliegend. Zusammen "
                        + de.kronwerke.core.loadtest.LoadTest.waited() + " Ticks auf Chunks gewartet. /kw admin loadtest stop entfernt sie.");
        return 1;
    }

    private static int loadtestStart(CommandContext<CommandSourceStack> c, int radius, double speed) {
        int settlers = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "settlers");
        int travellers = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "travellers");
        int n = de.kronwerke.core.loadtest.LoadTest.start(c.getSource().getServer(), settlers, travellers, radius, speed,
                de.kronwerke.core.loadtest.LoadTest.flyHeight(c.getSource().getServer()));
        if (n == 0 && de.kronwerke.core.loadtest.LoadTest.waiting()) ok(c, "Die Chunks um den Spawn laden noch, die Testspieler kommen gleich.");
        else ok(c, n + " Testspieler dazu (" + travellers + " fliegend, " + speed + " Blöcke pro Tick, Radius " + radius + "). Jetzt "
                + de.kronwerke.core.loadtest.LoadTest.count() + ".");
        return 1;
    }

    /** For the bot: the season in one line of JSON. */
    private static int seasonJson(CommandContext<CommandSourceStack> c) {
        var s = de.kronwerke.core.season.Season.get();
        var ob = de.kronwerke.core.obelisk.Obelisk.get();
        c.getSource().sendSuccess(() -> Component.literal("OK {\"running\":" + s.running() + ",\"number\":" + s.number() + ",\"startedAt\":" + s.startedAt()
                + ",\"gate\":" + s.gateClosed() + ",\"beta\":" + s.beta() + ",\"tier\":" + ob.tier() + ",\"slumbering\":" + ob.slumbering() + "}"), false);
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
        SlotData.StreamerEntry me = SlotManager.get().ownSlots(p.getUUID());
        if (me == null) {
            fail(c, Text.t("slots.none", "Du hast keine eigenen Plätze. Plätze vergeben Streamer und Season-1-Spieler über Discord.").getString());
            return 0;
        }
        if (!de.kronwerke.core.net.KwNetwork.allowAction(p, true)) {
            fail(c, Text.t("slots.slow", "Langsam, eins nach dem anderen.").getString());
            return 0;
        }
        String target = StringArgumentType.getString(c, "player");
        CommandSourceStack src = c.getSource();
        SlotManager.get().resolve(target, profile -> {
            SlotManager.Result r = profile.isEmpty() ? SlotManager.Result.UNKNOWN_PLAYER : SlotManager.get().invite(me, profile.get(), false);
            String used = me.used() + "/" + SlotManager.get().allowance(me);
            switch (r) {
                case OK -> src.sendSuccess(() -> Component.literal(profile.get().getName() + " steht auf der Whitelist. Plätze: " + used).withStyle(ChatFormatting.GREEN), false);
                case NO_SLOTS_LEFT -> src.sendFailure(Component.literal("Du hast keinen freien Platz mehr (" + used + ")."));
                case ALREADY_INVITED -> src.sendFailure(Component.literal(target + " hat schon einen Platz."));
                case ALREADY_WHITELISTED -> src.sendFailure(Component.literal(target + " ist schon auf der Whitelist."));
                case UNKNOWN_PLAYER -> src.sendFailure(Component.literal("Kein Minecraft-Konto mit dem Namen " + target + ". Schreibweise prüfen."));
                default -> src.sendFailure(Component.literal("Das hat nicht geklappt."));
            }
        });
        return 1;
    }

    private static int revoke(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        SlotData.StreamerEntry me = SlotManager.get().ownSlots(p.getUUID());
        String target = StringArgumentType.getString(c, "player");
        if (me == null) {
            fail(c, Text.t("slots.none", "Du hast keine eigenen Plätze. Plätze vergeben Streamer und Season-1-Spieler über Discord.").getString());
            return 0;
        }
        // the players one invited are known by name already: no lookup needed
        java.util.UUID id = null;
        for (var e : me.invited.entrySet()) if (e.getValue().equalsIgnoreCase(target)) id = e.getKey();
        if (id == null) {
            fail(c, target + " hat seinen Platz nicht von dir.");
            return 0;
        }
        SlotManager.Result r = SlotManager.get().revoke(me, new com.mojang.authlib.GameProfile(id, me.invited.get(id)));
        if (r == SlotManager.Result.OK) ok(c, target + " ist von der Whitelist runter. Plätze: " + me.used() + "/" + SlotManager.get().allowance(me));
        else fail(c, "Das hat nicht geklappt.");
        return r == SlotManager.Result.OK ? 1 : 0;
    }

    private static int slots(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        SlotData.StreamerEntry me = SlotManager.get().ownSlots(p.getUUID());
        if (me == null) {
            fail(c, Text.t("slots.none", "Du hast keine eigenen Plätze. Plätze vergeben Streamer und Season-1-Spieler über Discord.").getString());
            return 0;
        }
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
            fail(c, all ? "Nichts in deinem Inventar passt zum aktuellen Ziel (die Hotbar bleibt unberührt). /kw goals zeigt, was gebraucht wird."
                    : "Nichts in deiner Hand passt zum aktuellen Ziel. /kw goals zeigt, was gebraucht wird.");
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Abgegeben: " + Text.number(taken) + ". ").withStyle(ChatFormatting.GREEN)
                .append(de.kronwerke.core.obelisk.Obelisk.undoComponent()), false);
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
            c.getSource().sendSuccess(() -> Component.literal(" " + r + ". " + name + "  " + Text.number(e.getValue()) + " Punkte").withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    // ---- admin ----

    private static SlotData.StreamerEntry streamerArg(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "streamer");
        SlotData.StreamerEntry e = SlotManager.get().entryByName(name);
        if (e == null || !e.granted) {
            fail(c, name + " hat keinen eigenen Platz. Erst /kw admin grant " + name + ".");
            return null;
        }
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
            String tag = e.granted ? " (" + e.kind + ")" : " (ohne eigenen Platz)";
            c.getSource().sendSuccess(() -> Component.literal(e.name + tag + "  " + e.used() + "/" + SlotManager.get().allowance(e)).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("  " + String.join(", ", e.invited.values())).withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int goalOp(CommandContext<CommandSourceStack> c, String op) {
        GoalManager gm = GoalManager.get();
        Goal g = gm.goal(StringArgumentType.getString(c, "goal"));
        if (g == null) {
            fail(c, "Kein Ziel mit dieser id. Da sind: " + String.join(", ", java.util.stream.StreamSupport.stream(gm.allGoals().spliterator(), false).map(Goal::id).toList()));
            return 0;
        }
        switch (op) {
            case "complete" -> gm.complete(g);
            case "reset" -> gm.reset(g);
            case "open" -> gm.release(g);
            case "rescale_preview" -> {
                for (String line : rescalePreview(g)) c.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
                ok(c, "Mit /kw admin goal rescale " + g.id() + " confirm anwenden.");
                return 1;
            }
            case "rescale" -> {
                double old = gm.progressData().factor(g.id());
                double f = gm.rescale(g);
                ok(c, "Ziel " + g.id() + " neu skaliert: Faktor " + String.format("%.2f", old) + " auf " + String.format("%.2f", f) + ".");
                return 1;
            }
            case "progress" -> {
                String item = StringArgumentType.getString(c, "item");
                if (g.allItems().stream().noneMatch(it -> it.item().equals(item))) {
                    fail(c, item + " gehört nicht zu " + g.id() + ". Da sind: " + String.join(", ", g.allItems().stream().map(Goal.PillarItem::item).toList()));
                    return 0;
                }
                gm.progressData().setProgress(g.id(), item, IntegerArgumentType.getInteger(c, "amount"));
                gm.check(g);
            }
        }
        ok(c, "Ziel " + g.id() + ": " + switch (op) {
            case "complete" -> "geschafft";
            case "reset" -> "zurückgesetzt";
            case "open" -> "Halt aufgehoben";
            default -> "Stand gesetzt";
        } + ".");
        return 1;
    }

    /** /kw admin goal event <goal> 2027-01-23T20:00: the time of the event that opens a held goal, shown to everyone. */
    private static int goalEvent(CommandContext<CommandSourceStack> c, String when) {
        GoalManager gm = GoalManager.get();
        Goal g = gm.goal(StringArgumentType.getString(c, "goal"));
        if (g == null) {
            fail(c, "Kein Ziel mit dieser id.");
            return 0;
        }
        if (when == null) {
            gm.progressData().setEventAt(g.id(), 0);
            ok(c, "Kein Eventtermin mehr für " + g.id() + ".");
        } else {
            try {
                long at = java.time.LocalDateTime.parse(when.trim()).atZone(KronwerkeConfig.zone()).toInstant().toEpochMilli();
                gm.progressData().setEventAt(g.id(), at);
                ok(c, "Event für " + g.id() + " " + gm.eventText(g) + ".");
            } catch (java.time.format.DateTimeParseException e) {
                fail(c, "Zeit als 2027-01-23T20:00 angeben.");
                return 0;
            }
        }
        gm.refreshBossBar();
        return 1;
    }

    /** What a rescale would change: the factor and every item's target, against the progress. */
    public static List<String> rescalePreview(Goal g) {
        GoalManager gm = GoalManager.get();
        double now = gm.progressData().factor(g.id()), next = gm.currentFactor(g);
        List<String> out = new java.util.ArrayList<>();
        out.add(g.id() + ": Faktor " + String.format("%.2f", now) + " -> " + String.format("%.2f", next) + " (" + gm.activePlayers() + " aktive Spieler)");
        gm.targetsFor(g, next).forEach((item, t) -> out.add("  " + Text.item(item).getString() + ": " + gm.progressData().target(g.id(), item) + " -> " + t
                + ", da sind " + gm.progressData().progress(g.id(), item)));
        return out;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> goalIds(CommandContext<CommandSourceStack> c,
                                                                                                               com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        List<String> ids = new java.util.ArrayList<>();
        for (Goal g : GoalManager.get().allGoals()) ids.add(g.id());
        return net.minecraft.commands.SharedSuggestionProvider.suggest(ids, b);
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
    }

    private static void fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg).withStyle(ChatFormatting.RED));
    }

    /** /kw admin locks on|off: the machine stage locks, kept in the config; the recipes load again. */
    private static int setLocks(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c, boolean on) {
        // kept outside the pack's config file: a pack update does not turn it back
        de.kronwerke.core.config.Overrides.put("locks.machineLocks", Boolean.toString(on));
        var server = c.getSource().getServer();
        c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Stufensperre für Maschinen " + (on ? "an" : "aus") + ", die Rezepte laden neu (ein paar Sekunden)."), true);
        server.reloadResources(server.getPackRepository().getSelectedIds());
        return 1;
    }
}
