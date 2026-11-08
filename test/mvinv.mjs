// Multiverse-Inventories: die in group A, respawn in another group, go to group B, come back to A and collect.
// Items land in A's inventory, never B's, and collecting the moment a world change lands loses and leaks nothing.
import path from 'node:path'
import { Server, newServer, bot, sleep, check, summary, holdings, graveFiles, holdingsTally, graveTally, addTallies, sameTally, rightClickGrave, respawned, loaded, markers, JARS } from './harness.mjs'
import { kit, calm, loose } from './basic.mjs'

async function go(s, b, name, world, x, z) {
  await s.run(`execute in minecraft:${world} run tp ${name} ${x} -60 ${z}`)
  await sleep(900); loaded(b); await sleep(300)
}

export async function run(s) {
  await s.ask('mv create alpha normal --world-type FLAT --no-structures', 15000)
  await s.ask('mv create beta normal --world-type FLAT --no-structures', 15000)
  await s.ask('mvinv create-group groupa alpha all', 1500)
  await s.ask('mvinv create-group groupb beta all', 1500)
  const groups = (await s.ask('mvinv list', 1000)).join(' ')
  check('control: two inventory groups exist', /groupa/.test(groups) && /groupb/.test(groups), groups.slice(0, 200))
  const a = await bot('Alder')
  await calm(s)
  for (const w of ['alpha', 'beta']) await s.run(`execute in minecraft:${w} run gamerule spawn_mobs false`, `execute in minecraft:${w} run gamerule keep_inventory false`)

  console.log('\n# Die in A, respawn elsewhere, visit B, collect in A')
  await go(s, a, 'Alder', 'beta', 10.5, 10.5)
  await sleep(3500)
  await s.run('clear Alder', 'give Alder stick 10', 'give Alder bread 3')
  const bInv = await holdings(s, 'Alder')
  await go(s, a, 'Alder', 'alpha', 20.5, 20.5)
  await sleep(3500)
  const alphaEmpty = await holdings(s, 'Alder')
  check('control: group A has its own (empty) inventory', Object.keys(alphaEmpty).length === 0, Object.keys(alphaEmpty).length + ' slots')
  await kit(s, 'Alder')
  const aKit = await holdings(s, 'Alder')
  await s.ask('kill Alder', 1500)
  await respawned(a)
  await sleep(3500)
  const g = graveFiles(s).find(x => x.owner === 'Alder')
  check('the grave is in group A\'s world', g && g.world === 'alpha', g?.world)
  check('the grave holds A\'s kit', g && sameTally(holdingsTally(aKit), graveTally([g])).ok)
  const respawnInv = await holdings(s, 'Alder')
  check('respawned outside A with nothing of A\'s', !Object.values(respawnInv).some(v => v.includes('diamond_sword')), Object.keys(respawnInv).length + ' slots')
  await go(s, a, 'Alder', 'beta', 10.5, 10.5)
  await sleep(3500)
  check('group B\'s inventory is untouched', sameTally(holdingsTally(bInv), holdingsTally(await holdings(s, 'Alder'))).ok)

  // Back to A, and click the instant the world change lands: refused, nothing moves.
  await s.run(`execute in minecraft:alpha run tp Alder ${g.pos[0] + 1.5} -60 ${g.pos[2]}`)
  await sleep(250); loaded(a)
  for (let i = 0; i < 4; i++) { await rightClickGrave(a); await sleep(100) }
  const early = graveFiles(s).find(x => x.file === g.file)
  check('collecting right after the world change is held back', early && sameTally(graveTally([g]), graveTally([early])).ok, a.chat_log.slice(-1)[0])
  check('and the player is told to wait a moment', a.chat_log.some(m => /still being loaded/.test(m)))
  await sleep(3500)
  await rightClickGrave(a)
  const inA = await holdings(s, 'Alder')
  check('collected in A: A\'s inventory has the kit, slot for slot', Object.keys(aKit).every(k2 => aKit[k2] === inA[k2]) && !graveFiles(s).some(x => x.file === g.file))
  await go(s, a, 'Alder', 'beta', 10.5, 10.5)
  await sleep(3500)
  check('B\'s inventory still holds only B\'s things', sameTally(holdingsTally(bInv), holdingsTally(await holdings(s, 'Alder'))).ok)
  await go(s, a, 'Alder', 'alpha', 25.5, 25.5)
  await sleep(3500)
  check('and A still has the kit after the round trip', sameTally(holdingsTally(aKit), holdingsTally(await holdings(s, 'Alder'))).ok)
  check('nothing dropped anywhere', (await loose(s)).items === 0)

  console.log('\n# A grave moved into another group still only pays out in its own')
  await s.run('op Alder', 'clear Alder', 'give Alder emerald 6')
  await s.ask('kill Alder', 1500); await respawned(a)
  await go(s, a, 'Alder', 'beta', 30.5, 30.5)
  await sleep(3500)
  const bBefore = await holdings(s, 'Alder')
  a.chat('/lanterngraves move Alder 1'); await sleep(1000)
  await rightClickGrave(a)
  check('a grave moved into B is refused there, with the reason', a.chat_log.some(m => /can only be collected there/.test(m)) && sameTally(holdingsTally(bBefore), holdingsTally(await holdings(s, 'Alder'))).ok,
    a.chat_log.slice(-1)[0])
  await go(s, a, 'Alder', 'alpha', 40.5, 40.5)
  await sleep(3500)
  a.chat('/lanterngraves move Alder 1'); await sleep(1000)
  await rightClickGrave(a)
  check('moved back to A, it pays out', holdingsTally(await holdings(s, 'Alder')).emerald === 6)
  a.quit()
  await sleep(500)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const s = new Server(newServer('mvinv', { plugins: [path.join(JARS, 'multiverse-core-5.8.2.jar'), path.join(JARS, 'multiverse-inventories-5.3.6.jar')] }))
  await s.start()
  try { await run(s) } finally {
    check('Lantern Graves found Multiverse-Inventories', s.log.some(l => /Using Multiverse-Inventories groups/.test(l)))
    check('no Lantern Graves errors in the log', s.errors().length === 0, s.errors()[0]?.slice(0, 300))
    await s.stop(); s.discard()
  }
  summary()
}
