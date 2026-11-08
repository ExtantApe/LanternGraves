package com.extantape.lanterngraves.grave;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transaction ids kept on the player, in their own save file. Paper writes this data with the
 * inventory, so after a crash its presence says whether the inventory on disk includes the change.
 */
public final class Txns {
    private final NamespacedKey key;

    public Txns(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "txns");
    }

    public Set<UUID> read(Player p) {
        String s = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (s == null || s.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(s.split(",")).filter(t -> !t.isBlank()).map(t -> {
            try {
                return UUID.fromString(t.trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void add(Player p, UUID txn) {
        Set<UUID> s = read(p);
        s.add(txn);
        write(p, s);
    }

    public void remove(Player p, UUID txn) {
        Set<UUID> s = read(p);
        if (s.remove(txn)) write(p, s);
    }

    public void clear(Player p) {
        p.getPersistentDataContainer().remove(key);
    }

    private void write(Player p, Set<UUID> s) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (s.isEmpty()) pdc.remove(key);
        else pdc.set(key, PersistentDataType.STRING, s.stream().map(UUID::toString).collect(Collectors.joining(",")));
    }
}
