package com.extantape.lanterngraves.grave;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Works out where each stack from a grave goes in the collector's inventory: back to its own slot
 * if that's empty, otherwise the first empty storage slot. Stacks are never merged into partial
 * ones, so what goes in is exactly what came out. Whatever has nowhere to go stays in the grave.
 */
public final class Placement {
    public static final int STORAGE = 36;
    public static final int LAST_SLOT = 40;

    private Placement() {}

    /**
     * @param graveSlots the grave's slots, in order
     * @param empty      whether an inventory slot (0-40) is empty right now
     * @return grave slot to inventory slot, for the stacks that fit
     */
    public static Map<Integer, Integer> plan(List<Integer> graveSlots, IntPredicate empty) {
        boolean[] taken = new boolean[LAST_SLOT + 1];
        for (int i = 0; i <= LAST_SLOT; i++) taken[i] = !empty.test(i);
        Map<Integer, Integer> plan = new LinkedHashMap<>();
        // Own slots first, so an item that lived in slot 3 isn't pushed out by one placed earlier.
        for (int g : graveSlots) {
            if (g >= 0 && g <= LAST_SLOT && !taken[g]) {
                taken[g] = true;
                plan.put(g, g);
            }
        }
        for (int g : graveSlots) {
            if (plan.containsKey(g)) continue;
            for (int i = 0; i < STORAGE; i++) {
                if (!taken[i]) {
                    taken[i] = true;
                    plan.put(g, i);
                    break;
                }
            }
        }
        return plan;
    }
}
