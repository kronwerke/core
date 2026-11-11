package de.kronwerke.core.perf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.kronwerke.core.config.KronwerkeConfig;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Which entities carry which scoreboard tag, per dimension. A command selector like
 * {@code @e[type=marker,tag=x]} walks every entity of the dimension in vanilla; datapacks
 * (Incendium, Tool Trims) run about ten of them every tick, which was 15 percent of the tick
 * with 50 players in the Nether. A selector with a positive tag now only looks at the entities
 * that carry that tag, and still tests everything else of the selector as before.
 *
 * The index follows exactly the entities a vanilla selector sees: those the level tracks
 * (onTrackingStart to onTrackingEnd). Every entity's tag set is replaced by one that reports
 * its changes, so tags set by commands, by mods, from NBT and through getTags() all count.
 */
public final class TagIndex {

    /** An entity: the level whose index holds it, or null. */
    public interface Tracked {
        ServerLevel kronwerke$tagLevel();

        void kronwerke$tagLevel(ServerLevel level);
    }

    /** A selector or its parser: the tags it requires. */
    public interface Required {
        List<String> kronwerke$requiredTags();

        void kronwerke$requiredTags(List<String> tags);
    }

    private static final Map<ServerLevel, Map<String, ReferenceOpenHashSet<Entity>>> INDEX = new IdentityHashMap<>();

    private TagIndex() {}

    public static boolean enabled() {
        try {
            return KronwerkeConfig.TAG_INDEX.get();
        } catch (IllegalStateException e) {
            return false; // config not loaded yet
        }
    }

    /** The level starts tracking the entity: its tags go into that level's index. */
    public static void track(Entity e, ServerLevel level) {
        Tracked t = (Tracked) e;
        if (t.kronwerke$tagLevel() != null) untrack(e);
        t.kronwerke$tagLevel(level);
        for (String tag : e.getTags()) add(level, tag, e);
    }

    /** The level stops tracking the entity. */
    public static void untrack(Entity e) {
        Tracked t = (Tracked) e;
        ServerLevel level = t.kronwerke$tagLevel();
        if (level == null) return;
        for (String tag : e.getTags()) remove(level, tag, e);
        t.kronwerke$tagLevel(null);
    }

    static void added(Entity owner, String tag) {
        ServerLevel level = ((Tracked) owner).kronwerke$tagLevel();
        if (level != null) add(level, tag, owner);
    }

    static void removed(Entity owner, String tag) {
        ServerLevel level = ((Tracked) owner).kronwerke$tagLevel();
        if (level != null) remove(level, tag, owner);
    }

    private static void add(ServerLevel level, String tag, Entity e) {
        INDEX.computeIfAbsent(level, k -> new HashMap<>()).computeIfAbsent(tag, k -> new ReferenceOpenHashSet<>()).add(e);
    }

    private static void remove(ServerLevel level, String tag, Entity e) {
        Map<String, ReferenceOpenHashSet<Entity>> byTag = INDEX.get(level);
        if (byTag == null) return;
        ReferenceOpenHashSet<Entity> set = byTag.get(tag);
        if (set == null) return;
        set.remove(e);
        if (set.isEmpty()) byTag.remove(tag);
    }

    /** The entities of the level that carry the rarest of the tags (all of them must be carried). */
    public static Set<Entity> candidates(ServerLevel level, List<String> tags) {
        Map<String, ReferenceOpenHashSet<Entity>> byTag = INDEX.get(level);
        if (byTag == null) return Set.of();
        Set<Entity> best = null;
        for (String tag : tags) {
            Set<Entity> set = byTag.get(tag);
            if (set == null) return Set.of();
            if (best == null || set.size() < best.size()) best = set;
        }
        return best == null ? Set.of() : best;
    }

    public static void clear() {
        INDEX.clear();
    }

    /** Compares the index with every entity the levels track; for /kw admin tagindex. */
    public static String check(MinecraftServer server) {
        int entities = 0, tagged = 0, missing = 0, stale = 0;
        List<String> examples = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            Map<String, ReferenceOpenHashSet<Entity>> byTag = INDEX.getOrDefault(level, Map.of());
            Set<Entity> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (Entity e : level.getAllEntities()) {
                entities++;
                seen.add(e);
                for (String tag : e.getTags()) {
                    tagged++;
                    Set<Entity> set = byTag.get(tag);
                    if (set == null || !set.contains(e)) {
                        missing++;
                        if (examples.size() < 5) examples.add("fehlt: " + e.getType().toShortString() + " " + tag);
                    }
                }
            }
            for (var entry : byTag.entrySet()) {
                for (Entity e : entry.getValue()) {
                    if (!seen.contains(e) || !e.getTags().contains(entry.getKey())) {
                        stale++;
                        if (examples.size() < 5) examples.add("zu viel: " + e.getType().toShortString() + " " + entry.getKey());
                    }
                }
            }
        }
        int sets = 0;
        for (var byTag : INDEX.values()) sets += byTag.size();
        return "Tag-Index " + (enabled() ? "an" : "aus") + ": " + entities + " Entities, " + tagged + " Tags, " + sets
                + " Tag-Listen, " + missing + " fehlen, " + stale + " zu viel" + (examples.isEmpty() ? "" : " (" + String.join(", ", examples) + ")");
    }

    /** An entity's tag set that reports every change to the index. */
    public static final class Tags extends HashSet<String> {
        private final transient Entity owner;

        public Tags(Entity owner) {
            this.owner = owner;
        }

        @Override
        public boolean add(String tag) {
            boolean changed = super.add(tag);
            if (changed) added(owner, tag);
            return changed;
        }

        @Override
        public boolean remove(Object tag) {
            boolean changed = super.remove(tag);
            if (changed && tag instanceof String s) removed(owner, s);
            return changed;
        }

        @Override
        public void clear() {
            if (((Tracked) owner).kronwerke$tagLevel() != null) {
                for (String tag : this) removed(owner, tag);
            }
            super.clear();
        }

        @Override
        public Iterator<String> iterator() {
            Iterator<String> it = super.iterator();
            return new Iterator<>() {
                private String last;

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public String next() {
                    last = it.next();
                    return last;
                }

                @Override
                public void remove() {
                    it.remove();
                    removed(owner, last);
                }
            };
        }
    }
}
