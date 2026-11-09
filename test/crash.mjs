// Restarts, kill -9 at every point around a death and a collection, and damaged grave files.
// The ledger after each crash: what the player holds plus what their graves hold equals what they had.
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { Server, newServer, bot, sleep, check, summary, holdings, graveFiles, holdingsTally, graveTally, addTallies, sameTally, rightClickGrave, respawned, loaded, markers } from './harness.mjs'
import { kit, calm, loose } from './basic.mjs'

const DEATH_DELAYS = [0, 1, 3, 6, 10, 20, 35, 60, 100, 200]
const COLLECT_DELAYS = [0, 2, 5, 10, 20, 40, 80, 150]

async function rejoin(s, name) {
  const b = await bot(name)
  await sleep(1500)
  if (b.health <= 0) { await respawned(b) }
  loaded(b)
  await sleep(500)
  return b
}

async function restart(s) {
  await s.start()
  await calm(s)
}

async function at(s, b, name, x, z) {
  await s.run(`execute in minecraft:overworld run tp ${name} ${x} -60 ${z}`)
  await sleep(900); loaded(b); await sleep(300)
}

const mine = (s, name, except) => graveFiles(s).filter(g => g.owner === name && !except.has(g.file))

export async function run(s) {
  let a = await bot('Alder')
  let b = await bot('Birch')
  await calm(s)

  // ------------------------------------------------------------ clean restart
  console.log('\n# A clean restart')
  await at(s, a, 'Alder', 30.5, 30.5)
  await kit(s, 'Alder')
  const k = holdingsTally(await holdings(s, 'Alder'))
  await s.ask('kill Alder', 1500); await respawned(a)
  a.chat('/grave trust Birch 1'); await sleep(800)
  const g = graveFiles(s)[0]
  a.quit(); b.quit(); await sleep(500)
  await s.stop()
  await restart(s)
  const after = graveFiles(s)[0]
  check('the grave file survives a restart unchanged', after && after.text === g.text)
  check('the friend\'s access is still in it', after && /grants:/.test(after.text) && /name: Birch/.test(after.text))
  b = await rejoin(s, 'Birch')
  await at(s, b, 'Birch', 32.5, 30.5)
  await sleep(1500)
  check('the lantern is back after the restart', markers(b, 'interaction').length === 1 && (await s.count('@e[type=block_display]')) === 2)
  await sleep(2000)
  await rightClickGrave(b)
  check('the friend collects after the restart, in full', sameTally(k, holdingsTally(await holdings(s, 'Birch'))).ok && graveFiles(s).length === 0)
  await s.run('clear Birch')
  b.quit()

  // ------------------------------------------------------------ kill -9 around a death
  console.log('\n# kill -9 around a death')
  a = await rejoin(s, 'Alder')
  for (const d of DEATH_DELAYS) {
    const old = new Set(graveFiles(s).map(x => x.file))
    await at(s, a, 'Alder', 40.5 + d / 10, 40.5)
    await kit(s, 'Alder')
    const want = holdingsTally(await holdings(s, 'Alder'))
    await s.ask('save-all flush', 1500)
    s.send('kill Alder')
    await sleep(d)
    await s.kill9()
    await restart(s)
    a = await rejoin(s, 'Alder')
    await sleep(500)
    const graves = mine(s, 'Alder', old)
    const held = holdingsTally(await holdings(s, 'Alder'))
    const t = sameTally(want, addTallies(held, graveTally(graves)))
    check(`kill -9 ${d} ms after a death: nothing lost or copied (${graves.length ? 'grave kept' : 'death undone'})`, t.ok && graves.every(x => !x.pending) && (await loose(s)).items === 0,
      t.diff + (graves.some(x => x.pending) ? ' pending left' : ''))
    await s.run('clear Alder')
  }

  // ------------------------------------------------------------ kill -9 around a collection
  for (const half of [false, true]) {
    console.log(`\n# kill -9 around a${half ? ' half-finished' : ''} collection`)
    for (const d of COLLECT_DELAYS) {
      const old = new Set(graveFiles(s).map(x => x.file))
      await at(s, a, 'Alder', 60.5 + d / 10, half ? 70.5 : 60.5)
      await kit(s, 'Alder')
      const kitT = holdingsTally(await holdings(s, 'Alder'))
      await s.ask('kill Alder', 1500); await respawned(a)
      const fill = half ? Array.from({ length: 30 }, (_, i) => `item replace entity Alder ${i < 9 ? 'hotbar.' + i : 'inventory.' + (i - 9)} with oak_planks 64`) : []
      await s.run('clear Alder', ...fill)
      const planks = holdingsTally(await holdings(s, 'Alder'))
      const gr = mine(s, 'Alder', old)[0]
      await at(s, a, 'Alder', gr.pos[0] + 1.5, gr.pos[2])
      await sleep(3200) // the respawn's settle time
      await s.ask('save-all flush', 1500)
      const box = markers(a, 'interaction').sort((x, y) => x.position.distanceTo(a.entity.position) - y.position.distanceTo(a.entity.position))[0]
      a.activateEntityAt(box, box.position); a.activateEntity(box)
      await sleep(d)
      await s.kill9()
      await restart(s)
      a = await rejoin(s, 'Alder')
      await sleep(500)
      const graves = mine(s, 'Alder', old)
      const held = holdingsTally(await holdings(s, 'Alder'))
      const t = sameTally(addTallies(kitT, planks), addTallies(held, graveTally(graves)))
      const how = graves.length === 0 ? 'all collected' : sameTally(graveTally(graves), kitT).ok ? 'collection undone' : 'partly collected'
      check(`kill -9 ${d} ms into a${half ? ' half-finished' : ''} collection: nothing lost or copied (${how})`, t.ok && graves.every(x => !x.pending), t.diff)
      // Tidy: collect what's left, then clear.
      if (graves.length) { await s.run('clear Alder'); await at(s, a, 'Alder', graves[0].pos[0] + 1.5, graves[0].pos[2]); await sleep(3200); await rightClickGrave(a) }
      await s.run('clear Alder')
    }
  }
  a.quit(); await sleep(500)

  // ------------------------------------------------------------ exact crash points
  // The JVM halts, as kill -9 would, at each point where a grave and a player's save disagree for a moment.
  console.log('\n# Halting at each exact point')
  const points = [
    ['death-written', 'Dropped unfinished grave', 'the death is undone; their save still has the items'],
    ['death-saved', 'Recovered grave', 'the grave stands; their save has the empty inventory'],
    ['collect-written', 'Undid an interrupted collection', 'the items stay in the grave'],
    ['collect-saved', 'Finished an interrupted collection', 'the items are theirs and leave the grave'],
  ]
  for (const [point, logLine, meaning] of points) {
    await s.stop()
    s.jvm = [`-Dlanterngraves.test.halt=${point}`]
    await restart(s)
    a = await rejoin(s, 'Alder')
    const old = new Set(graveFiles(s).map(x => x.file))
    await at(s, a, 'Alder', 120.5, 120.5)
    await kit(s, 'Alder')
    const want = holdingsTally(await holdings(s, 'Alder'))
    if (point.startsWith('death')) {
      await s.ask('save-all flush', 1500)
      s.send('kill Alder')
    } else {
      await s.ask('kill Alder', 1500); await respawned(a)
      const gr = mine(s, 'Alder', old)[0]
      await at(s, a, 'Alder', gr.pos[0] + 1.5, gr.pos[2])
      await sleep(3200)
      await s.ask('save-all flush', 1500)
      const box = markers(a, 'interaction').sort((x, y) => x.position.distanceTo(a.entity.position) - y.position.distanceTo(a.entity.position))[0]
      a.activateEntityAt(box, box.position); a.activateEntity(box)
    }
    const halted = await Promise.race([s.exited.then(() => true), sleep(20000).then(() => false)])
    check(`control: the server halted at ${point}`, halted && s.log.some(l => l.includes('test halt at ' + point)))
    if (!halted) await s.kill9()
    s.jvm = []
    const from = s.log.length
    await restart(s)
    a = await rejoin(s, 'Alder')
    await sleep(800)
    const graves = mine(s, 'Alder', old)
    const t = sameTally(want, addTallies(holdingsTally(await holdings(s, 'Alder')), graveTally(graves)))
    check(`halt at ${point}: ${meaning}`, t.ok && graves.every(x => !x.pending) && s.log.slice(from).some(l => l.includes(logLine)),
      t.diff + (s.log.slice(from).some(l => l.includes(logLine)) ? '' : ' (no "' + logLine + '" in the log)'))
    if (graves.length) { await s.run('clear Alder'); await at(s, a, 'Alder', graves[0].pos[0] + 1.5, graves[0].pos[2]); await sleep(3200); await rightClickGrave(a) }
    await s.run('clear Alder')
    a.quit(); await sleep(300)
  }

  // ------------------------------------------------------------ damaged files
  console.log('\n# Damaged grave files')
  a = await rejoin(s, 'Alder')
  await s.run('op Alder')
  for (const x of [80.5, 85.5]) { await at(s, a, 'Alder', x, 80.5); await s.run('give Alder diamond 3'); await s.ask('kill Alder', 1500); await respawned(a) }
  const [one, two] = graveFiles(s)
  a.quit(); await sleep(500)
  await s.stop()
  const p1 = path.join(s.graveDir(), one.file), p2 = path.join(s.graveDir(), two.file)
  const cut = one.text.slice(0, Math.floor(one.text.length / 2))
  writeFileSync(p1, cut)
  const junk = Buffer.from(Array.from({ length: 300 }, (_, i) => (i * 37) % 256))
  writeFileSync(p2, junk)
  await restart(s)
  const loud = s.log.filter(l => /A GRAVE FILE IS DAMAGED/.test(l))
  check('both damaged files are reported loudly at startup', loud.length === 2, loud.length + ' reports')
  const dmgDir = path.join(s.dir, 'plugins/LanternGraves/damaged')
  const copies = existsSync(dmgDir) ? readdirSync(dmgDir) : []
  check('a copy of each is kept', copies.length === 2 && copies.some(f => readFileSync(path.join(dmgDir, f), 'utf8') === cut))
  const listed = (await s.ask('lanterngraves damaged', 800)).join(' ')
  check('/lanterngraves damaged lists them', listed.includes(one.file) && listed.includes(two.file), listed.slice(0, 200))
  a = await rejoin(s, 'Alder')
  await sleep(1000)
  check('an admin is told when they join', a.chat_log.some(m => /2 grave files are damaged/.test(m)))
  // Life goes on: a new death and collection, then a clean stop. The damaged files are never touched.
  await at(s, a, 'Alder', 90.5, 80.5); await s.run('give Alder emerald 4'); await s.ask('kill Alder', 1500); await respawned(a)
  await at(s, a, 'Alder', 92, 80.5); await sleep(3200); await rightClickGrave(a)
  check('other graves still work', holdingsTally(await holdings(s, 'Alder')).emerald === 4)
  a.quit(); await sleep(500)
  await s.stop()
  check('the damaged files are never overwritten or removed', readFileSync(p1, 'utf8') === cut && Buffer.compare(readFileSync(p2), junk) === 0)
  await restart(s)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const s = new Server(newServer('crash'))
  await s.start()
  try { await run(s) } finally {
    // The damaged-file reports are expected; anything else from LanternGraves at ERROR or SEVERE isn't.
    check('no other LanternGraves errors in the log', s.errors().length === 0, s.errors()[0]?.slice(0, 300))
    await s.stop(); s.discard()
  }
  summary()
}
