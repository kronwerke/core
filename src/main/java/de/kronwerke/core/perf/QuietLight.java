package de.kronwerke.core.perf;

/**
 * A full chunk read from disk goes through the light engine once more (lightChunk with "already
 * lit"), which flips its light flag off and on and with that marks the chunk as changed, although
 * nothing about its light changes. That undid the clean start of loaded chunks: 22 300 chunks in
 * two minutes of the load test. For that pass the flag changes without marking the chunk.
 */
public interface QuietLight {

    void kronwerke$quietLight();
}
