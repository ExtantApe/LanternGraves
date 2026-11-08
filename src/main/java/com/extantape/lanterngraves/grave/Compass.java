package com.extantape.lanterngraves.grave;

/** Turns an offset into a compass word: north is -Z and east is +X, as on the F3 screen. */
public final class Compass {
    private static final String[] NAMES = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};

    private Compass() {}

    public static String direction(double dx, double dz) {
        // Minecraft yaw: 0 faces south (+Z), 90 west (-X), 180 north, 270 east.
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        int i = (int) Math.round(((yaw % 360) + 360) % 360 / 45.0) % 8;
        return NAMES[i];
    }
}
