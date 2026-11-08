package com.extantape.lanterngraves.marker;

import com.extantape.lanterngraves.Settings;
import com.extantape.lanterngraves.grave.Grave;
import com.extantape.lanterngraves.grave.GraveService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The lantern over each grave. It's made of entities that are never saved with the world: a lantern
 * everyone sees, a glowing copy only the owner and their friends see (it shows through walls), the
 * owner's name, and an invisible box to right-click. Whenever the grave's chunk is loaded and any of
 * them is missing or has been moved, they're put back. Nothing about a grave is stored in them.
 */
public final class Markers {
    private static final float LANTERN_SCALE = 1.0f;

    private final Plugin plugin;
    private final GraveService graves;
    private final NamespacedKey key;
    private Settings settings;
    private final Map<UUID, Parts> live = new HashMap<>();

    private record Parts(BlockDisplay lantern, BlockDisplay glow, TextDisplay label, Interaction hitbox) {
        List<Entity> all() {
            return label == null ? List.of(lantern, glow, hitbox) : List.of(lantern, glow, label, hitbox);
        }
    }

    public Markers(Plugin plugin, GraveService graves, Settings settings) {
        this.plugin = plugin;
        this.graves = graves;
        this.settings = settings;
        this.key = new NamespacedKey(plugin, "grave");
    }

    public void settings(Settings settings) {
        this.settings = settings;
        for (Grave g : graves.all()) remove(g, false);
    }

    public Plugin plugin() {
        return plugin;
    }

    /** Whether an entity is one of the live parts of a lantern we're keeping. */
    public boolean tracked(Entity e) {
        for (Parts p : live.values()) if (p.all().contains(e)) return true;
        return false;
    }

    /** The grave an entity marks, or null if it isn't one of ours. */
    public UUID graveOf(Entity e) {
        String s = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean ours(Entity e) {
        return e.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public void spawn(Grave g) {
        if (!g.settled()) return;
        World w = Bukkit.getWorld(g.world());
        if (w == null || !w.isChunkLoaded(g.blockX() >> 4, g.blockZ() >> 4)) return;
        Parts p = live.get(g.id());
        if (p == null || p.all().stream().anyMatch(e -> !e.isValid())) {
            if (p != null) p.all().forEach(Entity::remove);
            live.put(g.id(), create(g, w));
        } else {
            Location base = base(g, w);
            for (Entity e : p.all()) {
                Location want = e == p.label ? base.clone().add(0, 1.15, 0) : base;
                if (e.getLocation().distanceSquared(want) > 0.0001) e.teleport(want);
            }
        }
        light(g, w);
    }

    private Location base(Grave g, World w) {
        return new Location(w, g.x(), g.y(), g.z());
    }

    private Parts create(Grave g, World w) {
        Location at = base(g, w);
        Lantern data = (Lantern) Material.LANTERN.createBlockData();
        data.setHanging(false);
        Transformation t = new Transformation(new Vector3f(-LANTERN_SCALE / 2, 0.15f, -LANTERN_SCALE / 2),
                new AxisAngle4f(), new Vector3f(LANTERN_SCALE), new AxisAngle4f());
        BlockDisplay lantern = w.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(t);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2.0f);
            tag(d, g);
        });
        BlockDisplay glow = w.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(t);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(0xFFC94A));
            d.setViewRange(4.0f);
            d.setVisibleByDefault(false);
            tag(d, g);
        });
        TextDisplay label = !settings.label() ? null : w.spawn(at.clone().add(0, 1.15, 0), TextDisplay.class, d -> {
            d.text(Component.text(g.ownerName() + "'s grave", NamedTextColor.GOLD));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(0.5f);
            d.setShadowed(true);
            tag(d, g);
        });
        Interaction hitbox = w.spawn(at, Interaction.class, i -> {
            i.setInteractionWidth(0.8f);
            i.setInteractionHeight(1.1f);
            i.setResponsive(true);
            tag(i, g);
        });
        Parts parts = new Parts(lantern, glow, label, hitbox);
        showGlowTo(g, parts);
        return parts;
    }

    private void tag(Entity e, Grave g) {
        e.setPersistent(false);
        e.setSilent(true);
        e.setInvulnerable(true);
        e.getPersistentDataContainer().set(key, PersistentDataType.STRING, g.id().toString());
    }

    private void showGlowTo(Grave g, Parts parts) {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (g.mayOpen(p.getUniqueId(), now)) p.showEntity(plugin, parts.glow);
            else p.hideEntity(plugin, parts.glow);
        }
    }

    /** After a grant changes, or a player joins: who sees the glowing copy. */
    public void refreshViewers(Grave g) {
        Parts p = live.get(g.id());
        if (p != null && p.glow.isValid()) showGlowTo(g, p);
    }

    public void refreshViewers() {
        for (Grave g : graves.all()) refreshViewers(g);
    }

    private void light(Grave g, World w) {
        Block b = w.getBlockAt(g.blockX(), g.blockY(), g.blockZ());
        if (g.lightPlaced() && b.getType() != Material.LIGHT) {
            g.lightPlaced(false); // something else took the spot; it's theirs now
            graves.persist(g);
        }
        if (settings.light() && !g.lightPlaced() && (b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR)) {
            Light light = (Light) Material.LIGHT.createBlockData();
            light.setLevel(settings.lightLevel());
            b.setBlockData(light, false);
            g.lightPlaced(true);
            graves.persist(g);
        } else if (!settings.light() && g.lightPlaced() && b.getType() == Material.LIGHT) {
            b.setType(Material.AIR, false);
            g.lightPlaced(false);
            graves.persist(g);
        }
    }

    /** Takes the lantern down for good: the grave is empty or has moved. */
    public void remove(Grave g) {
        remove(g, true);
    }

    private void remove(Grave g, boolean lightToo) {
        Parts p = live.remove(g.id());
        if (p != null) p.all().forEach(Entity::remove);
        if (!lightToo || !g.lightPlaced()) return;
        World w = Bukkit.getWorld(g.world());
        if (w == null) return;
        Block b = w.getBlockAt(g.blockX(), g.blockY(), g.blockZ());
        if (b.getType() == Material.LIGHT) b.setType(Material.AIR, false);
        g.lightPlaced(false);
    }

    /** Every second: put back anything missing or moved, for graves in loaded chunks. */
    public void tick() {
        for (Grave g : graves.all()) spawn(g);
        refreshViewers(); // grants can run out
        live.keySet().removeIf(id -> {
            if (graves.get(id) != null) return false;
            live.get(id).all().forEach(Entity::remove);
            return true;
        });
    }

    /** On shutdown. The entities would vanish anyway; this just doesn't leave them for a plugin reload. */
    public void removeAll() {
        for (Parts p : live.values()) p.all().forEach(Entity::remove);
        live.clear();
    }
}
