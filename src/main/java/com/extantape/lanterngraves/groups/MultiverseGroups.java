package com.extantape.lanterngraves.groups;

import org.bukkit.GameMode;
import org.mvplugins.multiverse.inventories.MultiverseInventoriesApi;
import org.mvplugins.multiverse.inventories.profile.group.WorldGroup;
import org.mvplugins.multiverse.inventories.share.Sharables;

import java.util.List;

/**
 * Multiverse-Inventories 5. Two worlds share an inventory if they're the same world or some group
 * holds both and shares the inventory. This class is only loaded when that plugin is enabled.
 */
public final class MultiverseGroups implements InventoryGroups {
    private final MultiverseInventoriesApi api = MultiverseInventoriesApi.get();

    @Override
    public boolean sameInventory(String deathWorld, String deathMode, String here, GameMode mode) {
        if (api.getInventoriesConfig().getEnableGamemodeShareHandling() && !deathMode.equals(mode.name())) return false;
        if (deathWorld.equals(here)) return true;
        for (WorldGroup g : api.getWorldGroupManager().getGroupsForWorld(here)) {
            if (g.containsWorld(deathWorld) && sharesInventory(g)) return true;
        }
        return false;
    }

    private static boolean sharesInventory(WorldGroup g) {
        return g.getApplicableShares().contains(Sharables.INVENTORY) && g.getApplicableShares().contains(Sharables.ARMOR)
                && g.getApplicableShares().contains(Sharables.OFF_HAND);
    }

    @Override
    public String describe(String world) {
        List<String> names = api.getWorldGroupManager().getGroupsForWorld(world).stream()
                .filter(MultiverseGroups::sharesInventory).map(WorldGroup::getName).toList();
        return names.isEmpty() ? world : String.join("/", names);
    }

    @Override
    public String crashWarning() {
        if (api.getInventoriesConfig().getApplyPlayerdataOnJoin()) {
            return "Multiverse-Inventories has apply-playerdata-on-join turned on. After a crash, it may replace the "
                    + "inventory Lantern Graves checks on join, so a grave being filled or collected at the moment of a "
                    + "crash could be settled the wrong way. Turn that option off to keep the crash guarantee.";
        }
        return null;
    }
}
