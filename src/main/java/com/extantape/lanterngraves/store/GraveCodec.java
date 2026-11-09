package com.extantape.lanterngraves.store;

import com.extantape.lanterngraves.grave.Grant;
import com.extantape.lanterngraves.grave.Grave;
import com.extantape.lanterngraves.grave.Pending;
import com.extantape.lanterngraves.grave.StoredItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a grave into a YAML file and back. The last line is a SHA-256 of everything above it, so
 * a file cut short or damaged on disk is caught instead of read as a smaller grave.
 */
public final class GraveCodec {
    public static final int FORMAT = 1;
    private static final String SUM = "# sha256: ";
    private static final String HEADER = """
            # A LanternGraves grave. The items are Paper's own item data, in base64; type and amount
            # are only there to read. Don't edit this file: the checksum on the last line will refuse it.
            """;

    private GraveCodec() {}

    public static String encode(Grave g) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("format", FORMAT);
        y.set("id", g.id().toString());
        y.set("owner", g.owner().toString());
        y.set("owner-name", g.ownerName());
        y.set("created", g.created());
        y.set("death-world", g.deathWorld());
        y.set("game-mode", g.gameMode());
        y.set("world", g.world());
        y.set("x", g.x());
        y.set("y", g.y());
        y.set("z", g.z());
        y.set("xp", g.xp());
        y.set("light-placed", g.lightPlaced());
        for (Map.Entry<Integer, StoredItem> e : g.items().entrySet()) {
            String p = "items." + e.getKey();
            y.set(p + ".type", e.getValue().type());
            y.set(p + ".amount", e.getValue().amount());
            y.set(p + ".data", Base64.getEncoder().encodeToString(e.getValue().data()));
        }
        for (Grant grant : g.grants().values()) {
            String p = "grants." + grant.friend();
            y.set(p + ".name", grant.name());
            y.set(p + ".until", grant.until());
        }
        Pending pending = g.pending();
        if (pending != null) {
            y.set("pending.kind", pending.kind().name());
            y.set("pending.txn", pending.txn().toString());
            y.set("pending.player", pending.player().toString());
            y.set("pending.slots", pending.slots());
            y.set("pending.xp", pending.xp());
        }
        String body = HEADER + y.saveToString();
        if (!body.endsWith("\n")) body += "\n";
        return body + SUM + sha256(body) + "\n";
    }

    public static Grave decode(String text) throws DamagedException {
        int cut = text.lastIndexOf(SUM);
        if (cut < 0) throw new DamagedException("no checksum line: the file is incomplete or was edited");
        String body = text.substring(0, cut);
        String sum = text.substring(cut + SUM.length()).trim();
        if (!sum.equals(sha256(body))) throw new DamagedException("checksum doesn't match: the file is damaged or was edited");
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(body);
        } catch (InvalidConfigurationException e) {
            throw new DamagedException("not valid YAML: " + e.getMessage());
        }
        try {
            int format = y.getInt("format", -1);
            if (format != FORMAT) throw new DamagedException("unknown format " + format);
            Grave g = new Grave(uuid(y, "id"), uuid(y, "owner"), y.getString("owner-name", "?"), y.getLong("created"),
                    req(y, "death-world"), req(y, "game-mode"), req(y, "world"),
                    num(y, "x"), num(y, "y"), num(y, "z"));
            g.xp(y.getInt("xp"));
            g.lightPlaced(y.getBoolean("light-placed"));
            ConfigurationSection items = y.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection s = items.getConfigurationSection(key);
                    if (s == null) throw new DamagedException("item " + key + " is not a section");
                    g.put(Integer.parseInt(key), new StoredItem(s.getString("type"), s.getInt("amount"),
                            Base64.getDecoder().decode(s.getString("data", ""))));
                }
            }
            ConfigurationSection grants = y.getConfigurationSection("grants");
            if (grants != null) {
                for (String key : grants.getKeys(false)) {
                    g.grant(new Grant(UUID.fromString(key), grants.getString(key + ".name", "?"), grants.getLong(key + ".until")));
                }
            }
            if (y.isConfigurationSection("pending")) {
                List<Integer> slots = new ArrayList<>(y.getIntegerList("pending.slots"));
                g.pending(new Pending(Pending.Kind.valueOf(req(y, "pending.kind")), uuid(y, "pending.txn"),
                        uuid(y, "pending.player"), slots, y.getInt("pending.xp")));
            }
            return g;
        } catch (DamagedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DamagedException("unreadable: " + e);
        }
    }

    private static String req(YamlConfiguration y, String path) throws DamagedException {
        String s = y.getString(path);
        if (s == null || s.isEmpty()) throw new DamagedException("missing " + path);
        return s;
    }

    private static UUID uuid(YamlConfiguration y, String path) throws DamagedException {
        return UUID.fromString(req(y, path));
    }

    private static double num(YamlConfiguration y, String path) throws DamagedException {
        if (!y.isDouble(path) && !y.isInt(path)) throw new DamagedException("missing " + path);
        return y.getDouble(path);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class DamagedException extends Exception {
        public DamagedException(String message) {
            super(message);
        }
    }
}
