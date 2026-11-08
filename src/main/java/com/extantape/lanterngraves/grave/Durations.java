package com.extantape.lanterngraves.grave;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads lengths like {@code 30m}, {@code 6h}, {@code 2d} or {@code 1h30m}. */
public final class Durations {
    private static final Pattern PART = Pattern.compile("(\\d{1,6})([smhdw])");

    private Durations() {}

    /** Millis, or -1 if the text isn't a length. */
    public static long parse(String text) {
        if (text == null || text.isEmpty()) return -1;
        Matcher m = PART.matcher(text.toLowerCase());
        long total = 0;
        int at = 0;
        while (m.find()) {
            if (m.start() != at) return -1;
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> 604_800_000L;
            };
            at = m.end();
        }
        return at == text.length() && total > 0 ? total : -1;
    }

    public static String describe(long millis) {
        long s = Math.max(0, millis / 1000);
        if (s < 60) return s + "s";
        long m = s / 60;
        if (m < 60) return m + "m";
        long h = m / 60;
        if (h < 48) return h + "h" + (m % 60 == 0 ? "" : " " + m % 60 + "m");
        return h / 24 + "d";
    }
}
