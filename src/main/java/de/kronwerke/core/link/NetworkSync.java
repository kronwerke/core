package de.kronwerke.core.link;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.Text;
import de.kronwerke.core.config.KronwerkeConfig;
import de.kronwerke.core.mixin.PlayerInfoUpdateAccessor;
import de.kronwerke.core.slot.SlotData;
import de.kronwerke.core.slot.SlotManager;
import de.kronwerke.core.tab.TabList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kronwerke's servers as one: chat, joins and leaves, and the tab list across every server
 * of the launcher's network, over the launcher's bus. What is shared is set in the launcher
 * (its Network page); this side follows the policy the launcher sends.
 * <p>
 * main also tells the side worlds who is a streamer and what the running goal is, so their
 * tab list looks the same.
 */
public final class NetworkSync {
    /** Another server of the network: how to name it. */
    public record Peer(String name, String label, int color, String role, String host, int port, boolean bus, boolean running) {}

    /** A player on another server, as shown in this server's tab list. */
    record Remote(UUID uuid, String name, String rank, int ping, String skin, String skinSig, String server) {}

    private static MinecraftServer server;
    private static Bus bus;
    private static JsonObject policy = new JsonObject();
    private static final Map<String, Peer> peers = new LinkedHashMap<>();
    /** Server thread only: players on the other servers, by server. */
    private static final Map<String, List<Remote>> remote = new HashMap<>();
    private static final Set<UUID> shown = new HashSet<>();
    /** Sent away at the door: their logout is no leave. */
    private static final Set<UUID> refused = new HashSet<>();
    private static volatile Set<String> streamers = Set.of();
    private static volatile String goalLine = "";
    private static int ticks;

    private NetworkSync() {
    }

    // ---- life ----

    public static void start(MinecraftServer s) {
        server = s;
        bus = Bus.fromLauncher(m -> s.execute(() -> handle(m)), () -> s.execute(NetworkSync::resync));
        if (bus != null) bus.start();
    }

    public static void stop() {
        if (bus != null) bus.stop();
        bus = null;
        remote.clear();
        shown.clear();
        peers.clear();
    }

    public static boolean on() {
        return bus != null && bus.connected();
    }

    // ---- what other parts ask ----

    /** For the tab list on a side world: a streamer according to main. */
    public static boolean streamer(String name) {
        return streamers.contains(name.toLowerCase());
    }

    /** For the tab list on a side world: the running goal according to main, or empty. */
    public static String goalLine() {
        return goalLine;
    }

    /** Players on the other servers of the network that this one shows. */
    public static int remoteCount() {
        int n = 0;
        for (List<Remote> l : remote.values()) n += l.size();
        return n;
    }

    private static String chatMode() {
        return policy.has("chat") ? policy.get("chat").getAsString() : "network";
    }

    private static boolean flag(String key) {
        return policy.has(key) && policy.get(key).getAsBoolean();
    }

    // ---- from the bus ----

    private static void handle(JsonObject m) {
        String op = str(m, "op");
        switch (op) {
            case "welcome", "policy" -> {
                if (m.get("policy") instanceof JsonObject p) policy = p;
                peers.clear();
                if (m.get("peers") instanceof JsonArray a) {
                    for (JsonElement e : a) {
                        JsonObject o = e.getAsJsonObject();
                        peers.put(str(o, "name"), new Peer(str(o, "name"), str(o, "label"), color(str(o, "color")), str(o, "role"),
                                str(o, "host"), num(o, "port", 25565), o.has("bus") && o.get("bus").getAsBoolean(),
                                o.has("running") && o.get("running").getAsBoolean()));
                    }
                }
                ownLabel = str(m, "label");
                ownColor = color(str(m, "color"));
                String r = str(m, "reset");
                try {
                    resetAt = r.isEmpty() ? 0 : java.time.Instant.parse(r).toEpochMilli();
                } catch (RuntimeException e) {
                    resetAt = 0;
                }
                for (String gone : new ArrayList<>(remote.keySet())) {
                    if (!peers.containsKey(gone) || !flag("tablist")) setRemote(gone, List.of());
                }
                if (flag("tablist") && m.get("players") instanceof JsonObject all) {
                    for (String from : all.keySet()) setRemote(from, remotes(from, all.getAsJsonArray(from)));
                }
                if (Role.main()) sendState();
            }
            case "chat" -> {
                if (!chatMode().equals("network")) return;
                String rank = m.get("extra") instanceof JsonObject x ? str(x, "rank") : "member";
                MutableComponent line = mark(str(m, "from")).append(TabList.badge(rank))
                        .append(Component.literal("<" + str(m, "player") + "> ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(str(m, "text")).withStyle(ChatFormatting.WHITE));
                server.getPlayerList().broadcastSystemMessage(line, false);
            }
            case "join", "leave" -> {
                if (!flag("joins")) return;
                String to = str(m, "to");
                if (op.equals("join") && !str(m, "via").isEmpty()) return; // a move: the leave already said it
                if (op.equals("leave") && to.equals(Role.server())) return; // coming here: the join says it
                MutableComponent line = mark(str(m, "from"));
                if (op.equals("leave") && !to.isEmpty()) {
                    Peer target = peers.get(to);
                    line.append(Text.t("network.moved", "%s geht nach %s", str(m, "player"), target == null ? to : target.label()).withStyle(ChatFormatting.YELLOW));
                } else {
                    line.append(Component.translatable(op.equals("join") ? "multiplayer.player.joined" : "multiplayer.player.left", str(m, "player")).withStyle(ChatFormatting.YELLOW));
                }
                server.getPlayerList().broadcastSystemMessage(line, false);
            }
            case "players" -> {
                if (flag("tablist")) setRemote(str(m, "from"), remotes(str(m, "from"), m.getAsJsonArray("list")));
            }
            case "say" -> server.getPlayerList().broadcastSystemMessage(Component.literal("[" + str(m, "who") + "] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(str(m, "text")).withStyle(ChatFormatting.WHITE)), false);
            case "evacuate" -> de.kronwerke.core.portal.Travel.evacuate();
            case "sent" -> de.kronwerke.core.portal.Travel.sent(str(m, "id"), m.has("delivered") && m.get("delivered").getAsBoolean());
            case "message" -> {
                String topic = str(m, "topic");
                if ((topic.startsWith("player.") || topic.startsWith("kw.items")) && m.get("data") instanceof JsonObject d) {
                    if (!d.has("id") && !str(m, "id").isEmpty()) d.addProperty("id", str(m, "id"));
                    de.kronwerke.core.portal.Travel.onMessage(str(m, "from"), topic, d);
                    return;
                }
                if (topic.equals("kw.state") && m.get("data") instanceof JsonObject d) {
                    Set<String> s = new HashSet<>();
                    if (d.get("streamers") instanceof JsonArray a) for (JsonElement e : a) s.add(e.getAsString().toLowerCase());
                    streamers = s;
                    goalLine = str(d, "goal");
                }
            }
            default -> {
                // pong, sent, error: nothing to do
            }
        }
    }

    /** After every (re)connect: who is here. */
    private static void resync() {
        sendPlayers(null);
        if (Role.main()) sendState();
    }

    // ---- to the bus ----

    public static void onChat(ServerChatEvent e) {
        if (!on()) return;
        ServerPlayer p = e.getPlayer();
        String mode = chatMode();
        JsonObject m = op("chat");
        m.addProperty("player", p.getGameProfile().getName());
        m.addProperty("uuid", p.getUUID().toString());
        m.addProperty("text", e.getRawText());
        m.addProperty("scope", mode);
        JsonObject extra = new JsonObject();
        extra.addProperty("rank", TabList.rankOf(p));
        m.add("extra", extra);
        if (mode.equals("radius")) {
            // only who stands close hears it; the console still gets the line
            e.setCanceled(true);
            int r = policy.has("radius") ? policy.get("radius").getAsInt() : 100;
            Component line = Component.translatable("chat.type.text", p.getDisplayName(), e.getMessage());
            for (ServerPlayer o : server.getPlayerList().getPlayers()) {
                if (o.level() == p.level() && o.distanceToSqr(p) <= (double) r * r) o.sendSystemMessage(line);
            }
            server.sendSystemMessage(line);
        }
        bus.send(m);
    }

    /** A player came: show them the other servers' players, then tell the others. */
    public static void onJoin(ServerPlayer p) {
        for (List<Remote> l : remote.values()) l.removeIf(r -> r.uuid().equals(p.getUUID()));
        shown.remove(p.getUUID());
        List<Remote> all = new ArrayList<>();
        for (List<Remote> l : remote.values()) all.addAll(l);
        if (!all.isEmpty()) p.connection.send(addPacket(all));
        if (!on()) return;
        JsonObject m = op("join");
        m.addProperty("player", p.getGameProfile().getName());
        m.addProperty("uuid", p.getUUID().toString());
        String via = de.kronwerke.core.portal.Travel.arrivedFrom(p.getUUID());
        if (via != null) m.addProperty("via", via);
        bus.send(m);
        sendPlayers(null);
    }

    public static void onLeave(ServerPlayer p) {
        if (refused.remove(p.getUUID()) || !on()) return;
        JsonObject m = op("leave");
        m.addProperty("player", p.getGameProfile().getName());
        m.addProperty("uuid", p.getUUID().toString());
        String to = de.kronwerke.core.portal.Travel.leavingTo(p.getUUID());
        if (to != null && peers.containsKey(to)) m.addProperty("to", to);
        bus.send(m);
        sendPlayers(p.getUUID());
    }

    /** Pings change: the list goes out again every 30 seconds; main also sends its state. */
    public static void onTick(ServerTickEvent.Post e) {
        if (server == null || ++ticks < 600) return;
        ticks = 0;
        if (!on()) return;
        sendPlayers(null);
        if (Role.main()) sendState();
    }

    private static void sendPlayers(UUID without) {
        if (!on()) return;
        JsonArray list = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(without)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getGameProfile().getName());
            o.addProperty("uuid", p.getUUID().toString());
            o.addProperty("rank", TabList.rankOf(p));
            o.addProperty("ping", p.connection.latency());
            Property tex = p.getGameProfile().getProperties().get("textures").stream().findFirst().orElse(null);
            if (tex != null) {
                o.addProperty("skin", tex.value());
                if (tex.signature() != null) o.addProperty("skinSig", tex.signature());
            }
            list.add(o);
        }
        JsonObject m = op("players");
        m.add("list", list);
        bus.send(m);
    }

    /** main only: streamers and the running goal for the side worlds' tab lists. */
    private static void sendState() {
        if (!on() || !Role.main()) return;
        JsonObject d = new JsonObject();
        JsonArray s = new JsonArray();
        try {
            for (SlotData.StreamerEntry e : SlotManager.get().allStreamers()) {
                if (SlotManager.get().allowance(e) > 2) s.add(e.name);
            }
        } catch (RuntimeException ignored) {
            // slots not loaded yet
        }
        d.add("streamers", s);
        d.addProperty("goal", TabList.goalLine());
        JsonObject m = op("send");
        m.addProperty("to", "*");
        m.addProperty("topic", "kw.state");
        m.add("data", d);
        bus.send(m);
    }

    // ---- the tab list ----

    private static List<Remote> remotes(String from, JsonArray list) {
        List<Remote> out = new ArrayList<>();
        if (list == null) return out;
        for (JsonElement e : list) {
            JsonObject o = e.getAsJsonObject();
            try {
                UUID id = UUID.fromString(str(o, "uuid"));
                if (server.getPlayerList().getPlayer(id) != null) continue; // here already, the real entry wins
                out.add(new Remote(id, str(o, "name"), str(o, "rank"), o.has("ping") ? o.get("ping").getAsInt() : 0,
                        str(o, "skin"), str(o, "skinSig"), from));
            } catch (IllegalArgumentException ignored) {
                // no uuid
            }
        }
        return out;
    }

    /** Replaces one server's players in every tab list here. */
    private static void setRemote(String from, List<Remote> now) {
        List<Remote> before = remote.getOrDefault(from, List.of());
        Set<UUID> keep = new HashSet<>();
        for (Remote r : now) keep.add(r.uuid());
        List<UUID> gone = new ArrayList<>();
        for (Remote r : before) {
            if (!keep.contains(r.uuid()) && server.getPlayerList().getPlayer(r.uuid()) == null) gone.add(r.uuid());
        }
        if (now.isEmpty()) remote.remove(from);
        else remote.put(from, new ArrayList<>(now));
        if (!gone.isEmpty()) {
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(gone));
            gone.forEach(shown::remove);
        }
        if (!now.isEmpty()) {
            server.getPlayerList().broadcastAll(addPacket(now));
            for (Remote r : now) shown.add(r.uuid());
        }
    }

    private static ClientboundPlayerInfoUpdatePacket addPacket(List<Remote> list) {
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>();
        for (Remote r : list) {
            GameProfile profile = new GameProfile(r.uuid(), r.name());
            if (!r.skin().isEmpty()) profile.getProperties().put("textures", new Property("textures", r.skin(), r.skinSig().isEmpty() ? null : r.skinSig()));
            Peer peer = peers.get(r.server());
            MutableComponent name = TabList.badge(r.rank()).append(Component.literal(r.name()).withStyle(ChatFormatting.GRAY));
            if (peer != null) name.append(Component.literal(" " + peer.label()).withStyle(s -> s.withColor(TextColor.fromRgb(peer.color()))));
            entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(r.uuid(), profile, true, r.ping(), GameType.SURVIVAL, name, null));
        }
        ((PlayerInfoUpdateAccessor) packet).kronwerke$setEntries(entries);
        return packet;
    }

    // ---- small things ----

    /** The other server's mark in front of its lines: its label in its colour. */
    private static MutableComponent mark(String from) {
        Peer p = peers.get(from);
        String label = p == null ? from : p.label();
        int c = p == null ? 0xAAAAAA : p.color();
        return Component.literal("[" + label + "] ").withStyle(s -> s.withColor(TextColor.fromRgb(c)));
    }

    private static int color(String hex) {
        try {
            return hex.startsWith("#") ? Integer.parseInt(hex.substring(1), 16) : 0xAAAAAA;
        } catch (NumberFormatException e) {
            return 0xAAAAAA;
        }
    }

    private static int num(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? Integer.parseInt(o.get(key).getAsString()) : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ---- for the travel between servers ----

    private static volatile String ownLabel = "";
    private static volatile int ownColor = 0xAAAAAA;
    private static volatile long resetAt;

    public static Peer peer(String name) {
        return peers.get(name);
    }

    /** The server a portal leads to: main from a side world, the first side world with the role from main. */
    public static Peer portalTarget() {
        for (Peer p : peers.values()) {
            if (Role.main() ? p.role().equals(KronwerkeConfig.PORTAL_TARGET.get()) : p.role().equals("main")) return p;
        }
        return null;
    }

    public static String ownLabel() {
        return ownLabel.isEmpty() ? Role.server() : ownLabel;
    }

    public static int ownColor() {
        return ownColor;
    }

    /** When this world is reset next (ms), or 0. */
    public static long resetAt() {
        return resetAt;
    }

    /** A message for one mod of the network (or "*"); topic starting with player. needs sync.players. */
    public static void send(String to, String topic, JsonObject data, String id) {
        if (!on()) return;
        JsonObject m = op("send");
        m.addProperty("to", to);
        m.addProperty("topic", topic);
        m.add("data", data);
        if (id != null) m.addProperty("id", id);
        bus.send(m);
    }

    /** Tells the launcher that every player has left (after an evacuate). */
    public static void evacuated() {
        if (on()) bus.send(op("evacuated"));
    }

    private static JsonObject op(String op) {
        JsonObject m = new JsonObject();
        m.addProperty("op", op);
        return m;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    /** Players who may only arrive through a move: everyone but operators, on a side world. */
    public static boolean refuse(ServerPlayer p) {
        if (Role.main() || KronwerkeConfig.DIRECT_JOIN.get() || p.hasPermissions(2)) return false;
        if (de.kronwerke.core.portal.Travel.expected(p.getUUID())) return false;
        KronwerkeCore.LOGGER.info("{} joined {} directly; sent away", p.getGameProfile().getName(), Role.server());
        refused.add(p.getUUID());
        p.connection.disconnect(Text.t("network.direct", "Diese Welt erreichst du nur über kronwerke.net."));
        return true;
    }
}
