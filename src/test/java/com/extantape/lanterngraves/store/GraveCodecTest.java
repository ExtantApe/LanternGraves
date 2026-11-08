package com.extantape.lanterngraves.store;

import com.extantape.lanterngraves.Fixtures;
import com.extantape.lanterngraves.grave.Grave;
import com.extantape.lanterngraves.grave.Pending;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GraveCodecTest {
    @Test
    void roundTripKeepsEverything() throws Exception {
        Grave g = Fixtures.grave();
        g.pending(new Pending(Pending.Kind.COLLECT, UUID.randomUUID(), g.owner(), List.of(0, 40), 1234));
        g.lightPlaced(true);
        Grave back = GraveCodec.decode(GraveCodec.encode(g));
        assertEquals(g.id(), back.id());
        assertEquals(g.owner(), back.owner());
        assertEquals(g.ownerName(), back.ownerName());
        assertEquals(g.created(), back.created());
        assertEquals(g.deathWorld(), back.deathWorld());
        assertEquals(g.gameMode(), back.gameMode());
        assertEquals(g.world(), back.world());
        assertEquals(g.x(), back.x());
        assertEquals(g.y(), back.y());
        assertEquals(g.z(), back.z());
        assertEquals(g.items(), back.items());
        assertEquals(g.xp(), back.xp());
        assertEquals(g.grants(), back.grants());
        assertEquals(g.pending(), back.pending());
        assertTrue(back.lightPlaced());
    }

    @Test
    void everyTruncationIsCaught() {
        String text = GraveCodec.encode(Fixtures.grave());
        for (int len = 0; len < text.length() - 1; len++) {
            String cut = text.substring(0, len);
            assertThrows(GraveCodec.DamagedException.class, () -> GraveCodec.decode(cut), "cut at " + len);
        }
    }

    @Test
    void anyChangedCharacterIsCaught() {
        String text = GraveCodec.encode(Fixtures.grave());
        int body = text.lastIndexOf("# sha256: ");
        for (int i = 0; i < body; i += 7) {
            char c = text.charAt(i);
            String bad = text.substring(0, i) + (c == 'x' ? 'y' : 'x') + text.substring(i + 1);
            assertThrows(GraveCodec.DamagedException.class, () -> GraveCodec.decode(bad), "changed at " + i);
        }
    }

    @Test
    void garbageIsCaught() {
        assertThrows(GraveCodec.DamagedException.class, () -> GraveCodec.decode("\0\0\0\0"));
        assertThrows(GraveCodec.DamagedException.class, () -> GraveCodec.decode(""));
    }
}
