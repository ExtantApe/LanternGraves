package com.extantape.lanterngraves.grave;

import java.util.UUID;

/** A friend's access to one grave. {@code until} is epoch millis, or 0 for "until revoked". */
public record Grant(UUID friend, String name, long until) {
    public boolean activeAt(long now) {
        return until == 0 || now < until;
    }
}
