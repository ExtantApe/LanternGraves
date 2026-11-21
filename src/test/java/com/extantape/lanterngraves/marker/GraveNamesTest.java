package com.extantape.lanterngraves.marker;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** D231: a grave's name follows the server's player groups, and fails closed. */
class GraveNamesTest {
    private final GraveNames names = new GraveNames(Logger.getAnonymousLogger());
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void ownerAlwaysSeesTheirName() {
        assertTrue(names.decide(owner, owner, null, null, "kids"));
        assertTrue(names.decide(owner, owner, (v, o, w) -> false, null, "kids"));
    }

    @Test
    void noGroupsMeansOthersSeeGrave() {
        assertFalse(names.decide(other, owner, null, null, "survival"));
    }

    @Test
    void groupsThatAllowShowTheName() {
        assertTrue(names.decide(other, owner, (v, o, w) -> o.equals(owner) && w.equals("kids"), null, "kids"));
    }

    @Test
    void groupsThatRefuseShowGrave() {
        // A kid looking at an adult stranger's grave, or the reverse: the groups say no.
        assertFalse(names.decide(other, owner, (v, o, w) -> false, null, "survival"));
    }

    @Test
    void rulesThatFailShowGrave() {
        assertFalse(names.decide(other, owner, (v, o, w) -> { throw new IllegalStateException("down"); }, null, "kids"));
        assertFalse(names.decide(other, owner, (v, o, w) -> { throw new NoSuchMethodError("old Mayor"); }, null, "kids"));
    }
}
