package de.kronwerke.core.share.mek;

import com.google.gson.JsonObject;
import de.kronwerke.core.KronwerkeCore;
import de.kronwerke.core.share.Layer;
import de.kronwerke.core.share.Share;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.security.SecurityMode;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.lib.frequency.Frequency;
import mekanism.common.lib.frequency.FrequencyManager;
import mekanism.common.lib.frequency.FrequencyType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mekanism's Quantum Entangloporter between servers. A frequency with the same name, owner and
 * security that has entangloporters loaded on two servers acts as one: items, fluids, chemicals and
 * energy flow to the side that empties its buffer (where entangloporters put things out), and
 * otherwise even out; heat evens out. Each server only ever gives what it took from its own buffer,
 * through {@link Share}, so nothing doubles.
 */
public final class QuantumLayer implements Layer {
    private static final long FRESH_NS = 2_000_000_000L;
    private static final long DRAIN_MS = 2000;
    private static final String[] AMOUNTS = {"i", "f", "c", "e"};

    private final Field managers;
    private final Field active;
    private final Map<String, Peer> peers = new HashMap<>();
    /** key|type: the amount after this side's last look and its own changes. */
    private final Map<String, Long> expected = new HashMap<>();
    /** key|type: until when this side counts as emptying its buffer. */
    private final Map<String, Long> drainUntil = new HashMap<>();
    private int shared;
    private int lastStateSize;
    private long lastEmptyState;
    private long moved;

    private record Peer(Map<String, JsonObject> freqs, long at) {}

    private QuantumLayer(Field managers, Field active) {
        this.managers = managers;
        this.active = active;
    }

    /** Null when this Mekanism differs from the one this was written against. */
    public static QuantumLayer create() {
        try {
            Field m = FrequencyManager.class.getDeclaredField("managers");
            m.setAccessible(true);
            Field a = InventoryFrequency.class.getDeclaredField("activeQEs");
            a.setAccessible(true);
            return new QuantumLayer(m, a);
        } catch (ReflectiveOperationException | RuntimeException e) {
            KronwerkeCore.LOGGER.warn("Shared network: Mekanism's entangloporters are not shared ({})", e.toString());
            return null;
        }
    }

    @Override
    public String name() {
        return "mek.qe";
    }

    // ---- finding frequencies ----

    @SuppressWarnings("unchecked")
    private List<InventoryFrequency> all() {
        List<InventoryFrequency> out = new ArrayList<>();
        try {
            for (FrequencyManager<?> m : (Collection<FrequencyManager<?>>) managers.get(null)) {
                if (m.getType() != FrequencyType.INVENTORY) continue;
                for (Frequency f : m.getFrequencies()) {
                    if (f instanceof InventoryFrequency q && !q.isRemoved()) out.add(q);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private boolean isActive(InventoryFrequency f) {
        try {
            return !((com.google.common.collect.Table<?, ?, ?>) active.get(f)).isEmpty();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The same on every server: security, owner (unless public) and name. */
    private static String key(Frequency f) {
        return f.getSecurity().name() + "/" + (f.getSecurity() == SecurityMode.PUBLIC || f.getOwner() == null ? "" : f.getOwner()) + "/" + f.getName();
    }

    private InventoryFrequency find(String key) {
        InventoryFrequency any = null;
        for (InventoryFrequency f : all()) {
            if (!key(f).equals(key)) continue;
            if (isActive(f)) return f;
            any = f;
        }
        return any;
    }

    private static IInventorySlot slot(InventoryFrequency f) {
        return f.getInventorySlots(null).get(0);
    }

    private static IExtendedFluidTank fluid(InventoryFrequency f) {
        return f.getFluidTanks(null).get(0);
    }

    private static IChemicalTank chemical(InventoryFrequency f) {
        return f.getChemicalTanks(null).get(0);
    }

    private static IEnergyContainer energy(InventoryFrequency f) {
        return f.getEnergyContainers(null).get(0);
    }

    private static IHeatCapacitor heat(InventoryFrequency f) {
        return f.getHeatCapacitors(null).get(0);
    }

    // ---- one buffer, whatever it holds ----

    /** What kind (empty when nothing or energy), how much, how much fits. */
    private record Amount(String kind, long n, long max) {}

    private static Amount amount(InventoryFrequency f, String type, HolderLookup.Provider reg) {
        return switch (type) {
            case "i" -> {
                IInventorySlot s = slot(f);
                ItemStack st = s.getStack();
                yield new Amount(st.isEmpty() ? "" : st.copyWithCount(1).save(reg).toString(), st.getCount(), st.isEmpty() ? 64 : s.getLimit(st));
            }
            case "f" -> {
                IExtendedFluidTank t = fluid(f);
                FluidStack st = t.getFluid();
                yield new Amount(st.isEmpty() ? "" : st.copyWithAmount(1).save(reg).toString(), st.getAmount(), t.getCapacity());
            }
            case "c" -> {
                IChemicalTank t = chemical(f);
                ChemicalStack st = t.getStack();
                yield new Amount(st.isEmpty() ? "" : st.copyWithAmount(1).save(reg).toString(), st.getAmount(), t.getCapacity());
            }
            default -> {
                IEnergyContainer e = energy(f);
                yield new Amount("", e.getEnergy(), e.getMaxEnergy());
            }
        };
    }

    /** Takes up to n out of this buffer; what was taken, ready for the bus, or null. */
    private static JsonObject take(InventoryFrequency f, String type, long n, HolderLookup.Provider reg) {
        JsonObject d = new JsonObject();
        d.addProperty("t", type);
        switch (type) {
            case "i" -> {
                ItemStack st = slot(f).extractItem((int) Math.min(n, Integer.MAX_VALUE), Action.EXECUTE, AutomationType.INTERNAL);
                if (st.isEmpty()) return null;
                d.addProperty("s", st.save(reg).toString());
            }
            case "f" -> {
                FluidStack st = fluid(f).extract((int) Math.min(n, Integer.MAX_VALUE), Action.EXECUTE, AutomationType.INTERNAL);
                if (st.isEmpty()) return null;
                d.addProperty("s", st.save(reg).toString());
            }
            case "c" -> {
                ChemicalStack st = chemical(f).extract(n, Action.EXECUTE, AutomationType.INTERNAL);
                if (st.isEmpty()) return null;
                d.addProperty("s", st.save(reg).toString());
            }
            case "e" -> {
                long got = energy(f).extract(n, Action.EXECUTE, AutomationType.INTERNAL);
                if (got <= 0) return null;
                d.addProperty("n", got);
            }
            default -> {
                return null;
            }
        }
        return d;
    }

    /**
     * Puts what came over into this buffer. Everything is read before anything changes, so an error
     * leaves the buffer as it was. Returns what did not fit, or null.
     */
    private static JsonObject put(InventoryFrequency f, JsonObject d, HolderLookup.Provider reg) {
        String type = d.get("t").getAsString();
        JsonObject rest = new JsonObject();
        rest.addProperty("f", d.has("f") ? d.get("f").getAsString() : "");
        rest.addProperty("t", type);
        try {
            switch (type) {
                case "i" -> {
                    ItemStack st = ItemStack.parse(reg, parse(d)).orElseThrow();
                    ItemStack left = slot(f).insertItem(st, Action.EXECUTE, AutomationType.INTERNAL);
                    if (left.isEmpty()) return null;
                    rest.addProperty("s", left.save(reg).toString());
                }
                case "f" -> {
                    FluidStack st = FluidStack.parse(reg, parse(d)).orElseThrow();
                    FluidStack left = fluid(f).insert(st, Action.EXECUTE, AutomationType.INTERNAL);
                    if (left.isEmpty()) return null;
                    rest.addProperty("s", left.save(reg).toString());
                }
                case "c" -> {
                    ChemicalStack st = ChemicalStack.parse(reg, parse(d)).orElseThrow();
                    ChemicalStack left = chemical(f).insert(st, Action.EXECUTE, AutomationType.INTERNAL);
                    if (left.isEmpty()) return null;
                    rest.addProperty("s", left.save(reg).toString());
                }
                case "e" -> {
                    long left = energy(f).insert(d.get("n").getAsLong(), Action.EXECUTE, AutomationType.INTERNAL);
                    if (left <= 0) return null;
                    rest.addProperty("n", left);
                }
                case "h" -> {
                    heat(f).handleHeat(d.get("q").getAsDouble());
                    return null;
                }
                default -> {
                    return d;
                }
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | java.util.NoSuchElementException e) {
            throw new IllegalArgumentException("unreadable " + type + ": " + e.getMessage(), e);
        }
        return rest;
    }

    private static Tag parse(JsonObject d) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CompoundTag t = TagParser.parseTag(d.get("s").getAsString());
        return t;
    }

    // ---- each tick ----

    @Override
    public void tick(MinecraftServer server) {
        HolderLookup.Provider reg = server.registryAccess();
        long now = System.currentTimeMillis();
        Map<String, InventoryFrequency> local = new LinkedHashMap<>();
        for (InventoryFrequency f : all()) {
            if (isActive(f)) local.put(key(f), f);
        }
        JsonObject state = new JsonObject();
        for (Map.Entry<String, InventoryFrequency> e : local.entrySet()) {
            String key = e.getKey();
            InventoryFrequency f = e.getValue();
            JsonObject s = new JsonObject();
            for (String type : AMOUNTS) {
                Amount a = amount(f, type, reg);
                Long exp = expected.get(key + "|" + type);
                if (exp != null && a.n() < exp) drainUntil.put(key + "|" + type, now + DRAIN_MS);
                expected.put(key + "|" + type, a.n());
                JsonObject o = new JsonObject();
                o.addProperty("k", a.kind());
                o.addProperty("n", a.n());
                o.addProperty("m", a.max());
                o.addProperty("d", drains(key, type, now));
                s.add(type, o);
            }
            IHeatCapacitor h = heat(f);
            JsonObject ho = new JsonObject();
            ho.addProperty("t", h.getTemperature());
            ho.addProperty("c", h.getHeatCapacity());
            s.add("h", ho);
            state.add(key, s);
        }
        expected.keySet().removeIf(k -> !local.containsKey(k.substring(0, k.lastIndexOf('|'))));
        drainUntil.keySet().removeIf(k -> !local.containsKey(k.substring(0, k.lastIndexOf('|'))));
        // an empty state only now and then: the other side needs to hear that frequencies went away, not ten times a second
        if (state.size() > 0 || lastStateSize > 0 || now - lastEmptyState > 5000) {
            Share.state(this, state);
            if (state.size() == 0) lastEmptyState = now;
        }
        lastStateSize = state.size();

        long nano = System.nanoTime();
        int both = 0;
        for (String peer : Share.peers()) {
            Peer p = peers.get(peer);
            if (p == null || nano - p.at() > FRESH_NS) continue;
            for (Map.Entry<String, InventoryFrequency> e : local.entrySet()) {
                JsonObject theirs = p.freqs().get(e.getKey());
                if (theirs == null) continue;
                both++;
                for (String type : AMOUNTS) give(server, peer, p, e.getKey(), e.getValue(), type, state.getAsJsonObject(e.getKey()).getAsJsonObject(type), theirs.getAsJsonObject(type), now, reg);
                giveHeat(peer, p, e.getKey(), e.getValue(), theirs.getAsJsonObject("h"));
            }
        }
        shared = both;
    }

    private boolean drains(String key, String type, long now) {
        return drainUntil.getOrDefault(key + "|" + type, 0L) > now;
    }

    private void give(MinecraftServer server, String peer, Peer p, String key, InventoryFrequency f, String type, JsonObject mine, JsonObject theirs, long now, HolderLookup.Provider reg) {
        if (theirs == null) return;
        String tag = key + "|" + type;
        if (Share.busy(this, peer, tag) || p.at() <= Share.answeredAt(this, peer, tag)) return;
        long n = mine.get("n").getAsLong();
        if (n <= 0) return;
        String kind = mine.get("k").getAsString(), theirKind = theirs.get("k").getAsString();
        long pn = theirs.get("n").getAsLong(), pm = theirs.get("m").getAsLong();
        if (!type.equals("e") && pn > 0 && !kind.equals(theirKind)) return;
        long room = pm - pn;
        if (room <= 0) return;
        boolean theyDrain = theirs.get("d").getAsBoolean(), weDrain = drains(key, type, now);
        // the side that empties its buffer gets everything and gives nothing back; otherwise half the difference
        if (weDrain && !theyDrain) return;
        long want = theyDrain && !weDrain ? n : (n - pn) / 2;
        want = Math.min(want, room);
        if (want <= 0 || !Share.canGive(peer)) return;
        JsonObject what = take(f, type, want, reg);
        if (what == null) return;
        what.addProperty("f", key);
        expected.put(tag, amount(f, type, reg).n());
        if (!Share.give(this, peer, tag, what)) {
            put(f, what, reg);
            expected.put(tag, amount(f, type, reg).n());
            return;
        }
        moved++;
    }

    /** Heat evens out: half the difference each time, both capacities counted. */
    private void giveHeat(String peer, Peer p, String key, InventoryFrequency f, JsonObject theirs) {
        if (theirs == null) return;
        String tag = key + "|h";
        if (Share.busy(this, peer, tag) || p.at() <= Share.answeredAt(this, peer, tag)) return;
        IHeatCapacitor h = heat(f);
        double t = h.getTemperature(), c = h.getHeatCapacity();
        double pt = theirs.get("t").getAsDouble(), pc = theirs.get("c").getAsDouble();
        if (t - pt < 5 || c <= 0 || pc <= 0) return;
        double q = (t - pt) * (c * pc / (c + pc)) / 2;
        if (!Share.canGive(peer)) return;
        h.handleHeat(-q);
        JsonObject what = new JsonObject();
        what.addProperty("t", "h");
        what.addProperty("f", key);
        what.addProperty("q", q);
        if (!Share.give(this, peer, tag, what)) h.handleHeat(q);
    }

    // ---- from the other server ----

    @Override
    public void onState(String from, JsonObject state) {
        Map<String, JsonObject> m = new HashMap<>();
        for (String k : state.keySet()) m.put(k, state.getAsJsonObject(k));
        peers.put(from, new Peer(m, System.nanoTime()));
    }

    @Override
    public JsonObject receive(MinecraftServer server, String from, JsonObject data) {
        String key = data.get("f").getAsString();
        InventoryFrequency f = find(key);
        if (f == null) return data;
        JsonObject rest = put(f, data, server.registryAccess());
        String type = data.get("t").getAsString();
        if (!type.equals("h")) expected.put(key + "|" + type, amount(f, type, server.registryAccess()).n());
        return rest;
    }

    @Override
    public JsonObject refund(MinecraftServer server, JsonObject data) {
        // the same as receiving it: back into the frequency's buffer, if it still exists
        String key = data.get("f").getAsString();
        InventoryFrequency f = find(key);
        if (f == null) return data;
        JsonObject rest = put(f, data, server.registryAccess());
        String type = data.get("t").getAsString();
        if (!type.equals("h")) expected.put(key + "|" + type, amount(f, type, server.registryAccess()).n());
        return rest;
    }

    @Override
    public String describe() {
        return shared + " frequencies on both sides, " + moved + " moves since start";
    }
}
