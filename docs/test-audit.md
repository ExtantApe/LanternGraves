# Test audit

Kept by this repo's owning session (Minecraft: Plugins Lead), per `~/Code/CLAUDE.md`, "Test self-audits". Newest first.

## 2026-11-10: Rewild (0.1.2)

Gap reported by the server session's audit: LanternGraves registered no Rewild KeepProvider, so with both installed Rewild reset nothing. Fixed in 0.1.2, and covered:

| Promise | Covered by |
| --- | --- |
| Every chunk holding a grave is reported to Rewild, once, with a reason | unit `RewildKeepsTest.everyChunkWithAGraveIsKeptOnceWithAReason`, `blockCoordinatesRoundDownIntoTheRightChunk`; bots `rewild.mjs` ("Rewild names the grave as the reason the chunk is kept") |
| While a grave file is damaged, LanternGraves refuses to answer, so Rewild resets nothing (fail closed) | unit `whileAGraveFileIsDamagedItThrowsSoRewildResetsNothing` |
| `rewild.keep-graves: false` means no report, said at startup, and Rewild resets nothing | bots `rewild.mjs` |
| Control: no graves, nothing kept; a far chunk has no grave reason | unit `controlNoGravesKeepsNothing`; bots |

`rewild.mjs` passed 8/8 on Paper 26.1.2 with Rewild 0.2.1.

**Skipped:** a damaged grave file in the bot test (the unit test covers the refusal; Rewild's side, turning a throw into "nothing resets", is Rewild's own tested behavior).


## 2026-11-09: first full audit (at 0.1.0, fixed in 0.1.1)

LanternGraves holds players' items, so data comes first: nothing lost, nothing copied. Every bot suite counts every item before and after, shulker and bundle contents included. Suites: `basic`, `extras`, `hazards`, `rules`, `crash`, `mvinv`, `griefprevention` on throwaway Paper 26.1.2, 26.2 and 26.3; unit tests in `src/test`.

| Promise | Covered by |
| --- | --- |
| **Nothing drops: inventory, armour, off hand, other drops, all XP** | `basic` ("no items or XP orbs drop", "the grave holds exactly what was carried"), unit `xpMatchesTheWikiTable` |
| **…including the cursor and the input slots of an open work screen** | **new** `extras`: the crafting grid and cursor, and an open anvil. **Both failed on 0.1.0**: the items were dropped. Fixed in 0.1.1 for every work screen (`WorkScreens`, unit `WorkScreensTest`); the grid and the anvil are bot-tested, the other screens share the code |
| **Items another plugin keeps stay with the player; items it adds go in the grave** | **new** `extras`, with the test hook playing the other plugin. Passed on 0.1.0 already |
| Everything comes back exactly, to its own slot; what doesn't fit stays; nothing on the ground | `basic`; unit `itemsGoBackToTheirOwnSlots`, `takenSlotsMoveToFreeStorageAndTheRestStays`, `anArmourSlotIsNeverFilledWithSomethingElse`, `roundTripKeepsEverything` |
| Locked to the owner; friends by `/grave trust`, one grave, expiry, revoke | `rules`, `griefprevention`; unit `grantsExpire` |
| Safe from the world (explosions, lava, fire, water, pistons, sand, `/kill`, WorldEdit, hoppers); the lantern comes back | `hazards` |
| A safe spot after lava or the void, in every dimension | `hazards` |
| Inventory groups respected, with the settle wait | `mvinv` |
| **Crash-safe: kill -9 at every step of a death and a collection; damaged files reported, copied, never overwritten** | `crash` (18 timings plus 8 half-finished, halts at each disagreement point); unit `everyTruncationIsCaught`, `anyChangedCharacterIsCaught`, `garbageIsCaught`, `leftoverTempFileFromACrashIsIgnored`, `damagedFileIsCopiedKeptAndNeverOverwritten` |
| Vanilla left alone: `keep_inventory`, Curse of Vanishing, `disabled-worlds` | `rules` |
| Easy to find: death message, light block, distance in words | `basic`; unit `compassWords` |

**Gaps added this audit:** the two in bold marked new.

**Bug found:** 0.1.0 dropped the cursor and the input slots of open work screens (crafting grid, anvil, and the rest) at death: data loss. Fixed in 0.1.1, with the release notes telling 0.1.0 users to upgrade. It wasn't installed on any of the owner's servers.

**Skipped, with reasons:**
- How the lantern, glow and outline look by day and night, and whether collecting feels clear: real client only, in the README's "What the bots can't show".
- Each work screen other than the crafting grid and the anvil (grindstone, smithing table, stonecutter, loom, cartography table, enchanting table, villager trades, beacon): they go through the same `WorkScreens` code. Its slot map is unit-tested for the anvil, the smithing table, both crafting grids and containers that must be left alone; the other screens' slot numbers come from Minecraft's screen layouts and aren't tested one by one. A bot case for each would add a lot of setup for little.
