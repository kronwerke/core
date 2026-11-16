package de.kronwerke.core.share.rs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.composite.CompositeAwareChild;
import com.refinedmods.refinedstorage.api.storage.composite.ParentComposite;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import de.kronwerke.core.share.Share;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Not main: main's Refined Storage network as one storage of the network a linked transmitter sits
 * in. What main has is known from its reports; taking something fetches it first (the first take of
 * a kind waits a moment, a stack is fetched ahead after that), and what this side holds, fetched and
 * not used yet or put in and not sent yet, is in {@link RsEscrow}. The network's list is kept up to
 * date by telling its composite storage about every change, the way Refined Storage's external
 * storage does.
 */
final class RsRemote implements CompositeAwareChild {
    private static final long IDLE_MS = 60_000, ASK_MS = 2000, FULL_MS = 30_000, SYNC_MS = 5000;

    final String link;
    private final RsBridgeLayer layer;
    private String host;
    private StorageNetworkComponent storage;
    private final Map<String, Long> hostInv = new HashMap<>();
    private long version;
    private boolean synced;
    private long syncAskedAt;
    private final Map<String, Long> sendSoon = new HashMap<>();
    private final Map<String, Long> lastUsed = new HashMap<>();
    private final Map<String, Long> asked = new HashMap<>();
    private final Map<String, Long> fullUntil = new HashMap<>();
    private final Set<ParentComposite> parents = new HashSet<>();
    /** What the network's list was last told this storage holds. */
    private final Map<String, Long> shown = new HashMap<>();
    private int ticks;

    RsRemote(RsBridgeLayer layer, String link) {
        this.layer = layer;
        this.link = link;
    }

    boolean attached() {
        return storage != null;
    }

    /** For /kw share: how this link stands. */
    String state() {
        return link + (synced ? " synced v" + version : " waiting for main") + ", " + hostInv.size() + " kinds on main, " + shown.size() + " shown, " + parents.size() + " lists, network lists " + (storage == null ? "-" : storage.getAll().size() + " kinds");
    }

    private RsEscrow escrow() {
        return RsEscrow.get(layer.server());
    }

    void attach(String host, StorageNetworkComponent s) {
        this.host = host;
        if (storage != s || !s.hasSource(x -> x == this)) {
            detach();
            this.host = host;
            storage = s;
            s.addSource(this);
        }
        long now = System.currentTimeMillis();
        if (!synced && now - syncAskedAt > SYNC_MS) askSync(now);
    }

    void detach() {
        if (storage != null) {
            try {
                storage.removeSource(this);
            } catch (RuntimeException e) {
                // the network is gone already
            }
            storage = null;
        }
        hostInv.clear();
        shown.clear();
        synced = false;
    }

    private void askSync(long now) {
        syncAskedAt = now;
        JsonObject m = new JsonObject();
        m.addProperty("t", "sync");
        m.addProperty("l", link);
        Share.tell(layer, host, m);
    }

    // ---- the list of the network ----

    private long view(String k) {
        return hostInv.getOrDefault(k, 0L) + escrow().get(link, k);
    }

    /** Tells the network's list how much of this kind is here now. */
    private void changed(String k) {
        long now = view(k), was = shown.getOrDefault(k, 0L);
        if (now == was) return;
        ResourceKey key = layer.key(k);
        if (key == null) return;
        if (now > 0) shown.put(k, now);
        else shown.remove(k);
        for (ParentComposite p : parents) {
            if (now > was) p.addToCache(key, now - was);
            else p.removeFromCache(key, was - now);
        }
    }

    // ---- what main reports and sends ----

    void inventory(JsonObject d) {
        long v = d.get("v").getAsLong();
        boolean full = d.get("full").getAsBoolean();
        if (!full && (!synced || v != version + 1)) {
            if (host != null) askSync(System.currentTimeMillis());
            synced = false;
            return;
        }
        Set<String> touched = new HashSet<>();
        if (full) {
            touched.addAll(hostInv.keySet());
            hostInv.clear();
        }
        for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("set").entrySet()) {
            hostInv.put(e.getKey(), e.getValue().getAsLong());
            touched.add(e.getKey());
        }
        for (JsonElement e : d.getAsJsonArray("del")) {
            hostInv.remove(e.getAsString());
            touched.add(e.getAsString());
        }
        version = v;
        synced = true;
        for (String k : touched) changed(k);
    }

    void arrived(JsonArray items) {
        long now = System.currentTimeMillis();
        for (JsonElement el : items) {
            String k = el.getAsJsonArray().get(0).getAsString();
            asked.remove(k);
            lastUsed.put(k, now);
            changed(k);
        }
    }

    void full(String k) {
        fullUntil.put(k, System.currentTimeMillis() + FULL_MS);
        sendSoon.remove(k);
        changed(k);
    }

    void tick() {
        ticks++;
        if (host == null || !Share.canGive(host)) return;
        if (!sendSoon.isEmpty()) {
            List<Object[]> out = new ArrayList<>();
            for (Map.Entry<String, Long> e : sendSoon.entrySet()) {
                long n = Math.min(e.getValue(), escrow().get(link, e.getKey()));
                if (n > 0) out.add(new Object[]{e.getKey(), n});
            }
            sendSoon.clear();
            give(out);
        }
        if (ticks % 20 == 0) {
            long now = System.currentTimeMillis();
            List<Object[]> idle = new ArrayList<>();
            for (Map.Entry<String, Long> e : escrow().held(link).entrySet()) {
                if (now - lastUsed.getOrDefault(e.getKey(), 0L) > IDLE_MS && fullUntil.getOrDefault(e.getKey(), 0L) < now) idle.add(new Object[]{e.getKey(), e.getValue()});
            }
            give(idle);
        }
    }

    /** Everything held here back to main: before the server stops, or for a link that went away. */
    void flushAll(String to) {
        host = to;
        if (!Share.canGive(to)) return;
        List<Object[]> all = new ArrayList<>();
        for (Map.Entry<String, Long> e : escrow().held(link).entrySet()) all.add(new Object[]{e.getKey(), e.getValue()});
        sendSoon.clear();
        give(all);
    }

    private void give(List<Object[]> what) {
        if (what.isEmpty()) return;
        RsEscrow e = escrow();
        JsonArray items = new JsonArray();
        for (Object[] o : what) {
            e.add(link, (String) o[0], -(Long) o[1]);
            JsonArray one = new JsonArray();
            one.add((String) o[0]);
            one.add((Long) o[1]);
            items.add(one);
        }
        JsonObject data = new JsonObject();
        data.addProperty("d", "put");
        data.addProperty("l", link);
        data.add("items", items);
        if (!Share.give(layer, host, "put|" + link, data)) {
            for (Object[] o : what) e.add(link, (String) o[0], (Long) o[1]);
            return;
        }
        // what was put in stays in the list: main has it now and says so in its next report
        for (Object[] o : what) {
            String k = (String) o[0];
            hostInv.merge(k, (Long) o[1], Long::sum);
            changed(k);
        }
    }

    private static long ahead(ResourceKey k) {
        return k instanceof FluidResource ? 16_000 : 64;
    }

    private void fetch(String k, ResourceKey key, long need) {
        long now = System.currentTimeMillis();
        if (host == null || now - asked.getOrDefault(k, 0L) < ASK_MS) return;
        long there = hostInv.getOrDefault(k, 0L);
        if (there <= 0) return;
        long n = Math.min(there, Math.max(need, ahead(key)));
        asked.put(k, now);
        JsonObject m = new JsonObject();
        m.addProperty("t", "fetch");
        m.addProperty("l", link);
        m.addProperty("k", k);
        m.addProperty("n", n);
        Share.tell(layer, host, m);
    }

    // ---- what Refined Storage asks ----

    @Override
    public long insert(ResourceKey what, long amount, Action action, Actor actor) {
        if (storage == null || amount <= 0) return 0;
        String k = layer.text(what);
        if (k == null || fullUntil.getOrDefault(k, 0L) > System.currentTimeMillis()) return 0;
        if (action == Action.EXECUTE) {
            escrow().add(link, k, amount);
            sendSoon.merge(k, amount, Long::sum);
            changed(k);
        }
        return amount;
    }

    @Override
    public long extract(ResourceKey what, long amount, Action action, Actor actor) {
        if (storage == null || amount <= 0) return 0;
        String k = layer.text(what);
        if (k == null) return 0;
        long have = escrow().get(link, k);
        if (amount > have) fetch(k, what, amount - have);
        if (action == Action.SIMULATE) return Math.min(amount, have);
        long take = Math.min(amount, have);
        if (take > 0) {
            escrow().add(link, k, -take);
            long soon = sendSoon.getOrDefault(k, 0L);
            if (soon > 0) {
                if (soon <= take) sendSoon.remove(k);
                else sendSoon.put(k, soon - take);
            }
            lastUsed.put(k, System.currentTimeMillis());
            changed(k);
            if (escrow().get(link, k) < ahead(what) / 4) fetch(k, what, ahead(what));
        }
        return take;
    }

    @Override
    public Collection<ResourceAmount> getAll() {
        List<ResourceAmount> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : shown.entrySet()) {
            ResourceKey key = layer.key(e.getKey());
            if (key != null && e.getValue() > 0) out.add(new ResourceAmount(key, e.getValue()));
        }
        return out;
    }

    @Override
    public long getStored() {
        long n = 0;
        for (long v : shown.values()) n += v;
        return n;
    }

    @Override
    public void onAddedIntoComposite(ParentComposite parent) {
        // the composite has just read getAll(); from now on it hears of every change
        parents.add(parent);
        Set<String> all = new HashSet<>(hostInv.keySet());
        all.addAll(escrow().held(link).keySet());
        for (String k : all) changed(k);
    }

    @Override
    public void onRemovedFromComposite(ParentComposite parent) {
        parents.remove(parent);
    }

    @Override
    public Amount compositeInsert(ResourceKey what, long amount, Action action, Actor actor) {
        return new Amount(insert(what, amount, action, actor), 0);
    }

    @Override
    public Amount compositeExtract(ResourceKey what, long amount, Action action, Actor actor) {
        return new Amount(extract(what, amount, action, actor), 0);
    }
}
