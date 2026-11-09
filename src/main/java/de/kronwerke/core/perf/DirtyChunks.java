package de.kronwerke.core.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Finds out who marks chunks as changed. A chunk marked changed is written again when it
 * unloads, and in the load tests that writing was a tenth of the server tick. While it runs,
 * every first change of a chunk is counted by the three code frames that caused it
 * (/kw admin dirty start, show, stop). Off it costs one boolean check.
 */
public final class DirtyChunks {

    public static volatile boolean on;
    private static final Map<String, LongAdder> COUNTS = new ConcurrentHashMap<>();
    private static final StackWalker WALKER = StackWalker.getInstance();

    private DirtyChunks() {
    }

    public static void start() {
        COUNTS.clear();
        on = true;
    }

    public static void stop() {
        on = false;
    }

    public static void record(String how) {
        List<String> frames = WALKER.walk(s -> s
                .map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "." + f.getMethodName())
                .filter(n -> !n.startsWith("DirtyChunks") && !n.startsWith("ChunkAccess") && !n.startsWith("LevelChunk.")
                        && !n.contains("handler$") && !n.startsWith("Level.") && !n.startsWith("ServerLevel.setBlock")
                        && !n.startsWith("Level.setBlock"))
                .limit(3)
                .collect(Collectors.toList()));
        COUNTS.computeIfAbsent(how + ": " + String.join(" < ", frames), k -> new LongAdder()).increment();
    }

    public static List<String> top(int n) {
        List<Map.Entry<String, LongAdder>> all = new ArrayList<>(COUNTS.entrySet());
        all.sort((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, all.size()); i++) {
            out.add(all.get(i).getValue().sum() + "  " + all.get(i).getKey());
        }
        return out;
    }
}
