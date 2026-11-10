package de.kronwerke.core.perf;

import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.server.MinecraftServer;

/**
 * Vanilla lets every primed TNT whose fuse runs out explode in the same tick. Each explosion
 * checks the line of sight to every entity in reach, and with thousands of TNT in one heap
 * that is thousands times thousands of rays in a single tick: 29,000 TNT kept the server in one
 * tick for 60 seconds until the watchdog killed it. Explosions now get a time budget per tick
 * (tntMsPerTick). A TNT that is due once the budget is used up keeps its last fuse tick and goes
 * off in the next tick. Each explosion is still exactly vanilla, they are only spread in time.
 */
public final class TntBudget {

    private static int tick = -1;
    private static long spent;
    private static int deferred;
    private static int deferredTicks;

    private TntBudget() {
    }

    /** True when the TNT due now has to wait for the next tick. */
    public static boolean full(MinecraftServer server) {
        double ms = KronwerkeConfig.TNT_MS_PER_TICK.get();
        if (ms <= 0) return false;
        roll(server);
        if (spent < (long) (ms * 1_000_000L)) return false;
        deferred++;
        return true;
    }

    public static void spent(MinecraftServer server, long nanos) {
        roll(server);
        spent += nanos;
    }

    private static void roll(MinecraftServer server) {
        int now = server.getTickCount();
        if (now == tick) return;
        if (deferred > 0) {
            deferredTicks++;
            if (deferredTicks % 100 == 1) {
                de.kronwerke.core.KronwerkeCore.LOGGER.info("TNT budget: {} TNT waited a tick (tick {})", deferred, tick);
            }
        } else {
            deferredTicks = 0;
        }
        tick = now;
        spent = 0;
        deferred = 0;
    }
}
