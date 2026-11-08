package com.extantape.lanterngraves;

import com.extantape.lanterngraves.command.AdminCommand;
import com.extantape.lanterngraves.command.GraveCommand;
import com.extantape.lanterngraves.grave.GraveService;
import com.extantape.lanterngraves.grave.SafeGround;
import com.extantape.lanterngraves.groups.InventoryGroups;
import com.extantape.lanterngraves.groups.MultiverseGroups;
import com.extantape.lanterngraves.listen.GraveListener;
import com.extantape.lanterngraves.marker.Markers;
import com.extantape.lanterngraves.store.GraveStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public final class LanternGravesPlugin extends JavaPlugin implements Listener {
    private GraveService graves;
    private Markers markers;
    private List<GraveStore.Damaged> damaged = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Settings settings = Settings.read(getConfig());
        GraveStore store = new GraveStore(getDataFolder().toPath());
        GraveStore.Loaded loaded;
        try {
            loaded = store.loadAll();
        } catch (IOException e) {
            // Without the grave folder there's no safe way on: items would drop and existing graves vanish from view.
            getLogger().log(Level.SEVERE, "Can't read " + store.dir() + ". Lantern Graves is turning itself off; deaths drop items as in vanilla.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        damaged = loaded.damaged();
        for (GraveStore.Damaged d : damaged) shoutDamaged(d);

        InventoryGroups groups = InventoryGroups.NONE;
        if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Inventories")) {
            try {
                groups = new MultiverseGroups();
                getLogger().info("Using Multiverse-Inventories groups: a grave pays out only in the group it came from.");
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Multiverse-Inventories is installed but its API didn't answer. Graves are off so no "
                        + "items move between inventory groups; deaths drop items as in vanilla.", t);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            String warning = groups.crashWarning();
            if (warning != null) getLogger().warning(warning);
        }

        SafeGround safeGround = new SafeGround();
        graves = new GraveService(this, store, groups, settings, safeGround);
        graves.load(loaded.graves());
        markers = new Markers(this, graves, settings);
        graves.markers(markers);

        getServer().getPluginManager().registerEvents(new GraveListener(graves, markers, safeGround), this);
        getServer().getPluginManager().registerEvents(this, this);
        GraveCommand grave = new GraveCommand(graves);
        getCommand("grave").setExecutor(grave);
        getCommand("grave").setTabCompleter(grave);
        AdminCommand admin = new AdminCommand(this, graves);
        getCommand("lanterngraves").setExecutor(admin);
        getCommand("lanterngraves").setTabCompleter(admin);

        // Players already online (a plugin reload): their live data is current, so settle against it.
        for (Player p : Bukkit.getOnlinePlayers()) {
            graves.settle(p);
            graves.resolve(p);
        }
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            graves.retryUnsaved();
            markers.tick();
        }, 20L, 20L);
        getLogger().info("Loaded " + loaded.graves().size() + " grave" + (loaded.graves().size() == 1 ? "" : "s") + ".");
    }

    @Override
    public void onDisable() {
        if (markers != null) markers.removeAll();
        if (graves != null) {
            graves.retryUnsaved();
            if (graves.hasUnsaved()) getLogger().severe("Some graves couldn't be saved at shutdown. Their last saved state is on disk.");
        }
    }

    public void reload() {
        reloadConfig();
        Settings settings = Settings.read(getConfig());
        graves.settings(settings);
        markers.settings(settings);
    }

    public List<GraveStore.Damaged> damaged() {
        return damaged;
    }

    private void shoutDamaged(GraveStore.Damaged d) {
        String bar = "*".repeat(72);
        getLogger().severe(bar);
        getLogger().severe("A GRAVE FILE IS DAMAGED and won't load: " + d.file());
        getLogger().severe("Reason: " + d.reason());
        getLogger().severe(d.copy() == null ? "A copy COULDN'T be made. Back up that file now." : "A copy was kept at " + d.copy());
        getLogger().severe("Lantern Graves won't overwrite or remove it. The items in it can't be collected until it's fixed.");
        getLogger().severe(bar);
    }

    @EventHandler
    public void onAdminJoin(PlayerJoinEvent e) {
        if (damaged.isEmpty() || !e.getPlayer().hasPermission("lanterngraves.admin")) return;
        e.getPlayer().sendMessage(Component.text("Lantern Graves: " + damaged.size() + " grave file" + (damaged.size() == 1 ? " is" : "s are")
                + " damaged and won't load. /lanterngraves damaged for details.", NamedTextColor.RED));
    }
}
