package de.kronwerke.core.goal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent state of the community goals: per item progress and target, which goals are
 * activated, held, released or completed, who contributed how much, who already got a starter kit.
 */
public class GoalData extends SavedData {
    public static final String NAME = "kronwerke_goals";
    public static final Factory<GoalData> FACTORY = new Factory<>(GoalData::new, GoalData::load, null);

    /** "goal/item" -> deposited */
    private final Map<String, Long> progress = new HashMap<>();
    /** "goal/item" -> target after scaling */
    private final Map<String, Long> targets = new HashMap<>();
    /** goal -> scale factor used at activation */
    private final Map<String, Double> factors = new HashMap<>();
    private final Set<String> activated = new HashSet<>();
    private final Set<String> released = new HashSet<>();
    private final Set<String> completed = new HashSet<>();
    /** goal -> (player -> contributed) */
    private final Map<String, Map<UUID, Long>> contributions = new HashMap<>();
    /** goal -> players who received the starter kit */
    private final Map<String, Set<UUID>> kits = new HashMap<>();
    /** goal -> players who received the goal's stages */
    private final Map<String, Set<UUID>> staged = new HashMap<>();
    /** players who have every stage for testing, see BypassCommand */
    private final Set<UUID> bypass = new HashSet<>();

    private static String key(String goal, String item) {
        return goal + "/" + item;
    }

    public long progress(String goal, String item) {
        return progress.getOrDefault(key(goal, item), 0L);
    }

    public long target(String goal, String item) {
        return targets.getOrDefault(key(goal, item), 0L);
    }

    public double factor(String goal) {
        return factors.getOrDefault(goal, 1.0);
    }

    public boolean isActivated(String goal) {
        return activated.contains(goal);
    }

    public boolean isReleased(String goal) {
        return released.contains(goal);
    }

    public boolean isCompleted(String goal) {
        return completed.contains(goal);
    }

    public Map<UUID, Long> contributions(String goal) {
        return contributions.computeIfAbsent(goal, k -> new HashMap<>());
    }

    public boolean hasKit(String goal, UUID player) {
        return kits.getOrDefault(goal, Set.of()).contains(player);
    }

    public void markKit(String goal, UUID player) {
        kits.computeIfAbsent(goal, k -> new HashSet<>()).add(player);
        setDirty();
    }

    public boolean hasStages(String goal, UUID player) {
        return staged.getOrDefault(goal, Set.of()).contains(player);
    }

    public void markStages(String goal, UUID player) {
        staged.computeIfAbsent(goal, k -> new HashSet<>()).add(player);
        setDirty();
    }

    public boolean hasBypass(UUID player) {
        return bypass.contains(player);
    }

    public Set<UUID> bypassing() {
        return Set.copyOf(bypass);
    }

    public void setBypass(UUID player, boolean on) {
        if (on ? bypass.add(player) : bypass.remove(player)) setDirty();
    }

    public void activate(String goal, double factor, Map<String, Long> itemTargets) {
        activated.add(goal);
        factors.put(goal, factor);
        itemTargets.forEach((item, t) -> targets.put(key(goal, item), t));
        setDirty();
    }

    public long add(String goal, String item, UUID player, long amount) {
        long now = progress(goal, item) + amount;
        progress.put(key(goal, item), now);
        contributions(goal).merge(player, amount, Long::sum);
        setDirty();
        return now;
    }

    public void setProgress(String goal, String item, long amount) {
        progress.put(key(goal, item), amount);
        setDirty();
    }

    public void setTarget(String goal, String item, long amount) {
        targets.put(key(goal, item), amount);
        setDirty();
    }

    public void release(String goal) {
        released.add(goal);
        setDirty();
    }

    public void markCompleted(String goal) {
        completed.add(goal);
        setDirty();
    }

    public void reset(String goal) {
        progress.keySet().removeIf(k -> k.startsWith(goal + "/"));
        targets.keySet().removeIf(k -> k.startsWith(goal + "/"));
        factors.remove(goal);
        activated.remove(goal);
        released.remove(goal);
        completed.remove(goal);
        contributions.remove(goal);
        kits.remove(goal);
        staged.remove(goal);
        setDirty();
    }

    // ---- nbt ----

    private static void putLongMap(CompoundTag tag, String name, Map<String, Long> m) {
        CompoundTag t = new CompoundTag();
        m.forEach(t::putLong);
        tag.put(name, t);
    }

    private static void getLongMap(CompoundTag tag, String name, Map<String, Long> m) {
        CompoundTag t = tag.getCompound(name);
        for (String k : t.getAllKeys()) m.put(k, t.getLong(k));
    }

    private static void putSet(CompoundTag tag, String name, Set<String> s) {
        CompoundTag t = new CompoundTag();
        s.forEach(k -> t.putBoolean(k, true));
        tag.put(name, t);
    }

    private static void getSet(CompoundTag tag, String name, Set<String> s) {
        s.addAll(tag.getCompound(name).getAllKeys());
    }

    public static GoalData load(CompoundTag tag, HolderLookup.Provider provider) {
        GoalData d = new GoalData();
        getLongMap(tag, "progress", d.progress);
        getLongMap(tag, "targets", d.targets);
        CompoundTag f = tag.getCompound("factors");
        for (String k : f.getAllKeys()) d.factors.put(k, f.getDouble(k));
        getSet(tag, "activated", d.activated);
        getSet(tag, "released", d.released);
        getSet(tag, "completed", d.completed);
        CompoundTag contrib = tag.getCompound("contributions");
        for (String goal : contrib.getAllKeys()) {
            CompoundTag per = contrib.getCompound(goal);
            Map<UUID, Long> m = d.contributions(goal);
            for (String u : per.getAllKeys()) m.put(UUID.fromString(u), per.getLong(u));
        }
        CompoundTag kits = tag.getCompound("kits");
        for (String goal : kits.getAllKeys()) {
            Set<UUID> s = d.kits.computeIfAbsent(goal, k -> new HashSet<>());
            for (String u : kits.getCompound(goal).getAllKeys()) s.add(UUID.fromString(u));
        }
        CompoundTag staged = tag.getCompound("staged");
        for (String goal : staged.getAllKeys()) {
            Set<UUID> s = d.staged.computeIfAbsent(goal, k -> new HashSet<>());
            for (String u : staged.getCompound(goal).getAllKeys()) s.add(UUID.fromString(u));
        }
        for (String u : tag.getCompound("bypass").getAllKeys()) d.bypass.add(UUID.fromString(u));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        putLongMap(tag, "progress", progress);
        putLongMap(tag, "targets", targets);
        CompoundTag f = new CompoundTag();
        factors.forEach(f::putDouble);
        tag.put("factors", f);
        putSet(tag, "activated", activated);
        putSet(tag, "released", released);
        putSet(tag, "completed", completed);
        CompoundTag contrib = new CompoundTag();
        contributions.forEach((goal, per) -> {
            CompoundTag t = new CompoundTag();
            per.forEach((u, a) -> t.putLong(u.toString(), a));
            contrib.put(goal, t);
        });
        tag.put("contributions", contrib);
        CompoundTag kitsTag = new CompoundTag();
        kits.forEach((goal, s) -> {
            CompoundTag t = new CompoundTag();
            s.forEach(u -> t.putBoolean(u.toString(), true));
            kitsTag.put(goal, t);
        });
        tag.put("kits", kitsTag);
        CompoundTag stagedTag = new CompoundTag();
        staged.forEach((goal, s) -> {
            CompoundTag t = new CompoundTag();
            s.forEach(u -> t.putBoolean(u.toString(), true));
            stagedTag.put(goal, t);
        });
        tag.put("staged", stagedTag);
        CompoundTag bypassTag = new CompoundTag();
        bypass.forEach(u -> bypassTag.putBoolean(u.toString(), true));
        tag.put("bypass", bypassTag);
        return tag;
    }
}
