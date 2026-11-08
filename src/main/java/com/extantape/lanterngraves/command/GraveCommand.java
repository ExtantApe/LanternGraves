package com.extantape.lanterngraves.command;

import com.extantape.lanterngraves.grave.Durations;
import com.extantape.lanterngraves.grave.Grant;
import com.extantape.lanterngraves.grave.Grave;
import com.extantape.lanterngraves.grave.GraveService;
import com.extantape.lanterngraves.grave.Xp;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static com.extantape.lanterngraves.grave.GraveService.tell;
import static com.extantape.lanterngraves.grave.GraveService.where;

/**
 * /grave for players: list your graves, collect one nearby, and give or take a friend's access.
 */
public final class GraveCommand {
    private final GraveService graves;

    public GraveCommand(GraveService graves) {
        this.graves = graves;
    }

    /** Runs a subcommand from its words, as typed after the command name. */
    public boolean handle(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            tell(sender, "Only players have graves. Admins: /lanterngraves list <player>.", NamedTextColor.RED);
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(p);
            case "collect" -> collect(p, args);
            case "trust" -> trust(p, args);
            case "untrust" -> untrust(p, args);
            default -> help(p, label);
        }
        return true;
    }

    private void help(Player p, String label) {
        tell(p, "/" + label + " list: your graves", NamedTextColor.GOLD);
        tell(p, "/" + label + " collect [number]: collect a grave you're standing next to", NamedTextColor.GOLD);
        tell(p, "/" + label + " trust <player> [number|all] [time]: let a friend collect a grave, until you untrust them or for a time like 30m or 2h", NamedTextColor.GOLD);
        tell(p, "/" + label + " untrust <player> [number|all]: take that access back", NamedTextColor.GOLD);
    }

    private void list(Player p) {
        List<Grave> mine = graves.of(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (mine.isEmpty()) {
            tell(p, "You have no graves.", NamedTextColor.GRAY);
        } else {
            tell(p, "Your graves:", NamedTextColor.GOLD);
            for (int i = 0; i < mine.size(); i++) {
                Grave g = mine.get(i);
                StringBuilder line = new StringBuilder("#" + (i + 1) + "  " + where(g) + "  " + g.itemCount() + " items");
                if (g.xp() > 0) line.append(", ").append(Xp.levelFor(g.xp())).append(" levels");
                line.append(", ").append(Durations.describe(now - g.created())).append(" ago");
                List<String> friends = g.grants().values().stream().filter(gr -> gr.activeAt(now)).map(Grant::name).toList();
                if (!friends.isEmpty()) line.append("  friends: ").append(String.join(", ", friends));
                p.sendMessage(net.kyori.adventure.text.Component.text(line.toString(), NamedTextColor.YELLOW)
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(contents(g))));
            }
        }
        List<Grave> shared = graves.all().stream()
                .filter(g -> g.settled() && !g.owner().equals(p.getUniqueId()) && g.mayOpen(p.getUniqueId(), now)).toList();
        for (Grave g : shared) {
            tell(p, "You can collect " + g.ownerName() + "'s grave #" + graves.numberOf(g) + " at " + where(g) + ".", NamedTextColor.AQUA);
        }
    }

    /** What's in a grave, grouped by item, for the hover text in /grave list. */
    private static net.kyori.adventure.text.Component contents(Grave g) {
        java.util.Map<String, Integer> byType = new java.util.TreeMap<>();
        g.items().values().forEach(i -> byType.merge(i.type(), i.amount(), Integer::sum));
        var text = net.kyori.adventure.text.Component.text().append(net.kyori.adventure.text.Component.text("In this grave:", NamedTextColor.GOLD));
        int shown = 0;
        for (var e : byType.entrySet()) {
            if (shown++ == 12) {
                text.appendNewline().append(net.kyori.adventure.text.Component.text("and " + (byType.size() - 12) + " more kinds", NamedTextColor.GRAY));
                break;
            }
            String name = e.getKey().toLowerCase(Locale.ROOT).replace('_', ' ');
            text.appendNewline().append(net.kyori.adventure.text.Component.text(e.getValue() + " x " + name, NamedTextColor.WHITE));
        }
        if (g.xp() > 0) text.appendNewline().append(net.kyori.adventure.text.Component.text(Xp.levelFor(g.xp()) + " levels of XP", NamedTextColor.WHITE));
        return text.build();
    }

    private void collect(Player p, String[] args) {
        long now = System.currentTimeMillis();
        double reach = graves.settings().reach();
        List<Grave> near = new ArrayList<>(graves.all().stream()
                .filter(g -> g.settled() && g.world().equals(p.getWorld().getName()))
                .filter(g -> distanceSq(p, g) <= reach * reach).toList());
        if (args.length >= 2) {
            Grave pick = numbered(p, p.getUniqueId(), args[1]);
            if (pick == null) return;
            if (!near.contains(pick)) {
                tell(p, "Grave #" + args[1] + " is at " + where(pick) + ". Stand next to its lantern.", NamedTextColor.YELLOW);
                return;
            }
            graves.collect(p, pick);
            return;
        }
        near.sort(java.util.Comparator.comparingDouble(g -> distanceSq(p, g)));
        Grave pick = near.stream().filter(g -> g.mayOpen(p.getUniqueId(), now)).findFirst().orElse(null);
        if (pick == null) {
            if (!near.isEmpty()) graves.collect(p, near.getFirst()); // explains whose it is
            else tell(p, "There's no grave within " + (int) reach + " blocks. /grave list shows where yours are.", NamedTextColor.YELLOW);
            return;
        }
        graves.collect(p, pick);
    }

    /** Distance to the grave or to its lantern, whichever is nearer (the lantern rises if its spot fills in). */
    private double distanceSq(Player p, Grave g) {
        double dx = p.getLocation().getX() - g.x(), dz = p.getLocation().getZ() - g.z();
        double dy = p.getLocation().getY() - g.y();
        double dy2 = p.getLocation().getY() - graves.markerY(g);
        return dx * dx + dz * dz + Math.min(dy * dy, dy2 * dy2);
    }

    private void trust(Player p, String[] args) {
        if (!p.hasPermission("lanterngraves.share")) {
            tell(p, "Your graves can only be opened by you on this server.", NamedTextColor.YELLOW);
            return;
        }
        if (args.length < 2) {
            tell(p, "/grave trust <player> [number|all] [time]", NamedTextColor.RED);
            return;
        }
        OfflinePlayer friend = known(p, args[1]);
        if (friend == null) return;
        String which = "all";
        long until = 0;
        for (int i = 2; i < args.length; i++) {
            long length = Durations.parse(args[i]);
            if (length > 0) until = System.currentTimeMillis() + length;
            else which = args[i];
        }
        List<Grave> targets = targets(p, which);
        if (targets == null) return;
        String name = friend.getName() == null ? args[1] : friend.getName();
        for (Grave g : targets) graves.grant(g, friend.getUniqueId(), name, until);
        String scope = which.equalsIgnoreCase("all") ? "all " + targets.size() + " of your graves" : "your grave #" + which;
        String time = until == 0 ? " until you untrust them" : " for " + Durations.describe(until - System.currentTimeMillis());
        tell(p, name + " can collect " + scope + time + ".", NamedTextColor.GREEN);
        Player online = friend.getPlayer();
        if (online != null) {
            tell(online, p.getName() + " let you collect " + (which.equalsIgnoreCase("all") ? "their graves" : "their grave #" + which) + time
                    + ". /grave list shows where.", NamedTextColor.AQUA);
        }
    }

    private void untrust(Player p, String[] args) {
        if (args.length < 2) {
            tell(p, "/grave untrust <player> [number|all]", NamedTextColor.RED);
            return;
        }
        OfflinePlayer friend = known(p, args[1]);
        if (friend == null) return;
        List<Grave> targets = targets(p, args.length >= 3 ? args[2] : "all");
        if (targets == null) return;
        int n = 0;
        for (Grave g : targets) if (graves.revoke(g, friend.getUniqueId())) n++;
        String name = friend.getName() == null ? args[1] : friend.getName();
        tell(p, n == 0 ? name + " didn't have access." : name + " can no longer collect " + n + " grave" + (n == 1 ? "" : "s") + ".", NamedTextColor.GREEN);
    }

    private List<Grave> targets(Player p, String which) {
        List<Grave> mine = graves.of(p.getUniqueId());
        if (mine.isEmpty()) {
            tell(p, "You have no graves.", NamedTextColor.YELLOW);
            return null;
        }
        if (which.equalsIgnoreCase("all")) return mine;
        Grave g = numbered(p, p.getUniqueId(), which);
        return g == null ? null : List.of(g);
    }

    private Grave numbered(CommandSender to, java.util.UUID owner, String text) {
        List<Grave> list = graves.of(owner);
        try {
            int n = Integer.parseInt(text.replace("#", ""));
            if (n >= 1 && n <= list.size()) return list.get(n - 1);
        } catch (NumberFormatException ignored) {
        }
        tell(to, "There's no grave #" + text + ". " + (list.isEmpty() ? "There are none." : "Use 1 to " + list.size() + "."), NamedTextColor.RED);
        return null;
    }

    private static OfflinePlayer known(CommandSender to, String name) {
        OfflinePlayer p = Bukkit.getPlayerExact(name);
        if (p == null) p = Bukkit.getOfflinePlayerIfCached(name);
        if (p == null) tell(to, "No player called " + name + " has been on this server.", NamedTextColor.RED);
        return p;
    }

    /** Suggestions for the word being typed, given the words so far. */
    public List<String> complete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        int count = graves.of(p.getUniqueId()).size();
        List<String> numbers = IntStream.rangeClosed(1, count).mapToObj(Integer::toString).toList();
        List<String> out = switch (args.length) {
            case 1 -> List.of("list", "collect", "trust", "untrust");
            case 2 -> args[0].equalsIgnoreCase("collect") ? numbers
                    : args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust")
                    ? Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> !n.equals(p.getName())).toList() : List.of();
            case 3 -> {
                List<String> l = new ArrayList<>(numbers);
                l.add("all");
                if (args[0].equalsIgnoreCase("trust")) l.addAll(List.of("30m", "1h", "1d"));
                yield l;
            }
            case 4 -> args[0].equalsIgnoreCase("trust") ? List.of("30m", "1h", "1d") : List.of();
            default -> List.of();
        };
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
