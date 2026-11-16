package de.kronwerke.core.share.rs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.networking.NetworkReceiverBlockEntity;
import com.refinedmods.refinedstorage.common.networking.NetworkTransmitterBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.NetworkSync;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Refined Storage's network transmitter between servers, the same way as AE2's quantum bridge. A
 * Network Card bound to a Network Receiver on main is marked with main's name (RsCardMixin); on
 * another server a Network Transmitter with that card shows main's network as one storage of its
 * own network, to take out of and to put into. The receiver's network stays on main, and so does
 * what it stores: what the other side takes is fetched first, what it does not use goes back, what
 * it puts in goes to main at once, all through {@link Share}, so nothing doubles. The receiver's
 * chunk on main has to be loaded. Autocrafting stays on each side.
 */
public final class RsBridgeLayer implements Layer {
    static final String NAME = "rs.link";
    /** Custom data on a network card: the server it was bound on. */
    public static final String SERVER_TAG = "kronwerke_server";
    private static final long FRESH_NS = 15_000_000_000L;
    private static final Actor ACTOR = () -> "Kronwerke";

    private static RsBridgeLayer instance;
    private static final Set<NetworkTransmitterBlockEntity> transmitters = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<NetworkTransmitterBlockEntity> linkedHere = Collections.newSetFromMap(new WeakHashMap<>());

    private final Method cardInventory;
    private MinecraftServer server;
    private int ticks;

    /** Other side: the links its transmitters want, transmitter by link. */
    private final Map<String, NetworkTransmitterBlockEntity> wanted = new HashMap<>();
    private final Map<String, RsRemote> remotes = new HashMap<>();
    /** main: links each peer wants, and which of them have a receiver here. */
    private final Map<String, Set<String>> peerWants = new HashMap<>();
    private final Map<String, Set<String>> peerHosts = new HashMap<>();
    private final Map<String, Long> peerAt = new HashMap<>();
    private final Map<String, Map<String, Long>> told = new HashMap<>();
    private final Map<String, Long> toldVersion = new HashMap<>();
    private final Map<ResourceKey, String> keyText = new LinkedHashMap<>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceKey, String> e) {
            return size() > 50_000;
        }
    };
    private final Map<String, ResourceKey> textKey = new LinkedHashMap<>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceKey> e) {
            return size() > 50_000;
        }
    };
    private long fetched, put;

    private RsBridgeLayer(Method cardInventory) {
        this.cardInventory = cardInventory;
        instance = this;
    }

    public static RsBridgeLayer create() {
        try {
            Method m = NetworkTransmitterBlockEntity.class.getDeclaredMethod("getNetworkCardInventory");
            m.setAccessible(true);
            return new RsBridgeLayer(m);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            KronwerkeCore.LOGGER.warn("Shared network: Refined Storage's transmitters are not shared ({})", e.toString());
            return null;
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    MinecraftServer server() {
        return server;
    }

    // ---- hooks from the mixins ----

    /** RsTransmitterMixin: a transmitter did its work this tick. */
    public static void seen(NetworkTransmitterBlockEntity t) {
        if (t.getLevel() != null && !t.getLevel().isClientSide()) transmitters.add(t);
    }

    /** RsTransmitterMixin: whether this transmitter is linked to main through Kronwerke. */
    public static boolean linked(NetworkTransmitterBlockEntity t) {
        return linkedHere.contains(t);
    }

    /** RsCardMixin: a network card was just bound on this server. */
    public static void bound(ItemStack card) {
        CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, card, tag -> tag.putString(SERVER_TAG, Role.server()));
    }

    /** What a linked transmitter says it is connected to: main's label. */
    public static String labelText() {
        NetworkSync.Peer q = NetworkSync.peerByRole("main");
        return q == null ? "main" : q.label();
    }

    static Component label() {
        NetworkSync.Peer q = NetworkSync.peerByRole("main");
        return Component.literal(q == null ? "main" : q.label());
    }

    // ---- resources over the bus ----

    String text(ResourceKey k) {
        if (!(k instanceof PlatformResourceKey p)) return null;
        String t = keyText.get(k);
        if (t != null) return t;
        try {
            t = ResourceCodecs.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, server.registryAccess()), p).getOrThrow().toString();
        } catch (RuntimeException e) {
            return null;
        }
        keyText.put(k, t);
        textKey.put(t, k);
        return t;
    }

    /** Null when this server does not know that kind of thing. */
    ResourceKey key(String s) {
        ResourceKey k = textKey.get(s);
        if (k != null) return k;
        try {
            Tag t = TagParser.parseTag(s);
            k = ResourceCodecs.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, server.registryAccess()), t).result().orElse(null);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
            k = null;
        }
        if (k != null) {
            textKey.put(s, k);
            keyText.put(k, s);
        }
        return k;
    }

    // ---- links ----

    private static String linkOf(GlobalPos p) {
        return p.dimension().location() + "|" + p.pos().getX() + "|" + p.pos().getY() + "|" + p.pos().getZ();
    }

    /** main: the network of the receiver a link names, when its chunk is loaded. */
    private StorageNetworkComponent hostStorage(String link) {
        String[] a = link.split("\\|");
        if (a.length != 4) return null;
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(a[0])));
        if (level == null) return null;
        BlockPos pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof NetworkReceiverBlockEntity r)) return null;
        Network n = r.getNetworkForItem();
        return n == null ? null : n.getComponent(StorageNetworkComponent.class);
    }

    /** Other side: every transmitter here with a card bound on main, by link. */
    private void findTransmitters() {
        wanted.clear();
        NetworkSync.Peer main = NetworkSync.peerByRole("main");
        if (main == null) return;
        for (NetworkTransmitterBlockEntity t : transmitters.toArray(new NetworkTransmitterBlockEntity[0])) {
            if (t.isRemoved() || t.getLevel() == null || !t.getLevel().isLoaded(t.getBlockPos())) {
                transmitters.remove(t);
                continue;
            }
            ItemStack card;
            try {
                card = ((Container) cardInventory.invoke(t)).getItem(0);
            } catch (ReflectiveOperationException e) {
                continue;
            }
            GlobalPos at = card.isEmpty() ? null : card.get(DataComponents.INSTANCE.getNetworkLocation());
            CustomData d = card.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            String bound = d == null ? null : d.copyTag().getString(SERVER_TAG);
            if (at == null || bound == null || !bound.equals(main.name())) continue;
            wanted.put(linkOf(at), t);
        }
    }

    // ---- each tick ----

    @Override
    public void tick(MinecraftServer s) {
        server = s;
        ticks++;
        long now = System.nanoTime();
        if (Role.main()) {
            if (ticks % 10 == 1) {
                Set<String> hosting = new HashSet<>();
                for (Map.Entry<String, Set<String>> e : peerWants.entrySet()) {
                    if (now - peerAt.getOrDefault(e.getKey(), 0L) > FRESH_NS) continue;
                    for (String l : e.getValue()) if (hostStorage(l) != null) hosting.add(l);
                }
                JsonObject state = new JsonObject();
                JsonArray list = new JsonArray();
                hosting.forEach(list::add);
                state.add("host", list);
                Share.state(this, state);
                for (Map.Entry<String, Set<String>> e : peerWants.entrySet()) {
                    if (now - peerAt.getOrDefault(e.getKey(), 0L) > FRESH_NS) continue;
                    for (String l : e.getValue()) if (hosting.contains(l)) sendInventory(e.getKey(), l, false);
                }
            }
            return;
        }
        if (ticks % 10 == 1) {
            findTransmitters();
            JsonObject state = new JsonObject();
            JsonArray list = new JsonArray();
            wanted.keySet().forEach(list::add);
            state.add("want", list);
            Share.state(this, state);
            linkRemotes(now);
        }
        for (RsRemote r : remotes.values()) r.tick();
    }

    private void linkRemotes(long now) {
        String host = null;
        for (String p : Share.peers()) {
            if (now - peerAt.getOrDefault(p, 0L) < FRESH_NS && !peerHosts.getOrDefault(p, Set.of()).isEmpty()) host = p;
        }
        linkedHere.clear();
        Set<String> keep = new HashSet<>();
        for (Map.Entry<String, NetworkTransmitterBlockEntity> e : wanted.entrySet()) {
            String l = e.getKey();
            if (host == null || !peerHosts.get(host).contains(l)) continue;
            Network n = e.getValue().getNetworkForItem();
            StorageNetworkComponent st = n == null ? null : n.getComponent(StorageNetworkComponent.class);
            if (st == null) continue;
            keep.add(l);
            linkedHere.add(e.getValue());
            remotes.computeIfAbsent(l, x -> new RsRemote(this, x)).attach(host, st);
        }
        for (RsRemote r : remotes.values()) if (!keep.contains(r.link)) r.detach();
        if (host != null) {
            for (String l : RsEscrow.get(server).links()) {
                if (!keep.contains(l) && peerHosts.get(host).contains(l)) remotes.computeIfAbsent(l, x -> new RsRemote(this, x)).flushAll(host);
            }
        }
    }

    /** main: what changed in a receiver's network since that peer was told, or all of it. */
    private void sendInventory(String peer, String link, boolean full) {
        StorageNetworkComponent st = hostStorage(link);
        if (st == null) return;
        String id = peer + "|" + link;
        Map<String, Long> before = full ? null : told.get(id);
        Map<String, Long> now = new HashMap<>();
        for (ResourceAmount a : st.getAll()) {
            String t = text(a.resource());
            if (t != null && a.amount() > 0) now.merge(t, a.amount(), Long::sum);
        }
        JsonObject set = new JsonObject();
        JsonArray del = new JsonArray();
        for (Map.Entry<String, Long> e : now.entrySet()) {
            Long was = before == null ? null : before.get(e.getKey());
            if (was == null || was.longValue() != e.getValue()) set.addProperty(e.getKey(), e.getValue());
        }
        if (before != null) for (String k : before.keySet()) if (!now.containsKey(k)) del.add(k);
        if (before != null && set.size() == 0 && del.isEmpty()) return;
        long v = toldVersion.getOrDefault(id, 0L) + 1;
        toldVersion.put(id, v);
        told.put(id, now);
        JsonObject m = new JsonObject();
        m.addProperty("t", "inv");
        m.addProperty("l", link);
        m.addProperty("v", v);
        m.addProperty("full", before == null);
        m.add("set", set);
        m.add("del", del);
        Share.tell(this, peer, m);
    }

    // ---- messages ----

    @Override
    public void onState(String from, JsonObject state) {
        peerAt.put(from, System.nanoTime());
        peerHosts.put(from, strings(state.getAsJsonArray("host")));
        Set<String> wants = strings(state.getAsJsonArray("want"));
        Set<String> before = peerWants.put(from, wants);
        for (String l : wants) if (before == null || !before.contains(l)) told.remove(from + "|" + l);
    }

    private static Set<String> strings(JsonArray a) {
        Set<String> out = new HashSet<>();
        if (a != null) for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }

    @Override
    public void onTell(MinecraftServer s, String from, JsonObject d) {
        server = s;
        String l = d.get("l").getAsString();
        switch (d.get("t").getAsString()) {
            case "inv" -> {
                RsRemote r = remotes.get(l);
                if (r != null) r.inventory(d);
            }
            case "sync" -> {
                if (Role.main()) sendInventory(from, l, true);
            }
            case "fetch" -> fetch(from, l, d.get("k").getAsString(), d.get("n").getAsLong());
            default -> {
            }
        }
    }

    /** main: the other side wants something out of the receiver's network; it goes over as a give. */
    private void fetch(String from, String link, String k, long n) {
        ResourceKey key = key(k);
        StorageNetworkComponent st = Role.main() ? hostStorage(link) : null;
        if (st == null || key == null || n <= 0 || !Share.canGive(from)) return;
        long got = st.extract(key, n, Action.EXECUTE, ACTOR);
        if (got <= 0) return;
        JsonObject what = new JsonObject();
        what.addProperty("d", "fetch");
        what.addProperty("l", link);
        JsonArray items = new JsonArray();
        JsonArray one = new JsonArray();
        one.add(k);
        one.add(got);
        items.add(one);
        what.add("items", items);
        if (!Share.give(this, from, "fetch|" + link, what)) {
            st.insert(key, got, Action.EXECUTE, ACTOR);
            return;
        }
        fetched += got;
    }

    @Override
    public JsonObject receive(MinecraftServer s, String from, JsonObject data) {
        server = s;
        String l = data.get("l").getAsString();
        JsonArray items = data.getAsJsonArray("items");
        if (data.get("d").getAsString().equals("fetch")) {
            // other side: what main sent is held here for this link; always fits
            for (JsonElement el : items) if (key(el.getAsJsonArray().get(0).getAsString()) == null) return data;
            RsEscrow e = RsEscrow.get(s);
            for (JsonElement el : items) e.add(l, el.getAsJsonArray().get(0).getAsString(), el.getAsJsonArray().get(1).getAsLong());
            RsRemote r = remotes.get(l);
            if (r != null) r.arrived(items);
            return null;
        }
        // main: into the receiver's network, whatever fits
        StorageNetworkComponent st = Role.main() ? hostStorage(l) : null;
        if (st == null) return data;
        JsonArray rest = into(st, items, true);
        if (rest.isEmpty()) return null;
        JsonObject back = new JsonObject();
        back.addProperty("d", "put");
        back.addProperty("l", l);
        back.add("items", rest);
        return back;
    }

    private JsonArray into(StorageNetworkComponent st, JsonArray items, boolean count) {
        JsonArray rest = new JsonArray();
        for (JsonElement el : items) {
            JsonArray a = el.getAsJsonArray();
            ResourceKey k = key(a.get(0).getAsString());
            long n = a.get(1).getAsLong();
            long in = k == null ? 0 : st.insert(k, n, Action.EXECUTE, ACTOR);
            if (count) put += in;
            if (in < n) {
                JsonArray r = new JsonArray();
                r.add(a.get(0));
                r.add(n - in);
                rest.add(r);
            }
        }
        return rest;
    }

    @Override
    public JsonObject refund(MinecraftServer s, JsonObject data) {
        server = s;
        String l = data.get("l").getAsString();
        if (data.get("d").getAsString().equals("put")) {
            // other side: main had no room; it stays here, usable, and is not offered again for a while
            RsEscrow e = RsEscrow.get(s);
            RsRemote r = remotes.get(l);
            for (JsonElement el : data.getAsJsonArray("items")) {
                String k = el.getAsJsonArray().get(0).getAsString();
                e.add(l, k, el.getAsJsonArray().get(1).getAsLong());
                if (r != null) r.full(k);
            }
            return null;
        }
        // main: a fetch came back; into the receiver's network again
        StorageNetworkComponent st = hostStorage(l);
        if (st == null) return data;
        JsonArray rest = into(st, data.getAsJsonArray("items"), false);
        if (rest.isEmpty()) return null;
        data.add("items", rest);
        return data;
    }

    @Override
    public void onStopping(MinecraftServer s) {
        server = s;
        if (Role.main()) return;
        String host = null;
        for (String p : Share.peers()) if (!peerHosts.getOrDefault(p, Set.of()).isEmpty()) host = p;
        if (host == null) return;
        for (String l : RsEscrow.get(s).links()) {
            if (peerHosts.get(host).contains(l)) remotes.computeIfAbsent(l, x -> new RsRemote(this, x)).flushAll(host);
        }
        for (RsRemote r : remotes.values()) r.detach();
    }

    @Override
    public String describe() {
        if (Role.main()) {
            int links = 0;
            for (Set<String> w : peerWants.values()) links += w.size();
            return links + " transmitters on other servers want a receiver here, " + fetched + " taken out, " + put + " put in";
        }
        long held = 0;
        for (String l : RsEscrow.get(server).links()) for (long v : RsEscrow.get(server).held(l).values()) held += v;
        StringBuilder b = new StringBuilder(remotes.values().stream().filter(RsRemote::attached).count() + " transmitters linked to main, " + held + " held here");
        for (RsRemote r : remotes.values()) if (r.attached()) b.append("; ").append(r.state());
        return b.toString();
    }
}
