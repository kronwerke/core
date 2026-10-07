package de.kronwerke.core.share.powah;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import net.minecraft.server.MinecraftServer;
import owmii.powah.block.ender.EnderNetwork;
import owmii.powah.lib.logistics.energy.Energy;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Powah's Ender Cells and Ender Gates between servers. Each player's twelve channels are one
 * buffer on every server; a channel that has cells on two servers is one channel. Energy flows to
 * the side that takes it out (where machines draw from the cells) and otherwise evens out, the
 * same way as Mekanism's entangloporters.
 */
public final class EnderLayer implements Layer {
    private static final long FRESH_NS = 2_000_000_000L;
    private static final long DRAIN_MS = 2000;

    private final Field map;
    private final Map<String, Peer> peers = new HashMap<>();
    private final Map<String, Long> expected = new HashMap<>();
    private final Map<String, Long> drainUntil = new HashMap<>();
    private int lastSize;
    private long lastEmpty, moved;

    private record Peer(Map<String, JsonObject> channels, long at) {}

    private EnderLayer(Field map) {
        this.map = map;
    }

    public static EnderLayer create() {
        try {
            Field m = EnderNetwork.class.getDeclaredField("map");
            m.setAccessible(true);
            return new EnderLayer(m);
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Shared network: Powah's ender cells are not shared ({})", e.toString());
            return null;
        }
    }

    @Override
    public String name() {
        return "powah.ender";
    }

    /** Every channel with cells on it here, as owner:channel. */
    @SuppressWarnings("unchecked")
    private Map<String, Energy> channels(MinecraftServer server) {
        Map<String, Energy> out = new LinkedHashMap<>();
        Map<UUID, ImmutableList<Energy>> all;
        try {
            all = (Map<UUID, ImmutableList<Energy>>) map.get(EnderNetwork.get(server));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        all.forEach((owner, list) -> {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getCapacity() > 0) out.put(owner + ":" + i, list.get(i));
            }
        });
        return out;
    }

    private static Energy channel(MinecraftServer server, String key) {
        int at = key.lastIndexOf(':');
        UUID owner = UUID.fromString(key.substring(0, at));
        int ch = Integer.parseInt(key.substring(at + 1));
        return EnderNetwork.get(server).getEnergy(owner, ch);
    }

    @Override
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Map<String, Energy> local = channels(server);
        JsonObject state = new JsonObject();
        for (Map.Entry<String, Energy> e : local.entrySet()) {
            long n = e.getValue().getStored();
            Long exp = expected.get(e.getKey());
            if (exp != null && n < exp) drainUntil.put(e.getKey(), now + DRAIN_MS);
            expected.put(e.getKey(), n);
            JsonObject o = new JsonObject();
            o.addProperty("n", n);
            o.addProperty("m", e.getValue().getCapacity());
            o.addProperty("d", drainUntil.getOrDefault(e.getKey(), 0L) > now);
            state.add(e.getKey(), o);
        }
        expected.keySet().retainAll(local.keySet());
        drainUntil.keySet().retainAll(local.keySet());
        if (state.size() > 0 || lastSize > 0 || now - lastEmpty > 5000) {
            Share.state(this, state);
            if (state.size() == 0) lastEmpty = now;
        }
        lastSize = state.size();

        long nano = System.nanoTime();
        for (String peer : Share.peers()) {
            Peer p = peers.get(peer);
            if (p == null || nano - p.at() > FRESH_NS) continue;
            for (Map.Entry<String, Energy> e : local.entrySet()) {
                JsonObject theirs = p.channels().get(e.getKey());
                if (theirs != null) give(server, peer, p, e.getKey(), e.getValue(), state.getAsJsonObject(e.getKey()), theirs, now);
            }
        }
    }

    private void give(MinecraftServer server, String peer, Peer p, String key, Energy energy, JsonObject mine, JsonObject theirs, long now) {
        if (Share.busy(this, peer, key) || p.at() <= Share.answeredAt(this, peer, key)) return;
        long n = mine.get("n").getAsLong(), pn = theirs.get("n").getAsLong(), room = theirs.get("m").getAsLong() - pn;
        if (n <= 0 || room <= 0) return;
        boolean theyDrain = theirs.get("d").getAsBoolean(), weDrain = drainUntil.getOrDefault(key, 0L) > now;
        // the side that takes energy out gets everything and gives nothing back; otherwise half the difference
        if (weDrain && !theyDrain) return;
        long want = Math.min(theyDrain && !weDrain ? n : (n - pn) / 2, room);
        if (want <= 0 || !Share.canGive(peer)) return;
        long got = energy.consume(want);
        if (got <= 0) return;
        EnderNetwork.get(server).setDirty();
        expected.put(key, energy.getStored());
        JsonObject what = new JsonObject();
        what.addProperty("c", key);
        what.addProperty("n", got);
        if (!Share.give(this, peer, key, what)) {
            energy.produce(got);
            expected.put(key, energy.getStored());
            return;
        }
        moved += got;
    }

    @Override
    public void onState(String from, JsonObject state) {
        Map<String, JsonObject> m = new HashMap<>();
        for (String k : state.keySet()) m.put(k, state.getAsJsonObject(k));
        peers.put(from, new Peer(m, System.nanoTime()));
    }

    /** Into the channel, up to what it holds; the rest goes back. */
    private JsonObject put(MinecraftServer server, JsonObject data) {
        String key = data.get("c").getAsString();
        long n = data.get("n").getAsLong();
        Energy e = channel(server, key);
        long in = e.getCapacity() > 0 ? e.produce(n) : 0;
        if (in > 0) {
            EnderNetwork.get(server).setDirty();
            expected.put(key, e.getStored());
        }
        if (in >= n) return null;
        JsonObject rest = new JsonObject();
        rest.addProperty("c", key);
        rest.addProperty("n", n - in);
        return rest;
    }

    @Override
    public JsonObject receive(MinecraftServer server, String from, JsonObject data) {
        return put(server, data);
    }

    @Override
    public JsonObject refund(MinecraftServer server, JsonObject data) {
        // energy that finds no room any more is not kept
        put(server, data);
        return null;
    }

    @Override
    public String describe() {
        int both = 0;
        for (Peer p : peers.values()) both += p.channels().size();
        return expected.size() + " channels here, " + both + " on other servers, " + moved + " FE moved since start";
    }
}
