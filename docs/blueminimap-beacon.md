# The BlueMinimap beacon

BlueMinimap belongs to another session. This was read, not changed.

## What works today, with no changes to BlueMinimap

BlueMinimap draws each player's death point, with an arrow at the map edge when it's off screen. It reads that point from the player's last death location (`Player#getLastDeathLocation`), and only shows it in the same world.

LanternGraves sets that location to the player's newest grave (`point-death-location-at-grave: true`, the default). So after a void or lava death, BlueMinimap's death marker points at the lantern on safe ground, not at the spot in the void. The vanilla recovery compass follows it too.

What this doesn't do:

- Only one grave is shown, the newest. When it's emptied, the marker moves to the next newest, and goes away when none are left.
- It uses BlueMinimap's death icon and colour, not a lantern.

## What a full beacon would need from BlueMinimap

BlueMinimap reads BlueMap POI markers too, but those are shared by every player and show on the public web map. That's wrong for graves, which are private to their owner.

A small per-player waypoint API in BlueMinimap would cover it, along these lines:

```java
// In BlueMinimap, a Bukkit service other plugins can look up.
public interface HudWaypoints {
    /** Shows a waypoint to one player only. Replaces any waypoint with the same id. */
    void set(UUID player, String id, World world, int x, int z, WaypointStyle style);
    void remove(UUID player, String id);
}
public record WaypointStyle(String iconName, int rgb, String label) {}
```

LanternGraves would call `set` for each grave, for its owner and anyone they trust, and `remove` when a grave is emptied. A lantern icon (9x9, in white so it can be tinted) would go in BlueMinimap's icon set. Until that exists, the death-location approach above stays the default.
