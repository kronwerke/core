package de.kronwerke.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 *   admin invite|revoke|grant|ungrant|goals json   for the Discord bot, see BotCommand
 */
public final class KwCommand {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kw")
                .then(Commands.literal("invite")
                        .requires(s -> KronwerkeConfig.STREAMERS_MANAGE_OWN_SLOTS.get() || s.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word()).executes(KwCommand::invite)))
                .then(Commands.literal("revoke")
                        .requires(s -> KronwerkeConfig.STREAMERS_MANAGE_OWN_SLOTS.get() || s.hasPermission(2))
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
                        .then(Commands.literal("goal")
                                .then(Commands.literal("reload").executes(c -> { GoalManager.get().reload(); ok(c, "Goals reloaded: " + GoalManager.get().goalCount()); return 1; }))
                                .then(Commands.literal("complete").then(Commands.argument("goal", StringArgumentType.word()).executes(c -> goalOp(c, "complete"))))
                                .then(Commands.literal("reset").then(Commands.argument("goal", StringArgumentType.word()).executes(c -> goalOp(c, "reset"))))
                                .then(Commands.literal("open").then(Commands.argument("goal", StringArgumentType.word()).executes(c -> goalOp(c, "open"))))
                                .then(Commands.literal("rescale").then(Commands.argument("goal", StringArgumentType.word()).executes(c -> goalOp(c, "rescale"))))
                                .then(Commands.literal("progress").then(Commands.argument("goal", StringArgumentType.word())
                                        .then(Commands.argument("item", StringArgumentType.string())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(c -> goalOp(c, "progress")))))))
                        .then(Commands.literal("active").executes(c -> { ok(c, "Active players (scaling window): " + GoalManager.get().activePlayers()); return 1; })))
                .then(TestCommand.build()));
    }

    // ---- streamer slot commands ----

    private static int invite(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        SlotData.StreamerEntry me = SlotManager.get().entry(p.getUUID(), p.getGameProfile().getName());
        String target = StringArgumentType.getString(c, "player");
        SlotManager.Result r = SlotManager.get().invite(me, target);
        switch (r) {
            case OK -> ok(c, target + " is whitelisted now. Slots used: " + me.used() + "/" + SlotManager.get().allowance(me));
            case NO_SLOTS_LEFT -> fail(c, "You have no free slots (" + me.used() + "/" + SlotManager.get().allowance(me) + ").");
            case ALREADY_INVITED -> fail(c, target + " already has a slot.");
            case ALREADY_WHITELISTED -> fail(c, target + " is already on the whitelist.");
            case UNKNOWN_PLAYER -> fail(c, "No Minecraft account named " + target + " was found. Check the spelling.");
            default -> fail(c, "That did not work.");
        }
        return r == SlotManager.Result.OK ? 1 : 0;
    }

    private static int revoke(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        SlotData.StreamerEntry me = SlotManager.get().entry(p.getUUID(), p.getGameProfile().getName());
        String target = StringArgumentType.getString(c, "player");
        SlotManager.Result r = SlotManager.get().revoke(me, target);
        switch (r) {
            case OK -> ok(c, target + " was removed. Slots used: " + me.used() + "/" + SlotManager.get().allowance(me));
            case NOT_INVITED_BY_YOU -> fail(c, target + " was not invited by you.");
            case UNKNOWN_PLAYER -> fail(c, "No Minecraft account named " + target + " was found.");
            default -> fail(c, "That did not work.");
        }
        return r == SlotManager.Result.OK ? 1 : 0;
    }

    private static int slots(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        SlotData.StreamerEntry me = SlotManager.get().entry(p.getUUID(), p.getGameProfile().getName());
        c.getSource().sendSuccess(() -> Component.literal("Your slots: ").withStyle(ChatFormatting.GOLD)
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
            fail(c, "Nothing you are holding fits the current goal. Use /kw goals to see what is needed.");
            return 0;
        }
        ok(c, "Deposited " + taken + ".");
        return 1;
    }

    private static int goals(CommandContext<CommandSourceStack> c) {
        GoalManager gm = GoalManager.get();
        GoalData d = gm.progressData();
        c.getSource().sendSuccess(() -> Component.literal("Community goals").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Goal g : gm.allGoals()) {
            boolean done = d.isCompleted(g.id());
            boolean active = gm.isActive(g);
            ChatFormatting color = done ? ChatFormatting.GREEN : active ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY;
            String state = done ? "done" : active ? (gm.isHeld(g) ? "waiting for the event" : Math.round(gm.fraction(g) * 100) + "%") : "locked";
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
                    c.getSource().sendSuccess(() -> Component.literal("     " + it.item() + "  " + have + " / " + need)
                            .withStyle(have >= need ? ChatFormatting.GREEN : ChatFormatting.WHITE), false);
                }
            }
        }
        return 1;
    }

    private static int top(CommandContext<CommandSourceStack> c, String goalId) {
        GoalManager gm = GoalManager.get();
        Goal g = goalId == null ? (gm.activeGoals().isEmpty() ? null : gm.activeGoals().get(0)) : gm.goal(goalId);
        if (g == null) {
            fail(c, "No such goal.");
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Top contributors: " + g.title()).withStyle(ChatFormatting.GOLD), false);
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
