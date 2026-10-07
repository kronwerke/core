package de.kronwerke.core.share.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.kronwerke.core.share.Share;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Not main: main's ME storage as one drive of the network this ring sits in. What main has is
 * known from its reports; taking something fetches it first, and whatever this side holds (fetched
 * and not used yet, or put in and not sent yet) is in {@link EscrowData}.
 */
final class RemoteStorage implements MEStorage {
    private static final long IDLE_MS = 60_000, ASK_MS = 2000, FULL_MS = 30_000, SYNC_MS = 5000;

    final long frequency;
    private final BridgeLayer layer;
    private final Provider provider = new Provider();
    private String host;
    private IGrid grid;
    private final Map<AEKey, Long> hostInv = new HashMap<>();
    private long version;
    private boolean synced;
    private long syncAskedAt;
    private final KeyCounter sendSoon = new KeyCounter();
    private final Map<AEKey, Long> lastUsed = new HashMap<>();
    private final Map<AEKey, Long> asked = new HashMap<>();
    private final Map<AEKey, Long> fullUntil = new HashMap<>();
    private int ticks;

    RemoteStorage(BridgeLayer layer, long frequency) {
        this.layer = layer;
        this.frequency = frequency;
    }

    private final class Provider implements IStorageProvider {
        @Override
        public void mountInventories(IStorageMounts mounts) {
            if (grid != null) mounts.mount(RemoteStorage.this);
        }
    }

    boolean attached() {
        return grid != null;
    }

    private KeyCounter held() {
        return EscrowData.get(layer.server()).held(frequency);
    }

    private void dirty() {
        EscrowData.get(layer.server()).setDirty();
    }

    void attach(String host, IGrid g) {
        this.host = host;
        if (grid != g) {
            detach();
            this.host = host;
            grid = g;
            g.getStorageService().addGlobalStorageProvider(provider);
        }
        long now = System.currentTimeMillis();
        if (!synced && now - syncAskedAt > SYNC_MS) askSync(now);
    }

    void detach() {
        if (grid != null) {
            grid.getStorageService().removeGlobalStorageProvider(provider);
            grid = null;
        }
        hostInv.clear();
        synced = false;
    }

    private void askSync(long now) {
        syncAskedAt = now;
        JsonObject m = new JsonObject();
        m.addProperty("t", "sync");
        m.addProperty("f", frequency);
        Share.tell(layer, host, m);
    }

    /** main's report of its storage: everything, or what changed since the last one. */
    void inventory(BridgeLayer l, String from, JsonObject d) {
        long v = d.get("v").getAsLong();
        boolean full = d.get("full").getAsBoolean();
        if (!full && (!synced || v != version + 1)) {
            // missed a report: ask for all of it again
            if (host != null) askSync(System.currentTimeMillis());
            synced = false;
            return;
        }
        if (full) hostInv.clear();
        for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("set").entrySet()) {
            AEKey k = l.key(e.getKey());
            if (k != null) hostInv.put(k, e.getValue().getAsLong());
        }
        for (JsonElement e : d.getAsJsonArray("del")) {
            AEKey k = l.key(e.getAsString());
            if (k != null) hostInv.remove(k);
        }
        version = v;
        synced = true;
    }

    /** Things main sent arrived (already in {@link EscrowData}). */
    void arrived(JsonObject data) {
        long now = System.currentTimeMillis();
        for (JsonElement el : data.getAsJsonArray("items")) {
            AEKey k = layer.key(el.getAsJsonArray().get(0).getAsString());
            if (k == null) continue;
            asked.remove(k);
            lastUsed.put(k, now);
        }
    }

    /** main had no room for this; keep it here for a while instead of offering it again. */
    void full(AEKey k) {
        fullUntil.put(k, System.currentTimeMillis() + FULL_MS);
        sendSoon.remove(k);
    }

    void tick(BridgeLayer l) {
        ticks++;
        if (host == null || !Share.canGive(host)) return;
        sendSoon.removeZeros();
        if (!sendSoon.isEmpty()) {
            KeyCounter held = held();
            List<Object[]> out = new ArrayList<>();
            for (Object2LongMap.Entry<AEKey> e : sendSoon) {
                long n = Math.min(e.getLongValue(), held.get(e.getKey()));
                if (n > 0) out.add(new Object[]{e.getKey(), n});
            }
            sendSoon.clear();
            give(out);
        }
        if (ticks % 20 == 0) {
            long now = System.currentTimeMillis();
            List<Object[]> idle = new ArrayList<>();
            KeyCounter held = held();
            held.removeZeros();
            for (Object2LongMap.Entry<AEKey> e : held) {
                if (now - lastUsed.getOrDefault(e.getKey(), 0L) > IDLE_MS && fullUntil.getOrDefault(e.getKey(), 0L) < now) idle.add(new Object[]{e.getKey(), e.getLongValue()});
            }
            give(idle);
        }
    }

    /** Everything held here back to main: before the server stops, or for a ring that went away. */
    void flushAll(String to) {
        host = to;
        if (!Share.canGive(to)) return;
        List<Object[]> all = new ArrayList<>();
        KeyCounter held = held();
        held.removeZeros();
        for (Object2LongMap.Entry<AEKey> e : held) all.add(new Object[]{e.getKey(), e.getLongValue()});
        sendSoon.clear();
        give(all);
    }

    /** Takes these out of what is held here and gives them to main. */
    private void give(List<Object[]> what) {
        if (what.isEmpty()) return;
        KeyCounter held = held();
        JsonArray items = new JsonArray();
        for (Object[] o : what) {
            AEKey k = (AEKey) o[0];
            long n = (Long) o[1];
            held.remove(k, n);
            JsonArray one = new JsonArray();
            one.add(layer.text(k));
            one.add(n);
            items.add(one);
        }
        held.removeZeros();
        dirty();
        JsonObject data = new JsonObject();
        data.addProperty("d", "put");
        data.addProperty("f", frequency);
        data.add("items", items);
        if (!Share.give(layer, host, "put|" + frequency, data)) {
            // not out after all: back where it was
            for (Object[] o : what) held.add((AEKey) o[0], (Long) o[1]);
        }
    }

    /** How much is fetched ahead: a stack of items, sixteen units of anything else. */
    private static long ahead(AEKey k) {
        return k.getAmountPerUnit() == 1 ? 64 : 16L * k.getAmountPerUnit();
    }

    private void fetch(AEKey k, long need) {
        long now = System.currentTimeMillis();
        if (host == null || now - asked.getOrDefault(k, 0L) < ASK_MS) return;
        long there = hostInv.getOrDefault(k, 0L);
        if (there <= 0) return;
        long n = Math.min(there, Math.max(need, ahead(k)));
        asked.put(k, now);
        JsonObject m = new JsonObject();
        m.addProperty("t", "fetch");
        m.addProperty("f", frequency);
        m.addProperty("k", layer.text(k));
        m.addProperty("n", n);
        Share.tell(layer, host, m);
    }

    // ---- what AE2 asks ----

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (grid == null || amount <= 0) return 0;
        if (fullUntil.getOrDefault(what, 0L) > System.currentTimeMillis()) return 0;
        if (mode == Actionable.MODULATE) {
            held().add(what, amount);
            sendSoon.add(what, amount);
            dirty();
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (grid == null || amount <= 0) return 0;
        KeyCounter held = held();
        long have = held.get(what);
        if (amount > have) fetch(what, amount - have);
        if (mode == Actionable.SIMULATE) return Math.min(amount, have + hostInv.getOrDefault(what, 0L));
        long take = Math.min(amount, have);
        if (take > 0) {
            held.remove(what, take);
            long soon = sendSoon.get(what);
            if (soon > 0) sendSoon.remove(what, Math.min(soon, take));
            lastUsed.put(what, System.currentTimeMillis());
            dirty();
            // keep some here for the next pull, so a row of clicks or a busy export bus does not wait
            if (held.get(what) < ahead(what) / 4) fetch(what, ahead(what));
        }
        return take;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (grid == null) return;
        for (Map.Entry<AEKey, Long> e : hostInv.entrySet()) out.add(e.getKey(), e.getValue());
        for (Object2LongMap.Entry<AEKey> e : held()) {
            if (e.getLongValue() > 0) out.add(e.getKey(), e.getLongValue());
        }
    }

    @Override
    public Component getDescription() {
        return BridgeLayer.label();
    }
}
