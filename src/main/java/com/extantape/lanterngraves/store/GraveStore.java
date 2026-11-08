package com.extantape.lanterngraves.store;

import com.extantape.lanterngraves.grave.Grave;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One file per grave under {@code graves/}. A save writes a temporary file, flushes it to disk and
 * renames it over the old one, so a crash leaves either the old grave or the new one, never half.
 *
 * <p>A file that won't read is never overwritten or removed. It's copied to {@code damaged/}, left
 * where it is, and its grave id is refused for writing until someone fixes it by hand.
 */
public final class GraveStore {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public record Damaged(Path file, Path copy, String reason) {}
    public record Loaded(List<Grave> graves, List<Damaged> damaged) {}

    private final Path dir;
    private final Path damagedDir;
    private final Set<String> refused = new HashSet<>();

    public GraveStore(Path dataFolder) {
        this.dir = dataFolder.resolve("graves");
        this.damagedDir = dataFolder.resolve("damaged");
    }

    public Path dir() {
        return dir;
    }

    public Loaded loadAll() throws IOException {
        Files.createDirectories(dir);
        List<Grave> graves = new ArrayList<>();
        List<Damaged> damaged = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.endsWith(".tmp")) {
                    // A save that never finished. The grave's real file is untouched, so this is debris.
                    Files.deleteIfExists(file);
                    continue;
                }
                if (!name.endsWith(".yml")) continue;
                String reason;
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    Grave g = GraveCodec.decode(text);
                    if (!name.equals(g.id() + ".yml")) throw new GraveCodec.DamagedException("file name doesn't match grave id " + g.id());
                    graves.add(g);
                    continue;
                } catch (GraveCodec.DamagedException e) {
                    reason = e.getMessage();
                } catch (IOException | RuntimeException e) {
                    reason = "couldn't read: " + e;
                }
                refused.add(name);
                damaged.add(new Damaged(file, keepCopy(file), reason));
            }
        }
        return new Loaded(graves, damaged);
    }

    private Path keepCopy(Path file) {
        try {
            Files.createDirectories(damagedDir);
            Path copy = damagedDir.resolve(file.getFileName() + "." + LocalDateTime.now().format(STAMP));
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return copy;
        } catch (IOException e) {
            return null;
        }
    }

    public void save(Grave g) throws IOException {
        String name = g.id() + ".yml";
        if (refused.contains(name)) throw new IOException("refusing to overwrite damaged file " + name);
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        Path tmp = dir.resolve(name + ".tmp");
        byte[] bytes = GraveCodec.encode(g).getBytes(StandardCharsets.UTF_8);
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.deleteIfExists(tmp);
            throw new IOException("this file system can't replace files atomically, so graves can't be saved safely", e);
        }
        syncDir();
    }

    public void delete(UUID id) throws IOException {
        String name = id + ".yml";
        if (refused.contains(name)) throw new IOException("refusing to delete damaged file " + name);
        Files.deleteIfExists(dir.resolve(name));
        syncDir();
    }

    private void syncDir() {
        // Makes the rename itself durable where the OS allows opening a directory; harmless where it doesn't.
        try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
            ch.force(true);
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
