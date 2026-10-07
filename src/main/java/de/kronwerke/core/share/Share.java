package de.kronwerke.core.share;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.NetworkSync;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared network: lets the mods' own wireless things (quantum entangloporters, quantum bridges,
 * ...) work between the servers of a network as if both worlds were one. Each mod has a {@link Layer};
 * this class carries what the layers give each other over the launcher's bus, so that nothing is
 * lost and nothing doubles:
 * <ul>
 * <li>Whatever a layer gives has already left its world and sits in the outbox (saved with the
 * world) until the other server answers.</li>
 * <li>Gives to one server are numbered, per start of this server. The receiver takes them in order,
 * each exactly once, and answers each with what did not fit; a give that arrives twice gets the
 * same answer again.</li>
 * <li>Every world has an epoch. When the other world was reset, what it never answered comes back.</li>
 * </ul>
 * Needs {@code sync.players} on both servers.
 */
public final class Share {
    public static final String TOPIC = "kw.share";
    private static final long RESEND_MS = 5000;

    private static final Map<String, Layer> layers = new LinkedHashMap<>();
    /** The other servers' world epochs, as they last said. */
    private static final Map<String, String> epochs = new HashMap<>();
    /** layer|peer|tag: when its last give was answered (System.nanoTime). */
    private static final Map<String, Long> answeredAt = new HashMap<>();
    private static final Map<String, Long> errorAt = new HashMap<>();
    private static MinecraftServer server;
    private static ShareData data;
    private static int ticks;

    private Share() {
    }

    // ---- life ----

    public static void start(MinecraftServer s) {
        server = s;
        data = ShareData.get(s);
        layers.clear();
        epochs.clear();
        answeredAt.clear();
        if (ModList.get().isLoaded("mekanism")) add(de.kronwerke.core.share.mek.QuantumLayer.create());
        if (ModList.get().isLoaded("ae2")) add(de.kronwerke.core.share.ae2.BridgeLayer.create());
        if (ModList.get().isLoaded("fluxnetworks")) add(de.kronwerke.core.share.flux.FluxLayer.create());
        if (ModList.get().isLoaded("powah")) add(de.kronwerke.core.share.powah.EnderLayer.create());
        if (ModList.get().isLoaded("ftbquests") && ModList.get().isLoaded("ftbteams")) add(de.kronwerke.core.share.ftb.QuestLayer.create());
        if (!layers.isEmpty()) KronwerkeCore.LOGGER.info("Shared network: {} (epoch {})", String.join(", ", layers.keySet()), data.epoch);
    }

    private static void add(Layer l) {
        if (l != null) layers.put(l.name(), l);
    }

    public static void stop() {
        if (server != null && data != null) {
            for (Layer l : layers.values()) {
                try {
                    l.onStopping(server);
                } catch (RuntimeException | LinkageError x) {
                    error(l.name(), x);
                }
            }
        }
        server = null;
        data = null;
        layers.clear();
        epochs.clear();
    }

    /** On the bus with sync.players on: the network is shared. */
    public static boolean on() {
        return server != null && data != null && NetworkSync.on() && NetworkSync.shares();
    }

    /** After every (re)connect to the bus. */
    public static void resync() {
        if (on()) hello();
    }

    public static void onTick(ServerTickEvent.Post e) {
        if (server == null) return;
        ticks++;
        if (!on() || layers.isEmpty()) return;
        if (ticks % 100 == 0) hello();
        for (Layer l : layers.values()) {
            try {
                l.tick(server);
            } catch (RuntimeException | LinkageError x) {
                error(l.name(), x);
            }
        }
        if (ticks % 20 == 0) {
            resend();
            retryStuck();
        }
    }

    // ---- what layers use ----

    /** The other servers of the network that share and are up. */
    public static List<String> peers() {
        List<String> out = new ArrayList<>();
        for (String p : epochs.keySet()) {
            NetworkSync.Peer q = NetworkSync.peer(p);
            if (q != null && q.bus() && q.running()) out.add(p);
        }
        return out;
    }

    /** Tells every other server how things stand here, for the layer of the same name there. */
    public static void state(Layer l, JsonObject s) {
        if (!on()) return;
        JsonObject d = msg("state");
        d.addProperty("layer", l.name());
        d.add("s", s);
        NetworkSync.send("*", TOPIC, d, null);
    }

    /** A message for the same layer on one other server; nothing moves with it, so it may get lost. */
    public static void tell(Layer l, String peer, JsonObject what) {
        if (!on()) return;
        JsonObject d = msg("tell");
        d.addProperty("layer", l.name());
        d.add("d", what);
        NetworkSync.send(peer, TOPIC, d, null);
    }

    /** Whether something of this layer and tag is still on its way to that server. */
    public static boolean busy(Layer l, String peer, String tag) {
        if (data == null) return true;
        for (ShareData.Entry e : data.outbox) {
            if (e.peer.equals(peer) && e.layer.equals(l.name()) && e.tag.equals(tag)) return true;
        }
        return false;
    }

    /** When the last give of this layer and tag to that server was answered (System.nanoTime), or 0. */
    public static long answeredAt(Layer l, String peer, String tag) {
        return answeredAt.getOrDefault(l.name() + "|" + peer + "|" + tag, 0L);
    }

    /** Whether {@link #give} would go out to that server now. */
    public static boolean canGive(String peer) {
        return on() && epochs.containsKey(peer);
    }

    /**
     * Gives the other server something this layer has just taken out of its world. From here on it
     * is in the outbox: placed there, or back here through {@link Layer#refund}. Call
     * {@link #canGive} first; when this still returns false, put it back yourself.
     */
    public static boolean give(Layer l, String peer, String tag, JsonObject what) {
        if (!canGive(peer)) return false;
        String pe = epochs.get(peer);
        ShareData.Entry e = new ShareData.Entry(peer, pe, data.run, data.next(peer, pe), l.name(), tag, what);
        data.outbox.add(e);
        data.setDirty();
        transmit(e);
        return true;
    }

    // ---- along with a player ----

    /** Adds every layer's part for a player who moves to another server to the move's extra data. */
    public static JsonObject pack(net.minecraft.server.level.ServerPlayer p, JsonObject extra) {
        if (server == null) return extra;
        for (Layer l : layers.values()) {
            try {
                JsonObject x = l.pack(p);
                if (x == null) continue;
                if (extra == null) extra = new JsonObject();
                extra.add("share:" + l.name(), x);
            } catch (RuntimeException | LinkageError e) {
                error(l.name(), e);
            }
        }
        return extra;
    }

    /** Hands each layer its part of what came along with a player. */
    public static void unpack(java.util.UUID player, JsonObject extra) {
        if (server == null || extra == null) return;
        for (Layer l : layers.values()) {
            if (!(extra.get("share:" + l.name()) instanceof JsonObject x)) continue;
            try {
                l.unpack(server, player, x);
            } catch (RuntimeException | LinkageError e) {
                error(l.name(), e);
            }
        }
    }

    // ---- from the bus ----

    public static void onMessage(String from, JsonObject d) {
        if (server == null || data == null) return;
        try {
            switch (str(d, "k")) {
                case "hello" -> epoch(from, str(d, "ep"));
                case "state" -> {
                    epoch(from, str(d, "ep"));
                    Layer l = layers.get(str(d, "layer"));
                    if (l != null && d.get("s") instanceof JsonObject s) l.onState(from, s);
                }
                case "give" -> take(from, d);
                case "tell" -> {
                    Layer l = layers.get(str(d, "layer"));
                    if (l != null && d.get("d") instanceof JsonObject w) l.onTell(server, from, w);
                }
                case "ack" -> answered(from, str(d, "ep"), str(d, "run"), d.get("seq").getAsLong(), d.get("rest") instanceof JsonObject r ? r : null, false);
                case "gone" -> answered(from, str(d, "was"), str(d, "run"), d.get("seq").getAsLong(), null, true);
                case "nack" -> {
                    long next = d.get("next").getAsLong();
                    String ep = str(d, "ep"), run = str(d, "run");
                    data.outbox.stream().filter(e -> e.peer.equals(from) && e.peerEpoch.equals(ep) && e.run.equals(run) && e.seq >= next)
                            .sorted(Comparator.comparingLong(e -> e.seq)).forEach(Share::transmit);
                }
                default -> {
                    // a newer Core
                }
            }
        } catch (RuntimeException x) {
            error("bus", x);
        }
    }

    /** Learns a server's epoch; what was given to an earlier world of it comes back. */
    private static void epoch(String from, String ep) {
        if (ep.isEmpty()) return;
        String old = epochs.put(from, ep);
        if (ep.equals(old)) return;
        if (old == null) {
            JsonObject h = msg("hello");
            NetworkSync.send(from, TOPIC, h, null);
        }
        for (Iterator<ShareData.Entry> it = data.outbox.iterator(); it.hasNext(); ) {
            ShareData.Entry e = it.next();
            if (e.peer.equals(from) && !e.peerEpoch.equals(ep)) {
                it.remove();
                back(e.layer, e.data);
                KronwerkeCore.LOGGER.info("Shared network: {} has a new world, a give of {} came back", from, e.layer);
            }
        }
        data.setDirty();
    }

    /** A give from another server: placed exactly once, in order. */
    private static void take(String from, JsonObject d) {
        String ep = str(d, "ep"), run = str(d, "run"), sender = ep + "/" + run;
        long seq = d.get("seq").getAsLong();
        epoch(from, ep);
        JsonObject a = new JsonObject();
        a.addProperty("seq", seq);
        a.addProperty("run", run);
        if (!str(d, "to").equals(data.epoch)) {
            // meant for a world of this server that no longer exists
            a.addProperty("k", "gone");
            a.addProperty("ep", data.epoch);
            a.addProperty("was", str(d, "to"));
            NetworkSync.send(from, TOPIC, a, null);
            return;
        }
        long last = data.last.getOrDefault(sender, 0L);
        long floor = d.has("floor") ? d.get("floor").getAsLong() : 1;
        if (last < floor - 1) {
            // everything below floor was answered before; this side lost track (a world saved earlier than it answered)
            last = floor - 1;
            data.last.put(sender, last);
            data.setDirty();
        }
        a.addProperty("ep", data.epoch);
        if (seq <= last) {
            a.addProperty("k", "ack");
            Map<Long, String> known = data.answers.get(sender);
            String rest = known == null ? null : known.get(seq);
            if (rest != null && !rest.isEmpty()) a.add("rest", JsonParser.parseString(rest));
            NetworkSync.send(from, TOPIC, a, null);
            return;
        }
        if (seq > last + 1) {
            a.addProperty("k", "nack");
            a.addProperty("next", last + 1);
            NetworkSync.send(from, TOPIC, a, null);
            return;
        }
        Layer l = layers.get(str(d, "layer"));
        JsonObject what = d.getAsJsonObject("d");
        JsonObject rest;
        if (l == null) {
            rest = what; // this server does not have that mod: all of it goes back
        } else {
            try {
                rest = l.receive(server, from, what);
            } catch (RuntimeException | LinkageError x) {
                // layers check before they change anything, so nothing was placed
                error(l.name(), x);
                rest = what;
            }
        }
        data.answered(sender, seq, rest);
        a.addProperty("k", "ack");
        if (rest != null) a.add("rest", rest);
        NetworkSync.send(from, TOPIC, a, null);
    }

    private static void answered(String from, String peerEpoch, String run, long seq, JsonObject rest, boolean gone) {
        for (Iterator<ShareData.Entry> it = data.outbox.iterator(); it.hasNext(); ) {
            ShareData.Entry e = it.next();
            if (!e.peer.equals(from) || !e.peerEpoch.equals(peerEpoch) || !e.run.equals(run) || e.seq != seq) continue;
            it.remove();
            data.setDirty();
            if (gone) back(e.layer, e.data);
            else if (rest != null) back(e.layer, rest);
            answeredAt.put(e.layer + "|" + from + "|" + e.tag, System.nanoTime());
            return;
        }
    }

    /** Puts back what came back, or keeps it until it fits. */
    private static void back(String layer, JsonObject what) {
        Layer l = layers.get(layer);
        JsonObject left = what;
        if (l != null) {
            try {
                left = l.refund(server, what);
            } catch (RuntimeException | LinkageError x) {
                error(layer, x);
            }
        }
        if (left != null) {
            data.stuck.add(new ShareData.Stuck(layer, left));
            data.setDirty();
        }
    }

    private static void transmit(ShareData.Entry e) {
        e.sentAt = System.currentTimeMillis();
        long floor = e.seq;
        for (ShareData.Entry o : data.outbox) {
            if (o.peer.equals(e.peer) && o.peerEpoch.equals(e.peerEpoch) && o.run.equals(e.run)) floor = Math.min(floor, o.seq);
        }
        JsonObject d = msg("give");
        d.addProperty("run", e.run);
        d.addProperty("to", e.peerEpoch);
        d.addProperty("seq", e.seq);
        d.addProperty("floor", floor);
        d.addProperty("layer", e.layer);
        d.add("d", e.data);
        NetworkSync.send(e.peer, TOPIC, d, null);
    }

    private static void resend() {
        long now = System.currentTimeMillis();
        data.outbox.stream().filter(e -> now - e.sentAt > RESEND_MS && e.peerEpoch.equals(epochs.get(e.peer)))
                .sorted(Comparator.comparingLong(e -> e.seq)).forEach(Share::transmit);
    }

    private static void retryStuck() {
        if (data.stuck.isEmpty()) return;
        List<ShareData.Stuck> now = new ArrayList<>(data.stuck);
        data.stuck.clear();
        for (ShareData.Stuck s : now) back(s.layer(), s.data());
        data.setDirty();
    }

    private static void hello() {
        NetworkSync.send("*", TOPIC, msg("hello"), null);
    }

    private static JsonObject msg(String k) {
        JsonObject d = new JsonObject();
        d.addProperty("k", k);
        d.addProperty("ep", data.epoch);
        return d;
    }

    private static void error(String where, Throwable x) {
        long now = System.currentTimeMillis();
        if (now - errorAt.getOrDefault(where, 0L) < 60_000) return;
        errorAt.put(where, now);
        KronwerkeCore.LOGGER.error("Shared network ({}): {}", where, x.toString(), x);
    }

    static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    /** For /kw share. */
    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        if (data == null) {
            out.add("not running");
            return out;
        }
        out.add("epoch " + data.epoch + (on() ? "" : ", off (bus or sync.players)") + ", peers " + epochs);
        out.add("outbox " + data.outbox.size() + ", waiting for room " + data.stuck.size());
        for (Layer l : layers.values()) {
            String s = l.describe();
            out.add(l.name() + (s.isEmpty() ? "" : ": " + s));
        }
        return out;
    }
}
