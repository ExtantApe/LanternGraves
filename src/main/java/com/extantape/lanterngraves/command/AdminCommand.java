package com.extantape.lanterngraves.command;

import com.extantape.lanterngraves.LanternGravesPlugin;
import com.extantape.lanterngraves.grave.Grave;
import com.extantape.lanterngraves.grave.GraveService;
import com.extantape.lanterngraves.store.GraveStore;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

import static com.extantape.lanterngraves.grave.GraveService.tell;
import static com.extantape.lanterngraves.grave.GraveService.where;

/** /lanterngraves for admins. Nothing here opens or empties a grave. */
public final class AdminCommand {
    private final LanternGravesPlugin plugin;
    private final GraveService graves;

    public AdminCommand(LanternGravesPlugin plugin, GraveService graves) {
        this.plugin = plugin;
        this.graves = graves;
    }

    /** Runs a subcommand from its words, as typed after the command name. */
    public boolean handle(CommandSender sender, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender, args);
            case "tp" -> tp(sender, args);
            case "move" -> move(sender, args);
            case "reload" -> {
                plugin.reload();
                tell(sender, "LanternGraves config reloaded.", NamedTextColor.GREEN);
            }
            case "damaged" -> damaged(sender);
            default -> {
                tell(sender, "/" + label + " list [player]: graves, everyone's or one player's", NamedTextColor.GOLD);
                tell(sender, "/" + label + " tp <player> <number>: go to a grave", NamedTextColor.GOLD);
                tell(sender, "/" + label + " move <player> <number>: move a grave's lantern to where you stand", NamedTextColor.GOLD);
                tell(sender, "/" + label + " damaged: grave files that wouldn't load", NamedTextColor.GOLD);
                tell(sender, "/" + label + " reload: reload config.yml", NamedTextColor.GOLD);
            }
        }
        return true;
    }

    private void list(CommandSender s, String[] args) {
        List<Grave> list;
        if (args.length >= 2) {
            OfflinePlayer who = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (who == null) {
                tell(s, "No player called " + args[1] + " has been on this server.", NamedTextColor.RED);
                return;
            }
            list = graves.of(who.getUniqueId());
        } else {
            list = graves.all().stream().filter(Grave::settled).toList();
        }
        long waiting = graves.all().stream().filter(g -> !g.settled()).count();
        tell(s, list.size() + " grave" + (list.size() == 1 ? "" : "s") + (waiting > 0 ? ", plus " + waiting + " waiting for a player to rejoin after a crash" : "") + ".", NamedTextColor.GOLD);
        for (Grave g : list) {
            tell(s, g.ownerName() + " #" + graves.numberOf(g) + "  " + where(g) + "  " + g.itemCount() + " items, " + g.xp() + " xp", NamedTextColor.YELLOW);
        }
    }

    private Grave pick(CommandSender s, String[] args) {
        if (args.length < 3) {
            tell(s, "Name a player and a grave number.", NamedTextColor.RED);
            return null;
        }
        OfflinePlayer who = Bukkit.getOfflinePlayerIfCached(args[1]);
        List<Grave> list = who == null ? List.of() : graves.of(who.getUniqueId());
        try {
            int n = Integer.parseInt(args[2].replace("#", ""));
            if (n >= 1 && n <= list.size()) return list.get(n - 1);
        } catch (NumberFormatException ignored) {
        }
        tell(s, args[1] + " has no grave #" + args[2] + ".", NamedTextColor.RED);
        return null;
    }

    private void tp(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        Grave g = pick(s, args);
        if (g == null) return;
        World w = Bukkit.getWorld(g.world());
        if (w == null) {
            tell(s, "World " + g.world() + " isn't loaded.", NamedTextColor.RED);
            return;
        }
        p.teleport(new Location(w, g.x(), g.y(), g.z()));
    }

    private void move(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        Grave g = pick(s, args);
        if (g == null) return;
        graves.move(g, p.getLocation());
        tell(s, "Moved " + g.ownerName() + "'s grave to " + where(g) + ". It still pays out only in "
                + graves.groups().describe(g.deathWorld()) + ".", NamedTextColor.GREEN);
    }

    private void damaged(CommandSender s) {
        List<GraveStore.Damaged> list = plugin.damaged();
        if (list.isEmpty()) {
            tell(s, "Every grave file loaded.", NamedTextColor.GREEN);
            return;
        }
        for (GraveStore.Damaged d : list) {
            tell(s, d.file().getFileName() + ": " + d.reason() + (d.copy() == null ? " (no copy could be made)" : " (copy: " + d.copy().getFileName() + ")"), NamedTextColor.RED);
        }
    }

    /** Suggestions for the word being typed, given the words so far. */
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("list", "tp", "move", "damaged", "reload").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("list", "tp", "move").contains(args[0].toLowerCase(Locale.ROOT))) {
            return graves.all().stream().map(Grave::ownerName).distinct().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
