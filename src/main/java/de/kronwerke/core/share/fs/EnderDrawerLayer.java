package de.kronwerke.core.share.fs;

import com.buuz135.functionalstorage.inventory.EnderInventoryHandler;
import com.buuz135.functionalstorage.world.EnderSavedData;
import com.google.gson.JsonObject;
import de.kronwerke.core.link.Role;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Functional Storage's Ender Drawers between servers. A frequency linked on main and on a side
 * world is one drawer whose contents live on main. The side world keeps a stack of it at hand
 * (main tops it up when it runs low), what goes in there beyond half again as much goes home, and when the
 * side world stops everything it holds goes home, so a reset loses nothing.
 */
public final class EnderDrawerLayer implements Layer {
    private static final long FRESH_NS = 2_000_000_000L;

    private final Map<String, Peer> peers = new HashMap<>();
    private int here;
    private int lastSize;
    private long lastEmpty, moved;

    private record Peer(Map<String, JsonObject> drawers, long at) {}

    public static EnderDrawerLayer create() {
        return new EnderDrawerLayer();
    }

    @Override
    public String name() {
        return "fs.ender";
    }

    private static Map<String, EnderInventoryHandler> drawers(MinecraftServer server) {
        return new LinkedHashMap<>(EnderSavedData.getInstance(server.overworld()).getItemHandlers());
    }

    /** What a side world keeps in its drawer: a stack, or half the drawer when that is smaller. */
    private static long stock(ItemStack st, long limit) {
        return Math.max(1, Math.min(st.getMaxStackSize(), limit / 2));
    }

    private static String kind(ItemStack st, HolderLookup.Provider reg) {
        return st.isEmpty() ? "" : st.copyWithCount(1).save(reg).toString();
    }

    @Override
    public void tick(MinecraftServer server) {
        HolderLookup.Provider reg = server.registryAccess();
        long now = System.currentTimeMillis();
        Map<String, EnderInventoryHandler> local = drawers(server);
        here = local.size();
        JsonObject state = new JsonObject();
        for (Map.Entry<String, EnderInventoryHandler> e : local.entrySet()) {
            EnderInventoryHandler h = e.getValue();
            ItemStack st = h.getStoredStacks().get(0).getStack();
            long n = st.isEmpty() ? 0 : h.getStoredStacks().get(0).getAmount();
            JsonObject o = new JsonObject();
            o.addProperty("k", kind(st, reg));
            o.addProperty("n", n);
            // what this drawer holds of its item; unknown (-1) while it is empty
            o.addProperty("m", st.isEmpty() ? -1 : h.getSlotLimit(0, st));
            state.add(e.getKey(), o);
        }
        if (state.size() > 0 || lastSize > 0 || now - lastEmpty > 5000) {
            Share.state(this, state);
            if (state.size() == 0) lastEmpty = now;
        }
        lastSize = state.size();

        long nano = System.nanoTime();
        for (String peer : Share.peers()) {
            Peer p = peers.get(peer);
            if (p == null || nano - p.at() > FRESH_NS) continue;
            for (Map.Entry<String, EnderInventoryHandler> e : local.entrySet()) {
                JsonObject theirs = p.drawers().get(e.getKey());
                if (theirs != null) give(peer, p, e.getKey(), e.getValue(), state.getAsJsonObject(e.getKey()), theirs, reg);
            }
        }
    }

    private void give(String peer, Peer p, String freq, EnderInventoryHandler h, JsonObject mine, JsonObject theirs, HolderLookup.Provider reg) {
        // a creative drawer gives without getting emptier: it never gives across
        if (h.isCreative() || Share.busy(this, peer, freq) || p.at() <= Share.answeredAt(this, peer, freq)) return;
        long n = mine.get("n").getAsLong();
        if (n <= 0) return;
        String kind = mine.get("k").getAsString(), theirKind = theirs.get("k").getAsString();
        long pn = theirs.get("n").getAsLong();
        if (pn > 0 && !kind.equals(theirKind)) return;
        ItemStack st = h.getStoredStacks().get(0).getStack();
        long theirLimit = theirs.get("m").getAsLong() < 0 ? h.getSlotLimit(0, st) : theirs.get("m").getAsLong();
        long room = theirLimit - pn;
        if (room <= 0) return;
        long want;
        if (Role.main()) {
            // home: keeps the side world's drawer stocked, so taking out there works at once
            long stock = stock(st, theirLimit);
            if (pn >= stock / 2) return;
            want = Math.min(n, stock - pn);
        } else {
            // a side world: keeps a stock here, what goes beyond half again as much goes home
            long stock = stock(st, h.getSlotLimit(0, st));
            if (n <= stock + stock / 2) return;
            want = n - stock;
        }
        want = Math.min(want, room);
        if (want <= 0 || !Share.canGive(peer)) return;
        ItemStack got = h.extractItem(0, (int) Math.min(want, Integer.MAX_VALUE), false);
        if (got.isEmpty()) return;
        JsonObject what = new JsonObject();
        what.addProperty("f", freq);
        what.addProperty("s", got.copyWithCount(1).save(reg).toString());
        what.addProperty("n", got.getCount());
        if (!Share.give(this, peer, freq, what)) {
            h.insertItem(0, got, false);
            return;
        }
        moved += got.getCount();
    }

    @Override
    public void onState(String from, JsonObject state) {
        Map<String, JsonObject> m = new HashMap<>();
        for (String k : state.keySet()) m.put(k, state.getAsJsonObject(k));
        peers.put(from, new Peer(m, System.nanoTime()));
    }

    /** Into the drawer of that frequency, whatever fits; the rest goes back. */
    private JsonObject put(MinecraftServer server, JsonObject data) {
        String freq = data.get("f").getAsString();
        EnderInventoryHandler h = EnderSavedData.getInstance(server.overworld()).getItemHandlers().get(freq);
        if (h == null) return data;
        ItemStack one;
        try {
            one = ItemStack.parse(server.registryAccess(), TagParser.parseTag(data.get("s").getAsString())).orElseThrow();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | java.util.NoSuchElementException e) {
            throw new IllegalArgumentException("unreadable item: " + e.getMessage(), e);
        }
        long n = data.get("n").getAsLong();
        ItemStack left = h.insertItem(0, one.copyWithCount((int) n), false);
        if (left.isEmpty()) return null;
        JsonObject rest = new JsonObject();
        rest.addProperty("f", freq);
        rest.addProperty("s", data.get("s").getAsString());
        rest.addProperty("n", left.getCount());
        return rest;
    }

    @Override
    public JsonObject receive(MinecraftServer server, String from, JsonObject data) {
        return put(server, data);
    }

    @Override
    public JsonObject refund(MinecraftServer server, JsonObject data) {
        return put(server, data);
    }

    @Override
    public void onStopping(MinecraftServer server) {
        if (Role.main()) return;
        // a side world stops or is reset: everything goes home now
        HolderLookup.Provider reg = server.registryAccess();
        for (String peer : Share.peers()) {
            Peer p = peers.get(peer);
            if (p == null) continue;
            for (Map.Entry<String, EnderInventoryHandler> e : drawers(server).entrySet()) {
                JsonObject theirs = p.drawers().get(e.getKey());
                if (theirs == null || !Share.canGive(peer)) continue;
                EnderInventoryHandler h = e.getValue();
                if (h.isCreative()) continue;
                ItemStack st = h.getStoredStacks().get(0).getStack();
                int n = st.isEmpty() ? 0 : h.getStoredStacks().get(0).getAmount();
                if (n <= 0) continue;
                ItemStack got = h.extractItem(0, n, false);
                if (got.isEmpty()) continue;
                JsonObject what = new JsonObject();
                what.addProperty("f", e.getKey());
                what.addProperty("s", got.copyWithCount(1).save(reg).toString());
                what.addProperty("n", got.getCount());
                if (!Share.give(this, peer, e.getKey(), what)) h.insertItem(0, got, false);
            }
        }
    }

    @Override
    public String describe() {
        int both = 0;
        for (Peer p : peers.values()) both += p.drawers().size();
        return here + " frequencies here, " + both + " on other servers, " + moved + " items moved since start";
    }
}
