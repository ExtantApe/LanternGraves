package com.extantape.lanterngraves.grave;

/**
 * For the crash tests only. With {@code -Dlanterngraves.test.halt=<point>} on the server's command line,
 * the JVM halts at that point exactly as a kill -9 would: no shutdown, no saving. Without it, nothing.
 */
public final class CrashPoint {
    private static final String WANT = System.getProperty("lanterngraves.test.halt", "");

    private CrashPoint() {}

    public static void at(String point) {
        if (!WANT.isEmpty() && WANT.equals(point)) {
            System.err.println("[LanternGraves] test halt at " + point);
            System.err.flush();
            Runtime.getRuntime().halt(137);
        }
    }
}
