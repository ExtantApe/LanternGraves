// Whose grave it is: a grave's owner is named to another player only when Posse says they may know (D231, K819).
// That holds for the label and for every message: the refusal when a grave isn't theirs, the graves shared with
// them in /grave list, and the notice that a grave was shared. Everyone else gets the same words with no name.
//
// Posse here is a stand-in (src/posseDouble, built by ./gradlew posseDoubleJar): a viewer with the scoreboard tag
// lg_circle may know, nobody else may, and the console can make it fail. The test hook stops and starts it, as if
// Posse had gone and come back, and the labels must follow at once, ahead of the next one-second refresh, saying
// "Grave" for as long as it's gone. The real Posse's own suite checks
// the label with this jar; this suite checks that LanternGraves follows whatever answer it's given, and fails closed.
// With no Posse installed at all, rules.mjs checks the same refusals.
//
// Made-up players: Alder owns the graves, Birch shares a circle with Alder, Cedar is a stranger.
import { Server, newServer, bot, sleep, check, summary, holdings, graveFiles, holdingsTally, graveTally, sameTally, rightClickGrave, markers, respawned, loaded, POSSE, HOOK } from './harness.mjs'
import { kit, calm, loose } from './basic.mjs'

const owned = (s, owner) => graveFiles(s).filter(g => g.owner === owner && !g.pending)
async function waitGraves(s, owner, n, ms = 10000) {
  const end = Date.now() + ms
  while (Date.now() < end) { if (owned(s, owner).length >= n) return owned(s, owner); await sleep(200) }
  return owned(s, owner)
}
const sameGrave = (a, b) => b && sameTally(graveTally([a]), graveTally([b])).ok && a.xp === b.xp
const fileOf = (s, g) => graveFiles(s).find(x => x.file === g.file)

/** Teleports a bot and makes sure it arrived: right after a respawn, a teleport can be lost on a busy server. */
async function at(s, b, name, x, z) {
  for (let i = 0; i < 4; i++) {
    await s.run(`execute in minecraft:overworld run tp ${name} ${x} -60 ${z}`)
    await sleep(900); loaded(b); await sleep(400)
    const pos = await s.data(name, 'Pos')
    if (pos && Math.abs(pos[0] - x) < 0.01 && Math.abs(pos[2] - z) < 0.01) return
    console.log(`  ${name} wasn't at ${x}, ${z} yet; teleporting again`)
  }
  throw new Error(`${name} never arrived at ${x}, ${z}`)
}

/**
 * Runs something, then returns the chat lines the bot got while it ran. With {@code expect}, waits up to 8 s for a
 * line that matches, so a slow moment on the server or the bot doesn't read as silence.
 */
async function during(b, act, wait = 900, expect = null) {
  const from = b.chat_log.length
  await act()
  const end = Date.now() + (expect ? 8000 : 0)
  while (Date.now() < end && !b.chat_log.slice(from).some(m => expect.test(m))) await sleep(50)
  await sleep(wait)
  return b.chat_log.slice(from)
}
const named = lines => lines.some(m => /Alder/.test(m))
const said = (lines, re) => lines.some(m => re.test(m))
const show = lines => lines.join(' | ').slice(0, 200)
const NOT_YOURS = /^This grave isn't yours\.$/, BELONGS = /^This grave belongs to Alder\.$/
const ENDED = /^Your access to this grave has ended\.$/, ENDED_NAMED = /^Your access to Alder's grave has ended\.$/

/** The words on the label the bot can see nearest to it: every text display within 3.5 blocks, as sent to it. */
const near = b => markers(b, 'text_display').filter(e => e.position.distanceTo(b.entity.position) < 3.5)
const labelNear = b => JSON.stringify(near(b).map(e => e.metadata))
const labels = b => near(b).length
const seesName = b => labels(b) === 1 && /Alder/.test(labelNear(b))
const seesGrave = b => labels(b) === 1 && /Grave/.test(labelNear(b)) && !/Alder/.test(labelNear(b))

/**
 * LanternGraves refreshes every label once a second, and sends the owner's distance hint to their action bar in the
 * same pass. So the moment the owner's bot gets a hint, the next refresh is a full second away. Sending a console
 * command right then, and timing how long a label takes to change, tells an immediate change (a few ticks) from one
 * that waited for the refresh (about a second). Returns the milliseconds taken, or -1 if it didn't change in 3 s.
 */
async function timedFromRefresh(s, ownerBot, cmd, changed) {
  await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('the owner got no distance hint in 5 s, so the refresh can\'t be timed')), 5000)
    ownerBot._client.once('action_bar', () => { clearTimeout(t); resolve() }) // the raw packet: mineflayer has no event for it
  })
  const t0 = Date.now()
  s.send(cmd)
  while (Date.now() - t0 < 3000) { if (changed()) return Date.now() - t0; await sleep(10) }
  return -1
}
/** Well under the one-second refresh, well over a few ticks and the trip to the bot. */
const AT_ONCE = 600

export async function run(s) {
  check('control: LanternGraves found the Posse stand-in', s.log.some(l => /Posse found: a grave shows its owner's name only to players the server's groups allow/.test(l)))
  const a = await bot('Alder')
  const b = await bot('Birch')
  const c = await bot('Cedar')
  await calm(s)
  await s.run('tag Birch add lg_circle')

  await at(s, a, 'Alder', 100.5, 100.5)
  await kit(s, 'Alder')
  const kit1 = holdingsTally(await holdings(s, 'Alder'))
  await s.ask('kill Alder', 1500); await respawned(a)
  await at(s, a, 'Alder', 120.5, 100.5)
  await s.run('clear Alder', 'give Alder diamond 7', 'give Alder emerald_block 3')
  const kit2 = holdingsTally(await holdings(s, 'Alder'))
  await s.ask('kill Alder', 1500); await respawned(a)
  const two = await waitGraves(s, 'Alder', 2)
  check('two graves for Alder', two.length === 2)
  const g1 = two.find(g => g.pos[0] === 100.5), g2 = two.find(g => g.pos[0] === 120.5)
  if (!g1 || !g2) throw new Error('the two graves aren\'t where the test put them: ' + two.map(g => g.pos).join(' / '))
  await at(s, a, 'Alder', 110.5, 110.5)

  // ------------------------------------------------------------ a viewer Posse refuses
  console.log('\n# A stranger: Posse refuses')
  await at(s, c, 'Cedar', 102.5, 100.5)
  await sleep(1500)
  let got = await during(c, () => rightClickGrave(c), 900, NOT_YOURS)
  check('a stranger right-clicking is refused with no name', said(got, NOT_YOURS) && !named(got) && sameGrave(g1, fileOf(s, g1)), show(got))
  got = await during(c, () => c.chat('/grave collect'), 900, NOT_YOURS)
  check('a stranger using /grave collect is refused with no name', said(got, NOT_YOURS) && !named(got) && sameGrave(g1, fileOf(s, g1))
    && sameTally(holdingsTally(await holdings(s, 'Cedar')), {}).ok, show(got))
  check('a stranger\'s label says Grave, with no name', seesGrave(c), labels(c) + ' labels')
  got = await during(c, () => c.chat('/grave list'), 900, /You have no graves/)
  check('a stranger\'s /grave list names nobody', said(got, /You have no graves/) && !named(got), show(got))
  got = await during(c, () => c.chat('/lanterngraves list'), 1500)
  check('a stranger gets nothing from the admin list', !named(got) && !said(got, /\d+ graves?/), show(got))
  const tab = await c.tabComplete('/lanterngraves list ', true, false, 2000).catch(() => [])
  check('a stranger gets no owner names from the admin command\'s suggestions', !JSON.stringify(tab).includes('Alder'), JSON.stringify(tab).slice(0, 120))

  // ------------------------------------------------------------ a viewer Posse allows
  console.log('\n# A circle member: Posse allows')
  await at(s, b, 'Birch', 102.5, 100.5)
  await sleep(1500)
  got = await during(b, () => rightClickGrave(b), 900, BELONGS)
  check('a circle member right-clicking is refused and told whose it is', said(got, BELONGS) && sameGrave(g1, fileOf(s, g1)), show(got))
  got = await during(b, () => b.chat('/grave collect'), 900, BELONGS)
  check('a circle member using /grave collect is refused and told whose it is', said(got, BELONGS) && sameGrave(g1, fileOf(s, g1))
    && sameTally(holdingsTally(await holdings(s, 'Birch')), {}).ok, show(got))
  check('a circle member\'s label has the name', seesName(b), labels(b) + ' labels')

  // ------------------------------------------------------------ sharing a grave
  console.log('\n# Sharing')
  await at(s, c, 'Cedar', 122.5, 100.5)
  await at(s, b, 'Birch', 122.5, 101.5)
  const SHARED = /^You can collect a grave shared with you at 120, -60, 100 in world\.$/
  got = await during(c, () => a.chat('/grave trust Cedar 2 10s'), 300, /let you collect/)
  check('a stranger a grave is shared with is told, with no name', said(got, /^Someone let you collect one of their graves for /) && !named(got), show(got))
  got = await during(c, () => c.chat('/grave list'), 300, /You can collect/)
  check('and their /grave list shows it with no name', said(got, SHARED) && !named(got), show(got))
  await sleep(10500)
  got = await during(c, () => rightClickGrave(c), 900, /access/)
  check('when that access runs out, the refusal has no name', said(got, ENDED) && !named(got) && sameGrave(g2, fileOf(s, g2)), show(got))

  got = await during(b, () => a.chat('/grave trust Birch 2 10s'), 300, /let you collect/)
  check('a circle member a grave is shared with is told by whom', said(got, /^Alder let you collect their grave #2 for /), show(got))
  got = await during(b, () => b.chat('/grave list'), 300, /You can collect/)
  check('and their /grave list names the owner', said(got, /^You can collect Alder's grave #2 at 120, -60, 100 in world\.$/), show(got))
  await sleep(10500)
  got = await during(b, () => rightClickGrave(b), 900, /access/)
  check('when that access runs out, the refusal names the owner', said(got, ENDED_NAMED) && sameGrave(g2, fileOf(s, g2)), show(got))

  // ------------------------------------------------------------ Posse not answering
  console.log('\n# Posse fails')
  await s.run('possedouble throw')
  await sleep(1500)
  got = await during(b, () => rightClickGrave(b), 900, /access/)
  // Birch's access to this grave ran out above, so the refusal is the access one, now without the name.
  check('while Posse fails, a circle member is refused with no name', said(got, ENDED) && !named(got) && sameGrave(g2, fileOf(s, g2)), show(got))
  got = await during(b, () => b.chat('/grave collect'), 900, /access/)
  check('and the same from /grave collect', said(got, ENDED) && !named(got), show(got))
  check('and their label says Grave', seesGrave(b), labels(b) + ' labels')
  got = await during(b, () => a.chat('/grave trust Birch 2 10s'), 300, /let you collect/)
  check('and a grave shared with them is announced with no name', said(got, /^Someone let you collect one of their graves for /) && !named(got), show(got))
  got = await during(b, () => b.chat('/grave list'), 300, /You can collect/)
  check('and listed with no name', said(got, SHARED) && !named(got), show(got))
  a.chat('/grave untrust Birch 2'); await sleep(600)
  await at(s, a, 'Alder', 121.5, 100.5)
  got = await during(a, () => rightClickGrave(a), 900, /Collected/)
  check('the owner collects their grave in full while Posse fails', sameTally(kit2, holdingsTally(await holdings(s, 'Alder'))).ok && !fileOf(s, g2), show(got))
  await s.run('clear Alder')
  await s.run('possedouble answer')
  await at(s, b, 'Birch', 102.5, 100.5)
  await sleep(1500)
  got = await during(b, () => rightClickGrave(b), 900, BELONGS)
  check('control: once Posse answers again, the circle member is told whose it is', said(got, BELONGS) && seesName(b), show(got))

  // ------------------------------------------------------------ Posse going and coming back
  console.log('\n# Posse stops, then starts again')
  // Alder stands 14 blocks from grave 1, so the action bar hint arrives every second; Birch stands at the grave.
  await at(s, a, 'Alder', 110.5, 110.5)
  await at(s, c, 'Cedar', 102.5, 101.5)
  await sleep(1500)
  check('control: before Posse stops, the circle member sees the name and the stranger sees Grave', seesName(b) && seesGrave(c), `${labels(b)} and ${labels(c)} labels`)
  let ms = await timedFromRefresh(s, a, 'lgtest disable Posse', () => seesGrave(b))
  check('when Posse stops, a circle member\'s label turns to Grave at once, ahead of the next refresh', ms >= 0 && ms < AT_ONCE, ms + ' ms')
  await sleep(1500)
  check('control: LanternGraves noticed Posse stop', s.log.some(l => /Posse is installed but offers no ReachService/.test(l)))
  check('while Posse is stopped, both labels stay Grave', seesGrave(b) && seesGrave(c), `${labels(b)} and ${labels(c)} labels`)
  got = await during(b, () => rightClickGrave(b), 900, NOT_YOURS)
  check('with Posse stopped, a circle member is refused with no name', said(got, NOT_YOURS) && !named(got) && sameGrave(g1, fileOf(s, g1)), show(got))
  got = await during(b, () => b.chat('/grave collect'), 900, NOT_YOURS)
  check('and the same from /grave collect', said(got, NOT_YOURS) && !named(got), show(got))
  check('the owner\'s label has their name while Posse is stopped', await (async () => { await at(s, a, 'Alder', 101.5, 100.5); await sleep(1500); return seesName(a) })(), labels(a) + ' labels')
  await at(s, a, 'Alder', 110.5, 110.5)
  await sleep(1200)

  ms = await timedFromRefresh(s, a, 'lgtest enable Posse', () => seesName(b))
  check('when Posse starts again, a circle member\'s label shows the name at once', ms >= 0 && ms < AT_ONCE, ms + ' ms')
  await sleep(1500)
  check('and the stranger\'s label still says Grave', seesGrave(c), labels(c) + ' labels')
  got = await during(b, () => rightClickGrave(b), 900, BELONGS)
  check('and the circle member is told whose it is again', said(got, BELONGS), show(got))
  got = await during(c, () => rightClickGrave(c), 900, NOT_YOURS)
  check('and the stranger still isn\'t', said(got, NOT_YOURS) && !named(got), show(got))

  // ------------------------------------------------------------ the owner
  console.log('\n# The owner')
  await s.run('lgtest disable Posse')
  await at(s, a, 'Alder', 101.5, 100.5)
  await sleep(1500)
  got = await during(a, () => rightClickGrave(a), 900, /Collected/)
  check('the owner collects their grave in full with Posse stopped', sameTally(kit1, holdingsTally(await holdings(s, 'Alder'))).ok && !fileOf(s, g1), show(got))
  check('nothing dropped', (await loose(s)).items === 0)
  a.quit(); b.quit(); c.quit()
  await sleep(500)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const s = new Server(newServer('names', { plugins: [POSSE, HOOK] }))
  await s.start()
  try { await run(s) } finally {
    // The stand-in fails on purpose; LanternGraves logs that once, as a warning, and nothing else should go wrong.
    const errors = s.errors().filter(l => !/WARN.*Posse couldn't say who may see a grave's name.*the Posse double was told to fail/.test(l))
    check('no LanternGraves errors in the log', errors.length === 0, errors[0]?.slice(0, 300))
    await s.stop(); s.discard()
  }
  summary()
}
