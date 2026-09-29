package de.kronwerke.core.goal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Play sessions per player, used to count active players when a goal is scaled.
 * A session is (start, end) in epoch milliseconds. Sessions older than 14 days are dropped.
 */
public class ActivityData extends SavedData {
    public static final String NAME = "kronwerke_activity";
    public static final Factory<ActivityData> FACTORY = new Factory<>(ActivityData::new, ActivityData::load, null);
    private static final long KEEP_MS = 14L * 24 * 3600 * 1000;

    private final Map<UUID, List<long[]>> sessions = new HashMap<>();
    private final Map<UUID, Long> open = new HashMap<>();

    public void login(UUID player, long now) {
        open.put(player, now);
    }

    public void logout(UUID player, long now) {
        Long start = open.remove(player);
        if (start != null) {
            sessions.computeIfAbsent(player, k -> new ArrayList<>()).add(new long[]{start, now});
            prune(now);
            setDirty();
        }
    }

    /** Closes every open session, for server stop. */
    public void closeAll(long now) {
        for (UUID u : new ArrayList<>(open.keySet())) logout(u, now);
    }

    private void prune(long now) {
        long cut = now - KEEP_MS;
        sessions.values().forEach(l -> l.removeIf(s -> s[1] < cut));
        sessions.values().removeIf(List::isEmpty);
    }

    /** Players with at least minHours of play in the last days, counting open sessions. */
    public int activePlayers(long now, int days, double minHours) {
        long since = now - days * 24L * 3600 * 1000;
        long need = (long) (minHours * 3600 * 1000);
        int count = 0;
        Map<UUID, Long> total = new HashMap<>();
        sessions.forEach((u, l) -> {
            long t = 0;
            for (long[] s : l) t += Math.max(0, s[1] - Math.max(s[0], since));
            total.merge(u, t, Long::sum);
        });
        open.forEach((u, start) -> total.merge(u, Math.max(0, now - Math.max(start, since)), Long::sum));
        for (long t : total.values()) if (t >= need) count++;
        return count;
    }

    public static ActivityData load(CompoundTag tag, HolderLookup.Provider provider) {
        ActivityData d = new ActivityData();
        ListTag list = tag.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            d.sessions.computeIfAbsent(t.getUUID("player"), k -> new ArrayList<>())
                    .add(new long[]{t.getLong("start"), t.getLong("end")});
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        sessions.forEach((u, l) -> {
            for (long[] s : l) {
                CompoundTag t = new CompoundTag();
                t.putUUID("player", u);
                t.putLong("start", s[0]);
                t.putLong("end", s[1]);
                list.add(t);
            }
        });
        tag.put("sessions", list);
        return tag;
    }
}
