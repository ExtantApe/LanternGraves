package com.extantape.lanterngraves.marker;

import com.extantape.lanterngraves.grave.Grave;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Who may see whose grave it is (owner, 2026-11-16, D231). The owner always sees their name. Everyone else sees it
 * only when the server's player groups allow the two to see each other, asked of Posse's ReachService. Without Posse,
 * or if Posse can't answer, others see "Grave". Fails closed.
 *
 * <p>The same answer decides every message that could carry the owner's name to another player: the refusal when a
 * grave isn't theirs, the shared graves in /grave list, and the notice that a grave was shared (K819). Those go
 * through {@link #nameFor} and the wording methods below, so the label and the chat text can't disagree.
 */
public final class GraveNames {
    /** One question, so the decision can be tested without a server. */
    @FunctionalInterface
    public interface Rules {
        boolean mayKnow(Player viewer, UUID owner, String world);
    }

    /** Posse's other question: may what this player sends reach that one? Asked before a grave is shared. */
    @FunctionalInterface
    public interface Reach {
        boolean mayReach(Player from, Player to);
    }

    /** Posse's answer to "may this player share a grave with that one?" */
    public enum Share {
        /** No Posse on this server: sharing works as it always has. */
        OPEN,
        YES,
        NO,
        /** Posse is installed but gave no answer: it failed, it's stopped or too old, or the other player is offline. */
        UNKNOWN
    }

    /** One reply for every name a player can't share with, so it never says whether that player exists. */
    public static final String CANT_SHARE = "You can't share a grave with that player.";

    private final Logger log;
    private volatile Rules rules;
    private volatile Reach reach;
    /** Whether a plugin called Posse is on this server at all, running or not. */
    private volatile boolean installed;
    private boolean warned;

    public GraveNames(Logger log) {
        this.log = log;
    }

    /** The only plugin name whose service is trusted. No older name is recognized (D196, D292, D295). */
    static final String PROVIDER = "Posse";

    /** Whether a plugin of this name is the one that decides who sees a grave's name. Exact match only. */
    public static boolean isProvider(String pluginName) {
        return PROVIDER.equals(pluginName);
    }

    /** Looks for Posse's service again: at start-up, and when plugins change. */
    public void connect(Plugin self) {
        rules = null;
        reach = null;
        installed = Bukkit.getPluginManager().getPlugin(PROVIDER) != null;
        if (!installed) {
            log.info("Posse isn't installed, so other players see a grave as just \"Grave\".");
            return;
        }
        try {
            var service = Bukkit.getServicesManager().load(com.extantape.posse.api.ReachService.class);
            if (service == null) {
                log.warning("Posse is installed but offers no ReachService, so other players see a grave as just \"Grave\".");
                return;
            }
            rules = service::mayKnow;
            reach = service::mayReach;
            log.info("Posse found: a grave shows its owner's name only to players the server's groups allow.");
        } catch (LinkageError e) {
            log.warning("This Posse can't say who may see a grave's name (it needs Posse 0.11.0 or newer), so other players see \"Grave\".");
        }
    }

    public boolean showName(Player viewer, Grave g) {
        return decide(viewer.getUniqueId(), g.owner(), rules, viewer, g.world());
    }

    /** The owner's name for a message to this viewer, or null if they may not know it. The label's answer. */
    public String nameFor(Player viewer, Grave g) {
        return showName(viewer, g) ? g.ownerName() : null;
    }

    /**
     * Whether a viewer may be told who shared these graves with them: only if they may know the owner from every one
     * of them. No graves, no name.
     */
    public boolean mayNameAll(Player viewer, java.util.Collection<Grave> graves) {
        if (graves.isEmpty()) return false;
        for (Grave g : graves) if (!showName(viewer, g)) return false;
        return true;
    }

    // ---------------------------------------------------------------- sharing a grave (/grave trust)

    /**
     * Whether {@code sender} may share a grave with {@code target}, and so may learn that they're here from a
     * suggestion or a reply. {@code target} is null for a player who isn't online. With Posse installed, anything
     * short of a yes from it is no answer, never a yes.
     */
    public Share mayShare(Player sender, Player target) {
        return share(installed, reach, sender, target);
    }

    /** The whole rule, testable. */
    Share share(boolean posseInstalled, Reach r, Player sender, Player target) {
        if (!posseInstalled) return Share.OPEN;
        if (r == null || target == null) return Share.UNKNOWN;
        try {
            return r.mayReach(sender, target) ? Share.YES : Share.NO;
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                log.warning("Posse couldn't say who may share a grave with whom, so nobody new can be added: " + e);
            }
            return Share.UNKNOWN;
        }
    }

    /**
     * Whether a /grave trust goes ahead. A player the sender already shares a grave with stays allowed when Posse
     * gives no answer, since the sender already knows them; a no from Posse is a no for everyone.
     */
    public static boolean shareAllowed(Share answer, boolean alreadyShared) {
        return switch (answer) {
            case OPEN, YES -> true;
            case NO -> false;
            case UNKNOWN -> alreadyShared;
        };
    }

    // ---------------------------------------------------------------- wording, with a name or without

    /** Told to a player who can't open a grave. {@code owner} is null when they may not know whose it is. */
    public static String refused(String owner, boolean accessEnded) {
        if (owner == null) return accessEnded ? "Your access to this grave has ended." : "This grave isn't yours.";
        return accessEnded ? "Your access to " + owner + "'s grave has ended." : "This grave belongs to " + owner + ".";
    }

    /** A line of /grave list for a grave someone shared with the reader. */
    public static String shared(String owner, int number, String where) {
        if (owner == null) return "You can collect a grave shared with you at " + where + ".";
        return "You can collect " + owner + "'s grave #" + number + " at " + where + ".";
    }

    /**
     * Told to a friend when graves are shared with them. {@code which} is "all" or a grave's number, and
     * {@code length} is how long it lasts in words, or null for until the owner takes it back.
     */
    public static String trusted(String owner, String which, String length) {
        boolean all = which.equalsIgnoreCase("all");
        String time = length != null ? " for " + length : all ? " until they stop sharing them" : " until they stop sharing it";
        if (owner == null) return "Someone let you collect " + (all ? "their graves" : "one of their graves") + time + ". /grave list shows where.";
        return owner + " let you collect " + (all ? "their graves" : "their grave #" + which) + time + ". /grave list shows where.";
    }

    /** The whole rule, testable: the owner yes; no rules no; otherwise the rules' answer, and no if they fail. */
    boolean decide(UUID viewerId, UUID owner, Rules r, Player viewer, String world) {
        if (viewerId.equals(owner)) return true;
        if (r == null) return false;
        try {
            return r.mayKnow(viewer, owner, world);
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                log.warning("Posse couldn't say who may see a grave's name, so others see \"Grave\": " + e);
            }
            return false;
        }
    }

    /** For tests. */
    void rules(Rules r) {
        this.rules = r;
    }

    /** For tests. */
    void reach(boolean posseInstalled, Reach r) {
        this.installed = posseInstalled;
        this.reach = r;
    }
}
