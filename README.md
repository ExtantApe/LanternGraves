# LanternGraves

When a player dies, their items and XP go into a grave marked by a lantern, locked to them, until they come back for it. For Paper 26.1 and up.

**Status:** early, version 0.1.2. Every test below passed with bots on fresh throwaway Paper servers. No real player has used it yet, and nobody has looked at the lantern in a real game client: whether it's easy to spot by day and by night, and whether collecting feels clear, are still unknown. See [Testing](#testing).

## What it does

- **Nothing drops.** On death, everything that would have dropped goes into a grave: inventory, armour and off hand, anything else in the drops, and items left on the cursor or in the input slots of a screen that was open, such as the crafting grid, a crafting table, an anvil or a smithing table. So does all of the player's XP. 0.1.0 dropped those cursor and screen items on the ground; 0.1.1 fixes that. Of the screens, the crafting grid and the anvil are bot-tested; the others use the same code.
- **Everything comes back exactly.** Enchantments, names, damage, shulker box and bundle contents, trims and maps are all kept. Right-click the lantern and each item goes back to the slot it came from if that slot is free, or to a free slot if not. Whatever doesn't fit stays in the grave. Nothing is ever dropped on the ground.
- **Graves never disappear, and only the owner can open one.** Other players can't open, collect, break or drain it with hoppers. The owner can let a friend collect a grave with `/grave trust`.
- **Safe from the world.** The items aren't in the world at all. They're in a file in the plugin folder, so explosions, lava, fire, water, pistons, sand, `/kill` and WorldEdit can't reach them. If the lantern is damaged or removed, it comes back within a second.
- **A safe place for the lantern.** After a death in lava or the void, the grave goes on the last solid ground the player stood on, in any dimension. If they hadn't stood anywhere in that world, it goes on solid ground at the world spawn.
- **Easy to find.**
  - The death screen says where the grave is.
  - The lantern really glows: an invisible light block goes in the spot, but only if it's empty air.
  - The owner and their friends see a gold outline through walls.
  - Within 100 blocks, the action bar says how far the grave is and which way, in words: "Your grave: 23 blocks northeast, 4 down".
  - The recovery compass points at the newest grave, and so does BlueMinimap's death marker if BlueMinimap is installed.
- **Inventory groups respected.** With Multiverse-Inventories, a grave pays out only in the inventory group it came from. For 3 seconds after joining or changing world or game mode, collecting waits, so an inventory swap can finish first.
- **Crash-safe.** A grave file is written in full and flushed before it replaces the old one, with a checksum on the last line. If the server dies partway through a death or a collection, the player's own save decides which way it goes on their next join, so nothing is lost or copied. A damaged grave file is reported loudly, copied, and never overwritten or removed.

It changes no blocks except that optional light block, and it uses no NMS.

### Left as vanilla

- **`keep_inventory` on:** no grave. The player keeps everything, as in vanilla.
- **Curse of Vanishing:** cursed items vanish on death, as in vanilla, and don't go in the grave.
- **Another plugin's choices:** items another plugin keeps on death stay with the player, and items another plugin adds to the drops go in the grave. Both are tested (`extras.mjs`).
- **`disabled-worlds`:** no graves there. Items drop as in vanilla.

## Install

1. Use Paper 26.1.2 or later, on Java 25.
2. Put `LanternGraves-0.1.1.jar` in `plugins/` and restart.
3. If AngelChest or another grave plugin is installed, remove it. Two grave plugins would both try to take the drops.
4. Optional: Multiverse-Inventories 5 for per-group graves, and BlueMinimap to see the grave on the minimap.

If Multiverse-Inventories is installed, leave its `apply-playerdata-on-join` off (the default). If it's on, LanternGraves warns at startup, because after a crash that setting can replace the saved inventory that LanternGraves checks.

## Commands

| Command | What it does |
| --- | --- |
| `/grave` or `/grave list` | Your graves, numbered from oldest, with where they are, what's in them (hover) and who else can open them. Also any graves friends have shared with you. |
| `/grave collect [number]` | Collects a grave within 4 blocks. Right-clicking the lantern does the same. |
| `/grave trust <player> [number\|all] [time]` | Lets a friend collect one grave, or all your current graves. Until you untrust them, or for a time such as `30m`, `2h` or `1d`. |
| `/grave untrust <player> [number\|all]` | Takes that access back. |
| `/lanterngraves list [player]` | Admins: every grave, or one player's. |
| `/lanterngraves tp <player> <number>` | Admins: go to a grave. |
| `/lanterngraves move <player> <number>` | Admins: move a grave's lantern to where you stand, for a grave somewhere unreachable. It still pays out only in its own inventory group. |
| `/lanterngraves damaged` | Admins: grave files that wouldn't load, and why. |
| `/lanterngraves reload` | Admins: reload `config.yml`. |

Admins can't open other players' graves. A friend who collects a grave gets the items and the XP, and the owner is told.

## Permissions

| Permission | Default | |
| --- | --- | --- |
| `lanterngraves.use` | everyone | `/grave`. Graves are made and collected whatever this is set to. |
| `lanterngraves.share` | everyone | `/grave trust`. Deny it for a group, such as children, whose graves only they should open. |
| `lanterngraves.admin` | op | `/lanterngraves`, and a message on join if a grave file is damaged. |

## Config

`plugins/LanternGraves/config.yml`:

| Setting | Default | |
| --- | --- | --- |
| `marker.light` | `true` | The invisible light block at the lantern. Only placed in empty air, and removed when the grave is emptied. `false` changes no blocks at all. |
| `marker.light-level` | `12` | 1 to 15. |
| `marker.label` | `true` | "Name's grave / Right-click to collect" above the lantern. |
| `collect.reach` | `4.0` | How close `/grave collect` needs you to be, in blocks. |
| `collect.settle-seconds` | `3` | How long collecting waits after joining or changing world or game mode. |
| `point-death-location-at-grave` | `true` | Points the recovery compass and BlueMinimap's death marker at the newest grave. |
| `distance-hint.enabled`, `distance-hint.range` | `true`, `100` | The action bar distance and direction. |
| `disabled-worlds` | `[]` | Worlds with no graves. |
| `rewild.keep-graves` | `true` | Tells [Rewild](https://github.com/ExtantApe/Rewild), if it's installed, which chunks hold a grave, so it never resets them. While a grave file can't be read, LanternGraves won't answer, and Rewild resets nothing in that world. Turned off, Rewild resets nothing while both run. Read at startup. |

## Files

Each grave is one YAML file in `plugins/LanternGraves/graves/`, named after the grave's id. Items are stored as Paper's own item data in base64. Each item also has its type and amount in plain text, for anyone reading the file. Don't edit these files: the checksum on the last line makes LanternGraves refuse an edited file. A file that won't load is copied to `plugins/LanternGraves/damaged/` and left where it is, and its items can't be collected until someone fixes it by hand.

## Testing

The bot tests in `test/` run on fresh Paper servers on 127.0.0.1, with mineflayer bots. They count every item before and after each test, including what's inside shulker boxes and bundles, so a test fails if anything is lost or copied.

| Suite | What it covers |
| --- | --- |
| `basic.mjs` | Die, walk back, collect. A full inventory. |
| `extras.mjs` | Items on the cursor, in the crafting grid and in an open anvil. Items another plugin keeps, and items another plugin adds to the drops. |
| `rewild.mjs` | With Rewild installed: a grave's chunk is kept, named "a grave (LanternGraves)" by `/rewild why`, and a far chunk isn't. With `rewild.keep-graves` off, Rewild resets nothing and says why. Needs a Rewild jar in the jars folder. |
| `hazards.mjs` | Lava, including lava flowing over the grave later. The void in the overworld, Nether and End, and with no safe ground known. TNT, creepers, a charged creeper, an end crystal, a respawn anchor and a bed in the Nether. Fire, water, a piston, falling sand, `/kill`, a WorldEdit cut with entities, and hoppers. |
| `rules.mjs` | Strangers can't open, collect or break a grave. Friend access: one grave only, expiry and revoke. Several graves per player. Five players dying in the same tick. A grave in an unloaded chunk. `keep_inventory`, Curse of Vanishing, dying during a logout, logging out on the death screen and during a collection. |
| `crash.mjs` | A clean restart. `kill -9` at 18 timings around a death and a collection, plus 8 around a half-finished one. A halt at each exact point where a grave and a player's save briefly disagree. A truncated file and a garbage file. |
| `mvinv.mjs` | Multiverse-Inventories groups, the settle wait, and a grave moved into another group. |
| `griefprevention.mjs` | GriefPrevention 16.18.7 on 26.1.2 and 26.2, 16.18.8 on 26.3, with made-up claims. A death inside someone else's claim, on its edge, in your own claim and in the wilderness: the grave lands where you died, only you can collect it (not the claim's owner, nor someone they trust), and the light block goes in and comes out. |

Results are in [docs/test-results.md](docs/test-results.md).

What the bots can't show:

- **How it looks.** Whether the lantern, its glow and its light are easy to find by day and night, whether the display's full brightness reads as a lit lantern, and whether collecting feels clear. Only a real client can tell.
- **Real movement on 26.3.** There, a 1.21.11 bot behind ViaVersion 5.12.2 is kicked if it moves on its own, so on 26.3 the bots are teleported. On 26.1.2 and 26.2 they really walk. For the same reason, the open-anvil case runs on 26.1.2 and 26.2 only.
- **Natural void damage right after a dimension change on 26.3.** A bot that has just changed dimension isn't ticked, so there the test kills it below the world with `out_of_world` damage instead. That's the same spot and cause the plugin acts on, and the test prints when it does this.
- **A combat-log plugin.** Dying during a logout uses a small test-only plugin that kills a tagged player as they quit. It's built separately and never shipped.

To run them: `./gradlew build hookJar`, then in `test/`: `npm install`, `LG_TEST_BASE=<empty folder> node fetch.mjs` (it checks each Paper jar against Paper's published SHA-256), and `LG_TEST_BASE=… LG_TEST_EULA_FROM=<folder with an accepted eula.txt> LG_TEST_VERSION=26.3 node basic.mjs`. Each run makes one server on port 25611 and deletes it when it's done.

## Building

`./gradlew build` needs Java 25. It compiles against the Paper 26.1.2 API and runs the unit tests: the file format, every truncation and edit of a file, damaged files, slot placement, XP arithmetic, lengths and compass words. The jar is `build/libs/LanternGraves-<version>.jar`. A `v*` tag builds it and attaches it to a GitHub Release.

More: [decisions](docs/decisions.md), [26.x audit](docs/26x-audit.md), [BlueMinimap beacon](docs/blueminimap-beacon.md).

## License

MIT. See [LICENSE](LICENSE).
