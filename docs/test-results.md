# Test results

Run on 2026-11-07, with the v0.1.0 code, on fresh local Paper servers: 26.1.2 (build 74), 26.2 (build 135) and 26.3 (build 244), each jar checked against Paper's published SHA-256. Each suite gets its own new server, deleted when it's done. The bots are mineflayer clients speaking 1.21.11, reaching 26.x through ViaVersion and ViaBackwards 5.12.2. Every test counts every item, including inside shulker boxes and bundles, before and after.

| Suite | 26.1.2 | 26.2 | 26.3 |
| --- | --- | --- | --- |
| basic | 20/20 | 20/20 | 20/20 |
| hazards | 53/53 | 53/53 | 53/53 |
| rules | 29/29 | 29/29 | 29/29 |
| crash | 45/45 | 45/45 | 45/45 |
| mvinv | 16/16 | 16/16 | 16/16 |

Unit tests: 16/16 (`./gradlew build`), also in CI.

## Fixed in the tests during the run

The first full run had failures in the hazards suite, all from how the test set things up, none from the plugin. In each the grave held exactly what it should:

- **26.1.2, the bed in the Nether:** the bot collected one gold nugget more than it died with. The creeper tests had left the difficulty on easy and Nether mobs could spawn, so a mob killed by the bed's blast most likely dropped it. The bed test now runs on peaceful with no Nether mobs and clears loose items before collecting.
- **26.3, the void in the Nether:** lava from the terrain around the test platform flowed onto it and killed the bot before it collected. The platform is now a closed obsidian room.
- **26.3, the void in the End:** that leftover Nether grave was then taken for the End's. The void test now only looks at graves made during it.

The hazards results above are from the rerun with those fixes.

## What was mocked or worked around

- **No real game client.** The look of the lantern, its glow and light, and how collecting feels are untested.
- **26.3 movement:** a 1.21.11 bot behind ViaVersion 5.12.2 is kicked if it moves on its own there, so on 26.3 the bots are teleported. They walk on 26.1.2 and 26.2.
- **26.3 void after a dimension change:** the bot isn't ticked, so it never takes void damage. The test kills it at that spot with `out_of_world` damage, which is the same cause the plugin handles, and prints a line when it does.
- **Dying during a logout:** a small test-only plugin (`src/testHook`, never shipped) kills a tagged player as they quit, standing in for a combat-log plugin.
- **Exact crash points:** a test-only system property, `-Dlanterngraves.test.halt=<point>`, halts the JVM at each point where a grave file and the player's save briefly disagree. Without the property the code does nothing.
- **Not tested:** land-claim plugins, combat-log plugins, other plugins changing death drops, items on the cursor or in the crafting grid, Folia, real players.
