package de.kronwerke.core.share.mek;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.portal.AwayData;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import mekanism.api.security.SecurityMode;
import mekanism.common.content.teleporter.TeleporterFrequency;
import mekanism.common.lib.frequency.Frequency;
import mekanism.common.lib.frequency.FrequencyManager;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.tile.TileEntityTeleporter;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mekanism's frequencies are one list on every server of the network: a teleporter, QIO or
 * entangloporter frequency made (or deleted) on one server is there (or gone) on all the others
 * a second later, with the same name, owner and security. What flows through a frequency is the
 * work of {@link QuantumLayer} and {@link QioLayer}; this layer only makes the same frequencies
 * exist everywhere, so a player picks from the same list wherever they stand.
 * <p>
 * Every frequency has a record of when it was last made and last deleted; the later one wins on
 * every server, so a server that was down hears of both.
 * <p>
 * Teleporters: every server tells where its active teleporters stand, and the others add those
 * places to their copy of the frequency. A teleporter (or a portable one) that picks such a
 * place sends the player to that server, see {@link #remote}.
 */
public final class FrequencyLayer implements Layer {
    private static final int EVERY = 20, KEEPALIVE = 200;
    private static final long FORGET_MS = 30L * 24 * 3600 * 1000;
    private static final List<FrequencyType<?>> TYPES = List.of(FrequencyType.TELEPORTER, FrequencyType.INVENTORY, FrequencyType.QIO);

    /** A teleporter on another server: which server, and where a player comes out. */
    public record Remote(String peer, String dim, BlockPos pos, BlockPos target) {}

    private static FrequencyLayer instance;

    private final Field managers, ownerName, mekLoaded;
    private long warnedAt;
    private MinecraftServer server;
    /** key to {made at, deleted at}. */
    private final Map<String, long[]> recs = new HashMap<>();
    private final Map<String, String> owners = new HashMap<>();
    private boolean loaded, dirty;
    private int ticks;
    private String lastSent = "";
    private int sinceSent;
    /** What the other servers said about their teleporters: peer to frequency key to places. */
    private final Map<String, Map<String, List<Remote>>> theirs = new HashMap<>();
    /** The places of other servers that sit in the frequencies here now. */
    private final Map<GlobalPos, Remote> injected = new HashMap<>();

    private FrequencyLayer(Field managers, Field ownerName, Field mekLoaded) {
        this.managers = managers;
        this.ownerName = ownerName;
        this.mekLoaded = mekLoaded;
    }

    public static FrequencyLayer create() {
        try {
            Field m = FrequencyManager.class.getDeclaredField("managers");
            m.setAccessible(true);
            Field o = Frequency.class.getDeclaredField("ownerName");
            o.setAccessible(true);
            Field l = FrequencyManager.class.getDeclaredField("loaded");
            l.setAccessible(true);
            instance = new FrequencyLayer(m, o, l);
            return instance;
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Shared network: Mekanism's frequencies are not shared ({})", e.toString());
            return null;
        }
    }

    /** The teleporter of another server at this place of a frequency here, or null. */
    public static Remote remote(GlobalPos pos) {
        return instance == null || pos == null ? null : instance.injected.get(pos);
    }

    @Override
    public String name() {
        return "mek.freq";
    }

    // ---- keys ----

    private static String key(Frequency f) {
        return f.getType().getName() + "|" + f.getSecurity().name() + "|" + (f.getOwner() == null ? "" : f.getOwner()) + "|" + f.getName();
    }

    private record Id(FrequencyType<?> type, SecurityMode security, UUID owner, String name) {}

    private static Id parse(String key) {
        String[] p = key.split("\\|", 4);
        if (p.length != 4 || p[2].isEmpty()) return null;
        for (FrequencyType<?> t : TYPES) {
            if (t.getName().equals(p[0])) {
                try {
                    return new Id(t, SecurityMode.valueOf(p[1]), UUID.fromString(p[2]), p[3]);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static FrequencyManager<?> manager(Id id) {
        return ((FrequencyType) id.type()).getManager(new Frequency.FrequencyIdentity(id.name(), id.security(), id.owner()), id.owner());
    }

    private static Frequency find(Id id) {
        FrequencyManager<?> m = manager(id);
        Frequency f = m == null ? null : m.getFrequency(id.name());
        return f == null || f.isRemoved() ? null : f;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Frequency> local() {
        Map<String, Frequency> out = new HashMap<>();
        // a private manager is only loaded once somebody asked for it: ask for every one a record names
        for (String k : new ArrayList<>(recs.keySet())) {
            Id id = parse(k);
            if (id != null) manager(id);
        }
        try {
            for (FrequencyManager<?> m : new ArrayList<>((Collection<FrequencyManager<?>>) managers.get(null))) {
                if (!TYPES.contains(m.getType())) continue;
                for (Frequency f : m.getFrequencies()) {
                    if (!f.isRemoved() && f.getOwner() != null) out.put(key(f), f);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    // ---- every second ----

    @Override
    public void tick(MinecraftServer s) {
        server = s;
        if (++ticks % EVERY != 0) return;
        try {
            if (!mekLoaded.getBoolean(null)) return; // Mekanism has not read its frequencies yet
        } catch (IllegalAccessException e) {
            return;
        }
        if (!loaded) load();
        long now = System.currentTimeMillis();
        Map<String, Frequency> here = local();
        // a player deletes one frequency at a time: when most are gone at once, the data is
        // (a world that was not read, a reset), and that must never delete them everywhere
        int alive = 0, gone = 0;
        for (var en : recs.entrySet()) {
            if (en.getValue()[0] <= en.getValue()[1]) continue;
            alive++;
            if (!here.containsKey(en.getKey())) gone++;
        }
        if (gone > 3 && gone * 2 > alive) {
            if (now - warnedAt > 60_000) {
                warnedAt = now;
                KronwerkeCore.LOGGER.warn("Shared network: {} of {} Mekanism frequencies are missing here at once; not telling the others to delete them", gone, alive);
            }
            return;
        }
        for (var en : here.entrySet()) {
            long[] r = recs.get(en.getKey());
            if (r == null || r[1] >= r[0]) {
                recs.put(en.getKey(), new long[] {now, r == null ? 0 : r[1]});
                dirty = true;
            }
            // Mekanism works the owner's name out when it makes or loads the frequency, from this
            // server's own list of players; for someone who was never here that is "<uuid>". Such
            // a stand-in is no name: it must not replace the real one that came from the server
            // where the owner plays (it did: after every start the Nether showed and passed on
            // "<c4ce4ea4-...>" for a player who had not been there yet, seen 12.11.2026), and a
            // frequency that carries one gets the real name as soon as it is known.
            String on = en.getValue().getOwnerName(), known = owners.get(en.getKey());
            if (named(on)) {
                if (!on.equals(known)) {
                    owners.put(en.getKey(), on);
                    dirty = true;
                }
            } else if (named(known)) {
                try {
                    ownerName.set(en.getValue(), known);
                } catch (IllegalAccessException | RuntimeException e) {
                    // then it shows without a name
                }
            }
        }
        for (var en : recs.entrySet()) {
            long[] r = en.getValue();
            if (r[0] > r[1] && !here.containsKey(en.getKey())) {
                r[1] = now; // deleted here
                dirty = true;
            }
        }
        if (recs.entrySet().removeIf(en -> en.getValue()[1] >= en.getValue()[0] && now - en.getValue()[1] > FORGET_MS)) dirty = true;
        teleporters(here);
        JsonObject state = state(here);
        String text = state.toString();
        sinceSent += EVERY;
        if (!text.equals(lastSent) || sinceSent >= KEEPALIVE) {
            lastSent = text;
            sinceSent = 0;
            Share.state(this, state);
        }
        if (dirty) save();
    }

    private JsonObject state(Map<String, Frequency> here) {
        JsonObject s = new JsonObject();
        JsonArray list = new JsonArray();
        for (var en : recs.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("k", en.getKey());
            o.addProperty("b", en.getValue()[0]);
            o.addProperty("d", en.getValue()[1]);
            String on = owners.get(en.getKey());
            if (on != null) o.addProperty("on", on);
            list.add(o);
        }
        s.add("recs", list);
        // this server's own active teleporters, by frequency
        JsonObject tele = new JsonObject();
        for (var en : here.entrySet()) {
            if (!(en.getValue() instanceof TeleporterFrequency t)) continue;
            JsonArray places = new JsonArray();
            for (GlobalPos g : t.getActiveCoords()) {
                if (injected.containsKey(g)) continue;
                ServerLevel level = server.getLevel(g.dimension());
                TileEntityTeleporter tile = level == null ? null : WorldUtils.getTileEntity(TileEntityTeleporter.class, level, g.pos());
                if (tile == null) continue;
                BlockPos out = tile.getTeleporterTargetPos();
                JsonArray p = new JsonArray();
                p.add(g.dimension().location().toString());
                for (int v : new int[] {g.pos().getX(), g.pos().getY(), g.pos().getZ(), out.getX(), out.getY(), out.getZ()}) p.add(v);
                places.add(p);
            }
            if (!places.isEmpty()) tele.add(en.getKey(), places);
        }
        s.add("tele", tele);
        return s;
    }

    /** Puts the other servers' teleporters into the frequencies here, and takes out the ones that are gone. */
    private void teleporters(Map<String, Frequency> here) {
        Set<String> up = new HashSet<>(Share.peers());
        theirs.keySet().removeIf(p -> !up.contains(p));
        Map<GlobalPos, Remote> want = new HashMap<>();
        Map<GlobalPos, TeleporterFrequency> where = new HashMap<>();
        for (var peer : theirs.values()) {
            for (var en : peer.entrySet()) {
                if (!(here.get(en.getKey()) instanceof TeleporterFrequency t)) continue;
                for (Remote r : en.getValue()) {
                    GlobalPos g = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(r.dim())), r.pos());
                    want.put(g, r);
                    where.put(g, t);
                }
            }
        }
        // out: what is not wanted any more, from whichever frequency holds it
        for (GlobalPos g : new ArrayList<>(injected.keySet())) {
            if (want.containsKey(g)) continue;
            injected.remove(g);
            for (Frequency f : here.values()) if (f instanceof TeleporterFrequency t) t.getActiveCoords().remove(g);
        }
        for (var en : want.entrySet()) {
            injected.put(en.getKey(), en.getValue());
            where.get(en.getKey()).getActiveCoords().add(en.getKey());
        }
    }

    // ---- from the others ----

    @Override
    public void onState(String from, JsonObject s) {
        MinecraftServer srv = server;
        if (srv == null) return;
        srv.execute(() -> {
            try {
                apply(from, s);
            } catch (RuntimeException e) {
                KronwerkeCore.LOGGER.warn("Shared network: Mekanism frequencies from {}: {}", from, e.toString());
            }
        });
    }

    private void apply(String from, JsonObject s) {
        if (!loaded) load();
        if (s.get("recs") instanceof JsonArray list) {
            for (JsonElement el : list) {
                JsonObject o = el.getAsJsonObject();
                String k = o.get("k").getAsString();
                Id id = parse(k);
                if (id == null) continue;
                long b = o.get("b").getAsLong(), d = o.get("d").getAsLong();
                long[] r = recs.get(k);
                long nb = Math.max(b, r == null ? 0 : r[0]), nd = Math.max(d, r == null ? 0 : r[1]);
                if (r == null || nb != r[0] || nd != r[1]) {
                    recs.put(k, new long[] {nb, nd});
                    dirty = true;
                }
                if (o.has("on") && named(o.get("on").getAsString()) && !named(owners.get(k))) owners.put(k, o.get("on").getAsString());
                Frequency f = find(id);
                if (nb > nd && f == null) make(id, k);
                else if (nd >= nb && f != null) remove(id, k);
            }
        }
        Map<String, List<Remote>> places = new HashMap<>();
        if (s.get("tele") instanceof JsonObject tele) {
            for (var en : tele.entrySet()) {
                List<Remote> l = new ArrayList<>();
                for (JsonElement el : en.getValue().getAsJsonArray()) {
                    JsonArray p = el.getAsJsonArray();
                    l.add(new Remote(from, p.get(0).getAsString(), new BlockPos(p.get(1).getAsInt(), p.get(2).getAsInt(), p.get(3).getAsInt()),
                            new BlockPos(p.get(4).getAsInt(), p.get(5).getAsInt(), p.get(6).getAsInt())));
                }
                places.put(en.getKey(), l);
            }
        }
        theirs.put(from, places);
    }

    /** A real player name, not Mekanism's stand-in for an owner this server does not know. */
    private static boolean named(String name) {
        return name != null && !name.isEmpty() && !name.startsWith("<");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void make(Id id, String key) {
        FrequencyManager m = manager(id);
        Frequency f = m.getOrCreateFrequency(new Frequency.FrequencyIdentity(id.name(), id.security(), id.owner()), id.owner());
        String on = owners.get(key);
        // the owner may never have been on this server: their name comes along
        if (f != null && named(on) && !on.equals(f.getOwnerName())) {
            try {
                ownerName.set(f, on);
            } catch (IllegalAccessException | RuntimeException e) {
                // then it shows without a name
            }
        }
        KronwerkeCore.LOGGER.info("Shared network: Mekanism frequency {} is here now", key);
    }

    private void remove(Id id, String key) {
        FrequencyManager<?> m = manager(id);
        if (m.remove(id.name(), id.owner())) KronwerkeCore.LOGGER.info("Shared network: Mekanism frequency {} was deleted on another server", key);
    }

    @Override
    public JsonObject receive(MinecraftServer server, String from, JsonObject data) {
        return null; // nothing is ever given here
    }

    @Override
    public JsonObject refund(MinecraftServer server, JsonObject data) {
        return null;
    }

    /** A player who took a teleporter to this server comes out in front of it. */
    @Override
    public AwayData.Back arrive(MinecraftServer server, JsonObject arrive) {
        if (!arrive.has("layer") || !name().equals(arrive.get("layer").getAsString())) return null;
        try {
            UUID id = UUID.fromString(arrive.get("id").getAsString());
            hold.put(id, System.currentTimeMillis() + 30_000);
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(arrive.get("dim").getAsString())));
            BlockPos at = new BlockPos(arrive.get("px").getAsInt(), arrive.get("py").getAsInt(), arrive.get("pz").getAsInt());
            TileEntityTeleporter tile = level == null ? null : WorldUtils.getTileEntity(TileEntityTeleporter.class, level, at);
            if (tile != null) {
                // Mekanism's own guard, for as long as it lasts
                tile.didTeleport.add(id);
                tile.teleDelay = 100;
            }
        } catch (RuntimeException e) {
            // an older Core on the other side: they arrive all the same
        }
        return new AwayData.Back(arrive.get("dim").getAsString(), arrive.get("x").getAsDouble(), arrive.get("y").getAsDouble(), arrive.get("z").getAsDouble(),
                arrive.get("yaw").getAsFloat());
    }

    /**
     * Who came out of a teleporter here from another server, and when they were last seen in
     * a frame (at first: when they are expected at the latest). Mekanism keeps arrivals from
     * going straight back by a list in the teleporter, which it clears of everyone who is not
     * standing there; a player from another server is not standing there yet for seconds.
     */
    private static final Map<UUID, Long> hold = new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether the player just arrived through a teleporter and has not left its frame since. */
    public static boolean held(UUID id) {
        long now = System.currentTimeMillis();
        // anyone who just arrived from another server, however: main puts a player who comes home
        // where they left, and for one who left through a teleporter that is inside its frame
        if (de.kronwerke.core.portal.Travel.arrivedFrom(id) != null) {
            hold.put(id, now);
            return true;
        }
        Long t = hold.get(id);
        if (t == null) return false;
        if (now > t + 1500) {
            hold.remove(id);
            return false;
        }
        if (now > t) hold.put(id, now);
        return true;
    }

    /** What the move of a player to a remote teleporter carries: where they come out. */
    public static JsonObject arrival(Remote r, float yaw, UUID player) {
        JsonObject a = new JsonObject();
        a.addProperty("layer", "mek.freq");
        a.addProperty("id", player.toString());
        a.addProperty("px", r.pos().getX());
        a.addProperty("py", r.pos().getY());
        a.addProperty("pz", r.pos().getZ());
        a.addProperty("dim", r.dim());
        a.addProperty("x", r.target().getX() + 0.5);
        a.addProperty("y", r.target().getY());
        a.addProperty("z", r.target().getZ() + 0.5);
        a.addProperty("yaw", yaw);
        return a;
    }

    @Override
    public String describe() {
        int alive = 0;
        for (long[] r : recs.values()) if (r[0] > r[1]) alive++;
        return alive + " Mekanism frequencies on every server, " + injected.size() + " teleporters of other servers";
    }

    // ---- the records ----

    private Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("kronwerke_mekfreq.json");
    }

    private void load() {
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (var en : o.entrySet()) {
                JsonObject r = en.getValue().getAsJsonObject();
                recs.put(en.getKey(), new long[] {r.get("b").getAsLong(), r.get("d").getAsLong()});
                if (r.has("on") && named(r.get("on").getAsString())) owners.put(en.getKey(), r.get("on").getAsString());
            }
        } catch (IOException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Shared network: the record of Mekanism's frequencies could not be read: {}", e.toString());
        }
    }

    private void save() {
        dirty = false;
        JsonObject o = new JsonObject();
        for (var en : recs.entrySet()) {
            JsonObject r = new JsonObject();
            r.addProperty("b", en.getValue()[0]);
            r.addProperty("d", en.getValue()[1]);
            if (owners.containsKey(en.getKey())) r.addProperty("on", owners.get(en.getKey()));
            o.add(en.getKey(), r);
        }
        try {
            Path f = file(), tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.createDirectories(f.getParent());
            Files.writeString(tmp, o.toString());
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            KronwerkeCore.LOGGER.warn("Shared network: the record of Mekanism's frequencies could not be saved: {}", e.toString());
        }
    }
}
