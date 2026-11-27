# Test audit

Kept by this repo's owning session (Minecraft: Plugins Lead), per `~/Code/CLAUDE.md`, "Test self-audits". Newest first.

## 2026-11-26: who is told whose grave it is (0.3.1)

Bug reported against 0.3.0: a refused `/grave collect` or right-click answered "This grave belongs to" and the owner's name to anyone, whatever Posse decided. The label followed Posse; the messages didn't. The owner decided on 2026-11-26 that only a child's circle sees their name on their grave. Every place the plugin reads an owner's name was checked:

| Where the name goes | Before | Now |
| --- | --- | --- |
| Refusal on a right-click or `/grave collect` | to anyone | Posse's answer; otherwise "This grave isn't yours." |
| Refusal after access ran out | to anyone whose access ran out | Posse's answer; otherwise "Your access to this grave has ended." |
| Shared graves in `/grave list` | to anyone the grave was shared with | Posse's answer; otherwise "a grave shared with you" |
| The message that a grave was shared | to the player it was shared with | Posse's answer for every grave shared; otherwise "Someone" |
| The label | Posse's answer already | unchanged |
| `/lanterngraves list`, `move` and their suggestions | admins only | unchanged, and now tested for a player who isn't an admin |
| The grave file and the server log | not shown to players | unchanged |
| The death message, the action bar hint, the compass and BlueMapMini's death marker | the owner only | unchanged |

| Promise | Covered by |
| --- | --- |
| **A viewer Posse refuses gets no name** from any message | **new** unit `NameLeakTest.aViewerPosseRefusesGetsNoNameInTheRefusal`, `theSharedListNamesTheOwnerOnlyWhenPosseAllows`, `theSharingNoticeNamesTheOwnerOnlyWhenPosseAllows`; bots `names` ("A stranger: Posse refuses", "Sharing") |
| **A viewer Posse allows still gets the name** | **new** unit `aViewerPosseAllowsStillGetsTheNameInTheRefusal` and the two above; bots `names` ("A circle member: Posse allows", "Sharing") |
| **The owner is always named to themselves, and collects** | **new** unit `theOwnerIsAlwaysNamedToThemselves`; bots `names` (the owner collects while Posse fails and after it stops), `rules`, `basic` |
| **Posse missing, failing, too old or stopped: no name (fail closed)** | **new** unit `withoutPosseOrWithAPosseThatFailsTheRefusalHasNoName`; bots `names` ("Posse fails, then stops") and `rules` (no Posse installed) |
| One grave Posse refuses keeps the name out of a message about several | **new** unit `oneGravePosseRefusesKeepsTheNameOutOfANoticeAboutSeveral` |
| A player who isn't an admin gets no names from `/lanterngraves` | **new** bots `names` (the command and its suggestions) |
| **When Posse stops or starts, labels follow at once, and say "Grave" while it's gone** (commit 96426b1, 2026-11-21, which changed no test) | **new** bots `names` ("Posse stops, then starts again"): the change is timed from the moment of a one-second refresh, so a label that waited for the next refresh takes about a second and fails |
| New code that reads an owner's name gets looked at | **new** unit `everyReadOfAnOwnersNameIsAccountedFor`: it lists each file that reads the name and why that's safe, and fails when the list changes |

**Gaps added this audit:** every row above marked new. The label one came from the 14-day sweep: 96426b1 made a Posse stop or start re-render labels at once, with no test. It's now timed. With those two refresh calls taken out of a scratch build, the two timed checks failed on Paper 26.1.2 (1007 ms and 1003 ms, against 106 ms and 62 ms with them in; the limit is 600 ms).

**Shown to fail on 0.3.0:** the `names` suite scored 26/38 and the updated `rules` suite 24/29 on the 0.3.0 jar, on each of Paper 26.1.2, 26.2 and 26.3. The unit tests call methods 0.3.0 doesn't have, so there they don't compile.

**Mocked:** Posse, in the `names` suite, is a test-only plugin with Posse's name (`src/posseDouble`). A player tagged `lg_circle` may know; nobody else may; the console can make it fail, and the test hook plugin stops and starts it.

**Skipped, with reasons:**
- The real Posse with these messages. They ask the same `GraveNames` question as the label, which Posse's own suite checks against the real plugin.
- The timed label check with the refresh calls removed was run on 26.1.2 only. It's the same code on each version, and the check itself passes on all three.
- `extras`, `hazards`, `crash`, `mvinv` and `griefprevention` weren't rerun: the change is in wording and in who is asked, and none of them cover either.

**Left for the lead to decide:** `/grave trust` suggests every online player's name, and answers differently for a name that has and hasn't been on the server. Neither is a grave owner's name, so they're outside this fix.

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
