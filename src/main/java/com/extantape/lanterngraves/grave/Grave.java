package com.extantape.lanterngraves.grave;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A grave: who owns it, where its lantern stands, what it holds and who else may open it.
 *
 * <p>Slots 0 to 40 are the owner's inventory slots as Paper numbers them (0-35 storage, 36-39 armour,
 * 40 off hand), so things go back where they were. Slots from {@link #EXTRA_BASE} up hold drops
 * that had no inventory slot: the cursor, the crafting grid, or anything another plugin added.
 */
public final class Grave {
    public static final int EXTRA_BASE = 100;

    private final UUID id;
    private final UUID owner;
    private String ownerName;
    private final long created;
    /** Where the player died. Decides which inventory group the grave pays out in. */
    private final String deathWorld;
    /** The game mode they died in, for inventory plugins that keep one inventory per mode. */
    private final String gameMode;
    private String world;
    private double x, y, z;
    private final TreeMap<Integer, StoredItem> items = new TreeMap<>();
    private int xp;
    private final Map<UUID, Grant> grants = new LinkedHashMap<>();
    private Pending pending;
    /** Whether this plugin placed the light block that's at the lantern now. */
    private boolean lightPlaced;

    public Grave(UUID id, UUID owner, String ownerName, long created, String deathWorld, String gameMode,
                 String world, double x, double y, double z) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.created = created;
        this.deathWorld = deathWorld;
        this.gameMode = gameMode;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public void ownerName(String name) { this.ownerName = name; }
    public long created() { return created; }
    public String deathWorld() { return deathWorld; }
    public String gameMode() { return gameMode; }
    public String world() { return world; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public int blockX() { return (int) Math.floor(x); }
    public int blockY() { return (int) Math.floor(y); }
    public int blockZ() { return (int) Math.floor(z); }

    public void moveTo(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Map<Integer, StoredItem> items() { return Collections.unmodifiableMap(items); }
    public void put(int slot, StoredItem item) { items.put(slot, item); }
    public StoredItem take(int slot) { return items.remove(slot); }
    public int itemCount() { return items.values().stream().mapToInt(StoredItem::amount).sum(); }
    public int xp() { return xp; }
    public void xp(int xp) { this.xp = Math.max(0, xp); }
    public boolean isEmpty() { return items.isEmpty() && xp == 0; }

    public Map<UUID, Grant> grants() { return Collections.unmodifiableMap(grants); }
    public void grant(Grant grant) { grants.put(grant.friend(), grant); }
    public boolean revoke(UUID friend) { return grants.remove(friend) != null; }

    public boolean mayOpen(UUID player, long now) {
        if (player.equals(owner)) return true;
        Grant g = grants.get(player);
        return g != null && g.activeAt(now);
    }

    public Pending pending() { return pending; }
    public void pending(Pending pending) { this.pending = pending; }
    /** A grave waiting on a player's save can't be opened until that's settled. */
    public boolean settled() { return pending == null; }

    public boolean lightPlaced() { return lightPlaced; }
    public void lightPlaced(boolean placed) { this.lightPlaced = placed; }
}
