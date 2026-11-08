package com.extantape.lanterngraves.grave;

import java.util.List;
import java.util.UUID;

/**
 * A change that touches both a grave file and a player's own save, written down before either
 * moves. If the server dies in between, the player's save decides which way it goes: the
 * transaction id is stored on the player, and it's only there if their save holds the change.
 *
 * <p>DEATH: the grave was filled from {@code player}'s inventory. Committed means the grave
 * stands; rolled back means the grave never existed, because the player's save still has the items.
 *
 * <p>COLLECT: {@code slots} and {@code xp} were handed to {@code player}. Committed means they
 * leave the grave; rolled back means they stay.
 */
public record Pending(Kind kind, UUID txn, UUID player, List<Integer> slots, int xp) {
    public enum Kind { DEATH, COLLECT }

    public Pending {
        slots = List.copyOf(slots);
    }
}
