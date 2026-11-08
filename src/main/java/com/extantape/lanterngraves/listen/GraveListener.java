package com.extantape.lanterngraves.listen;

import com.extantape.lanterngraves.grave.Grave;
import com.extantape.lanterngraves.grave.GraveService;
import com.extantape.lanterngraves.grave.SafeGround;
import com.extantape.lanterngraves.marker.Markers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public final class GraveListener implements Listener {
    private final GraveService graves;
    private final Markers markers;
    private final SafeGround safeGround;

    public GraveListener(GraveService graves, Markers markers, SafeGround safeGround) {
        this.graves = graves;
        this.markers = markers;
        this.safeGround = safeGround;
    }

    // Late, so other plugins have had their say about drops and keepInventory, but before MONITOR.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        graves.onDeath(e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        graves.settle(e.getPlayer());
        graves.onRespawn(e.getPlayer());
    }

    // LOWEST: settle anything a crash left before any other plugin looks at the inventory.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        graves.settle(p);
        graves.resolve(p);
        for (Grave g : graves.all()) {
            if (g.owner().equals(p.getUniqueId()) && !p.getName().equals(g.ownerName())) {
                g.ownerName(p.getName());
                graves.persist(g);
            }
        }
        markers.refreshViewers();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        graves.forget(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        graves.settle(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        graves.settle(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        safeGround.observe(e.getPlayer());
    }

    // ---------------------------------------------------------------- the lantern

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!markers.ours(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        open(e.getPlayer(), e.getRightClicked());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        if (markers.ours(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!markers.ours(e.getEntity())) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) open(p, e.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (markers.ours(e.getEntity())) e.setCancelled(true);
    }

    private void open(Player p, Entity marker) {
        UUID id = markers.graveOf(marker);
        Grave g = id == null ? null : graves.get(id);
        if (g == null) {
            marker.remove();
            return;
        }
        graves.collect(p, g);
    }

    // ---------------------------------------------------------------- chunks

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunk(ChunkLoadEvent e) {
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        String world = e.getWorld().getName();
        for (Grave g : graves.all()) {
            if (g.world().equals(world) && g.blockX() >> 4 == cx && g.blockZ() >> 4 == cz) {
                Bukkit.getScheduler().runTask(markers.plugin(), () -> markers.spawn(g));
            }
        }
    }

    /** Ours are never saved, but if one ever turns up in a chunk (an old crash, a copied world), it goes. */
    @EventHandler
    public void onEntities(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (markers.ours(entity) && !markers.tracked(entity)) entity.remove();
        }
    }
}
