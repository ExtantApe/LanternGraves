package com.extantape.lanterngraves.grave;

import com.extantape.lanterngraves.LanternGravesPlugin;
import com.extantape.lanterngraves.Settings;
import com.extantape.lanterngraves.groups.InventoryGroups;
import com.extantape.lanterngraves.marker.Markers;
import com.extantape.lanterngraves.store.GraveStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Makes graves on death, pays them out, and settles anything a crash left half done.
 * Everything here runs on the main thread.
 */
public final class GraveService {
    private final LanternGravesPlugin plugin;
    private final GraveStore store;
    private final InventoryGroups groups;
    private final Txns txns;
    private final SafeGround safeGround;
    private Settings settings;
    private Markers markers;

    private final Map<UUID, Grave> graves = new LinkedHashMap<>();
    /** Graves whose last save or delete failed; retried every second. */
    private final Set<UUID> unsaved = new HashSet<>();
    /** Server tick before which a player's inventory may still be mid-swap. */
    private final Map<UUID, Integer> settleUntil = new ConcurrentHashMap<>();
    /** The grave each player most recently got, so their respawn can point at it. */
    private final Map<UUID, UUID> newest = new HashMap<>();

    public GraveService(LanternGravesPlugin plugin, GraveStore store, InventoryGroups groups, Settings settings, SafeGround safeGround) {
        this.plugin = plugin;
        this.store = store;
        this.groups = groups;
        this.settings = settings;
        this.safeGround = safeGround;
        this.txns = new Txns(plugin);
    }

    public void markers(Markers markers) { this.markers = markers; }
    public void settings(Settings settings) { this.settings = settings; }
    public Settings settings() { return settings; }
    public InventoryGroups groups() { return groups; }

    public void load(Collection<Grave> loaded) {
        for (Grave g : loaded) graves.put(g.id(), g);
    }

    public Grave get(UUID id) { return graves.get(id); }

    public Collection<Grave> all() { return List.copyOf(graves.values()); }

    /** A player's settled graves, oldest first. Numbers in commands count from 1 in this order. */
    public List<Grave> of(UUID owner) {
        return graves.values().stream().filter(g -> g.owner().equals(owner) && g.settled())
                .sorted(Comparator.comparingLong(Grave::created).thenComparing(Grave::id)).toList();
    }

    /** The height the lantern shows at right now. */
    public double markerY(Grave g) {
        World w = Bukkit.getWorld(g.world());
        return w == null ? g.y() : markers.base(g, w).getY();
    }

    public int numberOf(Grave g) {
        return of(g.owner()).indexOf(g) + 1;
    }

    // ---------------------------------------------------------------- death

    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (e.getKeepInventory()) return; // vanilla or another plugin keeps everything: nothing to do
        if (settings.disabledWorlds().contains(p.getWorld().getName())) return;

        PlayerInventory inv = p.getInventory();
        List<ItemStack> drops = new ArrayList<>(e.getDrops());
        Map<Integer, ItemStack> taken = new LinkedHashMap<>();
        // Match each inventory slot to its drop, so things go back where they were. Anything vanilla
        // destroyed (Curse of Vanishing) or another plugin kept isn't among the drops, so it's skipped.
        for (int slot = 0; slot <= Placement.LAST_SLOT; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.isEmpty()) continue;
            for (int j = 0; j < drops.size(); j++) {
                if (item.equals(drops.get(j))) {
                    taken.put(slot, drops.remove(j));
                    break;
                }
            }
        }
        int extra = Grave.EXTRA_BASE;
        for (ItemStack rest : drops) {
            if (rest != null && !rest.isEmpty()) taken.put(extra++, rest);
        }
        // The cursor and the input slots of a work screen open at death (the 2x2 crafting grid, a crafting table,
        // an anvil, and so on) aren't in the drops: vanilla throws them out separately when the screen closes on
        // death. Take them into the grave too, and clear them only once the grave is saved.
        ItemStack cursor = p.getItemOnCursor();
        boolean tookCursor = cursor != null && !cursor.isEmpty();
        if (tookCursor) taken.put(extra++, cursor.clone());
        org.bukkit.inventory.Inventory top = p.getOpenInventory().getTopInventory();
        List<Integer> inputs = WorkScreens.inputSlots(top);
        List<Integer> tookInputs = new ArrayList<>();
        for (int slot : inputs) {
            ItemStack in = top.getItem(slot);
            if (in != null && !in.isEmpty()) { taken.put(extra++, in.clone()); tookInputs.add(slot); }
        }

        int xp = e.getKeepLevel() ? 0 : Xp.total(p.getLevel(), p.getExp());
        if (taken.isEmpty() && xp == 0) return;

        Location spot = chooseSpot(p);
        Grave g = new Grave(UUID.randomUUID(), p.getUniqueId(), p.getName(), System.currentTimeMillis(),
                p.getWorld().getName(), p.getGameMode().name(), spot.getWorld().getName(), spot.getX(), spot.getY(), spot.getZ());
        List<ItemStack> leftBehind = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> t : taken.entrySet()) {
            try {
                g.put(t.getKey(), new StoredItem(t.getValue().getType().name(), t.getValue().getAmount(), t.getValue().serializeAsBytes()));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Couldn't store " + t.getValue().getType() + " for " + p.getName() + "; it drops instead", ex);
                leftBehind.add(t.getValue());
            }
        }
        g.xp(xp);
        if (g.isEmpty()) return;
        UUID txn = UUID.randomUUID();
        g.pending(new Pending(Pending.Kind.DEATH, txn, p.getUniqueId(), List.of(), 0));
        try {
            store.save(g);
        } catch (IOException ex) {
            // Nothing has changed yet: vanilla drops everything, which is better than a grave we can't keep.
            plugin.getLogger().log(Level.SEVERE, "Couldn't save a grave for " + p.getName() + ". Their items drop as in vanilla.", ex);
            return;
        }
        CrashPoint.at("death-written");
        if (tookCursor) p.setItemOnCursor(null);
        for (int slot : tookInputs) top.setItem(slot, null);
        e.getDrops().clear();
        e.getDrops().addAll(leftBehind);
        if (xp > 0) {
            e.setDroppedExp(0);
            e.setNewExp(0);
            e.setNewLevel(0);
            e.setNewTotalExp(0);
        }
        txns.add(p, txn);
        Component said = e.deathMessage() == null ? Component.text("You died!") : e.deathMessage();
        e.deathScreenMessageOverride(said.appendNewline().appendNewline()
                .append(Component.text("Your things are safe in a grave at " + g.blockX() + ", " + g.blockY() + ", " + g.blockZ() + ".", NamedTextColor.GOLD)));
        graves.put(g.id(), g);
        newest.put(p.getUniqueId(), g.id());
        plugin.getLogger().info("Grave " + g.id() + " for " + p.getName() + " at " + where(g) + ": "
                + g.itemCount() + " items, " + xp + " xp");
        // Vanilla clears the inventory after this event. Next tick the player's save holds the empty
        // inventory and the transaction id together, and only then does the grave count as made.
        Bukkit.getScheduler().runTask(plugin, () -> commitDeath(g, p));
    }

    private void commitDeath(Grave g, Player p) {
        if (g.pending() == null || g.pending().kind() != Pending.Kind.DEATH) return;
        UUID txn = g.pending().txn();
        if (p.isOnline()) {
            try {
                p.saveData();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Couldn't save " + p.getName() + "'s player data after death; the grave waits for their next join", ex);
                return;
            }
        }
        CrashPoint.at("death-saved");
        g.pending(null);
        persist(g);
        txns.remove(p, txn);
        markers.spawn(g);
        pointAt(p, g);
    }

    /** Where the lantern goes: where they died, unless that's lava, the void or inside a block. */
    private Location chooseSpot(Player p) {
        Location at = p.getLocation();
        World w = at.getWorld();
        EntityDamageEvent last = p.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = last == null ? null : last.getCause();
        boolean intoVoid = cause == EntityDamageEvent.DamageCause.VOID || at.getY() < w.getMinHeight();
        boolean inLava = cause == EntityDamageEvent.DamageCause.LAVA || isLava(at.getBlock()) || isLava(p.getEyeLocation().getBlock());
        Location spot = null;
        if (!intoVoid && !inLava) {
            Block b = at.getBlock();
            for (int i = 0; i < 16 && b.getY() < w.getMaxHeight() - 1; i++) {
                if (!b.getType().isOccluding() && !isLava(b)) {
                    spot = b.getLocation();
                    break;
                }
                b = b.getRelative(0, 1, 0);
            }
        }
        if (spot == null) spot = safeGround.last(p, w);
        if (spot == null) spot = fallback(w);
        int y = Math.max(w.getMinHeight(), Math.min(w.getMaxHeight() - 2, spot.getBlockY()));
        // Two graves in one block would share a lantern: stack later ones above.
        for (int i = 0; i < 8 && occupied(w.getName(), spot.getBlockX(), y, spot.getBlockZ()); i++) y++;
        return new Location(w, spot.getBlockX() + 0.5, y, spot.getBlockZ() + 0.5);
    }

    private static Location fallback(World w) {
        Location spawn = w.getSpawnLocation();
        Block top = w.getHighestBlockAt(spawn);
        if (top.getType().isAir() || top.getY() <= w.getMinHeight()) return spawn.getBlock().getLocation();
        return top.getLocation().add(0, 1, 0);
    }

    private boolean occupied(String world, int x, int y, int z) {
        for (Grave g : graves.values()) {
            if (g.world().equals(world) && g.blockX() == x && g.blockY() == y && g.blockZ() == z) return true;
        }
        return false;
    }

    private static boolean isLava(Block b) {
        return b.getType() == Material.LAVA;
    }

    public void pointAt(Player p, Grave g) {
        if (!settings.pointDeathLocation() || !p.isOnline()) return;
        World w = Bukkit.getWorld(g.world());
        if (w != null) p.setLastDeathLocation(new Location(w, g.x(), g.y(), g.z()));
    }

    /** After a grave is emptied: the death location leads to the newest grave left, or nowhere. */
    private void repoint(UUID owner) {
        Player p = Bukkit.getPlayer(owner);
        if (p == null || !settings.pointDeathLocation()) return;
        List<Grave> left = of(owner);
        if (left.isEmpty()) p.setLastDeathLocation(null);
        else pointAt(p, left.getLast());
    }

    public void onRespawn(Player p) {
        UUID id = newest.remove(p.getUniqueId());
        Grave g = id == null ? null : graves.get(id);
        if (g == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            pointAt(p, g);
            p.sendMessage(Component.text("Your things are safe in a grave at " + where(g) + ". ", NamedTextColor.GOLD)
                    .append(Component.text("Right-click its lantern to collect them. /grave lists your graves.", NamedTextColor.YELLOW)));
        });
    }

    /**
     * Once a second: a player near one of their graves sees how far and which way on the action bar,
     * in words, so it reads the same whatever colours someone can see.
     */
    public void hints() {
        if (!settings.distanceHint()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;
            Grave best = null;
            double bestSq = settings.hintRange() * settings.hintRange();
            for (Grave g : graves.values()) {
                if (!g.settled() || !g.owner().equals(p.getUniqueId()) || !g.world().equals(p.getWorld().getName())) continue;
                double dx = g.x() - p.getX(), dz = g.z() - p.getZ(), dy = g.y() - p.getY();
                double d = dx * dx + dz * dz + dy * dy;
                if (d < bestSq) { bestSq = d; best = g; }
            }
            if (best == null) continue;
            int dist = (int) Math.round(Math.sqrt(bestSq));
            String text = dist <= 2 ? "Your grave is right here. Right-click the lantern."
                    : "Your grave: " + dist + " blocks " + Compass.direction(best.x() - p.getX(), best.z() - p.getZ())
                    + heightHint(best.y() - p.getY());
            p.sendActionBar(Component.text(text, NamedTextColor.GOLD));
        }
    }

    private static String heightHint(double dy) {
        if (dy > 3) return ", " + (int) Math.round(dy) + " up";
        if (dy < -3) return ", " + (int) Math.round(-dy) + " down";
        return "";
    }

    // ---------------------------------------------------------------- collecting

    public void settle(Player p) {
        settleUntil.put(p.getUniqueId(), Bukkit.getCurrentTick() + settings.settleTicks());
    }

    public void forget(Player p) {
        settleUntil.remove(p.getUniqueId());
    }

    /** Pays out as much of a grave as fits. Returns true if anything moved. */
    public boolean collect(Player c, Grave g) {
        long now = System.currentTimeMillis();
        if (!graves.containsKey(g.id())) return false;
        if (!g.mayOpen(c.getUniqueId(), now)) {
            Grant grant = g.grants().get(c.getUniqueId());
            tell(c, grant != null ? "Your access to " + g.ownerName() + "'s grave has ended." : "This grave belongs to " + g.ownerName() + ".", NamedTextColor.RED);
            return false;
        }
        if (!g.settled()) {
            tell(c, "This grave is still being saved. Try again in a moment.", NamedTextColor.YELLOW);
            return false;
        }
        if (c.isDead() || c.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        if (Bukkit.getCurrentTick() < settleUntil.getOrDefault(c.getUniqueId(), 0)) {
            tell(c, "Your inventory is still being loaded. Try again in a moment.", NamedTextColor.YELLOW);
            return false;
        }
        if (!groups.sameInventory(g.deathWorld(), g.gameMode(), c.getWorld().getName(), c.getGameMode())) {
            tell(c, "This grave holds the inventory from " + groups.describe(g.deathWorld())
                    + (g.gameMode().equals(c.getGameMode().name()) ? "" : " in " + g.gameMode().toLowerCase() + " mode")
                    + ". It can only be collected there.", NamedTextColor.RED);
            return false;
        }

        PlayerInventory inv = c.getInventory();
        Map<Integer, Integer> plan = Placement.plan(List.copyOf(g.items().keySet()), slot -> {
            ItemStack s = inv.getItem(slot);
            return s == null || s.isEmpty();
        });
        Map<Integer, ItemStack> give = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> step : plan.entrySet()) {
            StoredItem stored = g.items().get(step.getKey());
            try {
                give.put(step.getKey(), ItemStack.deserializeBytes(stored.data()));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Grave " + g.id() + " slot " + step.getKey() + " (" + stored.type()
                        + ") won't load on this server version; it stays in the grave", ex);
            }
        }
        int xp = g.xp();
        if (give.isEmpty() && xp == 0) {
            tell(c, "There's no room in your inventory.", NamedTextColor.YELLOW);
            return false;
        }

        // 1. Write down what's about to move, before anything does.
        UUID txn = UUID.randomUUID();
        g.pending(new Pending(Pending.Kind.COLLECT, txn, c.getUniqueId(), List.copyOf(give.keySet()), xp));
        try {
            store.save(g);
        } catch (IOException ex) {
            g.pending(null);
            plugin.getLogger().log(Level.SEVERE, "Couldn't save grave " + g.id() + " before collecting; nothing was moved", ex);
            tell(c, "The grave couldn't be opened safely, so nothing moved. Tell an admin.", NamedTextColor.RED);
            return false;
        }
        CrashPoint.at("collect-written");
        // 2. Move it, and save the player with the transaction id alongside.
        for (Map.Entry<Integer, ItemStack> it : give.entrySet()) inv.setItem(plan.get(it.getKey()), it.getValue());
        if (xp > 0) addXp(c, xp);
        txns.add(c, txn);
        try {
            c.saveData();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Couldn't save " + c.getName() + "'s player data while collecting; the next save covers it", ex);
        }
        CrashPoint.at("collect-saved");
        // 3. Take it out of the grave.
        for (int slot : give.keySet()) g.take(slot);
        g.xp(g.xp() - xp);
        g.pending(null);
        persist(g);
        txns.remove(c, txn);

        int count = give.values().stream().mapToInt(ItemStack::getAmount).sum();
        String got = count + " item" + (count == 1 ? "" : "s") + (xp > 0 ? " and " + Xp.levelFor(xp) + " levels of XP" : "");
        if (g.isEmpty()) {
            tell(c, "Collected " + got + ". The grave is empty and its lantern goes out.", NamedTextColor.GREEN);
        } else {
            int left = g.itemCount();
            tell(c, "Collected " + got + ". " + left + " item" + (left == 1 ? " doesn't" : "s don't")
                    + " fit and stay" + (left == 1 ? "s" : "") + " in the grave.", NamedTextColor.YELLOW);
        }
        c.playSound(c.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        if (!c.getUniqueId().equals(g.owner())) {
            Player owner = Bukkit.getPlayer(g.owner());
            if (owner != null) tell(owner, c.getName() + " collected " + got + " from your grave at " + where(g) + ".", NamedTextColor.GOLD);
            plugin.getLogger().info(c.getName() + " collected " + got + " from " + g.ownerName() + "'s grave " + g.id());
        }
        return true;
    }

    /** Adds points by setting level and bar directly; giveExp adds them in floats and can lose one. */
    private static void addXp(Player c, int points) {
        long total = Math.min(Integer.MAX_VALUE, (long) Xp.total(c.getLevel(), c.getExp()) + points);
        int level = Xp.levelFor((int) total);
        c.setLevel(level);
        c.setExp(Xp.progress(level, (int) total - Xp.toReach(level)));
        c.setTotalExperience((int) Math.min(Integer.MAX_VALUE, (long) c.getTotalExperience() + points));
    }

    // ---------------------------------------------------------------- crash recovery

    /** On join: settle anything a crash left half done for this player, using their save to decide. */
    public void resolve(Player p) {
        Set<UUID> held = txns.read(p);
        for (Grave g : List.copyOf(graves.values())) {
            Pending pd = g.pending();
            if (pd == null || !pd.player().equals(p.getUniqueId())) continue;
            boolean committed = held.contains(pd.txn());
            if (pd.kind() == Pending.Kind.DEATH) {
                if (committed) {
                    g.pending(null);
                    plugin.getLogger().info("Recovered grave " + g.id() + " for " + p.getName() + ": their save shows the death went through.");
                } else {
                    // Their save is from before the death and still holds the items, so this grave would be a copy.
                    plugin.getLogger().warning("Dropped unfinished grave " + g.id() + " for " + p.getName()
                            + ": the server stopped before their death was saved, so their saved inventory still has the items.");
                    graves.remove(g.id());
                    deleteFile(g);
                    continue;
                }
            } else {
                if (committed) {
                    for (int slot : pd.slots()) g.take(slot);
                    g.xp(g.xp() - pd.xp());
                    plugin.getLogger().info("Finished an interrupted collection from grave " + g.id() + " by " + p.getName() + ": their save has the items.");
                } else {
                    plugin.getLogger().info("Undid an interrupted collection from grave " + g.id() + " by " + p.getName() + ": their save doesn't have the items, so they stay in the grave.");
                }
                g.pending(null);
            }
            persist(g);
            if (graves.containsKey(g.id())) markers.spawn(g);
        }
        if (!held.isEmpty()) txns.clear(p);
    }

    // ---------------------------------------------------------------- friends

    public void grant(Grave g, UUID friend, String name, long until) {
        g.grant(new Grant(friend, name, until));
        persist(g);
        markers.refreshViewers(g);
    }

    public boolean revoke(Grave g, UUID friend) {
        boolean had = g.revoke(friend);
        if (had) {
            persist(g);
            markers.refreshViewers(g);
        }
        return had;
    }

    // ---------------------------------------------------------------- admin

    public void move(Grave g, Location to) {
        markers.remove(g);
        g.moveTo(to.getWorld().getName(), to.getBlockX() + 0.5, to.getBlockY(), to.getBlockZ() + 0.5);
        persist(g);
        markers.spawn(g);
    }

    // ---------------------------------------------------------------- saving

    /** Saves a grave, or deletes its file if it's empty. A failure is retried every second. */
    public void persist(Grave g) {
        if (g.isEmpty() && g.settled()) {
            graves.remove(g.id());
            markers.remove(g);
            deleteFile(g);
            repoint(g.owner());
            return;
        }
        try {
            store.save(g);
            unsaved.remove(g.id());
        } catch (IOException ex) {
            if (unsaved.add(g.id())) plugin.getLogger().log(Level.SEVERE, "Couldn't save grave " + g.id() + "; retrying every second", ex);
        }
    }

    private void deleteFile(Grave g) {
        try {
            store.delete(g.id());
            unsaved.remove(g.id());
        } catch (IOException ex) {
            if (unsaved.add(g.id())) plugin.getLogger().log(Level.SEVERE, "Couldn't remove emptied grave file " + g.id() + "; retrying every second", ex);
        }
    }

    public void retryUnsaved() {
        for (UUID id : List.copyOf(unsaved)) {
            Grave g = graves.get(id);
            unsaved.remove(id);
            if (g == null) {
                try {
                    store.delete(id);
                } catch (IOException ex) {
                    unsaved.add(id);
                }
            } else {
                persist(g);
            }
        }
    }

    public boolean hasUnsaved() { return !unsaved.isEmpty(); }

    // ---------------------------------------------------------------- text

    public static String where(Grave g) {
        return g.blockX() + ", " + g.blockY() + ", " + g.blockZ() + " in " + g.world();
    }

    public static void tell(org.bukkit.command.CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }
}
