// Runs every suite on every version, one server at a time, and prints one line per suite.
// Usage: LG_TEST_BASE=… LG_TEST_EULA_FROM=… node all.mjs [versions…] [--suites=a,b]
import { spawnSync } from 'node:child_process'
const args = process.argv.slice(2)
const versions = args.filter(a => !a.startsWith('--'))
const only = (args.find(a => a.startsWith('--suites=')) ?? '').slice(9).split(',').filter(Boolean)
const SUITES = only.length ? only : ['basic', 'extras', 'hazards', 'rules', 'crash', 'mvinv', 'griefprevention']
let failed = 0
for (const v of versions.length ? versions : ['26.1.2', '26.2', '26.3']) {
  for (const suite of SUITES) {
    const r = spawnSync('node', [`${suite}.mjs`], { env: { ...process.env, LG_TEST_VERSION: v }, encoding: 'utf8', maxBuffer: 64 << 20 })
    const out = (r.stdout ?? '') + (r.stderr ?? '')
    const total = out.match(/^(\d+)\/(\d+) passed$/m)
    const notes = [...new Set(out.split('\n').filter(l => /^ {2}\w+ wasn't ticked|^FAIL/.test(l)))]
    if (!total || total[1] !== total[2] || r.status !== 0) failed++
    console.log(`${v.padEnd(7)} ${suite.padEnd(8)} ${total ? total[0] : 'did not finish: ' + (out.match(/Error.*$/m)?.[0] ?? r.status)}`)
    for (const n of notes) console.log('          ' + n.trim())
  }
}
process.exitCode = failed ? 1 : 0
