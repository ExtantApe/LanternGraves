// GriefPrevention: deaths inside someone else's claim, on a claim's edge, in your own claim and in the wilderness.
// Where the grave lands, who can collect or break it, and whether the light block goes in. The claims are made up.
import path from 'node:path'
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import vec3 from 'vec3'
import { Server, newServer, bot, sleep, check, summary, holdings, graveFiles, holdingsTally, graveTally, sameTally, rightClickGrave, markers, respawned, loaded, JARS, VERSION, WALKS } from './harness.mjs'
import { kit, calm, loose } from './basic.mjs'

// 16.18.7 lists 26.1 to 26.3; 16.18.8, the newest, lists only 26.3.
export const GP = VERSION === '26.3' ? 'GriefPrevention-16.18.8.jar' : 'GriefPrevention-16.18.7.jar'

const Y = -60
const fileOf = (s, g) => graveFiles(s).find(x => x.file === g.file)
const sameGrave = (a, b) => b && sameTally(graveTally([a]), graveTally([b])).ok && a.xp === b.xp

async function at(s, b, name, x, z) {
  await s.run(`execute in minecraft:overworld run tp ${name} ${x} ${Y} ${z}`)
  await sleep(900); loaded(b); await sleep(400)
}

async function newGrave(s, owner, old, ms = 10000) {
  const end = Date.now() + ms
  while (Date.now() < end) {
    const g = graveFiles(s).find(x => x.owner === owner && !x.pending && !old.has(x.file))
    if (g) return g
    await sleep(200)
  }
}

async function blockIs(s, x, y, z, block) {
  const out = (await s.ask(`execute in minecraft:overworld if block ${x} ${y} ${z} minecraft:${block}`, 400)).join(' ')
  return /Test passed/.test(out)
}

/** GriefPrevention's own claim files: owner name and corners. */
function claims(s) {
  const dir = path.join(s.dir, 'plugins/GriefPreventionData/ClaimData')
  if (!existsSync(dir)) return []
  return readdirSync(dir).filter(f => f.endsWith('.yml')).map(f => {
    const t = readFileSync(path.join(dir, f), 'utf8')
    const corner = k => (t.match(new RegExp(k + ': (.*)')) ?? [])[1]?.split(';').slice(1).map(Number)
    return { lesser: corner('Lesser Boundary Corner'), greater: corner('Greater Boundary Corner'), owner: (t.match(/Owner: (.*)/) ?? [])[1] }
  })
}

/** Has the bot stand at (sx, z) and put a stone on the floor at (x, Y, z). True if it's there afterwards. */
async function place(s, bt, name, sx, x, z) {
  await at(s, bt, name, sx, z + 0.5)
  await s.run(`give ${name} stone 1`)
  await sleep(300)
  await bt.equip(bt.inventory.items().find(i => i.name === 'stone'), 'hand').catch(() => {})
  await bt.placeBlock(bt.blockAt(new vec3.Vec3(x, Y - 1, z)), new vec3.Vec3(0, 1, 0)).catch(() => {})
  await sleep(800)
  const there = await blockIs(s, x, Y, z, 'stone')
  await s.run(`clear ${name}`, `execute in minecraft:overworld run setblock ${x} ${Y} ${z} air`)
  return there
}

async function makeClaim(s, b, name, x, z, radius) {
  await at(s, b, name, x + 0.5, z + 0.5)
  // /claim needs a golden shovel in hand; an emptied inventory puts it in the selected first hotbar slot.
  await s.run(`acb ${name} 5000`, `clear ${name}`, `item replace entity ${name} hotbar.0 with golden_shovel`)
  b.setQuickBarSlot(0)
  await sleep(500)
  b.chat(`/claim ${radius}`)
  await sleep(1500)
  await s.run(`clear ${name}`)
}

export async function run(s) {
  check('control: GriefPrevention is running', s.log.some(l => /\[GriefPrevention\] .*(Enabling|enabled|Finished loading)/i.test(l)) && !s.log.some(l => /Could not load .*GriefPrevention|Error occurred while enabling GriefPrevention/i.test(l)))
  const a = await bot('Alder')
  const b = await bot('Birch')
  const o = await bot('Oak')
  await calm(s)

  // Oak's claim around 200,200 and Alder's around 300,200, both radius 8.
  await makeClaim(s, o, 'Oak', 200, 200, 8)
  await makeClaim(s, a, 'Alder', 300, 200, 8)
  const cs = claims(s)
  check('control: two claims were made', cs.length === 2, JSON.stringify(cs) + ' | ' + o.chat_log.slice(-2).join(' | '))
  const oak = cs.find(c => c.lesser && c.lesser[0] <= 200 && c.greater[0] >= 200) ?? { lesser: [192, 0, 192], greater: [208, 0, 208] }
  const edgeX = oak.greater[0] // the last block column inside Oak's claim

  // A control that the claim really protects: Birch, untrusted, can't place a block in it.
  // Placing a block turns the bot's head, which gets it kicked on 26.3, so this runs where bots move.
  if (WALKS) {
    check('control: Oak\'s claim stops Birch building', !(await place(s, b, 'Birch', 203.5, 204, 200)), b.chat_log.slice(-1)[0])
  } else console.log('  (block placing is skipped on 26.3: the bot would be kicked for turning)')

  const spots = [
    ['inside Oak\'s claim', 200, 200, true],
    ['on the edge of Oak\'s claim', edgeX, 200, true],
    ['in Alder\'s own claim', 300, 200, false],
    ['in the wilderness', 400, 200, false],
  ]
  for (const [where, x, z, oaks] of spots) {
    console.log(`\n# Alder dies ${where}`)
    const old = new Set(graveFiles(s).map(g => g.file))
    await at(s, a, 'Alder', x + 0.5, z + 0.5)
    await kit(s, 'Alder')
    const want = holdingsTally(await holdings(s, 'Alder'))
    await s.ask('kill Alder', 1500); await respawned(a)
    const g = await newGrave(s, 'Alder', old)
    check(`${where}: the grave is where Alder died`, g && g.world === 'world' && Math.floor(g.pos[0]) === x && g.pos[1] === Y && Math.floor(g.pos[2]) === z, String(g?.pos))
    check(`${where}: the grave holds everything`, g && sameTally(want, graveTally([g])).ok)
    if (!g) continue
    await sleep(1200)
    check(`${where}: the light block went in`, await blockIs(s, x, Y, z, 'light'))
    check(`${where}: nothing dropped`, (await loose(s)).items === 0)

    // Others: Birch (a stranger), and Oak, whose claim it may be, even with Birch trusted in that claim.
    if (oaks) {
      const said = o.chat_log.length
      o.chat('/trust Birch'); await sleep(800)
      check(`${where}: control: Oak trusted Birch in the claim`, o.chat_log.slice(said).some(m => /Granted Birch/i.test(m)), o.chat_log.slice(said).join(' | '))
    }
    for (const [who, bt, name] of [['Birch', b, 'Birch'], ['Oak', o, 'Oak']]) {
      await at(s, bt, name, x + 0.5, z + 2.5)
      await rightClickGrave(bt)
      bt.chat('/grave collect'); await sleep(800)
      const box = markers(bt, 'interaction').sort((p, q) => p.position.distanceTo(bt.entity.position) - q.position.distanceTo(bt.entity.position))[0]
      if (box) for (let i = 0; i < 4; i++) { bt.attack(box); await sleep(150) }
      await sleep(600)
      const note = oaks && who === 'Birch' ? ' (trusted in Oak\'s claim)' : oaks && who === 'Oak' ? ' (the claim\'s owner)' : ''
      check(`${where}: ${who}${note} can't collect or break it`, sameGrave(g, fileOf(s, g)) && sameTally(holdingsTally(await holdings(s, name)), {}).ok && markers(bt, 'interaction').some(e => e.position.distanceTo(box.position) < 0.5))
      await at(s, bt, name, 0.5, 0.5)
    }
    if (oaks) { o.chat('/untrust Birch'); await sleep(600) }

    // The owner collects with a right-click, and GriefPrevention says nothing to them about it.
    await at(s, a, 'Alder', x + 0.5, z + 2.5)
    await sleep(3200) // the respawn's settle time
    const said = a.chat_log.length
    await rightClickGrave(a)
    await sleep(500)
    const heard = a.chat_log.slice(said)
    check(`${where}: Alder collects it with a right-click, in full`, sameTally(want, holdingsTally(await holdings(s, 'Alder'))).ok && !fileOf(s, g), heard.join(' | '))
    check(`${where}: no claim message gets in the way`, !heard.some(m => /permission|Oak|claim/i.test(m)), heard.join(' | '))
    check(`${where}: the light block is gone`, await blockIs(s, x, Y, z, 'air'))
    await s.run('clear Alder')
  }

  // The light block is gone after collection, so Oak can build on the spot in their own claim.
  if (WALKS) check('afterwards Oak can build on the spot in their own claim', await place(s, o, 'Oak', 198.5, 200, 200))
  a.quit(); b.quit(); o.quit()
  await sleep(500)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const s = new Server(newServer('griefprevention', { plugins: [path.join(JARS, GP)] }))
  await s.start()
  try { await run(s) } finally {
    check('no LanternGraves errors in the log', s.errors().length === 0, s.errors()[0]?.slice(0, 300))
    const gpErr = s.log.filter(l => /GriefPrevention/.test(l) && /(ERROR|Exception|SEVERE)/.test(l))
    check('no GriefPrevention errors in the log', gpErr.length === 0, gpErr[0]?.slice(0, 300))
    await s.stop(); s.discard()
  }
  summary()
}
