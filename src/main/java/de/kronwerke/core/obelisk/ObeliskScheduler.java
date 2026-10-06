package de.kronwerke.core.obelisk;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/** Small things to do a few ticks from now on the server thread: notes of an arpeggio, the phases of the rite, one block of a tier. */
public final class ObeliskScheduler {
    private record Task(long at, long order, Runnable run) {
    }

    private final PriorityQueue<Task> queue = new PriorityQueue<>((a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Long.compare(a.order, b.order));
    private long now, order;

    /** Runs the task in delay ticks; a delay of 0 runs on the next tick. */
    public void at(int delay, Runnable run) {
        queue.add(new Task(now + Math.max(0, delay), order++, run));
    }

    public void tick() {
        now++;
        List<Task> due = new ArrayList<>();
        while (!queue.isEmpty() && queue.peek().at <= now) due.add(queue.poll());
        for (Task t : due) {
            try {
                t.run.run();
            } catch (Exception e) {
                de.kronwerke.core.KronwerkeCore.LOGGER.warn("Obelisk task failed", e);
            }
        }
    }

    public void clear() {
        queue.clear();
    }

    public boolean busy() {
        return !queue.isEmpty();
    }
}
