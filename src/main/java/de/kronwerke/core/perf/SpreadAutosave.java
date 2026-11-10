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

    private SpreadAutosave() {
    }
}
