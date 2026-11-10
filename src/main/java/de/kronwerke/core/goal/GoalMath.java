package de.kronwerke.core.goal;

/**
 * The arithmetic of a goal's bar, without Minecraft, so it can be tested: points, the hold
 * point and how much of an item one deposit may take.
 */
public final class GoalMath {
    private GoalMath() {
    }

    /** The points before the hold point, rounded up. */
    public static long holdPoints(long total, double hold) {
        return (long) Math.ceil(total * hold);
    }

    /** Whether the goal waits at its hold point. */
    public static boolean held(long total, long done, double hold, boolean released) {
        return hold < 1.0 && !released && total > 0 && done >= holdPoints(total, hold);
    }

    /**
     * How many of an item one deposit takes: no more than are missing, and before the hold
     * point no more than reach it. The item that crosses the hold point goes in whole (one
     * brass heart is worth 300 points, more than the last gap before the hold), otherwise a
     * heavy item could never cross it and the goal would stall short of its hold.
     */
    public static long take(long remaining, long offered, long points, long total, long done, double hold, boolean released) {
        long take = Math.min(remaining, offered);
        if (take <= 0) return 0;
        if (hold < 1.0 && !released) {
            long allowed = holdPoints(total, hold) - done;
            if (allowed <= 0) return 0;
            take = Math.min(take, (allowed + points - 1) / points);
        }
        return take;
    }
}
