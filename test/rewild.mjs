// Rewild never resets a chunk with a grave in it. LanternGraves tells Rewild which chunks hold graves; without that
// report, Rewild refuses to reset anything while LanternGraves runs. A made-up flat world, one bot.
// Needs a Rewild jar in LG_TEST_BASE/jars (Rewild-*.jar).
import path from 'node:path'
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { Server, newServer, bot, sleep, check, summary, graveFiles, respawned, JARS } from './harness.mjs'
import { kit } from './basic.mjs'

const rewildJar = readdirSync(JARS).find(f => /^Rewild-.*\.jar$/.test(f))
if (!rewildJar) throw new Error('No Rewild-*.jar in ' + JARS)

export async function run(s) {
  const a = await bot('Alder')
  await s.run('execute in minecraft:overworld run tp Alder 100 -60 100'); await sleep(1500)
  await kit(s, 'Alder')
  await s.ask('kill Alder', 1500); await respawned(a)
  let g
  for (let i = 0; i < 50 && !g; i++) { g = graveFiles(s).find(x => x.owner === 'Alder' && !x.pending); if (!g) await sleep(200) }
  check('a grave was made', !!g)
  const cx = Math.floor((g?.pos[0] ?? 100) / 16), cz = Math.floor((g?.pos[2] ?? 100) / 16)
  const why = (await s.ask(`rewild why world ${cx} ${cz}`, 2500)).join('\n')
  check("Rewild names the grave as the reason the chunk is kept", /a grave \(LanternGraves\)/.test(why), why.slice(0, 300))
  check("and doesn't say LanternGraves isn't reporting", !/LanternGraves is running but doesn't report/.test(why), why.slice(0, 300))
  check('LanternGraves said it registered', s.log.some(l => /Telling Rewild which chunks hold graves/.test(l)))
  const far = (await s.ask(`rewild why world ${cx + 20} ${cz + 20}`, 2500)).join('\n')
  check('control: a chunk far from the grave has no grave reason', !/a grave/.test(far), far.slice(0, 200))
  a.quit(); await sleep(500)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const dir = newServer('rewild', { plugins: [path.join(JARS, rewildJar)] })
  // Rewild with the main world switched on and no claim plugin, so only the keep report is in play.
  mkdirSync(path.join(dir, 'plugins/Rewild'), { recursive: true })
  const shipped = readFileSync(new URL('../../Rewild/src/main/resources/config.yml', import.meta.url), 'utf8')
  const cfg = shipped.replace('  example_world:\n    enabled: false', '  world:\n    enabled: true').replace('  plugin: GriefPrevention', '  plugin: none')
  if (cfg === shipped) throw new Error("Rewild's shipped config no longer has the lines this test changes")
  writeFileSync(path.join(dir, 'plugins/Rewild/config.yml'), cfg)
  const s = new Server(dir)
  await s.start()
  try {
    await run(s)
    // Switched off: LanternGraves stays quiet, and today's Rewild then resets nothing while both run.
    await s.stop()
    const lg = path.join(dir, 'plugins/LanternGraves/config.yml')
    writeFileSync(lg, readFileSync(lg, 'utf8').replace('  keep-graves: true', '  keep-graves: false'))
    await s.start()
    const off = (await s.ask('rewild why world 0 0', 2500)).join('\n')
    check('with rewild.keep-graves off, LanternGraves says so at startup', s.log.some(l => /rewild\.keep-graves is off/.test(l)))
    check("and Rewild resets nothing, naming LanternGraves as the reason", /LanternGraves is running but doesn't report/.test(off), off.slice(0, 300))
  } finally {
    check('no LanternGraves errors in the log', s.errors().length === 0, s.errors()[0]?.slice(0, 300))
    await s.stop(); s.discard()
  }
  summary()
}
