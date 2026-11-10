package de.kronwerke.core.perf;

/**
 * Remembers, per dimension (on its light engine), which chunks had a light relevant block change
 * or were lit for the first time in or next to them recently.
 *
 * Vanilla marks a chunk as changed whenever the light engine touches one of its sections. Most of
 * that is the engine settling light at the borders of chunks that are loading or unloading, which
 * follows from what is already on disk: 13 600 chunks in two minutes of the load test were marked
 * this way against a few hundred real changes, and each of them was serialized again on the server
 * thread on autosave and unload. Light changes now only mark a chunk when this record says light
 * really changed nearby (a block change that affects light within one chunk, or a neighbour lit for
 * the first time), so a torch at a chunk border still saves the light on both sides.
 */
public interface LightPending {

    /** How long after a change light updates still mark a chunk, generous since the light engine can lag. */
    long WINDOW_MS = 60_000L;

    void kronwerke$markAround(int chunkX, int chunkZ);

    boolean kronwerke$recent(long chunkPos);
}
