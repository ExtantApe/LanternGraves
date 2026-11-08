# Decisions

What the owner decided, and when. Don't reopen these without asking.

## From the design notes (before 2026-11-07)

- Graves stay locked to their owner and never disappear.
- The owner can give a friend access to one grave or to all their active graves with a command.
- The BlueMapHUD beacon is optional. Lantern Graves works fully without BlueMapHUD.
- It replaces AngelChest.

## 2026-11-07

- **Where items are stored:** in the plugin's own files, one per grave. The lantern is only a marker, so losing it never loses items.
- **The lantern:** whichever looks better, and both if that's cheap and helps. Built as both: a lantern made of display entities, which can't be griefed, plus an invisible light block where the spot is empty air, so it really glows. The light block never replaces a block, and `marker.light: false` turns it off.
- **Void and lava deaths:** the grave goes on the last safe ground the player stood on.
- **XP:** saved in full.
- **Friend access:** lasts until the owner revokes it. A length such as `30m` or `2h` can be given instead.
- **Inventory groups:** a grave pays out only in the Multiverse-Inventories group it came from.
- **Versions:** Minecraft 26.1 and up only, on Java 25, tested on 26.1.2, 26.2 and 26.3. No 1.21.x.
- **Paper only:** `paper-api` and `paper-plugin.yml`.
- **Kids first:** graves never let other players take a child's items. Only the owner can open a grave unless they grant access, and `lanterngraves.share` can be denied for a group so its members can't grant access at all.
- **Releases:** 0.x versions on GitHub Releases as soon as they're good. Modrinth, Hangar and Spigot wait for the owner's own code review.
- **Public:** the repo goes public with v0.1.0, after the pre-public audit and with the leak check in CI.
- **Sharing:** `lanterngraves.share` stays allowed for everyone, children included, so a child can let a parent or friend collect a grave they can't reach. Nobody can open another player's grave without the owner's `/grave trust`.
