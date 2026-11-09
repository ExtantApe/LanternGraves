// Basic: die, walk back, collect. Everything comes back exactly, in the same slots, with all the XP.
// Then a full inventory: what doesn't fit stays in the grave and nothing drops.
import { Server, newServer, bot, sleep, check, summary, holdings, graveFiles, holdingsTally, graveTally, addTallies, sameTally, rightClickGrave, markers, respawned, WALKS } from './harness.mjs'

export const KIT = [
  'clear {p}',
  'xp set {p} 0 points', 'xp set {p} 0 levels',
  'give {p} diamond_sword[enchantments={sharpness:5,looting:3},custom_name={text:"Old Faithful",color:"gold"},damage=17] 1',
  'give {p} bow[enchantments={power:4},repair_cost=3] 1',
  'give {p} dirt 64',
  'give {p} cobblestone 37',
  'give {p} shulker_box[container=[{slot:0,item:{id:"diamond",count:5}},{slot:4,item:{id:"golden_apple",count:2}},{slot:26,item:{id:"writable_book",count:1,components:{writable_book_content:{pages:["page one"]}}}}],custom_name="Stash"] 1',
  'give {p} bundle[bundle_contents=[{id:"emerald",count:3},{id:"arrow",count:16}]] 1',
  'give {p} potion[potion_contents={potion:"minecraft:strong_healing"}] 1',
  'give {p} filled_map[map_id=7] 1',
  'item replace entity {p} armor.head with netherite_helmet[enchantments={protection:4,unbreaking:3},trim={material:"gold",pattern:"coast"}]',
  'item replace entity {p} armor.chest with elytra[damage=200]',
  'item replace entity {p} armor.legs with leather_leggings[dyed_color=16711680]',
  'item replace entity {p} armor.feet with chainmail_boots',
  'item replace entity {p} weapon.offhand with shield[base_color="red"]',
  'item replace entity {p} hotbar.8 with torch 12',
  'item replace entity {p} inventory.20 with player_head[profile={name:"Steve"}]',
  'xp set {p} 33 levels', 'xp add {p} 20 points',
]

export async function kit(s, name) {
  await s.run(...KIT.map(c => c.replaceAll('{p}', name)))
  await sleep(300)
}

export async function xpOf(s, name) {
  const lv = (await s.ask(`xp query ${name} levels`, 400)).join(' ').match(/has (\d+) experience levels/)
  const pt = (await s.ask(`xp query ${name} points`, 400)).join(' ').match(/has (\d+) experience points/)
  return { levels: lv ? Number(lv[1]) : -1, points: pt ? Number(pt[1]) : -1 }
}

export async function loose(s) {
  return { items: await s.count('@e[type=item]'), orbs: await s.count('@e[type=experience_orb]') }
}

/** No mobs to interfere with the counts: no spawning, day, and anything hostile removed. */
export async function calm(s) {
  await s.run('gamerule keep_inventory false', 'gamerule immediate_respawn true', 'gamerule spawn_mobs false', 'gamerule spawn_monsters false',
    'gamerule advance_time false', 'gamerule advance_weather false', 'time set noon', 'kill @e[type=!player]')
}

export async function run(s) {
  console.log('\n# Basic: death, walk back, collect')
  const a = await bot('Alder')
  await calm(s)
  await s.run('tp Alder 20.5 -60 20.5')
  await sleep(800)
  await kit(s, 'Alder')
  const before = await holdings(s, 'Alder')
  const xpBefore = await xpOf(s, 'Alder')
  check('control: the kit is on', Object.keys(before).length >= 15 && xpBefore.levels === 33, `${Object.keys(before).length} slots, ${xpBefore.levels} levels`)

  await s.ask('kill Alder', 1500)
  const graves = graveFiles(s)
  const dropped = await loose(s)
  check('no items or XP orbs drop', dropped.items === 0 && dropped.orbs === 0, JSON.stringify(dropped))
  check('one grave file, settled', graves.length === 1 && !graves[0].pending, graves.map(g => g.file + (g.pending ? ' pending' : '')).join(', '))
  const t = sameTally(holdingsTally(before), graveTally(graves))
  check('the grave holds exactly what was carried, shulker and bundle contents included', t.ok, t.diff)
  check('the grave sits where they died', graves[0] && graves[0].pos[0] === 20.5 && graves[0].pos[1] === -60 && graves[0].pos[2] === 20.5, String(graves[0]?.pos))
  await respawned(a)
  await sleep(1500)
  check('the player is told where the grave is', a.chat_log.some(m => /safe in a grave at 20, -60, 20/.test(m)), a.chat_log.slice(-3).join(' | '))
  const xpDead = await xpOf(s, 'Alder')
  check('the respawned player has no XP (it is in the grave)', xpDead.levels === 0 && xpDead.points === 0, JSON.stringify(xpDead))

  // Walk back: from 6 blocks away, on foot.
  await s.run('tp Alder 26.5 -60 20.5')
  await sleep(1500)
  check('the lantern is there', markers(a, 'block_display').some(e => e.position.distanceTo(a.entity.position.offset(-6, 0, 0)) < 1.5), markers(a, 'block_display').length + ' displays seen')
  if (WALKS) {
    a.setControlState('forward', true)
    await a.lookAt(a.entity.position.offset(-6, 1.6, 0), true)
    await sleep(1200)
    a.setControlState('forward', false)
    await sleep(300)
  } else {
    await s.run('tp Alder 22.5 -60 20.5') // 26.3: bots can't walk behind ViaVersion, so they're carried the last steps
    await sleep(800)
  }
  await rightClickGrave(a)
  await sleep(800)
  const after = await holdings(s, 'Alder')
  const same = Object.keys(before).every(k => before[k] === after[k]) && Object.keys(after).length === Object.keys(before).length
  const off = Object.keys({ ...before, ...after }).filter(k => before[k] !== after[k])
  check('everything comes back exactly, in the same slots', same, off.slice(0, 4).map(k => `${k}: ${before[k]?.slice(0, 80)} -> ${after[k]?.slice(0, 80)}`).join(' ; '))
  const xpAfter = await xpOf(s, 'Alder')
  check('all the XP comes back', xpAfter.levels === xpBefore.levels && xpAfter.points === xpBefore.points, `${JSON.stringify(xpBefore)} -> ${JSON.stringify(xpAfter)}`)
  check('the emptied grave file is gone', graveFiles(s).length === 0)
  await sleep(1500)
  check('the lantern goes out', (await s.count('@e[type=block_display]')) === 0 && (await s.count('@e[type=interaction]')) === 0)
  check('the light block is removed', (await s.ask('execute if block 20 -60 20 air', 400)).some(l => /Test passed/.test(l)))
  check('the collector is told', a.chat_log.some(m => /Collected \d+ items and 33 levels/.test(m)), a.chat_log.slice(-2).join(' | '))

  console.log('\n# Full inventory: what doesn\'t fit stays in the grave, nothing drops')
  await kit(s, 'Alder')
  const full = await holdings(s, 'Alder')
  await s.ask('kill Alder', 1500)
  await respawned(a)
  await sleep(1000)
  // Fill every storage slot so only a few fit.
  await s.run('clear Alder', ...Array.from({ length: 33 }, (_, i) => `item replace entity Alder ${i < 9 ? 'hotbar.' + i : 'inventory.' + (i - 9)} with oak_planks 64`))
  const held = await holdings(s, 'Alder')
  await s.run('tp Alder 20.5 -60 22.5')
  await sleep(1200)
  await rightClickGrave(a)
  await sleep(800)
  const nowHeld = await holdings(s, 'Alder')
  const left = graveFiles(s)
  const drops = await loose(s)
  check('nothing drops when the inventory is full', drops.items === 0, JSON.stringify(drops))
  check('the rest stays in the grave', left.length === 1 && Object.keys(left[0].items).length > 0, left.length ? Object.keys(left[0].items).length + ' stacks left' : 'no grave')
  const whole = sameTally(addTallies(holdingsTally(full), holdingsTally(held)), addTallies(holdingsTally(nowHeld), graveTally(left)))
  check('nothing lost or copied: (kit + planks) = (inventory + grave)', whole.ok, whole.diff)
  check('the player is told some is left', a.chat_log.some(m => /don't fit and stay in the grave|doesn't fit and stays in the grave/.test(m)), a.chat_log.slice(-2).join(' | '))
  // Make room and finish.
  await s.run('clear Alder oak_planks')
  await rightClickGrave(a)
  await sleep(800)
  const final = await holdings(s, 'Alder')
  const done = sameTally(holdingsTally(full), holdingsTally(final))
  check('after making room, the rest comes back too', done.ok && graveFiles(s).length === 0, done.diff + ' | ' + a.chat_log.slice(-3).join(' | '))
  a.quit()
  await sleep(500)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const s = new Server(newServer('basic'))
  await s.start()
  try { await run(s) } finally { check('no LanternGraves errors in the log', s.errors().length === 0, s.errors()[0]?.slice(0, 300)); await s.stop(); s.discard() }
  summary()
}
