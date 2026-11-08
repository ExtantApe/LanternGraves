package com.extantape.lanterngraves.grave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HelpersTest {
    @Test
    void xpMatchesTheWikiTable() {
        assertEquals(0, Xp.total(0, 0));
        assertEquals(7, Xp.toReach(1));
        assertEquals(352, Xp.toReach(16));
        assertEquals(394, Xp.toReach(17));
        assertEquals(1395, Xp.toReach(30));
        assertEquals(1507, Xp.toReach(31));
        assertEquals(1628, Xp.toReach(32));
        assertEquals(2920, Xp.toReach(40));
        assertEquals(30970, Xp.toReach(100));
        assertEquals(Xp.toReach(30) + 56, Xp.total(30, 0.5f));
        assertEquals(Xp.toReach(33) + 20, Xp.total(33, 20f / 139 - 1e-7f), "float error just under a point still counts it");
        for (int l = 0; l < 120; l++) {
            for (int p = 0; p < Xp.barSize(l); p += 1 + Xp.barSize(l) / 13) {
                assertEquals(Xp.toReach(l) + p, Xp.total(l, Xp.progress(l, p)), "level " + l + " + " + p);
                assertEquals(p, (int) Math.floor(Xp.progress(l, p) * Xp.barSize(l)), "vanilla reads back " + p);
            }
        }
        for (int l = 0; l < 200; l++) {
            assertEquals(Xp.toReach(l + 1) - Xp.toReach(l), Xp.barSize(l), "bar at " + l);
            assertEquals(l, Xp.levelFor(Xp.toReach(l)));
        }
    }

    @Test
    void durations() {
        assertEquals(30 * 60_000L, Durations.parse("30m"));
        assertEquals(90 * 60_000L, Durations.parse("1h30m"));
        assertEquals(2 * 86_400_000L, Durations.parse("2d"));
        assertEquals(-1, Durations.parse("all"));
        assertEquals(-1, Durations.parse("3"));
        assertEquals(-1, Durations.parse("h"));
        assertEquals(-1, Durations.parse("0m"));
        assertEquals(-1, Durations.parse("5mx"));
    }

    @Test
    void itemsGoBackToTheirOwnSlots() {
        Map<Integer, Integer> plan = Placement.plan(List.of(0, 5, 36, 40), slot -> true);
        assertEquals(Map.of(0, 0, 5, 5, 36, 36, 40, 40), plan);
    }

    @Test
    void takenSlotsMoveToFreeStorageAndTheRestStays() {
        // Only storage slots 3 and 4 are free besides armour 37-39.
        Map<Integer, Integer> plan = Placement.plan(List.of(0, 36, 40, 100, 101), slot -> slot == 3 || slot == 4 || (slot >= 37 && slot <= 39));
        assertEquals(2, plan.size());
        assertEquals(3, plan.get(0));
        assertEquals(4, plan.get(36));
        assertFalse(plan.containsKey(40));
        assertFalse(plan.containsKey(100));
    }

    @Test
    void anArmourSlotIsNeverFilledWithSomethingElse() {
        Map<Integer, Integer> plan = Placement.plan(List.of(100), slot -> slot >= 36);
        assertTrue(plan.isEmpty(), "an extra stack only goes to storage slots 0-35");
    }

    @Test
    void ownSlotWinsOverAnEarlierSpill() {
        // Slot 0 is full, so grave slot 0 spills; it must not take slot 1, which grave slot 1 owns.
        Map<Integer, Integer> plan = Placement.plan(List.of(0, 1), slot -> slot != 0);
        assertEquals(1, plan.get(1));
        assertEquals(2, plan.get(0));
    }

    @Test
    void grantsExpire() {
        Grant forever = new Grant(java.util.UUID.randomUUID(), "a", 0);
        Grant brief = new Grant(java.util.UUID.randomUUID(), "b", 1000);
        assertTrue(forever.activeAt(Long.MAX_VALUE));
        assertTrue(brief.activeAt(999));
        assertFalse(brief.activeAt(1000));
    }

    @Test
    void compassWords() {
        assertEquals("north", Compass.direction(0, -10));
        assertEquals("south", Compass.direction(0, 10));
        assertEquals("east", Compass.direction(10, 0));
        assertEquals("west", Compass.direction(-10, 0));
        assertEquals("northeast", Compass.direction(10, -10));
        assertEquals("southwest", Compass.direction(-10, 10));
    }
}
