package de.kronwerke.core.tab;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sets a player's tag by hand, on every server of the network:
 *
 *   /kwtag <player> <tag>     the tag, at once in tab list, chat and above the head
 *   /kwtag <player> auto      back to the automatic rank (owner, admin, streamer, member)
 *   /kwtag list               who has a tag set by hand
 *   /kwtag tags               every tag there is, with its icon
 *
 * The same under /kw admin tag. Operators only.
 */
public final class TagCommand {
    private TagCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(build(Commands.literal("kwtag")));
        d.register(Commands.literal("kw").then(Commands.literal("admin").requires(s -> s.hasPermission(2)).then(build(Commands.literal("tag")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(TagCommand::list))
                .then(Commands.literal("tags").executes(TagCommand::tags))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(players(c.getSource()), b))
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    List<String> all = new ArrayList<>(Tags.ALL.keySet());
                                    all.add(0, "auto");
                                    return SharedSuggestionProvider.suggest(all, b);
                                })
                                .executes(TagCommand::set)));
    }

    private static List<String> players(CommandSourceStack src) {
        List<String> out = new ArrayList<>(List.of(src.getServer().getPlayerNames()));
        for (var r : de.kronwerke.core.link.NetworkSync.remotePlayers()) if (!out.contains(r.name())) out.add(r.name());
        return out;
    }

    private static int set(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        String name = StringArgumentType.getString(c, "player");
        String tag = StringArgumentType.getString(c, "tag").toLowerCase(java.util.Locale.ROOT);
        boolean auto = tag.equals("auto");
        if (!auto && Tags.get(tag) == null) {
            src.sendFailure(Component.literal("Diesen Tag gibt es nicht. Alle Tags: /kwtag tags"));
            return 0;
        }
        UUID id = null;
        ServerPlayer online = src.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            id = online.getUUID();
            name = online.getGameProfile().getName();
        }
        if (id == null) id = de.kronwerke.core.link.NetworkSync.remoteId(name);
        if (id == null && src.getServer().getProfileCache() != null) {
            var p = src.getServer().getProfileCache().get(name);
            if (p.isPresent()) {
                id = p.get().getId();
                name = p.get().getName();
            }
        }
        if (id == null) {
            src.sendFailure(Component.literal("Spieler " + name + " nicht gefunden. Er muss online sein oder schon einmal auf dem Server gewesen sein."));
            return 0;
        }
        Tags.put(id, name, auto ? null : tag);
        final String who = name;
        MutableComponent msg = auto
                ? Component.literal(who + " hat wieder den automatischen Rang.").withStyle(ChatFormatting.GREEN)
                : Component.literal(who + " hat jetzt den Tag ").withStyle(ChatFormatting.GREEN).append(Tags.get(tag).badge()).append(Component.literal(tag).withStyle(ChatFormatting.WHITE));
        src.sendSuccess(() -> msg, true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        Map<UUID, String> all = Tags.all();
        if (all.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Niemand hat einen Tag von Hand. Alle haben ihren automatischen Rang.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        for (var e : all.entrySet()) {
            Tags.Tag t = Tags.get(e.getValue());
            if (t == null) continue;
            MutableComponent line = t.badge().append(Component.literal(Tags.nameOf(e.getKey())).withStyle(t.colour()))
                    .append(Component.literal("  " + t.id()).withStyle(ChatFormatting.DARK_GRAY));
            c.getSource().sendSuccess(() -> line, false);
        }
        return all.size();
    }

    private static int tags(CommandContext<CommandSourceStack> c) {
        MutableComponent line = Component.empty();
        int n = 0;
        for (Tags.Tag t : Tags.ALL.values()) {
            line.append(t.badge()).append(Component.literal(t.id() + "   ").withStyle(t.colour()));
            if (++n % 4 == 0) {
                MutableComponent out = line;
                c.getSource().sendSuccess(() -> out, false);
                line = Component.empty();
            }
        }
        if (n % 4 != 0) {
            MutableComponent out = line;
            c.getSource().sendSuccess(() -> out, false);
        }
        return n;
    }
}
