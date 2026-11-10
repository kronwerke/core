package de.kronwerke.core.perf;

import de.kronwerke.core.config.KronwerkeConfig;
import net.minecraft.world.entity.Mob;

/**
 * Two vanilla AI goals search every entity around a mob each time the mob asks whether to start
 * them: fleeing from something (rabbits, cats, foxes, villagers, many modded animals) and a baby
 * following its parent. In a pen of 100 animals that is 100 times 100 checks every other tick,
 * most of the 26 ms per tick that 100 crowded animals cost. When such a goal said no, the mob
 * now asks again only after a few ticks (goalPauseTicks, a quarter second by default), spread
 * over the mobs by their id. A rabbit may start running a moment later; nothing else changes.
 */
public final class GoalPause {

    private GoalPause() {
    }

    /** True while a goal that said no at {@code nextCheck} should not be asked yet. */
    public static boolean waiting(Mob mob, long nextCheck) {
        return nextCheck != 0 && mob.level().getGameTime() < nextCheck;
    }

    /** When to ask next after a no. */
    public static long next(Mob mob) {
        int ticks = KronwerkeConfig.GOAL_PAUSE_TICKS.get();
        if (ticks <= 0) return 0;
        return mob.level().getGameTime() + ticks + (mob.getId() & 1);
    }
}
