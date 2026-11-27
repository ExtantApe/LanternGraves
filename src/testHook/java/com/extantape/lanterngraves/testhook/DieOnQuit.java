package com.extantape.lanterngraves.testhook;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test servers only, never shipped; built into its own jar for the bot tests. It plays other plugins:
 * kills a player tagged die_on_quit as they log out, the way a combat-log plugin does, and on death, for a
 * player tagged lg_keep, keeps their cobblestone with them (like a soulbound plugin), and for lg_add, adds a
 * nether star to the drops (like a head-drop plugin). From the console, {@code lgtest disable <plugin>} and
 * {@code lgtest enable <plugin>} stop and start another plugin while the server runs, as an admin's plugin manager
 * would.
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

    @EventHandler
    public void onConsole(ServerCommandEvent e) {
        String[] words = e.getCommand().trim().split("\\s+");
        if (!words[0].equalsIgnoreCase("lgtest")) return;
        e.setCancelled(true);
        Plugin target = words.length == 3 ? getServer().getPluginManager().getPlugin(words[2]) : null;
        if (target == null || target == this) {
            getLogger().warning("lgtest disable|enable <plugin>");
        } else if (words[1].equalsIgnoreCase("disable")) {
            getServer().getPluginManager().disablePlugin(target);
            getLogger().info("lgtest: disabled " + target.getName());
        } else if (words[1].equalsIgnoreCase("enable")) {
            getServer().getPluginManager().enablePlugin(target);
            getLogger().info("lgtest: enabled " + target.getName());
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
