package com.extantape.lanterngraves.testhook;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test servers only, never shipped; built into its own jar for the bot tests. It plays other plugins:
 * kills a player tagged die_on_quit as they log out, the way a combat-log plugin does, and on death, for a
 * player tagged lg_keep, keeps their cobblestone with them (like a soulbound plugin), and for lg_add, adds a
 * nether star to the drops (like a head-drop plugin).
 */
public final class DieOnQuit extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer().getScoreboardTags().contains("die_on_quit")) {
            e.getPlayer().removeScoreboardTag("die_on_quit");
            e.getPlayer().setHealth(0);
        }
    }

    // Before LanternGraves, which listens at HIGHEST.
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent e) {
        var tags = e.getPlayer().getScoreboardTags();
        if (tags.contains("lg_keep")) {
            var it = e.getDrops().iterator();
            while (it.hasNext()) {
                ItemStack drop = it.next();
                if (drop.getType() == Material.COBBLESTONE) { it.remove(); e.getItemsToKeep().add(drop); }
            }
        }
        if (tags.contains("lg_add")) e.getDrops().add(new ItemStack(Material.NETHER_STAR, 1));
    }
}
