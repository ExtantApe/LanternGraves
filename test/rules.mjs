// The lock and friends, many graves at once, and the vanilla rules LanternGraves leaves alone.
import { Server, newServer, bot, sleep, check, summary, holdings, graveFiles, holdingsTally, graveTally, addTallies, sameTally, rightClickGrave, markers, respawned, loaded, HOOK } from './harness.mjs'
import { kit, calm, loose } from './basic.mjs'

const owned = (s, owner) => graveFiles(s).filter(g => g.owner === owner && !g.pending)
async function waitGraves(s, owner, n, ms = 10000) {
  const end = Date.now() + ms
  while (Date.now() < end) { if (owned(s, owner).length >= n) return owned(s, owner); await sleep(200) }
  return owned(s, owner)
}
const sameGrave = (a, b) => b && sameTally(graveTally([a]), graveTally([b])).ok && a.xp === b.xp
const fileOf = (s, g) => graveFiles(s).find(x => x.file === g.file)

async function at(s, b, name, x, z) {
  await s.run(`execute in minecraft:overworld run tp ${name} ${x} -60 ${z}`)
  await sleep(900); loaded(b); await sleep(400)
}

export async function run(s) {
  const a = await bot('Alder')
  const b = await bot('Birch')
  await calm(s)

  // ------------------------------------------------------------ two graves for Alder
  console.log('\n# The lock')
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

  // Birch tries everything on grave 1.
  await at(s, b, 'Birch', 102.5, 100.5)
  await rightClickGrave(b)
  check('a stranger right-clicking is refused and told whose it is', b.chat_log.some(m => /belongs to Alder/.test(m)) && sameGrave(g1, fileOf(s, g1)))
  b.chat('/grave collect'); await sleep(800)
  check('a stranger using /grave collect is refused', sameGrave(g1, fileOf(s, g1)) && sameTally(holdingsTally(await holdings(s, 'Birch')), {}).ok)
  const box = markers(b, 'interaction').sort((x, y) => x.position.distanceTo(b.entity.position) - y.position.distanceTo(b.entity.position))[0]
  for (let i = 0; i < 5; i++) { b.attack(box); await sleep(150) }
  await sleep(800)
  check('a stranger hitting the lantern breaks nothing', sameGrave(g1, fileOf(s, g1)) && markers(b, 'interaction').length > 0 && markers(b, 'block_display').length >= 1)
  check('a stranger only sees the plain lantern, not the owner\'s glowing copy', markers(b, 'block_display').filter(e => e.position.distanceTo(box.position) < 1).length === 1,
    markers(b, 'block_display').filter(e => e.position.distanceTo(box.position) < 1).length + ' displays')

  // Access to grave 1 only.
  a.chat('/grave trust Birch 1'); await sleep(800)
  check('the owner is told the friend has access', a.chat_log.some(m => /Birch can collect your grave #1 until you untrust them/.test(m)), a.chat_log.slice(-1)[0])
  check('the friend is told', b.chat_log.some(m => /Alder let you collect their grave #1/.test(m)), b.chat_log.slice(-1)[0])
  await at(s, b, 'Birch', 122.5, 100.5)
  await rightClickGrave(b)
  check('access to grave 1 doesn\'t open grave 2', sameGrave(g2, fileOf(s, g2)) && b.chat_log.slice(-1)[0]?.includes('belongs to Alder'))

  // Expiry on grave 2.
  a.chat('/grave trust Birch 2 3s'); await sleep(4500)
  await rightClickGrave(b)
  check('an expired grant is refused and says so', b.chat_log.some(m => /access to Alder's grave has ended/.test(m)))
  check('an expired grant never empties a grave', sameGrave(g2, fileOf(s, g2)))
  // Revoke on grave 2.
  a.chat('/grave trust Birch 2'); await sleep(600)
  a.chat('/grave untrust Birch 2'); await sleep(600)
  await rightClickGrave(b)
  check('a revoked grant is refused', sameGrave(g2, fileOf(s, g2)))

  // The friend collects grave 1.
  await at(s, b, 'Birch', 102.5, 100.5)
  await rightClickGrave(b)
  const bHas = holdingsTally(await holdings(s, 'Birch'))
  check('the friend collects grave 1 in full', sameTally(kit1, bHas).ok && !fileOf(s, g1), sameTally(kit1, bHas).diff)
  check('the owner is told who collected it', a.chat_log.some(m => /Birch collected .* from your grave/.test(m)))
  await at(s, a, 'Alder', 122.5, 100.5)
  await rightClickGrave(a)
  check('the owner collects grave 2', sameTally(kit2, holdingsTally(await holdings(s, 'Alder'))).ok && !fileOf(s, g2))
  await s.run('clear Alder', 'clear Birch')

  // ------------------------------------------------------------ many graves
  console.log('\n# Many graves')
  for (const [i, x] of [[1, 140.5], [2, 150.5], [3, 160.5]]) {
    await at(s, a, 'Alder', x, 100.5)
    await s.run(`give Alder iron_ingot ${i * 10}`)
    await s.ask('kill Alder', 1200); await respawned(a)
  }
  a.chat('/grave list'); await sleep(800)
  const listed = a.chat_log.filter(m => /^#\d+ +\d+, -60, 100 in world/.test(m))
  check('/grave list shows all three graves, numbered', listed.length === 3 && listed[0].startsWith('#1') && listed[2].startsWith('#3'), listed.join(' | '))
  for (const x of [140.5, 150.5, 160.5]) { await at(s, a, 'Alder', x + 1.5, 100.5); await rightClickGrave(a) }
  check('all three collected', (await holdings(s, 'Alder')) && holdingsTally(await holdings(s, 'Alder')).iron_ingot === 60 && owned(s, 'Alder').length === 0)
  await s.run('clear Alder')

  const names = ['Cedar', 'Daisy', 'Elm', 'Fern', 'Gorse']
  const crowd = []
  for (const n of names) crowd.push(await bot(n))
  await s.run(...names.map((n, i) => `execute in minecraft:overworld run tp ${n} ${200.5 + i} -60 100.5`))
  await sleep(1000)
  for (const c of crowd) loaded(c)
  const kits = {}
  for (const n of names) { await kit(s, n); kits[n] = holdingsTally(await holdings(s, n)) }
  await s.ask('kill @a[name=!Alder,name=!Birch]', 2500)
  let allGood = true
  const diffs = []
  for (const n of names) {
    const gs = await waitGraves(s, n, 1)
    const t = sameTally(kits[n], graveTally(gs))
    if (gs.length !== 1 || !t.ok) { allGood = false; diffs.push(`${n}: ${gs.length} graves ${t.diff}`) }
  }
  check('five players dying in the same tick each get their own exact grave', allGood, diffs.join('; '))
  check('and nothing dropped', (await loose(s)).items === 0)
  for (const c of crowd) c.quit()
  await sleep(500)

  // A death whose grave goes into an unloaded chunk: the void, far from the last safe ground.
  await at(s, a, 'Alder', 2000.5, 2000.5)
  await sleep(1200)
  await s.run('give Alder gold_ingot 9', 'execute in minecraft:overworld run tp Alder 6000 -200 6000')
  await sleep(800); loaded(a)
  let far = (await waitGraves(s, 'Alder', 1, 8000))[0]
  if (!far) { await s.run('damage Alder 1000 minecraft:out_of_world'); far = (await waitGraves(s, 'Alder', 1))[0] }
  await respawned(a)
  check('a grave can go into a chunk nobody has loaded', far && far.pos[0] > 1990 && far.pos[0] < 2010, String(far?.pos))
  await at(s, a, 'Alder', 2001.5, 2000.5)
  await sleep(1500)
  check('its lantern appears when someone arrives', markers(a, 'interaction').some(e => e.position.distanceTo(a.entity.position) < 4))
  await rightClickGrave(a)
  check('and it can be collected', holdingsTally(await holdings(s, 'Alder')).gold_ingot === 9)
  await s.run('clear Alder')

  // ------------------------------------------------------------ vanilla rules
  console.log('\n# Vanilla rules')
  await at(s, a, 'Alder', 100.5, 140.5)
  await s.run('gamerule keep_inventory true')
  await kit(s, 'Alder')
  const kept = await holdings(s, 'Alder')
  await s.ask('kill Alder', 1500); await respawned(a)
  check('keep_inventory on: no grave, inventory kept as vanilla does', owned(s, 'Alder').length === 0 && sameTally(holdingsTally(kept), holdingsTally(await holdings(s, 'Alder'))).ok)
  await s.run('gamerule keep_inventory false', 'clear Alder')
  await at(s, a, 'Alder', 100.5, 140.5)
  await s.run('give Alder diamond_pickaxe[enchantments={vanishing_curse:1,efficiency:5}] 1', 'give Alder apple 5')
  await s.ask('kill Alder', 1500); await respawned(a)
  const vg = (await waitGraves(s, 'Alder', 1))[0]
  check('Curse of Vanishing: the cursed item is gone, as in vanilla; the rest is in the grave', vg && !graveTally([vg]).diamond_pickaxe && graveTally([vg]).apple === 5 && (await loose(s)).items === 0)
  await at(s, a, 'Alder', 101.5, 140.5); await rightClickGrave(a); await s.run('clear Alder')
  check('and the vanishing test grave is collected', owned(s, 'Alder').length === 0)
  const before = new Set(graveFiles(s).map(g => g.file))
  const fresh = () => graveFiles(s).filter(g => g.owner === 'Alder' && !before.has(g.file))

  // Death at the moment of logging out.
  console.log('\n# Logging out')
  await kit(s, 'Alder')
  const kitQ = holdingsTally(await holdings(s, 'Alder'))
  // As a combat-log plugin does: the test hook kills the player inside their logout.
  await s.run('tag Alder add die_on_quit')
  a.quit()
  await sleep(2500)
  const a2 = await bot('Alder')
  await sleep(1500)
  check('control: the hook really killed them on logout', s.log.some(l => /Grave .* for Alder at 101, -60, 140|Grave .* for Alder at 100, -60, 140/.test(l)) && fresh().length === 1, fresh().length + ' new graves')
  const ledger = addTallies(holdingsTally(await holdings(s, 'Alder')), graveTally(fresh()))
  check('death while logging out: nothing lost or copied', sameTally(kitQ, ledger).ok && (await loose(s)).items === 0, sameTally(kitQ, ledger).diff)
  // Logging out mid-collection.
  const lg = fresh()[0]
  if (lg) {
    await at(s, a2, 'Alder', lg.pos[0] + 1.5, lg.pos[2])
    await sleep(3200)
    const box2 = markers(a2, 'interaction').sort((x, y) => x.position.distanceTo(a2.entity.position) - y.position.distanceTo(a2.entity.position))[0]
    a2.activateEntityAt(box2, box2.position); a2.activateEntity(box2); a2.quit()
    await sleep(2500)
    const a3 = await bot('Alder')
    await sleep(1500)
    const l2 = addTallies(holdingsTally(await holdings(s, 'Alder')), graveTally(fresh()))
    check('logging out while collecting: nothing lost or copied', sameTally(kitQ, l2).ok && (await loose(s)).items === 0, sameTally(kitQ, l2).diff)
    a3.quit()
  } else {
    check('death while logging out left a grave to test collection with', false, 'the bot had already left, so no grave: see the check above')
  }
  // Dying, then logging out from the death screen before respawning.
  const a4 = await bot('Alder')
  await s.run('gamerule immediate_respawn false', 'clear Alder', 'give Alder copper_ingot 33')
  const before2 = new Set(graveFiles(s).map(g => g.file))
  s.send('kill Alder'); await sleep(400); a4.quit()
  await sleep(2500)
  const a5 = await bot('Alder')
  await sleep(1500)
  if (a5.health <= 0) { a5.respawn?.(); await sleep(1500) }
  const ds = graveFiles(s).filter(g => g.owner === 'Alder' && !before2.has(g.file))
  check('logging out on the death screen: one grave with everything, nothing lost', ds.length === 1 && graveTally(ds).copper_ingot === 33 && !holdingsTally(await holdings(s, 'Alder')).copper_ingot,
    `${ds.length} graves`)
  await s.run('gamerule immediate_respawn true')
  a5.quit()
  b.quit()
  await sleep(500)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const s = new Server(newServer('rules', { plugins: [HOOK] }))
  await s.start()
  try { await run(s) } finally { check('no LanternGraves errors in the log', s.errors().length === 0, s.errors()[0]?.slice(0, 300)); await s.stop(); s.discard() }
  summary()
}
