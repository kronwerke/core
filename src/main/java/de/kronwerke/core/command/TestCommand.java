package de.kronwerke.core.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.goal.GoalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /kw test ... : server side test players for exercising goals without a client.
 * Only registered when testCommands is on in the config. Never turn that on in a season.
 *
 *   test join <name>                  create a test player (not in the world, inventory and data only)
 *   test leave <name>
 *   test give <name> <item> <count>   put items into its inventory
 *   test inv <name>                   list its inventory
 *   test slots <name>                 every filled slot, with hand, offhand and armour marked
 *   test audit <name>                 run Chapters' inventory audit on it (Core replaces it, see LockedItems)
 *   test deposit <name> [all]         deposit the first stack (or everything) into the active goal
 *   test kits <name>                  hand out starter kits it is owed
 *   test list
 */
public final class TestCommand {
    private static final Map<String, FakePlayer> players = new LinkedHashMap<>();

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("test").requires(s -> s.hasPermission(2) && KronwerkeConfig.TEST_COMMANDS.get())
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).executes(TestCommand::join)))
                .then(Commands.literal("leave").then(Commands.argument("name", StringArgumentType.word()).executes(TestCommand::leave)))
                .then(Commands.literal("give").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1)).executes(TestCommand::give)))))
                .then(Commands.literal("inv").then(Commands.argument("name", StringArgumentType.word()).executes(TestCommand::inv)))
                .then(Commands.literal("slots").then(Commands.argument("name", StringArgumentType.word()).executes(TestCommand::slots)))
                .then(Commands.literal("audit").then(Commands.argument("name", StringArgumentType.word()).executes(TestCommand::audit)))
                .then(Commands.literal("deposit").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> deposit(c, false))
                        .then(Commands.literal("all").executes(c -> deposit(c, true)))))
                .then(Commands.literal("kits").then(Commands.argument("name", StringArgumentType.word()).executes(TestCommand::kits)))
                .then(Commands.literal("list").executes(TestCommand::list));
    }

    private static FakePlayer player(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name");
        FakePlayer p = players.get(name);
        if (p == null) c.getSource().sendFailure(Component.literal("No test player " + name + ". Use /kw test join " + name).withStyle(ChatFormatting.RED));
        return p;
    }

    private static int join(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name");
        if (players.containsKey(name)) {
            c.getSource().sendFailure(Component.literal(name + " already exists.").withStyle(ChatFormatting.RED));
            return 0;
        }
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID("test_" + name), name);
        FakePlayer p = FakePlayerFactory.get(c.getSource().getServer().overworld(), profile);
        players.put(name, p);
        GoalManager.get().onPlayerJoin(p);
        c.getSource().sendSuccess(() -> Component.literal("Test player " + name + " joined (" + profile.getId() + ").").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> c) {
        FakePlayer p = player(c);
        if (p == null) return 0;
        GoalManager.get().onPlayerLeave(p);
        players.remove(StringArgumentType.getString(c, "name"));
        c.getSource().sendSuccess(() -> Component.literal("Test player left.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> c) {
        FakePlayer p = player(c);
        if (p == null) return 0;
        ResourceLocation rl = ResourceLocationArgument.getId(c, "item");
        String id = rl.toString();
        Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (item == null) {
            c.getSource().sendFailure(Component.literal("Unknown item " + id).withStyle(ChatFormatting.RED));
            return 0;
        }
        int count = IntegerArgumentType.getInteger(c, "count");
        int left = count;
        while (left > 0) {
            int n = Math.min(left, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, n);
            if (!p.getInventory().add(stack)) {
                int l = left;
                c.getSource().sendFailure(Component.literal("Inventory full, " + l + " not given.").withStyle(ChatFormatting.RED));
                return 0;
            }
            left -= n;
        }
        c.getSource().sendSuccess(() -> Component.literal("Gave " + count + " " + id + ".").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int inv(CommandContext<CommandSourceStack> c) {
        FakePlayer p = player(c);
        if (p == null) return 0;
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty()) totals.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        if (totals.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Inventory empty.").withStyle(ChatFormatting.GRAY), false);
        }
        totals.forEach((k, v) -> c.getSource().sendSuccess(() -> Component.literal(" " + v + " x " + k).withStyle(ChatFormatting.GRAY), false));
        return 1;
    }

    private static int slots(CommandContext<CommandSourceStack> c) {
        FakePlayer p = player(c);
        if (p == null) return 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) continue;
            String line = " " + i + (i == inv.selected ? " (hand)" : "") + ": " + s.getCount() + " x " + BuiltInRegistries.ITEM.getKey(s.getItem());
            c.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            ItemStack s = inv.armor.get(i);
            if (s.isEmpty()) continue;
            String line = " armour " + i + ": " + BuiltInRegistries.ITEM.getKey(s.getItem());
            c.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        ItemStack off = inv.offhand.get(0);
        if (!off.isEmpty()) {
            String line = " offhand: " + off.getCount() + " x " + BuiltInRegistries.ITEM.getKey(off.getItem());
            c.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int audit(CommandContext<CommandSourceStack> c) {
        FakePlayer p = player(c);
        if (p == null) return 0;
        if (!net.neoforged.fml.ModList.get().isLoaded("chapters")) {
            c.getSource().sendFailure(Component.literal("Chapters is not installed.").withStyle(ChatFormatting.RED));
            return 0;
        }
        com.gabinx.chapters.event.InventoryAuditor.auditNow(p);
        c.getSource().sendSuccess(() -> Component.literal("Audited.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int deposit(CommandContext<CommandSourceStack> c, boolean all) {
        FakePlayer p = player(c);
        if (p == null) return 0;
        long taken;
        if (all) {
            taken = GoalManager.get().depositAll(p);
        } else {
            ItemStack first = ItemStack.EMPTY;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                if (!p.getInventory().getItem(i).isEmpty()) {
                    first = p.getInventory().getItem(i);
                    break;
                }
            }
            taken = GoalManager.get().deposit(p, first);
        }
        long t = taken;
        c.getSource().sendSuccess(() -> Component.literal("Deposited " + t + ".").withStyle(t > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    private static int kits(CommandContext<CommandSourceStack> c) {
        FakePlayer p = player(c);
        if (p == null) return 0;
        GoalManager.get().giveKits(p);
        return inv(c);
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Component.literal("Test players: " + String.join(", ", players.keySet())).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
