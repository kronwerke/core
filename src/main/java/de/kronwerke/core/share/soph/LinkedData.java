package de.kronwerke.core.share.soph;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where this server's linked storage groups are when they are not here, and (on main) the last
 * copy each side world sent of the groups it holds, so a side world that is gone for good does
 * not take a group with it. Saved with the world, together with Sophisticated's own records.
 */
public final class LinkedData extends SavedData {
    public static final String NAME = "kronwerke_soph_linked";
    private static final Factory<LinkedData> FACTORY = new Factory<>(LinkedData::new, LinkedData::load, null);

    /** A group that is on another server: which one, and which world of it (its epoch, "" until known). */
    public record Away(String peer, String epoch) {}

    /** A side world's last copy of a group it holds: the group's record as Sophisticated saves it. */
    public record Mirror(String peer, String group) {}

    final Map<UUID, Away> away = new HashMap<>();
    final Map<UUID, Mirror> mirrors = new HashMap<>();

    public static LinkedData get(MinecraftServer s) {
        LinkedData d = s.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        Gate.AWAY.clear();
        Gate.AWAY.addAll(d.away.keySet());
        return d;
    }

    void setAway(UUID g, Away a) {
        if (a == null) {
            away.remove(g);
            Gate.AWAY.remove(g);
        } else {
            away.put(g, a);
            Gate.AWAY.add(g);
        }
        setDirty();
    }

    private static LinkedData load(CompoundTag tag, HolderLookup.Provider provider) {
        LinkedData d = new LinkedData();
        CompoundTag a = tag.getCompound("away");
        for (String k : a.getAllKeys()) {
            CompoundTag o = a.getCompound(k);
            d.away.put(UUID.fromString(k), new Away(o.getString("peer"), o.getString("epoch")));
        }
        CompoundTag m = tag.getCompound("mirrors");
        for (String k : m.getAllKeys()) {
            CompoundTag o = m.getCompound(k);
            d.mirrors.put(UUID.fromString(k), new Mirror(o.getString("peer"), o.getString("group")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag a = new CompoundTag();
        away.forEach((g, w) -> {
            CompoundTag o = new CompoundTag();
            o.putString("peer", w.peer());
            o.putString("epoch", w.epoch());
            a.put(g.toString(), o);
        });
        tag.put("away", a);
        CompoundTag m = new CompoundTag();
        mirrors.forEach((g, w) -> {
            CompoundTag o = new CompoundTag();
            o.putString("peer", w.peer());
            o.putString("group", w.group());
            m.put(g.toString(), o);
        });
        tag.put("mirrors", m);
        return tag;
    }
}
