package com.extantape.lanterngraves.grave;

import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Screens that hold a player's items only while they're open, and hand them back when they close: on a death, vanilla
 * throws those items on the ground instead. Lists each screen's input slots, never its result slot, which holds no
 * real item. Containers that keep their items (chests, furnaces, crafters) aren't here: closing them loses nothing.
 */
final class WorkScreens {
    private WorkScreens() {}

    /** By the screen type's name, so this map needs no running server. */
    private static final Map<String, int[]> INPUTS = Map.of(
            "ANVIL", new int[]{0, 1},
            "GRINDSTONE", new int[]{0, 1},
            "SMITHING", new int[]{0, 1, 2},
            "STONECUTTER", new int[]{0},
            "LOOM", new int[]{0, 1, 2},
            "CARTOGRAPHY", new int[]{0, 1},
            "ENCHANTING", new int[]{0, 1},
            "MERCHANT", new int[]{0, 1},
            "BEACON", new int[]{0});

    /** The slots holding the player's items in this open screen, or none if it isn't a work screen. */
    static List<Integer> inputSlots(Inventory top) {
        List<Integer> out = new ArrayList<>();
        if (top == null) return out;
        if (top instanceof CraftingInventory grid) { // the 2x2 and a crafting table: the matrix, after the result
            for (int i = 0; i < grid.getMatrix().length; i++) out.add(i + 1);
            return out;
        }
        return slotsFor(top.getType().name(), top.getSize());
    }

    /** The input slots of a non-crafting work screen of this type and size, or none. */
    static List<Integer> slotsFor(String type, int size) {
        List<Integer> out = new ArrayList<>();
        int[] slots = INPUTS.get(type);
        if (slots != null) for (int s : slots) if (s < size) out.add(s);
        return out;
    }
}
