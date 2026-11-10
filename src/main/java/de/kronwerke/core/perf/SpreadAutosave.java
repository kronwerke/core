package de.kronwerke.core.perf;

/**
 * Vanilla's autosave (every five minutes) serializes every changed chunk of every dimension in
 * one tick on the server thread. With 50 players that was a freeze of almost seven seconds every
 * five minutes (11 % of all tick time in the load test). While the autosave runs, the chunk part
 * is now only queued, and each tick writes queued chunks until a small time budget is used up
 * (see ChunkMap mixin). Player data, level data and entities are still saved right away, and
 * save-all, save-all flush and shutdown save every chunk at once as before.
 */
public final class SpreadAutosave {

    /** True only while MinecraftServer runs its own periodic autosave. */
    public static boolean autosaving;

    /** Dimensions whose chunks of an autosave are still being written. */
    private static final java.util.Set<Object> draining = new java.util.HashSet<>();
    private static boolean round;

    /** An autosave begins: what was answered before it becomes durable when its chunks are written. */
    public static void begin() {
        round = true;
        de.kronwerke.core.share.Share.onSaveStart();
    }

    /** The autosave's own work is done; with nothing left to spread the round ends here. */
    public static void afterSave() {
        if (draining.isEmpty()) end();
    }

    public static void draining(Object level, boolean on) {
        if (on) draining.add(level);
        else if (draining.remove(level) && draining.isEmpty()) end();
    }

    /** While chunks of an autosave are still being written: the shared network's data waits for them. */
    public static boolean holding() {
        return !draining.isEmpty();
    }

    private static void end() {
        if (!round) return;
        round = false;
        de.kronwerke.core.share.Share.onSaved();
    }

    private SpreadAutosave() {
    }
}
