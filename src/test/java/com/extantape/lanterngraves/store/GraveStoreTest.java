package com.extantape.lanterngraves.store;

import com.extantape.lanterngraves.Fixtures;
import com.extantape.lanterngraves.grave.Grave;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GraveStoreTest {
    @TempDir
    Path dir;

    @Test
    void savesLoadsAndDeletes() throws Exception {
        GraveStore store = new GraveStore(dir);
        Grave g = Fixtures.grave();
        store.save(g);
        g.xp(5);
        store.save(g);
        GraveStore.Loaded loaded = new GraveStore(dir).loadAll();
        assertEquals(1, loaded.graves().size());
        assertEquals(5, loaded.graves().getFirst().xp());
        assertTrue(loaded.damaged().isEmpty());
        store.delete(g.id());
        assertTrue(new GraveStore(dir).loadAll().graves().isEmpty());
    }

    @Test
    void leftoverTempFileFromACrashIsIgnored() throws Exception {
        GraveStore store = new GraveStore(dir);
        Grave g = Fixtures.grave();
        store.save(g);
        Files.writeString(dir.resolve("graves/" + g.id() + ".yml.tmp"), "half a fi");
        GraveStore.Loaded loaded = new GraveStore(dir).loadAll();
        assertEquals(1, loaded.graves().size());
        assertTrue(loaded.damaged().isEmpty());
        assertFalse(Files.exists(dir.resolve("graves/" + g.id() + ".yml.tmp")));
    }

    @Test
    void damagedFileIsCopiedKeptAndNeverOverwritten() throws Exception {
        GraveStore store = new GraveStore(dir);
        Grave g = Fixtures.grave();
        store.save(g);
        Path file = dir.resolve("graves/" + g.id() + ".yml");
        String text = Files.readString(file);
        String truncated = text.substring(0, text.length() / 2);
        Files.writeString(file, truncated);

        GraveStore fresh = new GraveStore(dir);
        GraveStore.Loaded loaded = fresh.loadAll();
        assertTrue(loaded.graves().isEmpty());
        assertEquals(1, loaded.damaged().size());
        GraveStore.Damaged d = loaded.damaged().getFirst();
        assertNotNull(d.copy());
        assertEquals(truncated, Files.readString(d.copy()));
        assertEquals(truncated, Files.readString(file), "the damaged file stays where it was");

        assertThrows(IOException.class, () -> fresh.save(g));
        assertThrows(IOException.class, () -> fresh.delete(g.id()));
        assertEquals(truncated, Files.readString(file), "and is never overwritten or removed");
    }

    @Test
    void aFileUnderTheWrongNameIsDamaged() throws Exception {
        GraveStore store = new GraveStore(dir);
        Grave g = Fixtures.grave();
        store.save(g);
        Files.move(dir.resolve("graves/" + g.id() + ".yml"), dir.resolve("graves/copy.yml"));
        GraveStore.Loaded loaded = new GraveStore(dir).loadAll();
        assertTrue(loaded.graves().isEmpty());
        assertEquals(1, loaded.damaged().size());
    }
}
