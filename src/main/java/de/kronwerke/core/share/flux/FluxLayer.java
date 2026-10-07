package de.kronwerke.core.share.flux;

import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.FluxNetworkData;
import sonar.fluxnetworks.common.connection.ServerFluxNetwork;
import sonar.fluxnetworks.common.connection.TransferHandler;
import sonar.fluxnetworks.common.device.FluxPlugHandler;
import sonar.fluxnetworks.common.device.FluxPointHandler;
import sonar.fluxnetworks.common.device.FluxStorageHandler;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flux Networks between servers. main's networks exist on the other servers too, with the same
 * number, name, colour, owner, members and password, so a plug on main and a point on the mining
 * world can be on one network. Networks are made and changed on main; the other servers follow.
 * <p>
 * Energy: what a server's plugs have left after its own points, and what its storages hold, goes
 * to the points of the other server that still want energy; what plugs have left beyond that fills
 * the other server's storages. Storages never fill each other, so nothing goes back and forth.
 */
public final class FluxLayer implements Layer {
    /** Networks made on a side world get numbers from here, so they never meet main's. */
    private static final int SIDE_IDS = 1_000_000;
    private static final long FRESH_NS = 2_000_000_000L;
    /** Points ask for one tick; a give takes a few ticks to arrive, so it brings a few ticks' worth. */
    private static final int TICKS_AHEAD = 4;

    private final Field networks, uniqueId, members, desired;
    private final Constructor<ServerFluxNetwork> make;
    private final Map<String, Peer> peers = new HashMap<>();
    private String sentDefs = "";
    private long sentDefsAt;
    private int ticks;
    private long moved;

    private record Peer(Map<Integer, JsonObject> nets, long at) {}

    /** One network on this server, summed up after Flux's own tick. */
    private record Sum(long plug, long store, long want, long room) {}

    private FluxLayer(Field networks, Field uniqueId, Field members, Field desired, Constructor<ServerFluxNetwork> make) {
        this.networks = networks;
        this.uniqueId = uniqueId;
        this.members = members;
        this.desired = desired;
        this.make = make;
    }

    public static FluxLayer create() {
        try {
            Field n = FluxNetworkData.class.getDeclaredField("mNetworks");
            n.setAccessible(true);
            Field u = FluxNetworkData.class.getDeclaredField("mUniqueID");
            u.setAccessible(true);
            Field m = FluxNetwork.class.getDeclaredField("mMemberMap");
            m.setAccessible(true);
            Field d = FluxPointHandler.class.getDeclaredField("mDesired");
            d.setAccessible(true);
            Constructor<ServerFluxNetwork> c = ServerFluxNetwork.class.getDeclaredConstructor();
            c.setAccessible(true);
            return new FluxLayer(n, u, m, d, c);
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Shared network: Flux Networks are not shared ({})", e.toString());
            return null;
        }
    }

    @Override
    public String name() {
        return "flux";
    }

    @SuppressWarnings("unchecked")
    private Int2ObjectOpenHashMap<FluxNetwork> all() {
        try {
            return (Int2ObjectOpenHashMap<FluxNetwork>) networks.get(FluxNetworkData.getInstance());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- each tick ----

    @Override
    public void tick(MinecraftServer server) {
        ticks++;
        if (ticks == 1 && !Role.main()) keepIdsApart();
        if (Role.main() && ticks % 40 == 0) sendDefinitions(false);

        JsonObject state = new JsonObject();
        Map<Integer, Sum> mine = new HashMap<>();
        for (FluxNetwork n : all().values()) {
            if (n.getNetworkID() >= SIDE_IDS || !n.isValid()) continue;
            Sum s = sum(n);
            if (s.plug() == 0 && s.store() == 0 && s.want() == 0 && s.room() == 0) continue;
            mine.put(n.getNetworkID(), s);
            JsonObject o = new JsonObject();
            o.addProperty("plug", s.plug());
            o.addProperty("store", s.store());
            o.addProperty("want", s.want());
            o.addProperty("room", s.room());
            state.add(Integer.toString(n.getNetworkID()), o);
        }
        if (state.size() > 0 || ticks % 20 == 0) Share.state(this, state);

        long now = System.nanoTime();
        for (String peer : Share.peers()) {
            Peer p = peers.get(peer);
            if (p == null || now - p.at() > FRESH_NS) continue;
            for (Map.Entry<Integer, Sum> e : mine.entrySet()) {
                JsonObject theirs = p.nets().get(e.getKey());
                if (theirs != null) give(peer, p, e.getKey(), e.getValue(), theirs);
            }
        }
    }

    private Sum sum(FluxNetwork n) {
        long plug = 0, store = 0, want = 0, room = 0;
        for (TileFluxDevice d : n.getLogicalDevices(FluxNetwork.ANY)) {
            TransferHandler h = d.getTransferHandler();
            if (h instanceof FluxStorageHandler s) {
                store += s.getBuffer();
                room += Math.max(0, s.getRequest());
            } else if (h instanceof FluxPlugHandler) {
                plug += h.getBuffer();
            } else if (h instanceof FluxPointHandler) {
                want += Math.max(0, desired(h) - h.getChange());
            }
        }
        return new Sum(plug, store, want, room);
    }

    private long desired(TransferHandler h) {
        try {
            return desired.getLong(h);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private void give(String peer, Peer p, int id, Sum s, JsonObject theirs) {
        String tag = Integer.toString(id);
        if (Share.busy(this, peer, tag) || p.at() <= Share.answeredAt(this, peer, tag)) return;
        long want = theirs.get("want").getAsLong(), room = theirs.get("room").getAsLong();
        // to their points: from plugs first, then from storages
        long toPoints = Math.min(s.plug() + s.store(), want * TICKS_AHEAD);
        // to their storages: only what plugs have left, never from a storage
        long plugLeft = Math.max(0, s.plug() - toPoints);
        long toStore = s.room() > 0 ? 0 : Math.min(plugLeft, room);
        if (toPoints + toStore <= 0 || !Share.canGive(peer)) return;
        FluxNetwork n = all().get(id);
        long fromPlugs = take(n, toPoints + toStore, false);
        long fromStore = toPoints + toStore > fromPlugs ? take(n, Math.min(toPoints, toPoints + toStore - fromPlugs), true) : 0;
        long got = fromPlugs + fromStore;
        if (got <= 0) return;
        JsonObject what = new JsonObject();
        what.addProperty("net", id);
        what.addProperty("points", Math.min(got, toPoints));
        what.addProperty("store", got - Math.min(got, toPoints));
        if (!Share.give(this, peer, tag, what)) {
            put(n, got, true);
            return;
        }
        moved += got;
    }

    /** Takes up to n out of the plugs' (or storages') buffers. */
    private static long take(FluxNetwork n, long want, boolean storages) {
        long got = 0;
        for (TileFluxDevice d : n.getLogicalDevices(FluxNetwork.ANY)) {
            if (got >= want) break;
            TransferHandler h = d.getTransferHandler();
            if (storages ? h instanceof FluxStorageHandler : h instanceof FluxPlugHandler) {
                got += storages ? ((FluxStorageHandler) h).removeFromBuffer(want - got) : ((FluxPlugHandler) h).removeFromBuffer(want - got);
            }
        }
        return got;
    }

    /** Puts energy into the points (as much as they want, the first one the rest) or the storages; returns what found no place. */
    private long put(FluxNetwork n, long amount, boolean points) {
        long left = amount;
        List<TileFluxDevice> list = new ArrayList<>(n.getLogicalDevices(FluxNetwork.ANY));
        FluxPointHandler first = null;
        for (TileFluxDevice d : list) {
            if (left <= 0) break;
            TransferHandler h = d.getTransferHandler();
            if (points && h instanceof FluxPointHandler ph) {
                if (first == null) first = ph;
                long a = Math.min(left, Math.max(0, desired(h) - h.getChange()) * TICKS_AHEAD);
                if (a > 0) {
                    ph.addToBuffer(a);
                    left -= a;
                }
            } else if (!points && h instanceof FluxStorageHandler sh) {
                long a = Math.min(left, Math.max(0, sh.getMaxEnergyStorage() - sh.getBuffer()));
                if (a > 0) {
                    sh.addToBuffer(a);
                    left -= a;
                }
            }
        }
        if (points && left > 0 && first != null) {
            // a point buffers what its machines do not take yet and passes it on over the next ticks
            first.addToBuffer(left);
            left = 0;
        }
        return left;
    }

    // ---- network definitions, main to the others ----

    private void keepIdsApart() {
        try {
            FluxNetworkData d = FluxNetworkData.getInstance();
            if (uniqueId.getInt(d) < SIDE_IDS) {
                uniqueId.setInt(d, SIDE_IDS);
                d.setDirty();
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sendDefinitions(boolean always) {
        CompoundTag list = new CompoundTag();
        for (FluxNetwork n : all().values()) {
            if (n.getNetworkID() >= SIDE_IDS || !n.isValid()) continue;
            CompoundTag t = new CompoundTag();
            n.writeCustomTag(t, (byte) 1);
            t.remove("connections");
            t.remove("statistics");
            list.put(Integer.toString(n.getNetworkID()), t);
        }
        String text = list.toString();
        long now = System.currentTimeMillis();
        if (!always && text.equals(sentDefs) && now - sentDefsAt < 30_000) return;
        sentDefs = text;
        sentDefsAt = now;
        JsonObject m = new JsonObject();
        m.addProperty("t", "defs");
        m.addProperty("defs", text);
        for (String peer : Share.peers()) Share.tell(this, peer, m);
    }

    /** Not main: main's networks as they are now; the ones main no longer has go. */
    private void applyDefinitions(String text) {
        CompoundTag list;
        try {
            list = TagParser.parseTag(text);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        Int2ObjectOpenHashMap<FluxNetwork> nets = all();
        Set<Integer> seen = new HashSet<>();
        try {
            for (String k : list.getAllKeys()) {
                int id = Integer.parseInt(k);
                seen.add(id);
                CompoundTag t = list.getCompound(k);
                FluxNetwork n = nets.get(id);
                if (n == null) {
                    n = make.newInstance();
                    n.readCustomTag(t, (byte) 1);
                    nets.put(id, n);
                } else {
                    ((Map<?, ?>) members.get(n)).clear();
                    n.readCustomTag(t, (byte) 1);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        for (FluxNetwork n : new ArrayList<>(nets.values())) {
            if (n.getNetworkID() < SIDE_IDS && !seen.contains(n.getNetworkID())) FluxNetworkData.getInstance().deleteNetwork(n);
        }
        FluxNetworkData.getInstance().setDirty();
    }

    // ---- from the other server ----

    @Override
    public void onState(String from, JsonObject state) {
        Map<Integer, JsonObject> m = new HashMap<>();
        for (String k : state.keySet()) m.put(Integer.parseInt(k), state.getAsJsonObject(k));
        boolean fresh = !peers.containsKey(from);
        peers.put(from, new Peer(m, System.nanoTime()));
        if (fresh && Role.main()) sendDefinitions(true);
    }

    @Override
    public void onTell(MinecraftServer server, String from, JsonObject data) {
        if (!Role.main() && data.get("t").getAsString().equals("defs")) applyDefinitions(data.get("defs").getAsString());
    }

    @Override
    public JsonObject receive(MinecraftServer server, String from, JsonObject data) {
        FluxNetwork n = all().get(data.get("net").getAsInt());
        if (n == null || !n.isValid()) return data;
        long points = data.get("points").getAsLong(), store = data.get("store").getAsLong();
        long left = put(n, points, true) + put(n, store, false);
        if (left <= 0) return null;
        JsonObject rest = new JsonObject();
        rest.addProperty("net", data.get("net").getAsInt());
        rest.addProperty("points", 0);
        rest.addProperty("store", left);
        return rest;
    }

    @Override
    public JsonObject refund(MinecraftServer server, JsonObject data) {
        // back into this network, storages first; energy that finds no place is not kept
        FluxNetwork n = all().get(data.get("net").getAsInt());
        if (n == null || !n.isValid()) return null;
        long left = put(n, data.get("points").getAsLong() + data.get("store").getAsLong(), false);
        if (left > 0) put(n, left, true);
        return null;
    }

    @Override
    public String describe() {
        int shared = 0;
        for (Peer p : peers.values()) shared += p.nets().size();
        return all().size() + " networks here, " + shared + " busy on other servers, " + moved + " FE moved since start";
    }
}
