package de.kronwerke.core.perf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;

/**
 * ServerCore lets every mob category spawn only every n ticks (monsters every 2nd tick, animals
 * every 400th), but vanilla still counts every mob in the level and walks every chunk through the
 * spawner on every single tick, only for ServerCore to cancel each category again. That count
 * alone was 4 % of the tick with 50 players. On ticks where no category is due, the count is
 * skipped (the previous one stays as the last spawn state) and the spawner is not entered at all.
 * On due ticks nothing changes, so spawning works exactly as configured.
 */
public final class SpawnGate {

    private static final MobCategory[] CATEGORIES = MobCategory.values();
    private static MethodHandle interval;
    private static boolean looked;
    private static boolean reported;

    private static ServerLevel lastLevel;
    private static long lastTime = Long.MIN_VALUE;
    private static boolean lastDue;

    private SpawnGate() {
    }

    /** True when at least one mob category may spawn in this level on this tick. */
    public static boolean due(ServerLevel level) {
        long time = level.getGameTime();
        if (level == lastLevel && time == lastTime) return lastDue;
        boolean due = compute(time);
        lastLevel = level;
        lastTime = time;
        lastDue = due;
        return due;
    }

    private static boolean compute(long time) {
        MethodHandle handle = handle();
        if (handle == null) return true;
        if (!reported) {
            reported = true;
            StringBuilder all = new StringBuilder();
            for (MobCategory category : CATEGORIES) {
                try {
                    all.append(category.getName()).append('=').append((int) handle.invokeExact(category)).append(' ');
                } catch (Throwable t) {
                    all.append(category.getName()).append("=? ");
                }
            }
            de.kronwerke.core.KronwerkeCore.LOGGER.info("Spawn gate intervals: {}", all.toString().trim());
        }
        try {
            for (MobCategory category : CATEGORIES) {
                if (category == MobCategory.MISC) continue;
                int every = (int) handle.invokeExact(category);
                if (every <= 1 || time % every == 0) return true;
            }
            return false;
        } catch (Throwable t) {
            if (!reported) de.kronwerke.core.KronwerkeCore.LOGGER.warn("Spawn gate failed: {}", t.toString());
            return true;
        }
    }

    private static MethodHandle handle() {
        if (!looked) {
            looked = true;
            try {
                Class<?> type = Class.forName("me.wesley1808.servercore.common.interfaces.IMobCategory");
                interval = MethodHandles.publicLookup().findStatic(type, "getSpawnInterval", MethodType.methodType(int.class, MobCategory.class));
            } catch (Throwable t) {
                interval = null; // no ServerCore: every tick is due, vanilla behaviour
                de.kronwerke.core.KronwerkeCore.LOGGER.warn("Spawn gate off, ServerCore spawn intervals not found: {}", t.toString());
            }
        }
        return interval;
    }
}
