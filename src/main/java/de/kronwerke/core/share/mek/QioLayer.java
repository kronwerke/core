package de.kronwerke.core.share.mek;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import mekanism.api.Action;
import mekanism.api.security.SecurityMode;
import mekanism.common.content.qio.QIODriveData;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.lib.frequency.Frequency;
import mekanism.common.lib.frequency.FrequencyManager;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.lib.inventory.HashedItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mekanism's QIO between servers: a frequency is one storage on every server of the network.
 * A dashboard, an exporter or a crafting window on any server sees everything the frequency
 * holds anywhere, and what is taken comes from the server that has it.
 * <p>
 * The things stay in the drives they were put in. Every server tells the others what its own
 * drives of a frequency hold, and each puts that into the frequency as a drive that only
 * shows (the mirror, see {@link QioDrives}): so Mekanism itself counts, lists, filters and
 * searches the whole storage. Taking something a server does not have itself gives nothing in
 * that moment, but asks the server that has it; that one takes it out of its drives and gives
 * it here through {@link Share} (so nothing doubles and nothing is lost), where it lands in
 * the transit drive, a split second later. What was not used after a while, and what is put
 * in on a server whose real drives are full or missing, goes to a server with room.
 * <p>
 * The mining world is reset now and then: before it stops it sends home everything its drives
 * hold, as before.
 */
public final class QioLayer implements Layer {
    private static final int EVERY = 10, PART = 400, DRAIN_TYPES = 64;
    private static final long REST_MS = 10_000, ASK_AGAIN_MS = 1_000;

    private final Field managers, typeCount, typeDrives, totalCount, totalCountCapacity, totalTypeCapacity;
    private final Method createType, removeType, needsUpdate;

    private MinecraftServer server;
    private int ticks;
    private long moved, pulled;
    /** Set while this server takes something out for another one: its own mirror must not ask a third. */
    private boolean serving;

    private final Map<QIOFrequency, QioDrives.Holder> holders = new IdentityHashMap<>();
    /** What each other server holds: peer, frequency key, item, count. */
    private final Map<String, Map<String, Map<String, Long>>> theirs = new HashMap<>();
    /** Room in real drives on each other server: peer, frequency key, {count capacity, type capacity, count}. */
    private final Map<String, Map<String, long[]>> theirRoom = new HashMap<>();
    /** The last message of each peer and frequency, to notice a gap. */
    private final Map<String, Long> theirSeq = new HashMap<>();
    /** What this server last told about each frequency. */
    private final Map<String, Map<HashedItem, Long>> sent = new HashMap<>();
    private final Map<String, Long> seq = new HashMap<>();
    private final Set<String> sendAll = new HashSet<>();
    private final Map<String, long[]> sentRoom = new HashMap<>();
    private Set<String> knownPeers = Set.of();

    private final Map<HashedItem, String> names = new IdentityHashMap<>();
    private final Map<String, HashedItem> items = new HashMap<>();

    private record Want(QioDrives.Holder holder, HashedItem type) {}

    private final Map<Want, Long> wants = new HashMap<>();
    private final Map<Want, Long> askedAt = new HashMap<>();
    /** When a kind of thing last landed in a transit drive (it rests a while before it is sent on). */
    private final Map<Want, Long> landedAt = new HashMap<>();
    private Map<String, String> transitFile;
    /** Frequencies Core's drives did not go into; not tried again until the next start. */
    private final Set<String> failed = new HashSet<>();
    private int pullTag;

    private QioLayer(Field managers, Field typeCount, Field typeDrives, Field totalCount, Field totalCountCapacity, Field totalTypeCapacity,
                     Method createType, Method removeType, Method needsUpdate) {
        this.managers = managers;
        this.typeCount = typeCount;
        this.typeDrives = typeDrives;
        this.totalCount = totalCount;
        this.totalCountCapacity = totalCountCapacity;
        this.totalTypeCapacity = totalTypeCapacity;
        this.createType = createType;
        this.removeType = removeType;
        this.needsUpdate = needsUpdate;
    }

    public static QioLayer create() {
        try {
            Field m = open(FrequencyManager.class.getDeclaredField("managers"));
            Class<?> data = QIOFrequency.QIOItemTypeData.class;
            return new QioLayer(m, open(data.getDeclaredField("count")), open(data.getDeclaredField("containingDrives")),
                    open(QIOFrequency.class.getDeclaredField("totalCount")), open(QIOFrequency.class.getDeclaredField("totalCountCapacity")),
                    open(QIOFrequency.class.getDeclaredField("totalTypeCapacity")),
                    open(QIOFrequency.class.getDeclaredMethod("createTypeDataForAbsent", HashedItem.class)),
                    open(QIOFrequency.class.getDeclaredMethod("removeItemData", HashedItem.class)),
                    open(QIOFrequency.class.getDeclaredMethod("setNeedsUpdate", HashedItem.class)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Shared network: Mekanism's QIO is not shared ({})", e.toString());
            return null;
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T open(T o) {
        o.setAccessible(true);
        return o;
    }

    @Override
    public String name() {
        return "mek.qio";
    }

    // ---- the frequencies here ----

    @SuppressWarnings("unchecked")
    private List<QIOFrequency> all() {
        List<QIOFrequency> out = new ArrayList<>();
        try {
            for (FrequencyManager<?> m : new ArrayList<>((Collection<FrequencyManager<?>>) managers.get(null))) {
                if (m.getType() != FrequencyType.QIO) continue;
                for (Frequency f : m.getFrequencies()) {
                    if (f instanceof QIOFrequency q && !q.isRemoved()) out.add(q);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static String key(Frequency f) {
        return f.getSecurity().name() + "/" + (f.getSecurity() == SecurityMode.PUBLIC || f.getOwner() == null ? "" : f.getOwner()) + "/" + f.getName();
    }

    private QioDrives.Holder holder(String key) {
        for (QioDrives.Holder h : holders.values()) if (h.key.equals(key)) return h;
        return null;
    }

    private String name(HashedItem type) {
        return names.computeIfAbsent(type, t -> t.getInternalStack().copyWithCount(1).save(server.registryAccess()).toString());
    }

    private HashedItem item(String name) {
        HashedItem known = items.get(name);
        if (known != null) return known;
        try {
            ItemStack st = ItemStack.parse(server.registryAccess(), TagParser.parseTag(name)).orElse(ItemStack.EMPTY);
            if (st.isEmpty()) return null;
            HashedItem h = HashedItem.create(st);
            items.put(name, h);
            return h;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
            return null; // an item this server does not know
        }
    }

    /** Puts the two drives into every frequency that does not have them yet, and forgets deleted frequencies. */
    private void attach() {
        List<QIOFrequency> here = all();
        holders.keySet().removeIf(f -> f.isRemoved() || !here.contains(f));
        for (QIOFrequency f : here) {
            if (holders.containsKey(f)) continue;
            String key = key(f);
            ItemStack kept = ItemStack.EMPTY;
            String nbt = transitFile().get(key);
            if (nbt != null) {
                try {
                    kept = ItemStack.parse(server.registryAccess(), TagParser.parseTag(nbt)).orElse(ItemStack.EMPTY);
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
                    KronwerkeCore.LOGGER.warn("Shared network: the transit drive of QIO frequency {} could not be read: {}", key, e.toString());
                }
            }
            if (failed.contains(key)) continue;
            QioDrives.Holder h;
            try {
                h = new QioDrives.Holder(this, f, key, kept);
                f.addDrive(new QIODriveData.QIODriveKey(h, 0));
                f.addDrive(new QIODriveData.QIODriveKey(h, 1));
            } catch (RuntimeException | LinkageError e) {
                failed.add(key);
                KronwerkeCore.LOGGER.warn("Shared network: QIO frequency {} stays as it is, Core's drives did not go in: {}", key, e.toString());
                continue;
            }
            if (h.transit == null || h.mirror == null) {
                // the mixin did not take: Mekanism made two plain drives of them, which must not stay
                failed.add(key);
                f.removeDrive(new QIODriveData.QIODriveKey(h, 0), true);
                f.removeDrive(new QIODriveData.QIODriveKey(h, 1), true);
                KronwerkeCore.LOGGER.warn("Shared network: QIO frequency {} did not take Core's drives", key);
                continue;
            }
            holders.put(f, h);
            // what the others already said about this frequency
            for (var peer : theirs.entrySet()) {
                Map<String, Long> stock = peer.getValue().get(key);
                if (stock != null) for (String name : new ArrayList<>(stock.keySet())) mirror(h, name);
            }
            mirrorRoom(h);
            sendAll.add(key);
        }
    }

    // ---- Mekanism's own books, for a change in one drive that Mekanism did not make ----

    /** After a drive's own count of a thing changed by delta: the frequency's count, list and viewers follow. */
    private void booked(QIOFrequency f, HashedItem type, QIODriveData drive, long delta) {
        if (delta == 0) return;
        try {
            Map<HashedItem, QIOFrequency.QIOItemTypeData> map = f.getItemDataMap();
            QIOFrequency.QIOItemTypeData data = map.get(type);
            if (delta > 0) {
                if (data == null) {
                    data = (QIOFrequency.QIOItemTypeData) createType.invoke(f, type);
                    map.put(type, data);
                }
                typeCount.setLong(data, typeCount.getLong(data) + delta);
                totalCount.setLong(f, totalCount.getLong(f) + delta);
                @SuppressWarnings("unchecked") Set<QIODriveData.QIODriveKey> drives = (Set<QIODriveData.QIODriveKey>) typeDrives.get(data);
                drives.add(drive.getKey());
            } else if (data != null) {
                long now = Math.max(0, typeCount.getLong(data) + delta);
                totalCount.setLong(f, Math.max(0, totalCount.getLong(f) - (typeCount.getLong(data) - now)));
                typeCount.setLong(data, now);
                if (drive.getStored(type) == 0) {
                    @SuppressWarnings("unchecked") Set<QIODriveData.QIODriveKey> drives = (Set<QIODriveData.QIODriveKey>) typeDrives.get(data);
                    drives.remove(drive.getKey());
                }
                if (now == 0) removeType.invoke(f, type);
            }
            needsUpdate.invoke(f, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Brings the mirror's count of one thing in line with what the other servers said. */
    private void mirror(QioDrives.Holder h, String name) {
        HashedItem type = item(name);
        if (type == null) return;
        long want = 0;
        for (var peer : theirs.values()) {
            Map<String, Long> stock = peer.get(h.key);
            if (stock != null) want += stock.getOrDefault(name, 0L);
        }
        var map = h.mirror.getItemMap();
        long had = map.getLong(type);
        if (want == had) return;
        if (want > 0) map.put(type, want);
        else map.removeLong(type);
        h.mirror.count += want - had;
        booked(h.freq, type, h.mirror, want - had);
    }

    /** The mirror is as big as the other servers' real drives, so the totals a player reads are the network's. */
    private void mirrorRoom(QioDrives.Holder h) {
        long cap = 0;
        int types = 0;
        for (var peer : theirRoom.values()) {
            long[] r = peer.get(h.key);
            if (r != null) {
                cap += r[0];
                types += (int) r[1];
            }
        }
        if (cap == h.mirror.capacity && types == h.mirror.types) return;
        try {
            totalCountCapacity.setLong(h.freq, totalCountCapacity.getLong(h.freq) + cap - h.mirror.capacity);
            totalTypeCapacity.setInt(h.freq, totalTypeCapacity.getInt(h.freq) + types - h.mirror.types);
            h.mirror.capacity = cap;
            h.mirror.types = types;
            needsUpdate.invoke(h.freq, (Object) null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- every half second ----

    @Override
    public void tick(MinecraftServer s) {
        server = s;
        if (!wants.isEmpty()) ask();
        if (++ticks % EVERY != 0) return;
        attach();
        Set<String> up = new HashSet<>(Share.peers());
        // a server that went away: its things are not reachable now
        for (String gone : new ArrayList<>(theirs.keySet())) {
            if (up.contains(gone)) continue;
            Map<String, Map<String, Long>> was = theirs.remove(gone);
            theirRoom.remove(gone);
            theirSeq.keySet().removeIf(k -> k.startsWith(gone + "|"));
            for (var en : was.entrySet()) {
                QioDrives.Holder h = holder(en.getKey());
                if (h == null) continue;
                for (String name : en.getValue().keySet()) mirror(h, name);
                mirrorRoom(h);
            }
        }
        // a server that came: it knows nothing yet
        if (!knownPeers.containsAll(up)) for (QioDrives.Holder h : holders.values()) sendAll.add(h.key);
        knownPeers = up;
        for (QioDrives.Holder h : holders.values()) {
            tell(h);
            drain(h);
        }
        landedAt.values().removeIf(t -> System.currentTimeMillis() - t > 60_000);

        askedAt.values().removeIf(t -> System.currentTimeMillis() - t > 60_000);
    }

    /** What this server's own drives of a frequency hold: everything the frequency counts, less the mirror. */
    private void tell(QioDrives.Holder h) {
        if (Share.peers().isEmpty()) return;
        boolean all = sendAll.remove(h.key);
        Map<HashedItem, Long> last = sent.computeIfAbsent(h.key, k -> new HashMap<>());
        Map<HashedItem, Long> now = new HashMap<>();
        for (var en : h.freq.getItemDataMap().entrySet()) {
            long own = en.getValue().getCount() - h.mirror.getStored(en.getKey());
            if (own > 0) now.put(en.getKey(), own);
        }
        List<Object[]> changes = new ArrayList<>();
        for (var en : now.entrySet()) if (all || !en.getValue().equals(last.get(en.getKey()))) changes.add(new Object[] {en.getKey(), en.getValue()});
        for (HashedItem was : last.keySet()) if (!now.containsKey(was)) changes.add(new Object[] {was, 0L});
        long[] room = {h.physicalCapacity(), h.physicalTypes(), h.physicalCount()};
        long[] lastRoom = sentRoom.get(h.key);
        boolean roomChanged = lastRoom == null || lastRoom[0] != room[0] || lastRoom[1] != room[1] || lastRoom[2] != room[2];
        if (!all && changes.isEmpty() && !roomChanged) return;
        sent.put(h.key, now);
        sentRoom.put(h.key, room);
        int at = 0;
        do {
            JsonObject s = new JsonObject();
            s.addProperty("f", h.key);
            long n = seq.merge(h.key, 1L, Long::sum);
            s.addProperty("n", n);
            if (all && at == 0) s.addProperty("reset", true);
            JsonArray list = new JsonArray();
            for (int i = at; i < Math.min(changes.size(), at + PART); i++) {
                JsonArray one = new JsonArray();
                one.add(name((HashedItem) changes.get(i)[0]));
                one.add((Long) changes.get(i)[1]);
                list.add(one);
            }
            s.add("items", list);
            JsonArray r = new JsonArray();
            for (long v : room) r.add(v);
            s.add("room", r);
            Share.state(this, s);
            at += PART;
        } while (at < changes.size());
    }

    @Override
    public void onState(String from, JsonObject s) {
        MinecraftServer srv = server;
        if (srv == null) return;
        srv.execute(() -> {
            try {
                heard(from, s);
            } catch (RuntimeException e) {
                KronwerkeCore.LOGGER.warn("Shared network: QIO from {}: {}", from, e.toString());
            }
        });
    }

    private void heard(String from, JsonObject s) {
        if (!s.has("f") || !s.has("n")) return; // an older Core
        String key = s.get("f").getAsString();
        long n = s.get("n").getAsLong();
        boolean reset = s.has("reset") && s.get("reset").getAsBoolean();
        Long last = theirSeq.get(from + "|" + key);
        if (!reset && (last == null || n != last + 1)) {
            // missed something (this server started later, or a message was lost): once more from the start
            JsonObject again = new JsonObject();
            again.addProperty("again", key);
            Share.tell(this, from, again);
            return;
        }
        theirSeq.put(from + "|" + key, n);
        Map<String, Long> stock = theirs.computeIfAbsent(from, p -> new HashMap<>()).computeIfAbsent(key, k -> new HashMap<>());
        QioDrives.Holder h = holder(key);
        Set<String> touched = new HashSet<>();
        if (reset) {
            touched.addAll(stock.keySet());
            stock.clear();
        }
        for (JsonElement el : s.getAsJsonArray("items")) {
            JsonArray a = el.getAsJsonArray();
            String name = a.get(0).getAsString();
            long count = a.get(1).getAsLong();
            if (count > 0) stock.put(name, count);
            else stock.remove(name);
            touched.add(name);
        }
        if (s.get("room") instanceof JsonArray r && r.size() >= 3) {
            theirRoom.computeIfAbsent(from, p -> new HashMap<>()).put(key, new long[] {r.get(0).getAsLong(), r.get(1).getAsLong(), r.get(2).getAsLong()});
        }
        if (h != null) {
            for (String name : touched) mirror(h, name);
            mirrorRoom(h);
        }
    }

    // ---- taking what another server has ----

    /** From the mirror: something was to be taken that is only on another server. */
    void want(QioDrives.Holder h, HashedItem type, long amount) {
        if (serving) return;
        wants.merge(new Want(h, type), amount, Long::sum);
    }

    private void ask() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Want, Long>> it = wants.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            it.remove();
            Want w = en.getKey();
            Long asked = askedAt.get(w);
            if (asked != null && now - asked < ASK_AGAIN_MS) continue;
            String name = name(w.type());
            String best = null;
            long most = 0;
            for (var peer : theirs.entrySet()) {
                Map<String, Long> stock = peer.getValue().get(w.holder().key);
                long has = stock == null ? 0 : stock.getOrDefault(name, 0L);
                if (has > most && Share.canGive(peer.getKey())) {
                    most = has;
                    best = peer.getKey();
                }
            }
            if (best == null) continue;
            askedAt.put(w, now);
            JsonObject d = new JsonObject();
            d.addProperty("pull", w.holder().key);
            d.addProperty("item", name);
            // a stack at least: the next click or tick wants more of the same
            d.addProperty("n", Math.min(most, Math.max(en.getValue(), w.type().getInternalStack().getMaxStackSize())));
            Share.tell(this, best, d);
        }
    }

    @Override
    public void onTell(MinecraftServer srv, String from, JsonObject d) {
        srv.execute(() -> {
            try {
                if (d.has("again")) {
                    sendAll.add(d.get("again").getAsString());
                } else if (d.has("pull")) {
                    serve(srv, from, d.get("pull").getAsString(), d.get("item").getAsString(), d.get("n").getAsLong());
                }
            } catch (RuntimeException e) {
                KronwerkeCore.LOGGER.warn("Shared network: QIO request from {}: {}", from, e.toString());
            }
        });
    }

    /** Another server asks for something this one has: out of the drives here, and given to it. */
    private void serve(MinecraftServer srv, String to, String key, String name, long n) {
        QioDrives.Holder h = holder(key);
        HashedItem type = item(name);
        if (h == null || type == null || n <= 0 || !Share.canGive(to)) return;
        ItemStack stack = type.getInternalStack();
        long got;
        serving = true;
        try {
            got = h.freq.massExtract(stack, n, Action.EXECUTE);
        } finally {
            serving = false;
        }
        if (got <= 0) return;
        JsonObject what = new JsonObject();
        what.addProperty("f", key);
        what.addProperty("pulled", true);
        JsonArray list = new JsonArray();
        JsonArray one = new JsonArray();
        one.add(name);
        one.add(got);
        list.add(one);
        what.add("items", list);
        if (!Share.give(this, to, key + "#" + (pullTag++), what)) {
            insert(h, stack, got, true);
            return;
        }
        pulled += got;
    }

    /** Into the frequency here; open: also into the transit drive when real drives have room. */
    private long insert(QioDrives.Holder h, ItemStack stack, long n, boolean open) {
        h.transit.open = open;
        try {
            return h.freq.massInsert(stack, n, Action.EXECUTE);
        } finally {
            h.transit.open = false;
        }
    }

    // ---- what waits in the transit drive goes to a server with room ----

    private void drain(QioDrives.Holder h) {
        if (h.transit.getTotalCount() <= 0) return;
        long now = System.currentTimeMillis();
        List<HashedItem> rested = new ArrayList<>();
        for (HashedItem type : h.transit.getItemMap().keySet()) {
            Long landed = landedAt.get(new Want(h, type));
            if (landed == null) landedAt.put(new Want(h, type), now); // put in by a player or a machine: it rests as well
            else if (now - landed >= REST_MS) rested.add(type);
            if (rested.size() >= DRAIN_TYPES) break;
        }
        if (rested.isEmpty()) return;
        if (h.physicalRoom()) {
            // real drives here have room again
            for (HashedItem type : rested) {
                long n = take(h, type);
                long put = insert(h, type.getInternalStack(), n, false);
                if (put < n) insert(h, type.getInternalStack(), n - put, true);
            }
            return;
        }
        String best = null;
        long most = 0;
        for (var peer : theirRoom.entrySet()) {
            long[] r = peer.getValue().get(h.key);
            long free = r == null ? 0 : r[0] - r[2];
            if (free > most && Share.canGive(peer.getKey()) && !Share.busy(this, peer.getKey(), h.key)) {
                most = free;
                best = peer.getKey();
            }
        }
        if (best == null) return; // no room anywhere: it stays, and the transit drive fills up
        JsonArray list = new JsonArray();
        List<Object[]> took = new ArrayList<>();
        long sum = 0;
        for (HashedItem type : rested) {
            if (sum >= most) break;
            long n = take(h, type);
            if (n <= 0) continue;
            sum += n;
            took.add(new Object[] {type, n});
            JsonArray one = new JsonArray();
            one.add(name(type));
            one.add(n);
            list.add(one);
        }
        if (list.isEmpty()) return;
        JsonObject what = new JsonObject();
        what.addProperty("f", h.key);
        what.add("items", list);
        if (!Share.give(this, best, h.key, what)) {
            for (Object[] t : took) insert(h, ((HashedItem) t[0]).getInternalStack(), (Long) t[1], true);
            return;
        }
        moved += sum;
    }

    /** Everything of one kind out of the transit drive. */
    private long take(QioDrives.Holder h, HashedItem type) {
        long n = h.transit.getStored(type);
        if (n <= 0) return 0;
        long got = h.transit.remove(type, n, Action.EXECUTE);
        booked(h.freq, type, h.transit, -got);
        landedAt.remove(new Want(h, type));
        return got;
    }

    // ---- what another server gave ----

    private JsonObject put(JsonObject data, String from) {
        QioDrives.Holder h = holder(data.get("f").getAsString());
        if (h == null) return data;
        boolean wasAsked = data.has("pulled");
        JsonArray rest = new JsonArray();
        for (JsonElement el : data.getAsJsonArray("items")) {
            JsonArray a = el.getAsJsonArray();
            String name = a.get(0).getAsString();
            long n = a.get(1).getAsLong();
            HashedItem type = item(name);
            long in = type == null ? 0 : insert(h, type.getInternalStack(), n, wasAsked || from == null);
            if (type != null && in > 0) {
                if (h.transit.getStored(type) > 0) landedAt.put(new Want(h, type), System.currentTimeMillis());
                if (from != null) {
                    // the other server has that much less now; its next message says the same
                    Map<String, Long> stock = theirs.getOrDefault(from, Map.of()).get(h.key);
                    if (stock != null && stock.containsKey(name)) {
                        long left = stock.get(name) - in;
                        if (left > 0) stock.put(name, left);
                        else stock.remove(name);
                        mirror(h, name);
                    }
                }
            }
            if (in < n) {
                JsonArray one = new JsonArray();
                one.add(name);
                one.add(n - in);
                rest.add(one);
            }
        }
        if (rest.isEmpty()) return null;
        JsonObject back = new JsonObject();
        back.addProperty("f", data.get("f").getAsString());
        back.add("items", rest);
        return back;
    }

    @Override
    public JsonObject receive(MinecraftServer srv, String from, JsonObject data) {
        server = srv;
        return put(data, from);
    }

    @Override
    public JsonObject refund(MinecraftServer srv, JsonObject data) {
        server = srv;
        return put(data, null);
    }

    /** The mining world may be reset: everything its drives hold goes home before it stops. */
    @Override
    public void onStopping(MinecraftServer srv) {
        saveTransit();
        if (Role.main() || !Role.role().equals(de.kronwerke.core.config.KronwerkeConfig.PORTAL_TARGET.get())) return;
        String main = null;
        for (String p : Share.peers()) {
            var q = de.kronwerke.core.link.NetworkSync.peer(p);
            if (q != null && q.role().equals("main")) main = p;
        }
        if (main == null) return;
        for (QioDrives.Holder h : holders.values()) {
            long[] r = theirRoom.getOrDefault(main, Map.of()).get(h.key);
            if (r == null || r[0] - r[2] <= 0) continue;
            JsonArray list = new JsonArray();
            serving = true;
            try {
                for (var en : new ArrayList<>(h.freq.getItemDataMap().entrySet())) {
                    long own = en.getValue().getCount() - h.mirror.getStored(en.getKey());
                    if (own <= 0) continue;
                    long got = h.freq.massExtract(en.getKey().getInternalStack(), own, Action.EXECUTE);
                    if (got <= 0) continue;
                    JsonArray one = new JsonArray();
                    one.add(name(en.getKey()));
                    one.add(got);
                    list.add(one);
                    moved += got;
                }
            } finally {
                serving = false;
            }
            if (list.isEmpty()) continue;
            JsonObject what = new JsonObject();
            what.addProperty("f", h.key);
            what.add("items", list);
            if (!Share.give(this, main, h.key + "#stop", what)) put(what, null);
        }
        saveTransit();
    }

    @Override
    public String describe() {
        long mirrored = 0, waiting = 0;
        for (QioDrives.Holder h : holders.values()) {
            mirrored += h.mirror.count;
            waiting += h.transit.getTotalCount();
        }
        return holders.size() + " frequencies here, " + mirrored + " items on other servers, " + waiting + " waiting in transit, " + pulled + " fetched for others and "
                + moved + " sent on since start";
    }

    // ---- the transit drives are kept with the world ----

    private Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("kronwerke_qio_transit.json");
    }

    private Map<String, String> transitFile() {
        if (transitFile != null) return transitFile;
        transitFile = new HashMap<>();
        try {
            Path f = file();
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                for (var en : o.entrySet()) transitFile.put(en.getKey(), en.getValue().getAsString());
            }
        } catch (IOException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Shared network: the QIO transit drives could not be read: {}", e.toString());
        }
        return transitFile;
    }

    @Override
    public void onSave(MinecraftServer srv) {
        saveTransit();
    }

    /** With every world save, and when the server stops. */
    public void saveTransit() {
        if (server == null) return;
        boolean any = false;
        for (QioDrives.Holder h : holders.values()) {
            if (!h.dirty) continue;
            h.dirty = false;
            any = true;
            h.save(0, h.transit);
            if (h.transit.getTotalCount() > 0) transitFile().put(h.key, ((CompoundTag) h.transitStack.save(server.registryAccess())).toString());
            else transitFile().remove(h.key);
        }
        if (!any) return;
        JsonObject o = new JsonObject();
        for (var en : transitFile().entrySet()) o.addProperty(en.getKey(), en.getValue());
        try {
            Path f = file(), tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.createDirectories(f.getParent());
            Files.writeString(tmp, o.toString());
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            KronwerkeCore.LOGGER.warn("Shared network: the QIO transit drives could not be saved: {}", e.toString());
        }
    }
}
