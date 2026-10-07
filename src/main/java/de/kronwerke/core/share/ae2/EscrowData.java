package de.kronwerke.core.share.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Not main: what a linked ring holds on this server, per singularity frequency. Fetched from
 * main's storage and not used yet, or put in here and not at main yet. Saved with the world;
 * it goes back to main when unused, when the ring goes away and before the server stops.
 */
public final class EscrowData extends SavedData {
    public static final String NAME = "kronwerke_ae2_held";
    private static final Factory<EscrowData> FACTORY = new Factory<>(EscrowData::new, EscrowData::load, null);

    private final Map<Long, KeyCounter> held = new HashMap<>();

    public static EscrowData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public KeyCounter held(long f) {
        return held.computeIfAbsent(f, x -> new KeyCounter());
    }

    /** Frequencies that hold something. */
    public List<Long> frequencies() {
        List<Long> out = new ArrayList<>();
        held.forEach((f, c) -> {
            c.removeZeros();
            if (!c.isEmpty()) out.add(f);
        });
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag all = new CompoundTag();
        held.forEach((f, c) -> {
            ListTag l = new ListTag();
            for (Object2LongMap.Entry<AEKey> e : c) {
                if (e.getLongValue() <= 0) continue;
                CompoundTag one = new CompoundTag();
                one.put("k", e.getKey().toTagGeneric(provider));
                one.putLong("n", e.getLongValue());
                l.add(one);
            }
            if (!l.isEmpty()) all.put(Long.toString(f), l);
        });
        tag.put("held", all);
        return tag;
    }

    public static EscrowData load(CompoundTag tag, HolderLookup.Provider provider) {
        EscrowData d = new EscrowData();
        CompoundTag all = tag.getCompound("held");
        for (String f : all.getAllKeys()) {
            KeyCounter c = d.held(Long.parseLong(f));
            for (Tag t : all.getList(f, Tag.TAG_COMPOUND)) {
                CompoundTag one = (CompoundTag) t;
                AEKey k = AEKey.fromTagGeneric(provider, one.getCompound("k"));
                // a key of a mod that is gone is dropped with it
                if (k != null) c.add(k, one.getLong("n"));
            }
        }
        return d;
    }
}
