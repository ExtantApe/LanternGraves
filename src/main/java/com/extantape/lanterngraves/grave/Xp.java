package com.extantape.lanterngraves.grave;

/** Vanilla experience arithmetic, used to save a player's XP exactly instead of trusting a counter. */
public final class Xp {
    private Xp() {}

    /** Points needed to go from level 0 to the start of {@code level}. */
    public static int toReach(int level) {
        long l = level;
        if (l <= 16) return (int) (l * l + 6 * l);
        if (l <= 31) return (int) Math.round(2.5 * l * l - 40.5 * l + 360);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(4.5 * l * l - 162.5 * l + 2220));
    }

    /** Points in the bar at {@code level}, from empty to the next level. */
    public static int barSize(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    /**
     * A player's total points, from their level and how full the bar is (0 to 1). Vanilla counts the
     * points in the bar by rounding down, and float error can put 20 points at 19.9999, so a hair
     * of slack goes in before rounding down.
     */
    public static int total(int level, float progress) {
        int bar = barSize(level);
        long inBar = Math.min(bar - 1, (long) Math.floor(Math.max(0, Math.min(1, progress)) * bar + 0.01));
        return (int) Math.min(Integer.MAX_VALUE, (long) toReach(level) + Math.max(0, inBar));
    }

    /** The bar fill that vanilla reads back as exactly {@code points} into {@code level}. */
    public static float progress(int level, int points) {
        return Math.min(0.9999f, (points + 0.5f) / barSize(level));
    }

    /** The level a number of points reaches, for messages. */
    public static int levelFor(int points) {
        int level = 0;
        while (level < 21863 && toReach(level + 1) <= points) level++;
        return level;
    }
}
