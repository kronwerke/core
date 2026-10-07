package de.kronwerke.core.share.ae2;

import appeng.api.config.Actionable;
import appeng.api.features.Locatables;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.me.cluster.implementations.QuantumCluster;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * AE2's Quantum Network Bridge between servers. Two rings with the two singularities of one pair,
 * one ring on main and one on another server, link the networks: the other server's network sees
 * main's ME storage as a drive of its own and can take out of it and put into it. Channels,
 * autocrafting and the other server's own storage stay on each side.
 * <p>
 * Main's storage stays on main. What the other side takes is fetched first (a short wait the first
 * time, a stack ahead after that) and what it does not use goes back; what it puts in goes to main
 * at once. Everything moves through {@link Share}, so nothing doubles.
 */
public final class BridgeLayer implements Layer {
    static final String NAME = "ae2.qnb";
    private static final int INV_EVERY = 10;
    private static final long FRESH_NS = 15_000_000_000L;

    private final Field objects;
    private final Field connection;
    private MinecraftServer server;
    private int ticks;

    /** Rings without a partner on this server, by singularity frequency. */
    private final Map<Long, QuantumCluster> alone = new HashMap<>();
    /** What the others host and want, from their states. */
    private final Map<String, Set<Long>> peerHosts = new HashMap<>();
    private final Map<String, Set<Long>> peerWants = new HashMap<>();
    private final Map<String, Long> peerAt = new HashMap<>();
    /** Host: per peer and frequency, what that peer was last told about main's storage. */
    private final Map<String, Map<AEKey, Long>> told = new HashMap<>();
    private final Map<String, Long> toldVersion = new HashMap<>();
    /** Other side: the linked storages, by frequency. */
    private final Map<Long, RemoteStorage> remotes = new HashMap<>();
    private final Map<AEKey, String> keyText = new LinkedHashMap<>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<AEKey, String> e) {
            return size() > 50_000;
        }
    };
    private final Map<String, AEKey> textKey = new LinkedHashMap<>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AEKey> e) {
            return size() > 50_000;
        }
    };
    private long fetched, put;

    private BridgeLayer(Field objects, Field connection) {
        this.objects = objects;
        this.connection = connection;
    }

    public static BridgeLayer create() {
        try {
            Field o = Locatables.Type.class.getDeclaredField("objects");
            o.setAccessible(true);
            Field c = QuantumCluster.class.getDeclaredField("connection");
            c.setAccessible(true);
            return new BridgeLayer(o, c);
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Shared network: AE2's quantum bridges are not shared ({})", e.toString());
            return null;
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    // ---- keys over the bus ----

    String text(AEKey k) {
        return keyText.computeIfAbsent(k, x -> x.toTagGeneric(server.registryAccess()).toString());
    }

    /** Null when this server does not know that kind of thing. */
    AEKey key(String s) {
        AEKey k = textKey.get(s);
        if (k != null) return k;
        try {
            k = AEKey.fromTagGeneric(server.registryAccess(), TagParser.parseTag(s));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
            k = null;
        }
        if (k != null) textKey.put(s, k);
        return k;
    }

    // ---- the rings here ----

    private void findRings() {
        alone.clear();
        Map<?, ?> all;
        try {
            all = (Map<?, ?>) objects.get(Locatables.quantumNetworkBridges());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        for (Object o : all.values()) {
            if (!(o instanceof QuantumCluster c) || c.isDestroyed()) continue;
            QuantumBridgeBlockEntity center = c.getCenter();
            if (center == null || center.isRemoved() || !center.isFormed()) continue;
            long f = center.getQEFrequency();
            if (f == 0) continue;
            Object w;
            try {
                w = connection.get(c);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            boolean paired = w instanceof appeng.me.service.helpers.ConnectionWrapper cw && cw.getConnection() != null;
            if (!paired) alone.put(f, c);
        }
    }

    private static IGrid grid(QuantumCluster c) {
        QuantumBridgeBlockEntity center = c.getCenter();
        if (center == null) return null;
        IGridNode n = center.getMainNode().getNode();
        return n == null ? null : n.getGrid();
    }

    /** main hosts its storage; every other server links to it. */
    private static boolean hosting() {
        return Role.main();
    }

    // ---- each tick ----

    @Override
    public void tick(MinecraftServer s) {
        server = s;
        ticks++;
        if (ticks % 10 == 1) {
            findRings();
            JsonObject state = new JsonObject();
            JsonArray list = new JsonArray();
            for (long f : alone.keySet()) list.add(f);
            state.add(hosting() ? "host" : "want", list);
            Share.state(this, state);
        }
        long now = System.nanoTime();
        if (hosting()) {
            if (ticks % INV_EVERY == 0) {
                for (String peer : Share.peers()) {
                    if (now - peerAt.getOrDefault(peer, 0L) > FRESH_NS) continue;
                    for (long f : peerWants.getOrDefault(peer, Set.of())) {
                        QuantumCluster c = alone.get(f);
                        if (c != null) sendInventory(peer, f, c, false);
                    }
                }
            }
        } else {
            linkRemotes(now);
            for (RemoteStorage r : remotes.values()) r.tick(this);
        }
    }

    /** Host: what changed in main's storage since that peer was told, or all of it. */
    private void sendInventory(String peer, long f, QuantumCluster c, boolean full) {
        IGrid g = grid(c);
        if (g == null) return;
        String id = peer + "|" + f;
        Map<AEKey, Long> before = full ? null : told.get(id);
        Map<AEKey, Long> now = new HashMap<>();
        for (Object2LongMap.Entry<AEKey> e : g.getStorageService().getCachedInventory()) {
            if (e.getLongValue() > 0) now.put(e.getKey(), e.getLongValue());
        }
        JsonObject set = new JsonObject();
        JsonArray del = new JsonArray();
        for (Map.Entry<AEKey, Long> e : now.entrySet()) {
            Long was = before == null ? null : before.get(e.getKey());
            if (was == null || was.longValue() != e.getValue()) set.addProperty(text(e.getKey()), e.getValue());
        }
        if (before != null) {
            for (AEKey k : before.keySet()) if (!now.containsKey(k)) del.add(text(k));
        }
        if (before != null && set.size() == 0 && del.isEmpty()) return;
        long v = toldVersion.getOrDefault(id, 0L) + 1;
        toldVersion.put(id, v);
        told.put(id, now);
        JsonObject m = new JsonObject();
        m.addProperty("t", "inv");
        m.addProperty("f", f);
        m.addProperty("v", v);
        m.addProperty("full", before == null);
        m.add("set", set);
        m.add("del", del);
        Share.tell(this, peer, m);
    }

    /** Other side: one linked storage per ring that main hosts, mounted into the ring's network. */
    private void linkRemotes(long now) {
        String host = null;
        for (String p : Share.peers()) {
            if (now - peerAt.getOrDefault(p, 0L) < FRESH_NS && !peerHosts.getOrDefault(p, Set.of()).isEmpty()) host = p;
        }
        Set<Long> keep = new HashSet<>();
        for (Map.Entry<Long, QuantumCluster> e : alone.entrySet()) {
            long f = e.getKey();
            if (host == null || !peerHosts.get(host).contains(f)) continue;
            IGrid g = grid(e.getValue());
            if (g == null) continue;
            keep.add(f);
            RemoteStorage r = remotes.computeIfAbsent(f, x -> new RemoteStorage(this, x));
            r.attach(host, g);
        }
        for (RemoteStorage r : remotes.values()) {
            if (!keep.contains(r.frequency)) r.detach();
        }
        // what is still held here for rings that went away goes back as soon as main hosts them again
        if (host != null) {
            for (long f : EscrowData.get(server).frequencies()) {
                if (!keep.contains(f) && peerHosts.get(host).contains(f)) remotes.computeIfAbsent(f, x -> new RemoteStorage(this, x)).flushAll(host);
            }
        }
    }

    MinecraftServer server() {
        return server;
    }

    // ---- messages ----

    @Override
    public void onState(String from, JsonObject state) {
        peerAt.put(from, System.nanoTime());
        peerHosts.put(from, longs(state.getAsJsonArray("host")));
        Set<Long> wants = longs(state.getAsJsonArray("want"));
        Set<Long> before = peerWants.put(from, wants);
        // a ring that just came up there gets everything
        for (long f : wants) {
            if (before == null || !before.contains(f)) told.remove(from + "|" + f);
        }
    }

    private static Set<Long> longs(JsonArray a) {
        Set<Long> out = new HashSet<>();
        if (a != null) for (JsonElement e : a) out.add(e.getAsLong());
        return out;
    }

    @Override
    public void onTell(MinecraftServer s, String from, JsonObject d) {
        server = s;
        long f = d.get("f").getAsLong();
        switch (d.get("t").getAsString()) {
            case "inv" -> {
                RemoteStorage r = remotes.get(f);
                if (r != null) r.inventory(this, from, d);
            }
            case "sync" -> {
                QuantumCluster c = alone.get(f);
                if (hosting() && c != null) sendInventory(from, f, c, true);
            }
            case "fetch" -> fetch(from, f, d.get("k").getAsString(), d.get("n").getAsLong());
            default -> {
            }
        }
    }

    /** Host: the other side wants something out of main's storage; it goes over as a give. */
    private void fetch(String from, long f, String k, long n) {
        QuantumCluster c = alone.get(f);
        AEKey key = key(k);
        if (!hosting() || c == null || key == null || n <= 0 || !Share.canGive(from)) return;
        IGrid g = grid(c);
        if (g == null) return;
        long got = g.getStorageService().getInventory().extract(key, n, Actionable.MODULATE, IActionSource.ofMachine(c));
        if (got <= 0) return;
        JsonObject what = new JsonObject();
        what.addProperty("d", "fetch");
        what.addProperty("f", f);
        JsonArray items = new JsonArray();
        JsonArray one = new JsonArray();
        one.add(k);
        one.add(got);
        items.add(one);
        what.add("items", items);
        if (!Share.give(this, from, "fetch|" + f, what)) {
            g.getStorageService().getInventory().insert(key, got, Actionable.MODULATE, IActionSource.ofMachine(c));
            return;
        }
        fetched += got;
    }

    @Override
    public JsonObject receive(MinecraftServer s, String from, JsonObject data) {
        server = s;
        long f = data.get("f").getAsLong();
        if (data.get("d").getAsString().equals("fetch")) {
            // other side: what main sent is held here for this ring's network; always fits
            EscrowData e = EscrowData.get(s);
            for (JsonElement el : data.getAsJsonArray("items")) {
                JsonArray a = el.getAsJsonArray();
                AEKey k = key(a.get(0).getAsString());
                if (k == null) return data; // read before changing anything: all of it goes back
            }
            for (JsonElement el : data.getAsJsonArray("items")) {
                JsonArray a = el.getAsJsonArray();
                e.held(f).add(key(a.get(0).getAsString()), a.get(1).getAsLong());
            }
            e.setDirty();
            RemoteStorage r = remotes.get(f);
            if (r != null) r.arrived(data);
            return null;
        }
        // host: into main's storage, whatever fits
        QuantumCluster c = alone.get(f);
        IGrid g = c == null || !hosting() ? null : grid(c);
        if (g == null) return data;
        JsonArray rest = new JsonArray();
        MEStorage inv = g.getStorageService().getInventory();
        for (JsonElement el : data.getAsJsonArray("items")) {
            JsonArray a = el.getAsJsonArray();
            AEKey k = key(a.get(0).getAsString());
            long n = a.get(1).getAsLong();
            long in = k == null ? 0 : inv.insert(k, n, Actionable.MODULATE, IActionSource.ofMachine(c));
            put += in;
            if (in < n) {
                JsonArray r = new JsonArray();
                r.add(a.get(0));
                r.add(n - in);
                rest.add(r);
            }
        }
        if (rest.isEmpty()) return null;
        JsonObject back = new JsonObject();
        back.addProperty("d", "put");
        back.addProperty("f", f);
        back.add("items", rest);
        return back;
    }

    @Override
    public JsonObject refund(MinecraftServer s, JsonObject data) {
        server = s;
        long f = data.get("f").getAsLong();
        if (data.get("d").getAsString().equals("put")) {
            // other side: main had no room; it stays here, usable, and is not offered again for a while
            EscrowData e = EscrowData.get(s);
            for (JsonElement el : data.getAsJsonArray("items")) {
                JsonArray a = el.getAsJsonArray();
                AEKey k = key(a.get(0).getAsString());
                if (k == null) continue;
                e.held(f).add(k, a.get(1).getAsLong());
                RemoteStorage r = remotes.get(f);
                if (r != null) r.full(k);
            }
            e.setDirty();
            return null;
        }
        // host: a fetch came back; into main's storage again
        QuantumCluster c = alone.get(f);
        IGrid g = c == null ? null : grid(c);
        if (g == null) return data;
        JsonArray rest = new JsonArray();
        for (JsonElement el : data.getAsJsonArray("items")) {
            JsonArray a = el.getAsJsonArray();
            AEKey k = key(a.get(0).getAsString());
            long n = a.get(1).getAsLong();
            long in = k == null ? 0 : g.getStorageService().getInventory().insert(k, n, Actionable.MODULATE, IActionSource.ofMachine(c));
            if (in < n) {
                JsonArray r = new JsonArray();
                r.add(a.get(0));
                r.add(n - in);
                rest.add(r);
            }
        }
        if (rest.isEmpty()) return null;
        data.add("items", rest);
        return data;
    }

    @Override
    public void onStopping(MinecraftServer s) {
        server = s;
        if (hosting()) return;
        String host = null;
        for (String p : Share.peers()) if (!peerHosts.getOrDefault(p, Set.of()).isEmpty()) host = p;
        if (host == null) return;
        for (long f : EscrowData.get(s).frequencies()) {
            if (peerHosts.get(host).contains(f)) remotes.computeIfAbsent(f, x -> new RemoteStorage(this, x)).flushAll(host);
        }
        for (RemoteStorage r : remotes.values()) r.detach();
    }

    @Override
    public String describe() {
        if (hosting()) {
            int links = 0;
            for (Set<Long> w : peerWants.values()) for (long f : w) if (alone.containsKey(f)) links++;
            return alone.size() + " rings without a partner here, " + links + " linked to other servers, " + fetched + " taken out, " + put + " put in";
        }
        long held = 0;
        for (long f : EscrowData.get(server).frequencies()) for (Object2LongMap.Entry<AEKey> e : EscrowData.get(server).held(f)) held += e.getLongValue();
        return remotes.values().stream().filter(RemoteStorage::attached).count() + " rings linked to main's storage, " + held + " held here";
    }

    /** What the linked storage is called in AE2's tooltips: main's label. */
    static Component label() {
        for (String p : Share.peers()) {
            NetworkSync.Peer q = NetworkSync.peer(p);
            if (q != null && q.role().equals("main")) return Component.literal(q.label());
        }
        return Component.literal("main");
    }
}
