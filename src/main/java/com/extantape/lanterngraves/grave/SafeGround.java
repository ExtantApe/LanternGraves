package com.extantape.lanterngraves.grave;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The last block each player stood on safely, per world: solid ground, no lava, room to stand.
 * A grave from a void or lava death goes there. Kept in memory only; after a restart a void death
 * before the player has stood anywhere falls back to the world's spawn.
 */
public final class SafeGround {
    private final Map<UUID, Map<String, Location>> last = new HashMap<>();

    public void observe(Player p) {
        // Judged from the server's own view of the player, not the client's on-ground flag.
        if (p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isSwimming()) return;
        Location at = p.getLocation();
        if (at.getY() - Math.floor(at.getY()) > 0.01 || Math.abs(p.getVelocity().getY()) > 0.1) return;
        Block feet = at.getBlock();
        Block below = feet.getRelative(0, -1, 0);
        Block head = feet.getRelative(0, 1, 0);
        if (!below.getType().isSolid() || below.getType() == Material.MAGMA_BLOCK) return;
        if (!passable(feet) || !passable(head)) return;
        last.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(at.getWorld().getName(), feet.getLocation());
    }

    private static boolean passable(Block b) {
        Material m = b.getType();
        return !m.isSolid() && m != Material.LAVA && m != Material.FIRE && m != Material.SOUL_FIRE
                && m != Material.POWDER_SNOW && m != Material.SWEET_BERRY_BUSH && m != Material.WITHER_ROSE && m != Material.COBWEB;
    }

    public Location last(Player p, World w) {
        Map<String, Location> m = last.get(p.getUniqueId());
        Location l = m == null ? null : m.get(w.getName());
        return l == null ? null : l.clone();
    }

    public void forget(Player p) {
        last.remove(p.getUniqueId());
    }
}
