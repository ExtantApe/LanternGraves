package com.extantape.mayor.api;

import org.bukkit.entity.Player;

/** Compile-only copy of the part of Mayor's ReachService that LanternGraves uses (Mayor 0.7.0). */
public interface ReachService {
    boolean mayKnow(Player viewer, java.util.UUID owner, String world);
}
