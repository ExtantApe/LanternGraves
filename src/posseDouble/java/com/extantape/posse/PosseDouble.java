package com.extantape.posse;

import com.extantape.posse.api.ReachService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Test servers only, never shipped; built into its own jar for the bot tests. It stands in for Posse: it carries
 * Posse's plugin name and offers a ReachService, so LanternGraves asks it who may know whose grave it is. Its
 * answer is simple on purpose. A viewer with the scoreboard tag {@code lg_circle} may know; nobody else may. Two
 * players who both have the tag {@code lg_reach} may reach each other; no other pair may. The real Posse's "may
 * know" is stricter than its "may reach", and two tags let a test set them apart.
 *
 * <p>From the console: {@code possedouble throw} makes every answer fail and {@code possedouble answer} puts the
 * answers back. To stop and start it, as if Posse had gone and come back, the test hook plugin takes
 * {@code lgtest disable Posse} and {@code lgtest enable Posse}.
 */
public final class PosseDouble extends JavaPlugin implements ReachService, Listener {
    private volatile boolean failing;

    @Override
    public void onEnable() {
        failing = false;
        getServer().getServicesManager().register(ReachService.class, this, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean mayKnow(Player viewer, UUID owner, String world) {
        if (failing) throw new IllegalStateException("the Posse double was told to fail");
        return viewer.getUniqueId().equals(owner) || viewer.getScoreboardTags().contains("lg_circle");
    }

    @Override
    public boolean mayReach(Player from, Player to) {
        if (failing) throw new IllegalStateException("the Posse double was told to fail");
        return from.equals(to) || (from.getScoreboardTags().contains("lg_reach") && to.getScoreboardTags().contains("lg_reach"));
    }

    @EventHandler
    public void onConsole(ServerCommandEvent e) {
        String[] words = e.getCommand().trim().split("\\s+");
        if (!words[0].equalsIgnoreCase("possedouble")) return;
        e.setCancelled(true);
        switch (words.length > 1 ? words[1] : "") {
            case "throw" -> failing = true;
            case "answer" -> failing = false;
            default -> { getLogger().warning("possedouble throw|answer"); return; }
        }
        getLogger().info("Posse double: " + words[1]);
    }
}
