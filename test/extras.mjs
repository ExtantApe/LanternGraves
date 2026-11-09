// Promises the README listed as untested until the 2026-11-09 audit: items on the cursor and in the 2x2
// crafting grid go into the grave, items another plugin keeps stay with the player, and items another plugin
// adds to the drops go into the grave. Nothing drops on the ground and nothing is lost or copied.
import { Server, newServer, bot, sleep, check, summary, holdings, graveFiles, holdingsTally, graveTally, sameTally, respawned, HOOK, WALKS } from './harness.mjs'
import { calm, loose } from './basic.mjs'

export async function run(s) {
  console.log('\n# The cursor and the crafting grid')
  const a = await bot('Birch')
  await calm(s)
  await s.run('tp Birch 30.5 -60 30.5', 'clear Birch', 'give Birch oak_log 9', 'give Birch iron_ingot 4', 'give Birch gold_ingot 2')
  await sleep(800)
  const inv = a.inventory
  const slotOf = name => inv.slots.findIndex((it, i) => i >= 9 && it && it.name === name)
  // Put the oak logs into crafting slot 1, and pick the iron up onto the cursor.
  await a.clickWindow(slotOf('oak_log'), 0, 0); await a.clickWindow(1, 0, 0)
  await a.clickWindow(slotOf('iron_ingot'), 0, 0)
  await sleep(500)
  check('control: logs are in the crafting grid and iron is on the cursor', inv.slots[1]?.name === 'oak_log' && inv.selectedItem?.name === 'iron_ingot',
    `grid ${inv.slots[1]?.name} cursor ${inv.selectedItem?.name}`)
  await s.ask('kill Birch', 1500)
  const graves = graveFiles(s)
  const dropped = await loose(s)
  check('nothing drops on the ground', dropped.items === 0, JSON.stringify(dropped))
  const t = sameTally({ oak_log: 9, iron_ingot: 4, gold_ingot: 2 }, graveTally(graves))
  check('the grave holds the grid and cursor items too, exactly', t.ok, t.diff)
  await respawned(a); await sleep(800)
  a.quit(); await sleep(500)

  console.log('\n# An anvil open at death')
  if (!WALKS) {
    console.log('  26.3: a bot behind ViaVersion can\'t open an anvil, so this case runs on 26.1.2 and 26.2 only')
  } else {
    const d = await bot('Dell')
    await calm(s)
    await s.run('tp Dell 50.5 -60 50.5', 'clear Dell', 'setblock 51 -60 50 anvil', 'give Dell iron_sword 1', 'give Dell name_tag 3')
    await sleep(1000)
    const anvil = d.blockAt(d.entity.position.offset(1, 0, 0).floored())
    const opened = new Promise(r => d.once('windowOpen', r))
    await d.activateBlock(anvil).catch(() => {})
    const w = await Promise.race([opened, sleep(5000).then(() => null)])
    const inWin = name => w ? w.slots.findIndex((it, i) => i >= 3 && it && it.name === name) : -1
    if (w) { await d.clickWindow(inWin('iron_sword'), 0, 0); await d.clickWindow(0, 0, 0); await d.clickWindow(inWin('name_tag'), 0, 0); await d.clickWindow(1, 0, 0) }
    await sleep(500)
    check('control: the anvil is open with a sword and name tags in it', w && w.slots[0]?.name === 'iron_sword' && w.slots[1]?.name === 'name_tag', w ? `${w.slots[0]?.name} ${w.slots[1]?.name}` : 'no window')
    const beforeAnvil = graveFiles(s).map(g => g.file)
    await s.ask('kill Dell', 1500)
    const anvilGraves = graveFiles(s).filter(g => !beforeAnvil.includes(g.file))
    const droppedA = await loose(s)
    check('with an anvil open, nothing drops on the ground', droppedA.items === 0, JSON.stringify(droppedA))
    const ta = sameTally({ iron_sword: 1, name_tag: 3 }, graveTally(anvilGraves))
    check('the grave holds the anvil\'s items, exactly', ta.ok, ta.diff)
    await respawned(d); await sleep(800)
    d.quit(); await sleep(500)
  }

  console.log('\n# Another plugin keeps an item, and another adds one')
  const b = await bot('Cedar')
  await calm(s)
  await s.run('tp Cedar 40.5 -60 40.5', 'clear Cedar', 'give Cedar cobblestone 10', 'give Cedar dirt 5', 'tag Cedar add lg_keep', 'tag Cedar add lg_add')
  await sleep(800)
  const before = graveFiles(s).map(g => g.file)
  await s.ask('kill Cedar', 1500)
  const mine = graveFiles(s).filter(g => !before.includes(g.file))
  const dropped2 = await loose(s)
  check('nothing drops on the ground', dropped2.items === 0, JSON.stringify(dropped2))
  const g = sameTally({ dirt: 5, nether_star: 1 }, graveTally(mine))
  check('items another plugin adds to the drops go in the grave', g.ok, g.diff)
  await respawned(b); await sleep(1000)
  const kept = holdingsTally(await holdings(s, 'Cedar'))
  check('items another plugin keeps stay with the player, not in the grave', kept.cobblestone === 10 && !graveTally(mine).cobblestone, JSON.stringify(kept))
  await s.run('tag Cedar remove lg_keep', 'tag Cedar remove lg_add')
  b.quit(); await sleep(500)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const s = new Server(newServer('extras', { plugins: [HOOK] }))
  await s.start()
  try { await run(s) } finally { check('no LanternGraves errors in the log', s.errors().length === 0, s.errors()[0]?.slice(0, 300)); await s.stop(); s.discard() }
  summary()
}
