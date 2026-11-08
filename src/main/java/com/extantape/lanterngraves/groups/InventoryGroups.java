package com.extantape.lanterngraves.groups;

import org.bukkit.GameMode;

/** Answers whether two worlds share one inventory, so a grave never pays out into the wrong one. */
public interface InventoryGroups {
    /** True if a player standing in {@code here}, in {@code mode}, holds the inventory that died in {@code deathWorld}. */
    boolean sameInventory(String deathWorld, String deathMode, String here, GameMode mode);

    /** Names the group a world belongs to, for messages. */
    String describe(String world);

    /** A warning to show at startup if this setup can't keep the crash promise, or null. */
    default String crashWarning() {
        return null;
    }

    /** No inventory plugin: every world shares one inventory, as in vanilla. */
    InventoryGroups NONE = new InventoryGroups() {
        @Override
        public boolean sameInventory(String deathWorld, String deathMode, String here, GameMode mode) {
            return true;
        }

        @Override
        public String describe(String world) {
            return world;
        }
    };
}
