package com.extantape.lanterngraves.grave;

import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkScreensTest {

    private static Inventory grid(int cells) {
        return (Inventory) Proxy.newProxyInstance(CraftingInventory.class.getClassLoader(), new Class<?>[]{CraftingInventory.class},
                (proxy, m, a) -> switch (m.getName()) {
                    case "getMatrix" -> new ItemStack[cells];
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    void anAnvilsTwoInputsButNotItsResult() {
        assertEquals(List.of(0, 1), WorkScreens.slotsFor("ANVIL", 3));
    }

    @Test
    void aSmithingTableHasThreeInputs() {
        assertEquals(List.of(0, 1, 2), WorkScreens.slotsFor("SMITHING", 4));
    }

    @Test
    void containersThatKeepTheirItemsAreLeftAlone() {
        assertTrue(WorkScreens.slotsFor("CHEST", 27).isEmpty());
        assertTrue(WorkScreens.slotsFor("FURNACE", 3).isEmpty());
        assertTrue(WorkScreens.slotsFor("CRAFTER", 10).isEmpty());
        assertTrue(WorkScreens.inputSlots(null).isEmpty());
    }

    @Test
    void craftingGridsAreTheCellsAfterTheResult() {
        assertEquals(List.of(1, 2, 3, 4), WorkScreens.inputSlots(grid(4)));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), WorkScreens.inputSlots(grid(9)));
    }
}
