# 26.x audit

LanternGraves targets Paper 26.1 and up on Java 25. It's compiled against the Paper 26.1.2 API (build 74, the oldest stable 26.x build; 26.1 and 26.1.1 only ever had alpha builds) with `-Xlint:deprecation` and `-Xlint:removal`, and builds with no warnings. Checked on 2026-11-07 against the API jars for 26.1.2, and run on Paper 26.1.2 (build 74), 26.2 (build 135) and 26.3 (build 244).

## Modernised

- **`paper-plugin.yml`** instead of `plugin.yml`. Multiverse-Inventories is an optional `BEFORE` dependency with `join-classpath: true`, so LanternGraves can read its groups.
- **Commands through Brigadier**, registered in `LifecycleEvents.COMMANDS`. The client checks the shape of `/grave` and `/lanterngraves` and gets suggestions as you type: subcommands, online players, grave numbers and lengths like `30m`.
- **Java 25** toolchain and release level.
- **Server-side footing.** `Player#isOnGround` is deprecated because the client reports it. Safe ground is now judged from the server's own position and velocity, and sampled every second as well as on movement.
- **Display entities** for the lantern: a `BlockDisplay` everyone sees, a glowing copy shown only to the owner and their friends with `Player#showEntity` and `hideEntity`, a `TextDisplay` label and an `Interaction` hitbox. All are marked not persistent, so they're never saved with the world and can't be left behind.
- **Adventure** text everywhere: chat, the action bar distance hint, hover text in `/grave list`, and the death screen through `PlayerDeathEvent#deathScreenMessageOverride`.
- **`Player#setLastDeathLocation`** points the recovery compass, and BlueMinimap's death marker, at the grave.

## Kept, and why

- **Item storage is `ItemStack#serializeAsBytes`.** It's Paper's own format: it keeps every data component (enchantments, names, shulker and bundle contents, trims, maps) and records the data version, so an item saved on 26.1 is upgraded by the game's data fixers when it's loaded on a later version. Nothing in LanternGraves reads or writes `ItemMeta`.
- **One YAML file per grave** rather than a database. Each is small, can be read without the plugin, and is written atomically with a checksum. A damaged one is refused and kept, never overwritten (see the README).
- **The Bukkit scheduler.** Paper only, not Folia, so the main-thread scheduler is the simple, correct choice. All grave changes happen on the main thread.
- **No dialog or inventory menu.** Collecting is one right-click, and what doesn't fit stays in the grave. A menu would add a second way to move items, and a second place for things to go wrong, without making collection easier. `/grave list` shows each grave's contents on hover instead.
- **Material names in grave files** are only labels for people reading the file. The item bytes are what pays out.

## Not checked

- Folia: not supported.
- A real 26.x game client. The bots speak 1.21.11 and reach 26.x through ViaVersion and ViaBackwards 5.12.2.
