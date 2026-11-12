package com.extantape.lanterngraves.listen;

import com.extantape.lanterngraves.grave.Grave;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** What LanternGraves tells Rewild to keep, through a stand-in shaped exactly like Rewild's KeepProvider. */
class RewildKeepsTest {
    /** Same shape as com.extantape.rewild.api.KeepProvider. */
    public interface FakeKeepProvider {
        record Keep(int chunkX, int chunkZ, String reason) {}
        Collection<Keep> keeps(World world);
        /** Added in Rewild 0.3.0, with a default of none. */
        default Collection<Object> areas(World world) {
            return List.of();
        }
    }

    static Grave grave(String world, double x, double z) {
        return new Grave(UUID.randomUUID(), UUID.randomUUID(), "Ann", 0, world, "SURVIVAL", world, x, 64, z);
    }

    static World world(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (p, m, a) -> m.getName().equals("getName") ? name : null);
    }

    FakeKeepProvider provider(List<Grave> graves, boolean damaged) throws Exception {
        return (FakeKeepProvider) RewildKeeps.provider(FakeKeepProvider.class, FakeKeepProvider.Keep.class, () -> graves, () -> damaged);
    }

    @Test void everyChunkWithAGraveIsKeptOnceWithAReason() throws Exception {
        var p = provider(List.of(grave("world", 5, 5), grave("world", 10, 12), grave("world", -1, -17), grave("nether", 5, 5)), false);
        var keeps = List.copyOf(p.keeps(world("world")));
        assertEquals(List.of(new FakeKeepProvider.Keep(0, 0, "a grave"), new FakeKeepProvider.Keep(-1, -2, "a grave")), keeps,
            "two graves in chunk 0,0 count once; -1,-17 is chunk -1,-2; the nether grave isn't in this world");
    }

    @Test void rewild030sAreasCallGetsNoneInsteadOfAnError() throws Exception {
        // Rewild 0.3.0 asks providers for block areas too. Graves keep whole chunks, so the answer is none, not a refusal.
        assertTrue(provider(List.of(grave("world", 5, 5)), false).areas(world("world")).isEmpty());
    }

    @Test void controlNoGravesKeepsNothing() throws Exception {
        assertTrue(provider(List.of(), false).keeps(world("world")).isEmpty());
    }

    @Test void whileAGraveFileIsDamagedItThrowsSoRewildResetsNothing() throws Exception {
        var p = provider(List.of(grave("world", 5, 5)), true);
        var e = assertThrows(IllegalStateException.class, () -> p.keeps(world("world")));
        assertTrue(e.getMessage().contains("couldn't be read"), e.getMessage());
    }

    @Test void blockCoordinatesRoundDownIntoTheRightChunk() {
        assertArrayEquals(new long[] {-1, 0}, RewildKeeps.chunksIn(List.of(grave("w", -0.5, 15.9)), "w").getFirst());
        assertArrayEquals(new long[] {1, -1}, RewildKeeps.chunksIn(List.of(grave("w", 16.0, -0.1)), "w").getFirst());
    }
}
