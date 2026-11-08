package com.extantape.lanterngraves;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** config.yml, read once per load or reload. */
public record Settings(boolean light, int lightLevel, boolean label, double reach, int settleTicks,
                       boolean pointDeathLocation, List<String> disabledWorlds) {
    public static Settings read(FileConfiguration c) {
        return new Settings(
                c.getBoolean("marker.light", true),
                Math.max(1, Math.min(15, c.getInt("marker.light-level", 12))),
                c.getBoolean("marker.label", true),
                Math.max(1, c.getDouble("collect.reach", 4.0)),
                Math.max(0, (int) Math.round(c.getDouble("collect.settle-seconds", 3) * 20)),
                c.getBoolean("point-death-location-at-grave", true),
                List.copyOf(c.getStringList("disabled-worlds")));
    }
}
