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
 *   admin invite <streamer> <player> [uuid]   use one of the streamer's slots for the player
 *   admin revoke <streamer> <player> [uuid]   free it again
 *   admin grant <player> [slots [kind [uuid]]] whitelist without a streamer's slot, with slots of
 *                                      their own (the config default when slots is left out);
 *                                      kind is streamer, season1 or team
 *   admin ungrant <player> [uuid]      take that place back, with every slot the player gave
 * With the uuid (the bot resolves names itself) Core never asks Mojang, and a renamed account
 * stays the same player.
 *   admin goals json                   every goal with state, progress and top five
 *   admin roster json                  every streamer and granted player with slots and invited players
 */
public final class BotCommand {
    private static final Gson GSON = new Gson();

    static LiteralArgumentBuilder<CommandSourceStack> invite() {
        return Commands.literal("invite")
                .then(Commands.argument("streamer", StringArgumentType.word())
                        .then(Commands.argument("player", StringArgumentType.word()).executes(c -> slot(c, true))
                                .then(Commands.argument("uuid", StringArgumentType.word()).executes(c -> slot(c, true)))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> revoke() {
        return Commands.literal("revoke")
                .then(Commands.argument("streamer", StringArgumentType.word())
                        .then(Commands.argument("player", StringArgumentType.word()).executes(c -> slot(c, false))
                                .then(Commands.argument("uuid", StringArgumentType.word()).executes(c -> slot(c, false)))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> grant() {
        return Commands.literal("grant")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(c -> grant(c, -1))
                        // -1: the default number of slots, so a streamer's kind and UUID can follow
                        .then(Commands.argument("slots", IntegerArgumentType.integer(-1, 64))
                                .executes(c -> grant(c, IntegerArgumentType.getInteger(c, "slots")))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("streamer", "season1", "team"), b))
                                        .executes(c -> grant(c, IntegerArgumentType.getInteger(c, "slots")))
                                        .then(Commands.argument("uuid", StringArgumentType.word())
                                                .executes(c -> grant(c, IntegerArgumentType.getInteger(c, "slots")))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> ungrant() {
        return Commands.literal("ungrant")
                .then(Commands.argument("player", StringArgumentType.word()).executes(BotCommand::ungrant)
                        .then(Commands.argument("uuid", StringArgumentType.word()).executes(BotCommand::ungrant)));
    }

    /** An optional argument, or null. */
    private static String opt(CommandContext<CommandSourceStack> c, String name) {
        try {
            return StringArgumentType.getString(c, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The player's profile: with the uuid the bot sends along no name lookup happens (and a
     * renamed account is still the same player); without it, the profile cache as before.
     */
    private static java.util.Optional<com.mojang.authlib.GameProfile> player(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "player");
        String u = opt(c, "uuid");
        java.util.UUID id = null;
        if (u != null) {
            try {
                id = java.util.UUID.fromString(u);
            } catch (IllegalArgumentException e) {
                return java.util.Optional.empty();
            }
        }
        return SlotManager.get().profile(name, id);
    }

    static LiteralArgumentBuilder<CommandSourceStack> roster() {
        return Commands.literal("roster").then(Commands.literal("json").executes(BotCommand::rosterJson));
    }

    private static int rosterJson(CommandContext<CommandSourceStack> c) {
        SlotManager sm = SlotManager.get();
        JsonArray list = new JsonArray();
        for (SlotData.StreamerEntry e : sm.allStreamers()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", e.uuid.toString());
            o.addProperty("name", e.name);
            o.addProperty("granted", e.granted);
            o.addProperty("kind", e.kind);
            o.addProperty("slots", sm.allowance(e));
            o.addProperty("override", e.slotOverride);
            o.addProperty("bonus", e.bonusSlots);
            o.addProperty("used", e.used());
            JsonArray inv = new JsonArray();
            e.invited.forEach((id, n) -> {
                JsonObject i = new JsonObject();
                i.addProperty("uuid", id.toString());
                i.addProperty("name", n);
                inv.add(i);
            });
            o.add("invited", inv);
            list.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("defaultSlots", de.kronwerke.core.config.KronwerkeConfig.DEFAULT_SLOTS.get());
        out.add("streamers", list);
        answer(c, "OK " + GSON.toJson(out));
        return 1;
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
        // the streamer by UUID (the bot sends it, names change) or by name
        SlotData.StreamerEntry e = null;
        try {
            e = sm.find(java.util.UUID.fromString(streamerName));
        } catch (IllegalArgumentException notUuid) {
            e = sm.entryByName(streamerName);
        }
        if (e == null) {
            answer(c, "ERR " + streamerName + " has no place of their own (grant them first)");
            return 0;
        }
        var profile = player(c);
        if (profile.isEmpty()) {
            answer(c, "ERR no Minecraft account named " + player);
            return 0;
        }
        SlotManager.Result r = invite ? sm.invite(e, profile.get(), false) : sm.revoke(e, profile.get());
        if (!invite && sm.gone(profile.get(), r)) {
            // nothing of theirs is left here: the bot may forget them too (it got stuck on ERR before)
            answer(c, "OK " + player + " was not on the whitelist any more, slot freed, now " + e.used() + "/" + sm.allowance(e));
            return 1;
        }
        String used = e.used() + "/" + sm.allowance(e);
        switch (r) {
            case OK -> answer(c, "OK " + (invite ? "slots used " : "slot freed, now ") + used + " uuid " + profile.get().getId());
            case NO_SLOTS_LEFT -> answer(c, "ERR " + e.name + " has no free slots (" + used + ")");
            case ALREADY_INVITED -> answer(c, "ERR " + player + " already has a slot");
            case ALREADY_WHITELISTED -> answer(c, "ERR " + player + " is already on the whitelist");
            case UNKNOWN_PLAYER -> answer(c, "ERR no Minecraft account named " + player);
            case NOT_INVITED_BY_YOU -> answer(c, "ERR " + player + " was not invited by " + e.name);
            case NOT_GRANTED -> answer(c, "ERR " + e.name + " has no place of their own (grant them first)");
        }
        return r == SlotManager.Result.OK ? 1 : 0;
    }

    private static int grant(CommandContext<CommandSourceStack> c, int slots) {
        String player = StringArgumentType.getString(c, "player");
        String kind = opt(c, "kind");
        if (kind != null && !java.util.List.of(SlotManager.STREAMER, SlotManager.SEASON1, SlotManager.TEAM).contains(kind)) {
            answer(c, "ERR kind must be streamer, season1 or team");
            return 0;
        }
        SlotManager sm = SlotManager.get();
        var profile = player(c);
        if (profile.isEmpty()) {
            answer(c, "ERR no Minecraft account named " + player);
            return 0;
        }
        SlotManager.Result r = sm.grant(profile.get(), slots, kind);
        SlotData.StreamerEntry e = sm.find(profile.get().getId());
        switch (r) {
            case OK -> answer(c, "OK " + e.name + " is whitelisted as " + e.kind + " with " + sm.allowance(e) + " slots of their own, uuid " + e.uuid);
            // the same grant again (the bot's restore after a restart): the numbers follow, nothing else
            case ALREADY_WHITELISTED -> answer(c, "OK " + e.name + " already had a place, now " + e.kind + " with " + sm.allowance(e) + " slots, uuid " + e.uuid);
            case ALREADY_INVITED -> answer(c, "ERR " + player + " already has a slot from " + sm.inviterOf(profile.get().getId()).name);
            default -> answer(c, "ERR " + r);
        }
        return r == SlotManager.Result.OK || r == SlotManager.Result.ALREADY_WHITELISTED ? 1 : 0;
    }

    private static int ungrant(CommandContext<CommandSourceStack> c) {
        String player = StringArgumentType.getString(c, "player");
        var profile = player(c);
        if (profile.isEmpty()) {
            answer(c, "OK " + player + " was not on the whitelist any more");
            return 1;
        }
        SlotManager.Result r = SlotManager.get().ungrant(profile.get());
        if (SlotManager.get().gone(profile.get(), r)) {
            answer(c, "OK " + player + " was not on the whitelist any more");
            return 1;
        }
        switch (r) {
            case OK -> answer(c, "OK " + player + " is off the whitelist, with every slot they gave");
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
                t.addProperty("points", e.getValue()); // the bar's unit: items times their weight
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
