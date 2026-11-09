package de.kronwerke.core.perf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Nuclear Radiation works out the dose of every living entity each round (every 40 ticks in
 * this pack): sources around it, contamination, background, inventory. Players keep that.
 * Mobs are worked out every fourth round, and that round counts four times as long, so the
 * dose a mob collects stays the same, it only arrives in larger steps.
 */
public final class RadiationDose {

    private static final int MOB_FACTOR = 4;
    private static MethodHandle tick;
    private static boolean broken;

    private RadiationDose() {
    }

    public static void tick(ServerLevel level, LivingEntity entity, long time, int interval) {
        if (entity instanceof Player || interval <= 0) {
            call(level, entity, time, interval);
            return;
        }
        long round = time / interval;
        if (Math.floorMod(round + entity.getId(), MOB_FACTOR) == 0) {
            call(level, entity, time, interval * MOB_FACTOR);
        }
    }

    private static void call(ServerLevel level, LivingEntity entity, long time, int interval) {
        if (broken) {
            return;
        }
        try {
            if (tick == null) {
                Class<?> processor = Class.forName("igentuman.nr.radiation.simulation.entity.EntityDoseProcessor");
                tick = MethodHandles.publicLookup().findStatic(processor, "tick",
                        MethodType.methodType(void.class, ServerLevel.class, LivingEntity.class, long.class, int.class));
            }
            tick.invokeExact(level, entity, time, interval);
        } catch (Throwable t) {
            broken = true;
            org.slf4j.LoggerFactory.getLogger("Kronwerke").error("[Kronwerke] Radiation dose call failed, radiation doses are off now", t);
        }
    }
}
