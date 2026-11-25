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
 */
public final class GraveNames {
    /** One question, so the decision can be tested without a server. */
    @FunctionalInterface
    public interface Rules {
        boolean mayKnow(Player viewer, UUID owner, String world);
    }

    private final Logger log;
    private volatile Rules rules;
    private boolean warned;

    public GraveNames(Logger log) {
        this.log = log;
    }

    /** Looks for Posse's service again: at start-up, and when plugins change. */
    public void connect(Plugin self) {
        rules = null;
        if (Bukkit.getPluginManager().getPlugin("Posse") == null) {
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
            log.info("Posse found: a grave shows its owner's name only to players the server's groups allow.");
        } catch (LinkageError e) {
            log.warning("This Posse can't say who may see a grave's name (it needs Posse 0.11.0 or newer), so other players see \"Grave\".");
        }
    }

    public boolean showName(Player viewer, Grave g) {
        return decide(viewer.getUniqueId(), g.owner(), rules, viewer, g.world());
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
}
