package de.kronwerke.core.goal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent progress for community goals. */
public class GoalData extends SavedData {
    public static final String NAME = "kronwerke_goals";
    public static final Factory<GoalData> FACTORY = new Factory<>(GoalData::new, GoalData::load, null);

    private final Map<String, Long> progress = new HashMap<>();
    private final Set<String> completed = new HashSet<>();
    /** goal id -> (player uuid -> contributed amount), for leaderboards */
    private final Map<String, Map<UUID, Long>> contributions = new HashMap<>();

    public long progress(String goalId) {
        return progress.getOrDefault(goalId, 0L);
    }

    public boolean isCompleted(String goalId) {
        return completed.contains(goalId);
    }

    public Set<String> completed() {
        return completed;
    }

    public Map<UUID, Long> contributions(String goalId) {
        return contributions.computeIfAbsent(goalId, k -> new HashMap<>());
    }

    public long add(String goalId, UUID player, long amount) {
        long now = progress(goalId) + amount;
        progress.put(goalId, now);
        contributions(goalId).merge(player, amount, Long::sum);
        setDirty();
        return now;
    }

    public void setProgress(String goalId, long amount) {
        progress.put(goalId, amount);
        setDirty();
    }

    public void markCompleted(String goalId) {
        completed.add(goalId);
        setDirty();
    }

    public void reset(String goalId) {
        progress.remove(goalId);
        completed.remove(goalId);
        contributions.remove(goalId);
        setDirty();
    }

    public static GoalData load(CompoundTag tag, HolderLookup.Provider provider) {
        GoalData d = new GoalData();
        CompoundTag p = tag.getCompound("progress");
        for (String k : p.getAllKeys()) d.progress.put(k, p.getLong(k));
        CompoundTag c = tag.getCompound("completed");
        d.completed.addAll(c.getAllKeys());
        CompoundTag contrib = tag.getCompound("contributions");
        for (String goal : contrib.getAllKeys()) {
            CompoundTag per = contrib.getCompound(goal);
            Map<UUID, Long> m = d.contributions(goal);
            for (String u : per.getAllKeys()) m.put(UUID.fromString(u), per.getLong(u));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag p = new CompoundTag();
        progress.forEach(p::putLong);
        tag.put("progress", p);
        CompoundTag c = new CompoundTag();
        completed.forEach(id -> c.putBoolean(id, true));
        tag.put("completed", c);
        CompoundTag contrib = new CompoundTag();
        contributions.forEach((goal, per) -> {
            CompoundTag t = new CompoundTag();
            per.forEach((u, a) -> t.putLong(u.toString(), a));
            contrib.put(goal, t);
        });
        tag.put("contributions", contrib);
        return tag;
    }
}
