package de.kronwerke.core.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.kronwerke.core.goal.Goal;
import de.kronwerke.core.goal.GoalData;
import de.kronwerke.core.goal.GoalManager;
import de.kronwerke.core.slot.SlotData;
import de.kronwerke.core.slot.SlotManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Admin commands meant for the Discord bot over RCON. Every answer is one line that
 * starts with "OK" or "ERR", so the bot can tell success from failure without parsing
 * prose.
 *
 *   admin invite <streamer> <player>   use one of the streamer's slots for the player
 *   admin revoke <streamer> <player>   free it again
 *   admin grant <player> [slots]       whitelist without a streamer's slot, with slots of their own
 *                                      (the config default when slots is left out)
 *   admin ungrant <player>             take that place back, with every slot the player gave
 *   admin goals json                   every goal with state, progress and top five
 */
public final class BotCommand {
    private static final Gson GSON = new Gson();

    static LiteralArgumentBuilder<CommandSourceStack> invite() {
        return Commands.literal("invite")
                .then(Commands.argument("streamer", StringArgumentType.word())
                        .then(Commands.argument("player", StringArgumentType.word()).executes(c -> slot(c, true))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> revoke() {
        return Commands.literal("revoke")
                .then(Commands.argument("streamer", StringArgumentType.word())
                        .then(Commands.argument("player", StringArgumentType.word()).executes(c -> slot(c, false))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> grant() {
        return Commands.literal("grant")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(c -> grant(c, -1))
                        .then(Commands.argument("slots", IntegerArgumentType.integer(0, 64))
                                .executes(c -> grant(c, IntegerArgumentType.getInteger(c, "slots")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> ungrant() {
        return Commands.literal("ungrant")
                .then(Commands.argument("player", StringArgumentType.word()).executes(BotCommand::ungrant));
    }

    static LiteralArgumentBuilder<CommandSourceStack> goals() {
        return Commands.literal("goals").then(Commands.literal("json").executes(BotCommand::goalsJson));
    }

    private static void answer(CommandContext<CommandSourceStack> c, String line) {
        c.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    private static int slot(CommandContext<CommandSourceStack> c, boolean invite) {
        String streamerName = StringArgumentType.getString(c, "streamer");
        String player = StringArgumentType.getString(c, "player");
        SlotManager sm = SlotManager.get();
        SlotData.StreamerEntry e = sm.entryByName(streamerName);
        if (e == null) {
            var profile = sm.lookup(streamerName);
            if (profile.isEmpty()) {
                answer(c, "ERR no Minecraft account named " + streamerName + " (the streamer)");
                return 0;
            }
            e = sm.entry(profile.get().getId(), profile.get().getName());
        }
        SlotManager.Result r = invite ? sm.invite(e, player) : sm.revoke(e, player);
        String used = e.used() + "/" + sm.allowance(e);
        switch (r) {
            case OK -> answer(c, "OK " + (invite ? "slots used " : "slot freed, now ") + used);
            case NO_SLOTS_LEFT -> answer(c, "ERR " + e.name + " has no free slots (" + used + ")");
            case ALREADY_INVITED -> answer(c, "ERR " + player + " already has a slot");
            case ALREADY_WHITELISTED -> answer(c, "ERR " + player + " is already on the whitelist");
            case UNKNOWN_PLAYER -> answer(c, "ERR no Minecraft account named " + player);
            case NOT_INVITED_BY_YOU -> answer(c, "ERR " + player + " was not invited by " + e.name);
            case NOT_GRANTED -> answer(c, "ERR " + player + " has no granted place");
        }
        return r == SlotManager.Result.OK ? 1 : 0;
    }

    private static int grant(CommandContext<CommandSourceStack> c, int slots) {
        String player = StringArgumentType.getString(c, "player");
        SlotManager sm = SlotManager.get();
        SlotManager.Result r = sm.grant(player, slots);
        switch (r) {
            case OK -> {
                SlotData.StreamerEntry e = sm.entryByName(player);
                answer(c, "OK " + e.name + " is whitelisted with " + sm.allowance(e) + " slots of their own");
            }
            case UNKNOWN_PLAYER -> answer(c, "ERR no Minecraft account named " + player);
            case ALREADY_INVITED -> answer(c, "ERR " + player + " already has a slot from " + sm.inviterOf(sm.lookup(player).get().getId()).name);
            case ALREADY_WHITELISTED -> answer(c, "ERR " + player + " already has a granted place");
            default -> answer(c, "ERR " + r);
        }
        return r == SlotManager.Result.OK ? 1 : 0;
    }

    private static int ungrant(CommandContext<CommandSourceStack> c) {
        String player = StringArgumentType.getString(c, "player");
        SlotManager.Result r = SlotManager.get().ungrant(player);
        switch (r) {
            case OK -> answer(c, "OK " + player + " is off the whitelist, with every slot they gave");
            case UNKNOWN_PLAYER -> answer(c, "ERR no Minecraft account named " + player);
            case NOT_GRANTED -> answer(c, "ERR " + player + " has no granted place");
            default -> answer(c, "ERR " + r);
        }
        return r == SlotManager.Result.OK ? 1 : 0;
    }

    private static int goalsJson(CommandContext<CommandSourceStack> c) {
        GoalManager gm = GoalManager.get();
        GoalData d = gm.progressData();
        JsonArray out = new JsonArray();
        for (Goal g : gm.allGoals()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", g.id());
            o.addProperty("title", g.title());
            String state = d.isCompleted(g.id()) ? "done" : !gm.isActive(g) ? "locked" : gm.isHeld(g) ? "held" : "active";
            o.addProperty("state", state);
            o.addProperty("percent", (int) Math.floor(gm.fraction(g) * 100));
            JsonArray pillars = new JsonArray();
            for (Goal.Pillar p : g.pillars()) {
                JsonObject po = new JsonObject();
                po.addProperty("title", p.title());
                JsonArray items = new JsonArray();
                for (Goal.PillarItem it : p.items()) {
                    JsonObject io = new JsonObject();
                    io.addProperty("item", it.item());
                    io.addProperty("name", displayName(it.item()));
                    io.addProperty("have", d.progress(g.id(), it.item()));
                    io.addProperty("target", d.target(g.id(), it.item()));
                    io.addProperty("weight", it.points());
                    items.add(io);
                }
                po.add("items", items);
                pillars.add(po);
            }
            o.add("pillars", pillars);
            JsonArray top = new JsonArray();
            for (Map.Entry<UUID, Long> e : gm.leaderboard(g, 5)) {
                JsonObject t = new JsonObject();
                var cache = c.getSource().getServer().getProfileCache();
                String name = cache == null ? e.getKey().toString()
                        : cache.get(e.getKey()).map(pr -> pr.getName()).orElse(e.getKey().toString());
                t.addProperty("name", name);
                t.addProperty("amount", e.getValue());
                top.add(t);
            }
            o.add("top", top);
            JsonArray recent = new JsonArray();
            for (GoalManager.Recent r : gm.recent()) {
                if (!r.goal().equals(g.id())) continue;
                JsonObject ro = new JsonObject();
                ro.addProperty("at", r.at());
                ro.addProperty("name", r.name());
                ro.addProperty("item", r.item());
                ro.addProperty("itemName", displayName(r.item()));
                ro.addProperty("amount", r.amount());
                recent.add(ro);
            }
            o.add("recent", recent);
            out.add(o);
        }
        answer(c, "OK " + GSON.toJson(out));
        return 1;
    }

    private static String displayName(String id) {
        if (id.startsWith("#")) return id;
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null);
        return item == null ? id : new ItemStack(item).getHoverName().getString();
    }

    private BotCommand() {}
}
