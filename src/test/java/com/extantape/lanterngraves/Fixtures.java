package com.extantape.lanterngraves;

import com.extantape.lanterngraves.grave.Grant;
import com.extantape.lanterngraves.grave.Grave;
import com.extantape.lanterngraves.grave.StoredItem;

import java.util.UUID;

/** Made-up graves for tests. Item bytes are arbitrary: these tests never ask Paper to read them. */
public final class Fixtures {
    private Fixtures() {}

    public static Grave grave() {
        Grave g = new Grave(UUID.randomUUID(), UUID.randomUUID(), "Tester", 1_700_000_000_000L, "testworld", "SURVIVAL",
                "testworld", 10.5, 64, -3.5);
        g.put(0, new StoredItem("DIAMOND_SWORD", 1, new byte[] {1, 2, 3, 4}));
        g.put(38, new StoredItem("IRON_CHESTPLATE", 1, new byte[] {5, 6, 7}));
        g.put(40, new StoredItem("SHIELD", 1, new byte[] {8}));
        g.put(Grave.EXTRA_BASE, new StoredItem("STICK", 3, new byte[] {9, 9}));
        g.xp(1234);
        g.grant(new Grant(UUID.randomUUID(), "Friend", 0));
        return g;
    }
}
