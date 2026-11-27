package com.extantape.posse.api;

import org.bukkit.entity.Player;

/** Compile-only copy of the part of Posse's ReachService that LanternGraves uses (Posse 0.11.0). */
public interface ReachService {
    boolean mayKnow(Player viewer, java.util.UUID owner, String world);

    /** Whether anything {@code from} says or sends can reach {@code to}. LanternGraves asks before a grave is shared. */
    boolean mayReach(Player from, Player to);
}
