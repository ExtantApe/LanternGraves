package com.extantape.lanterngraves.testhook;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test servers only: kills a player tagged die_on_quit as they log out, the way a combat-log plugin
 * does. Never shipped; it's built into its own jar for the bot tests.
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
}
