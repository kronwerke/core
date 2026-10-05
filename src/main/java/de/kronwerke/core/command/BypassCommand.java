package de.kronwerke.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.goal.Goal;
import de.kronwerke.core.goal.GoalData;
import de.kronwerke.core.goal.GoalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /kw admin bypass ...
 *   bypass                  toggle for yourself
 *   bypass on|off [player]  set it for yourself or someone else
 *   bypass list             who has it on
 *
 * On gives every stage a goal grants, so locked items, recipes and dimensions can be tested
 * before the community gets there. Off takes back the stages that no completed goal grants,
 * so the player is where everybody else is. The state is saved with the goals.
 */
public final class BypassCommand {
    private BypassCommand() {}

    /** Adds the bypass to /kw admin; brigadier merges it into the tree KwCommand built. */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kw")
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(build())));
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("bypass")
                .requires(s -> s.hasPermission(2))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return set(c, p, !data().hasBypass(p.getUUID()));
                })
                .then(Commands.literal("on")
                        .executes(c -> set(c, c.getSource().getPlayerOrException(), true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> set(c, EntityArgument.getPlayer(c, "player"), true))))
                .then(Commands.literal("off")
                        .executes(c -> set(c, c.getSource().getPlayerOrException(), false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> set(c, EntityArgument.getPlayer(c, "player"), false))))
                .then(Commands.literal("list").executes(BypassCommand::list));
    }

    private static GoalData data() {
        return GoalManager.get().progressData();
    }

    /** Every stage a goal grants, in the order of the goals. */
    static List<String> allStages() {
        Set<String> all = new LinkedHashSet<>();
        for (Goal g : GoalManager.get().allGoals()) all.addAll(g.stages());
        return new ArrayList<>(all);
    }

    /** Turns the bypass on or off and returns the stages that were added or taken back. */
    static List<String> apply(MinecraftServer server, ServerPlayer player, boolean on) {
        Set<String> earned = new HashSet<>();
        for (Goal g : GoalManager.get().allGoals()) {
            if (data().isCompleted(g.id())) earned.addAll(g.stages());
        }
        List<String> changed = new ArrayList<>();
        for (String stage : allStages()) {
            if (earned.contains(stage)) continue;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "chapters " + (on ? "add " : "remove ") + player.getGameProfile().getName() + " " + stage);
            changed.add(stage);
        }
        data().setBypass(player.getUUID(), on);
        KronwerkeCore.LOGGER.info("Stage bypass {} for {}", on ? "on" : "off", player.getGameProfile().getName());
        return changed;
    }

    private static int set(CommandContext<CommandSourceStack> c, ServerPlayer p, boolean on) throws CommandSyntaxException {
        List<String> changed = apply(c.getSource().getServer(), p, on);
        String name = p.getGameProfile().getName();
        String what = changed.isEmpty() ? "keine Stufe geändert" : changed.size() + (changed.size() == 1 ? " Stufe" : " Stufen") + (on ? " offen" : " wieder zu");
        c.getSource().sendSuccess(() -> Component.literal("Bypass " + (on ? "an" : "aus") + " für " + name + ": " + what)
                .withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
        if (c.getSource().getPlayer() != p) {
            p.sendSystemMessage(Component.literal(on ? "Bypass an: jede Stufe ist für dich offen."
                    : "Bypass aus: du bist wieder auf den Stufen der Gemeinschaft.").withStyle(ChatFormatting.GOLD));
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<String> names = data().bypassing().stream()
                .map(u -> name(c, u))
                .sorted()
                .toList();
        c.getSource().sendSuccess(() -> Component.literal(names.isEmpty() ? "Niemand hat den Bypass an."
                : "Bypass an: " + String.join(", ", names)).withStyle(ChatFormatting.GOLD), false);
        return names.size();
    }

    private static String name(CommandContext<CommandSourceStack> c, UUID u) {
        var cache = c.getSource().getServer().getProfileCache();
        return cache == null ? u.toString() : cache.get(u).map(p -> p.getName()).orElse(u.toString());
    }
}
