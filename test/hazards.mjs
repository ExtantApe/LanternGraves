// Lava, the void in all three dimensions, explosions and everything else that can happen to a lantern.
// After each: the items are all there exactly once, the lock holds, and the lantern comes back.
import path from 'node:path'
import { Server, newServer, bot, sleep, check, summary, holdings, graveFiles, holdingsTally, graveTally, sameTally, rightClickGrave, markers, respawned, JARS, WALKS, loaded } from './harness.mjs'
import { kit, calm, loose } from './basic.mjs'

const near = (g, x, y, z, r = 1.6) => g && Math.hypot(g.pos[0] - x, g.pos[1] - y, g.pos[2] - z) <= r

async function ownedGraves(s, owner) { return graveFiles(s).filter(g => g.owner === owner) }

/**
 * A death below the world. On 26.3, a bot that has just changed dimension isn't ticked by the server, so it
 * never takes void damage on its own; then it's killed where it is with out_of_world damage, which is the
 * same position and cause the plugin sees, and the test says so.
 */
async function voidDeath(s, name) {
  const g = await deathGrave(s, name, 8000)
  if (g) return g
  console.log(`  ${name} wasn't ticked below the world: killing them there with out_of_world damage`)
  await s.run(`damage ${name} 1000 minecraft:out_of_world`)
  return deathGrave(s, name)
}

/** Waits until the owner has a settled grave (deaths by lava or the void take a few seconds). */
async function deathGrave(s, owner, ms = 25000) {
  const end = Date.now() + ms
  while (Date.now() < end) {
    const g = graveFiles(s).filter(g => g.owner === owner && !g.pending)
    if (g.length) { await sleep(500); return g[0] }
    await sleep(250)
  }
  return undefined
}

/** Lantern parts within 2 blocks of a point, counted on the server. */
async function lanternAt(s, x, y, z, dim = 'overworld') {
  const sel = t => `@e[type=${t},x=${x},y=${y},z=${z},distance=..2.5]`
  const pre = `execute in minecraft:${dim} if entity `
  const n = async t => { const out = await s.ask(pre + sel(t), 350); const m = out.join(' ').match(/count: (\d+)/i); return m ? Number(m[1]) : 0 }
  return { displays: await n('block_display'), boxes: await n('interaction'), labels: await n('text_display') }
}

async function lanternBack(s, g, dim, ms = 4000) {
  const end = Date.now() + ms
  let l
  while (Date.now() < end) {
    l = await lanternAt(s, g.pos[0], g.pos[1], g.pos[2], dim)
    if (l.displays === 2 && l.boxes === 1) return { ok: true, l }
    await sleep(300)
  }
  return { ok: false, l }
}

async function graveUnchanged(s, before) {
  const now = graveFiles(s).find(g => g.file === before.file)
  return now && sameTally(graveTally([before]), graveTally([now])).ok && now.xp === before.xp
}

/** Puts the bot on solid ground and walks it a few blocks so the plugin sees where it was safe. */
async function standAndWalk(s, b, name, dim, x, y, z) {
  await s.run(`execute in minecraft:${dim} run tp ${name} ${x + 0.5} ${y} ${z + 0.5} -90 0`)
  await sleep(1200)
  loaded(b)
  if (WALKS) {
    b.setControlState('forward', true)
    await sleep(700)
    b.setControlState('forward', false)
  }
  await sleep(1400) // the plugin also samples footing every second
}

export async function run(s) {
  const a = await bot('Alder')
  const b = await bot('Birch')
  await calm(s)
  await s.run('op Alder', 'gamemode survival Alder', 'gamemode survival Birch')

  // ------------------------------------------------------------ lava
  console.log('\n# Lava')
  await s.run('fill 40 -63 40 48 -61 48 lava', 'fill 39 -61 39 39 -61 49 stone')
  await standAndWalk(s, a, 'Alder', 'overworld', 36, -60, 44)
  await kit(s, 'Alder')
  const kitLava = holdingsTally(await holdings(s, 'Alder'))
  await s.run('tp Alder 44.5 -61 44.5')
  let gl = await deathGrave(s, 'Alder')
  await respawned(a)
  check('lava death: one grave', (await ownedGraves(s, 'Alder')).length === 1)
  check('lava death: the grave is on the last safe ground, not in the lava', gl && gl.pos[0] < 40 && gl.pos[1] === -60, String(gl?.pos))
  const lavaLoose = await loose(s)
  if (lavaLoose.items) console.log('  loose items: ' + (await s.ask('data get entity @e[type=item,limit=2] Item', 600)).join(' ').slice(0, 300))
  check('lava death: nothing burnt or dropped', gl && sameTally(kitLava, graveTally([gl])).ok && lavaLoose.items === 0, (gl ? sameTally(kitLava, graveTally([gl])).diff : '') + ' ' + JSON.stringify(lavaLoose))
  // Lava flows over the grave spot later.
  await s.run(`setblock ${Math.floor(gl.pos[0])} -60 ${Math.floor(gl.pos[2])} lava`)
  await sleep(2500)
  check('lava over the grave: the items are untouched', await graveUnchanged(s, gl))
  check('lava over the grave: the lantern stays', (await lanternBack(s, gl, 'overworld')).ok)
  await s.run('effect give Alder fire_resistance 120 0 true', `tp Alder ${gl.pos[0] - 2} -60 ${gl.pos[2]}`)
  await sleep(1200)
  await rightClickGrave(a)
  await sleep(600)
  check('lava over the grave: it can still be collected, in full', sameTally(kitLava, holdingsTally(await holdings(s, 'Alder'))).ok && (await ownedGraves(s, 'Alder')).length === 0)
  await s.run('fill 30 -61 30 50 -60 50 air replace lava', 'fill 39 -63 39 49 -61 49 stone replace lava')

  // ------------------------------------------------------------ void, in each dimension
  for (const [dim, floor] of [['overworld', -60], ['the_nether', 101], ['the_end', 81]]) {
    console.log(`\n# Void: ${dim}`)
    const px = 300, pz = 300
    await s.run(`execute in minecraft:${dim} run forceload add ${px - 16} ${pz - 16} ${px + 16} ${pz + 16}`)
    if (dim !== 'overworld') {
      await s.run(`execute in minecraft:${dim} run fill ${px - 3} ${floor - 1} ${pz - 3} ${px + 6} ${floor - 1} ${pz + 3} obsidian`,
        `execute in minecraft:${dim} run fill ${px - 3} ${floor} ${pz - 3} ${px + 6} ${floor + 3} ${pz + 3} air`)
    }
    await standAndWalk(s, a, 'Alder', dim, px, floor, pz)
    await kit(s, 'Alder')
    const kitVoid = holdingsTally(await holdings(s, 'Alder'))
    await s.run(`execute in minecraft:${dim} run tp Alder ${px} -200 ${pz}`)
    const gv = await voidDeath(s, 'Alder')
    await respawned(a)
    check(`${dim} void: one grave, nothing lost`, gv && sameTally(kitVoid, graveTally([gv])).ok, gv ? '' : 'no grave')
    check(`${dim} void: the grave is on the ground they last stood on`, gv && gv.pos[1] === floor && Math.abs(gv.pos[0] - px) < 5 && Math.abs(gv.pos[2] - pz) < 3, String(gv?.pos))
    check(`${dim} void: the grave records the right world`, gv && (dim === 'overworld' ? gv.world === 'world' : gv.world.includes(dim === 'the_nether' ? 'nether' : 'end')), gv?.world)
    await s.run(`execute in minecraft:${dim} run tp Alder ${gv.pos[0] - 2} ${floor} ${gv.pos[2]}`)
    await sleep(800); loaded(a)
    await sleep(3800) // the respawn put them in the overworld: wait out the settle time
    await rightClickGrave(a)
    await sleep(600)
    check(`${dim} void: reachable and collected in full`, sameTally(kitVoid, holdingsTally(await holdings(s, 'Alder'))).ok && (await ownedGraves(s, 'Alder')).length === 0)
  }
  // No safe ground known in the End: a straight drop on arrival.
  console.log('\n# Void with no safe ground known')
  await kit(s, 'Birch')
  const kitB = holdingsTally(await holdings(s, 'Birch'))
  await s.run('execute in minecraft:the_end run tp Birch 2000 -200 2000')
  await sleep(800); loaded(b)
  const gn = await voidDeath(s, 'Birch')
  await respawned(b)
  check('no safe ground: a grave is still made, nothing lost', gn && sameTally(kitB, graveTally([gn])).ok)
  if (gn) await s.run(`execute in minecraft:the_end run forceload add ${Math.floor(gn.pos[0])} ${Math.floor(gn.pos[2])}`)
  const floorUnder = gn && (await s.ask(`execute in minecraft:the_end if block ${Math.floor(gn.pos[0])} ${gn.pos[1] - 1} ${Math.floor(gn.pos[2])} air`, 400)).some(l => /Test failed/.test(l))
  check('no safe ground: it falls back to solid ground at the End spawn', gn && Math.hypot(gn.pos[0], gn.pos[2]) < 4 && floorUnder, String(gn?.pos))
  if (gn) {
    await s.run(`execute in minecraft:the_end run tp Birch ${gn.pos[0] - 1.5} ${gn.pos[1]} ${gn.pos[2]}`)
    await sleep(800); loaded(b)
    await sleep(3800)
    await rightClickGrave(b)
    await sleep(600)
    check('no safe ground: collected from the platform', sameTally(kitB, holdingsTally(await holdings(s, 'Birch'))).ok && (await ownedGraves(s, 'Birch')).length === 0)
  }
  await s.run('clear Birch')

  // ------------------------------------------------------------ explosions
  console.log('\n# Explosions')
  await s.run('difficulty easy') // creepers need it; spawn_monsters is off so none turn up on their own
  await s.run('execute in minecraft:overworld run tp Alder 80.5 -60 80.5', 'execute in minecraft:overworld run tp Birch 90.5 -60 80.5')
  await sleep(1000); loaded(a); loaded(b); await sleep(500)
  await kit(s, 'Alder')
  const kitE = holdingsTally(await holdings(s, 'Alder'))
  // Killed by TNT: drops would normally be blown up too.
  await s.run('damage Alder 19', 'summon tnt 80.5 -60 80.5 {fuse:0}')
  await sleep(2500)
  await respawned(a)
  const ge = (await ownedGraves(s, 'Alder'))[0]
  check('TNT death: the grave has everything', ge && sameTally(kitE, graveTally([ge])).ok, ge ? sameTally(kitE, graveTally([ge])).diff : 'no grave')
  check('TNT death: the lantern is up', ge && (await lanternBack(s, ge, 'overworld')).ok)
  const blasts = [
    ['TNT', [`summon tnt ${ge.pos[0]} ${ge.pos[1]} ${ge.pos[2]} {fuse:0}`]],
    ['creeper', [`summon creeper ${ge.pos[0]} ${ge.pos[1]} ${ge.pos[2]} {Fuse:0,ignited:1b}`]],
    ['charged creeper', [`summon creeper ${ge.pos[0]} ${ge.pos[1]} ${ge.pos[2]} {Fuse:0,ignited:1b,powered:1b}`]],
    ['end crystal', [`summon end_crystal ${ge.pos[0]} ${ge.pos[1] + 1} ${ge.pos[2] + 1}`, `damage @e[type=end_crystal,limit=1,sort=nearest,x=${ge.pos[0]},y=${ge.pos[1]},z=${ge.pos[2]}] 1`]],
  ]
  for (const [name, cmds] of blasts) {
    await s.run(...cmds)
    await sleep(2000)
    check(`${name} at the grave: items and lock intact`, await graveUnchanged(s, ge))
    check(`${name} at the grave: the lantern comes back`, (await lanternBack(s, ge, 'overworld')).ok)
  }
  // A respawn anchor used in the overworld, and a bed in the Nether, set off by a player next to the grave.
  await s.run('effect give Alder resistance 60 4 true', 'effect give Alder fire_resistance 60 0 true')
  const [gx, gy, gz] = [Math.floor(ge.pos[0]), Math.floor(ge.pos[1]), Math.floor(ge.pos[2])]
  await s.run(`fill ${gx - 3} ${gy - 1} ${gz - 3} ${gx + 3} ${gy - 1} ${gz + 3} stone`, `setblock ${gx + 1} ${gy} ${gz} respawn_anchor[charges=1]`, `tp Alder ${gx + 3.5} ${gy} ${gz + 0.5} 90 30`)
  await sleep(1200)
  await a.activateBlock(a.blockAt(a.entity.position.offset(-2, 0, 0).floored())).catch(e => console.log('  anchor: ' + e.message))
  await sleep(2000)
  check('respawn anchor at the grave: items and lock intact', await graveUnchanged(s, ge))
  check('respawn anchor at the grave: the lantern comes back', (await lanternBack(s, ge, 'overworld')).ok)

  // Bed: a grave in the Nether, a bed beside it.
  await s.run('execute in minecraft:the_nether run forceload add 480 480 520 520',
    'execute in minecraft:the_nether run fill 495 99 495 505 99 505 obsidian', 'execute in minecraft:the_nether run fill 495 100 495 505 104 505 air')
  await standAndWalk(s, b, 'Birch', 'the_nether', 498, 100, 500)
  await kit(s, 'Birch')
  const kitN = holdingsTally(await holdings(s, 'Birch'))
  await s.ask('kill Birch', 1500)
  await respawned(b)
  const gb = (await ownedGraves(s, 'Birch'))[0]
  await s.run('effect give Alder resistance 60 4 true', 'effect give Alder fire_resistance 60 0 true')
  const [bx, by, bz] = [Math.floor(gb.pos[0]), Math.floor(gb.pos[1]), Math.floor(gb.pos[2])]
  await s.run(`execute in minecraft:the_nether run setblock ${bx + 1} ${by} ${bz} red_bed[facing=east,part=foot]`,
    `execute in minecraft:the_nether run setblock ${bx + 2} ${by} ${bz} red_bed[facing=east,part=head]`,
    `execute in minecraft:the_nether run tp Alder ${bx + 4.5} ${by} ${bz + 0.5} 90 30`)
  await sleep(1500); loaded(a); await sleep(500)
  await a.activateBlock(a.blockAt(a.entity.position.offset(-2, 0, 0).floored())).catch(e => console.log('  bed: ' + e.message))
  await sleep(2000)
  check('bed in the Nether at the grave: items and lock intact', gb && await graveUnchanged(s, gb) && sameTally(kitN, graveTally([gb])).ok)
  check('bed in the Nether at the grave: the lantern comes back', gb && (await lanternBack(s, gb, 'the_nether')).ok)
  await s.run(`execute in minecraft:the_nether run tp Birch ${gb.pos[0] - 1.5} ${gb.pos[1]} ${gb.pos[2]}`)
  await sleep(3800)
  await rightClickGrave(b)
  await sleep(600)
  check('bed: Birch collects the Nether grave in full', sameTally(kitN, holdingsTally(await holdings(s, 'Birch'))).ok && (await ownedGraves(s, 'Birch')).length === 0,
    sameTally(kitN, holdingsTally(await holdings(s, 'Birch'))).diff + ' | ' + b.chat_log.slice(-3).join(' | '))
  await s.run('clear Birch')

  // ------------------------------------------------------------ other marker damage
  console.log('\n# Other damage to the lantern')
  await s.run(`execute in minecraft:overworld run tp Birch ${ge.pos[0] + 2} ${ge.pos[1]} ${ge.pos[2]}`, `execute in minecraft:overworld run tp Alder ${ge.pos[0] + 3} ${ge.pos[1]} ${ge.pos[2] + 2}`)
  await sleep(1000); loaded(a); loaded(b)
  const at = `${gx} ${gy} ${gz}`
  const damage = [
    ['fire', [`setblock ${gx} ${gy - 1} ${gz} netherrack`, `setblock ${at} fire`]],
    ['water', [`setblock ${at} water`]],
    ['piston', [`setblock ${gx - 1} ${gy} ${gz} piston[facing=east]`, `setblock ${gx - 2} ${gy} ${gz} redstone_block`]],
    ['falling sand', [`setblock ${at} air`, `setblock ${gx} ${gy + 3} ${gz} sand`]],
    ['/kill', ['kill @e[type=block_display]', 'kill @e[type=interaction]', 'kill @e[type=text_display]']],
    ['/kill everything', ['kill @e[type=!player]']],
  ]
  for (const [name, cmds] of damage) {
    await s.run(...cmds)
    await sleep(2200)
    const base = name === 'falling sand' ? { ...ge, pos: [ge.pos[0], ge.pos[1] + 1, ge.pos[2]] } : ge
    check(`${name}: items and lock intact`, await graveUnchanged(s, ge))
    check(`${name}: the lantern is back${name === 'falling sand' ? ', on top of the sand' : ''}`, (await lanternBack(s, base, 'overworld')).ok, JSON.stringify((await lanternBack(s, base, 'overworld', 300)).l))
  }
  await s.run(`fill ${gx - 3} ${gy} ${gz - 3} ${gx + 3} ${gy + 4} ${gz + 3} air`)
  // WorldEdit: set the region to air and cut it with its entities.
  a.chat(`//pos1 ${gx - 2},${gy - 1},${gz - 2}`); await sleep(300)
  a.chat(`//pos2 ${gx + 2},${gy + 2},${gz + 2}`); await sleep(300)
  a.chat('//cut -e'); await sleep(800)
  a.chat('//set air'); await sleep(1500)
  check('WorldEdit cut with entities: items and lock intact', await graveUnchanged(s, ge))
  check('WorldEdit cut with entities: the lantern comes back', (await lanternBack(s, { ...ge, pos: [ge.pos[0], ge.pos[1] - 1 + 0, ge.pos[2]] }, 'overworld', 5000)).ok || (await lanternBack(s, ge, 'overworld')).ok)
  await s.run(`fill ${gx - 3} ${gy - 1} ${gz - 3} ${gx + 3} ${gy - 1} ${gz + 3} stone`)

  // ------------------------------------------------------------ hoppers
  await s.run(`setblock ${gx} ${gy - 1} ${gz} hopper`, `setblock ${gx} ${gy - 2} ${gz} chest`, `summon hopper_minecart ${gx + 0.5} ${gy} ${gz + 0.5}`)
  await sleep(3000)
  check('hopper and hopper minecart under the grave: nothing drained', await graveUnchanged(s, ge) && (await s.ask(`data get block ${gx} ${gy - 2} ${gz} Items`, 400)).join(' ').match(/\[\]|has the following block data: \[\]/) !== null)
  await s.run('kill @e[type=hopper_minecart]', `setblock ${gx} ${gy - 1} ${gz} stone`, `setblock ${gx} ${gy - 2} ${gz} stone`)

  // Finally Alder collects: the whole kit is there.
  await s.run('effect clear Alder', `execute in minecraft:overworld run tp Alder ${ge.pos[0] + 2} ${ge.pos[1]} ${ge.pos[2]}`, 'clear Alder')
  await sleep(1000); loaded(a)
  await sleep(3000)
  await rightClickGrave(a)
  await sleep(600)
  check('after all that, Alder collects everything', sameTally(kitE, holdingsTally(await holdings(s, 'Alder'))).ok && (await ownedGraves(s, 'Alder')).length === 0,
    sameTally(kitE, holdingsTally(await holdings(s, 'Alder'))).diff)
  a.quit(); b.quit()
  await sleep(500)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const s = new Server(newServer('hazards', { plugins: [path.join(JARS, 'worldedit-bukkit-7.4.6.jar')] }))
  await s.start()
  try { await run(s) } finally { check('no Lantern Graves errors in the log', s.errors().length === 0, s.errors()[0]?.slice(0, 300)); await s.stop(); s.discard() }
  summary()
}
