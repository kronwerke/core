package de.kronwerke.core.share.rs;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Not main: what a linked Refined Storage network holds on this server, per link (the receiver on
 * main that the network card names). Fetched from main and not used yet, or put in here and not at
 * main yet; resources are kept as their text, so a mod that goes away loses nothing silently.
 */
public final class RsEscrow extends SavedData {
    public static final String NAME = "kronwerke_rs_held";
    private static final Factory<RsEscrow> FACTORY = new Factory<>(RsEscrow::new, RsEscrow::load, null);

    private final Map<String, Map<String, Long>> held = new HashMap<>();

    public static RsEscrow get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Map<String, Long> held(String link) {
        return held.computeIfAbsent(link, x -> new HashMap<>());
    }

    public long get(String link, String key) {
        return held(link).getOrDefault(key, 0L);
    }

    public void add(String link, String key, long n) {
        if (n == 0) return;
        Map<String, Long> m = held(link);
        long v = m.getOrDefault(key, 0L) + n;
        if (v <= 0) m.remove(key);
        else m.put(key, v);
        setDirty();
    }

    /** Links that hold something. */
    public List<String> links() {
        List<String> out = new ArrayList<>();
        held.forEach((l, m) -> {
            if (!m.isEmpty()) out.add(l);
        });
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag all = new CompoundTag();
        held.forEach((l, m) -> {
            if (m.isEmpty()) return;
            CompoundTag one = new CompoundTag();
            m.forEach(one::putLong);
            all.put(l, one);
        });
        tag.put("held", all);
        return tag;
    }

    public static RsEscrow load(CompoundTag tag, HolderLookup.Provider provider) {
        RsEscrow d = new RsEscrow();
        CompoundTag all = tag.getCompound("held");
        for (String l : all.getAllKeys()) {
            CompoundTag one = all.getCompound(l);
            Map<String, Long> m = d.held(l);
            for (String k : one.getAllKeys()) m.put(k, one.getLong(k));
        }
        return d;
    }
}
