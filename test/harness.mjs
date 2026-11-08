// Test harness: builds a fresh throwaway Paper server in a temp folder, drives it from the console and
// with mineflayer bots, and keeps a ledger of every item so a test can prove nothing was lost or copied.
// Modelled on Waystation's harness, minus its copy of a real server: everything here starts empty.
import { spawn, execSync } from 'node:child_process'
import { existsSync, readFileSync, readdirSync, writeFileSync, copyFileSync, mkdirSync, rmSync } from 'node:fs'
import { gunzipSync } from 'node:zlib'
import path from 'node:path'
import mineflayer from 'mineflayer'
import nbt from 'prismarine-nbt'

export const BASE = process.env.LG_TEST_BASE
if (!BASE) throw new Error('Set LG_TEST_BASE to a throwaway folder (node fetch.mjs fills its jars/)')
export const JARS = path.join(BASE, 'jars')
export const PORT = 25611
const ROOT = new URL('../', import.meta.url).pathname
const LIBS = path.join(ROOT, 'build/libs/')
const built = existsSync(LIBS) ? readdirSync(LIBS).filter(f => /^LanternGraves-[\d.]+\.jar$/.test(f)) : []
if (built.length !== 1) throw new Error(`Run ./gradlew build first: expected one LanternGraves jar in build/libs, found ${built.length}`)
export const JAR = path.join(LIBS, built[0])
// Paper 26.1.2, 26.2 or 26.3. The bots speak 1.21.11, the newest mineflayer knows, and reach 26.x
// through ViaVersion and ViaBackwards, which every test server gets.
export const VERSION = process.env.LG_TEST_VERSION ?? '26.3'
export const JAVA = process.env.LG_TEST_JAVA ?? '/Library/Java/JavaVirtualMachines/openjdk-25.jdk/Contents/Home/bin/java'
const VIA = ['ViaVersion-5.12.2.jar', 'ViaBackwards-5.12.2.jar']

export const sleep = ms => new Promise(r => setTimeout(r, ms))

/** A new server folder: Paper, the plugins asked for, a flat world, 127.0.0.1 only, offline mode. */
export function newServer(name, { plugins = [], props = {}, version = VERSION } = {}) {
  const dir = path.join(BASE, 'run', `${name}-${version}`)
  rmSync(dir, { recursive: true, force: true, maxRetries: 10, retryDelay: 500 })
  mkdirSync(path.join(dir, 'plugins'), { recursive: true })
  copyFileSync(path.join(JARS, `paper-${version}.jar`), path.join(dir, 'paper.jar'))
  const warm = path.join(BASE, 'warm', version)
  for (const f of ['libraries', 'versions', 'cache']) if (existsSync(path.join(warm, f))) execSync(`cp -cR "${path.join(warm, f)}" "${path.join(dir, f)}"`)
  for (const p of [JAR, ...VIA.map(v => path.join(JARS, v)), ...plugins]) copyFileSync(p, path.join(dir, 'plugins', path.basename(p)))
  // The EULA answer is the owner's: copy it from a folder where they've already accepted it.
  const eulaFrom = process.env.LG_TEST_EULA_FROM
  if (!eulaFrom || !/^eula=true$/m.test(readFileSync(path.join(eulaFrom, 'eula.txt'), 'utf8'))) {
    throw new Error('Set LG_TEST_EULA_FROM to a folder whose eula.txt the owner has already accepted')
  }
  copyFileSync(path.join(eulaFrom, 'eula.txt'), path.join(dir, 'eula.txt'))
  const all = {
    'server-ip': '127.0.0.1', 'server-port': PORT, 'online-mode': false, 'level-type': 'minecraft\\:flat',
    'spawn-protection': 0, 'enable-query': false, 'enable-rcon': false, 
    'view-distance': 6, 'simulation-distance': 6, 'generate-structures': false, 'white-list': false, difficulty: 'peaceful', 'enforce-whitelist': false, ...props,
  }
  writeFileSync(path.join(dir, 'server.properties'), Object.entries(all).map(([k, v]) => `${k}=${v}`).join('\n') + '\n')
  return dir
}

/** After a first boot, keep Paper's patched jars so the next server starts in seconds. */
function keepWarm(dir, version) {
  const warm = path.join(BASE, 'warm', version)
  if (existsSync(warm)) return
  mkdirSync(warm, { recursive: true })
  for (const f of ['libraries', 'versions', 'cache']) if (existsSync(path.join(dir, f))) execSync(`cp -cR "${path.join(dir, f)}" "${path.join(warm, f)}"`)
}

export class Server {
  log = []
  constructor(dir, { version = VERSION, memory = '2G' } = {}) { Object.assign(this, { dir, version, memory }) }
  async start() {
    const from = this.log.length
    this.proc = spawn(JAVA, [`-Xms${this.memory}`, `-Xmx${this.memory}`, '-Dterminal.jline=false', '-Dterminal.ansi=false',
      '-Dpaper.disableChannelLimit=true', '-jar', 'paper.jar', 'nogui'], { cwd: this.dir, stdio: ['pipe', 'pipe', 'pipe'] })
    this.exited = new Promise(r => this.proc.on('exit', r))
    const proc = this.proc
    process.on('exit', () => { if (proc.exitCode === null) proc.kill('SIGKILL') }) // never leave a server behind
    let partial = ''
    const add = d => {
      const parts = (partial + d.toString()).split('\n')
      partial = parts.pop()
      for (const l of parts) if (l.trim()) this.log.push(l)
    }
    this.proc.stdout.on('data', add)
    this.proc.stderr.on('data', add)
    await this.waitFor(/Done \(\d/, 240000, from)
    keepWarm(this.dir, this.version)
    await sleep(1500)
  }
  async waitFor(re, ms = 15000, from = 0) {
    const end = Date.now() + ms
    while (Date.now() < end) {
      const hit = this.log.slice(from).find(l => re.test(l))
      if (hit) return hit
      if (this.proc.exitCode !== null) throw new Error('Server exited while waiting for ' + re)
      await sleep(50)
    }
    throw new Error('Timed out waiting for ' + re)
  }
  send(cmd) { this.proc.stdin.write(cmd + '\n') }
  /** Sends a console command and returns the log lines it produced, without the time prefix. */
  async ask(cmd, ms = 500) {
    const from = this.log.length
    this.send(cmd)
    await sleep(ms)
    return this.log.slice(from).map(l => l.replace(/^\[[\d:]+ \w+\]: /, '').replace(/^\[[^\]]+\] ?/, '').replace(/^System chat: /, ''))
  }
  async run(...cmds) { for (const c of cmds) { this.send(c) ; await sleep(80) } await sleep(300) }
  async count(selector) {
    const out = await this.ask(`execute if entity ${selector}`, 400)
    const hit = out.find(l => /Test passed|Test failed/.test(l))
    const m = hit && hit.match(/count: (\d+)/i)
    return m ? Number(m[1]) : 0
  }
  /** One piece of an entity's data, as a JS value, or undefined if it has none. */
  async data(target, field = '') {
    const out = await this.ask(`data get entity ${target} ${field}`.trim(), 600)
    const line = out.find(l => / has the following entity data: /.test(l))
    if (!line) return undefined
    return parseSnbt(line.slice(line.indexOf(' has the following entity data: ') + 32))
  }
  errors() { return this.log.filter(l => /LanternGraves/.test(l) && /(ERROR|Exception|SEVERE)/.test(l) && !/DAMAGED|damaged|won't load|Reason:|A copy|won't overwrite|\*{20}/.test(l)) }
  async stop() {
    this.send('stop')
    const done = await Promise.race([this.exited.then(() => true), sleep(90000).then(() => false)])
    if (!done) { console.log('  server stuck on stop: killing it'); await this.kill9() }
  }
  kill9() { this.proc.kill('SIGKILL'); return this.exited }
  graveDir() { return path.join(this.dir, 'plugins/LanternGraves/graves') }
}

// ---------------------------------------------------------------- bots

/**
 * physics: on Paper 26.3 behind ViaVersion 5.12.2 a 1.21.11 bot is kicked for "Invalid move player packet"
 * once it moves on its own (seen in ReturnGate and WildernessRegen too). So there the bots don't move
 * themselves: their movement packets are dropped and the console teleports them. On 26.1.2 and 26.2 they walk.
 */
export const WALKS = VERSION !== '26.3'
export async function bot(username, { port = PORT } = {}) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port, username, version: '1.21.11', auth: 'offline', viewDistance: 'tiny', physicsEnabled: WALKS })
  if (!WALKS) {
    const write = b._client.write.bind(b._client)
    b._client.write = (name, data) => (/^(position|position_look|look|flying)$/.test(name) ? undefined : write(name, data))
  }
  b.chat_log = []
  b.on('messagestr', m => b.chat_log.push(m))
  b.on('error', e => console.log(`  [${username}] error: ${e.message}`))
  b.on('kicked', r => console.log(`  [${username}] kicked: ${JSON.stringify(r).slice(0, 200)}`))
  await new Promise((resolve, reject) => {
    b.once('spawn', resolve)
    b.once('end', () => reject(new Error(username + ' disconnected before spawning')))
    setTimeout(() => reject(new Error(username + ' spawn timeout')), 60000)
  })
  await sleep(1200)
  return b
}

/** Waits for a bot to be alive again after dying (mineflayer respawns on its own). */
export async function respawned(b, ms = 10000) {
  const end = Date.now() + ms
  await sleep(300)
  while (Date.now() < end) {
    if (b.health > 0 && b.entity) { if (!WALKS) b.physicsEnabled = false; return true }
    await sleep(100)
  }
  return false
}

export const markers = (b, kind) => Object.values(b.entities).filter(e => e.name === kind)

/** Right-clicks the nearest grave hitbox the bot can see. */
export async function rightClickGrave(b, near) {
  const boxes = markers(b, 'interaction').filter(e => !near || e.position.distanceTo(near) < 3)
  boxes.sort((x, y) => x.position.distanceTo(b.entity.position) - y.position.distanceTo(b.entity.position))
  if (!boxes.length) return false
  if (WALKS) await b.lookAt(boxes[0].position.offset(0, 0.5, 0), true)
  // A real client sends both: "interact at" a point on the entity, then "interact".
  b.activateEntityAt(boxes[0], boxes[0].position.offset(0, 0.5, 0))
  b.activateEntity(boxes[0])
  await sleep(600)
  return true
}

// ---------------------------------------------------------------- items

/** Every item a player holds: their inventory and equipment, slot by slot, as SNBT-like JSON strings. */
export async function holdings(s, name) {
  const inv = (await s.data(name, 'Inventory')) ?? []
  const eq = (await s.data(name, 'equipment')) ?? {}
  const slots = {}
  for (const it of inv) {
    const { Slot, ...rest } = it
    slots[Number(Slot)] = canon(rest)
  }
  for (const [k, v] of Object.entries(eq)) slots[k] = canon(v)
  return slots
}

/** All the items in a grave folder, decoded from Paper's bytes. */
export function graveFiles(s) {
  const dir = s.graveDir()
  if (!existsSync(dir)) return []
  return readdirSync(dir).filter(f => f.endsWith('.yml')).map(f => {
    const text = readFileSync(path.join(dir, f), 'utf8')
    return { file: f, text, ...readGrave(text) }
  })
}

export function readGrave(text) {
  const items = {}
  const re = /^ {2}'?(\d+)'?:\n {4}type: (\S+)\n {4}amount: (\d+)\n {4}data: (\S+)/gm
  let m
  while ((m = re.exec(text))) items[m[1]] = { type: m[2], amount: Number(m[3]), nbt: decodeItem(m[4]) }
  const xp = Number((text.match(/^xp: (\d+)/m) ?? [0, 0])[1])
  const pending = /^pending:/m.test(text)
  const owner = (text.match(/^owner-name: (\S+)/m) ?? [])[1]
  const world = (text.match(/^world: (\S+)/m) ?? [])[1]
  const pos = ['x', 'y', 'z'].map(k => Number((text.match(new RegExp(`^${k}: (\\S+)`, 'm')) ?? [0, NaN])[1]))
  return { items, xp, pending, owner, world, pos }
}

function decodeItem(b64) {
  const raw = gunzipSync(Buffer.from(b64, 'base64'))
  const parsed = nbt.parseUncompressed(raw, 'big')
  return nbt.simplify(parsed)
}

/** Counts items by id, opening shulker boxes and bundles, from SNBT/NBT-shaped values. */
export function tally(items, into = {}) {
  for (const it of items) {
    if (!it) continue
    const id = String(it.id).replace(/^minecraft:/, '')
    const count = Number(it.count ?? it.Count ?? 1)
    into[id] = (into[id] ?? 0) + count
    const comps = it.components ?? {}
    for (const c of comps['minecraft:container'] ?? []) tally([c.item], into)
    for (const c of comps['minecraft:bundle_contents'] ?? []) tally([c], into)
  }
  return into
}

export function holdingsTally(slots) { return tally(Object.values(slots).map(s => JSON.parse(s))) }
export function graveTally(graves) {
  const t = {}
  for (const g of graves) tally(Object.values(g.items).map(i => ({ ...i.nbt, count: i.amount })), t)
  return t
}
export function addTallies(...ts) {
  const out = {}
  for (const t of ts) for (const [k, v] of Object.entries(t)) out[k] = (out[k] ?? 0) + v
  return out
}
export function sameTally(a, b) {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)])
  const diff = [...keys].filter(k => (a[k] ?? 0) !== (b[k] ?? 0)).map(k => `${k}: ${a[k] ?? 0} vs ${b[k] ?? 0}`)
  return { ok: diff.length === 0, diff: diff.join(', ') }
}

function canon(v) {
  if (Array.isArray(v)) return '[' + v.map(canon).join(',') + ']'
  if (v && typeof v === 'object') return '{' + Object.keys(v).sort().map(k => JSON.stringify(k) + ':' + canon(v[k])).join(',') + '}'
  return JSON.stringify(v)
}
export function parseCanon(s) { return JSON.parse(s) }

// ---------------------------------------------------------------- SNBT, as printed by /data get

export function parseSnbt(text) {
  let i = 0
  const ws = () => { while (i < text.length && /\s/.test(text[i])) i++ }
  const value = () => {
    ws()
    const c = text[i]
    if (c === '{') {
      i++; const o = {}; ws()
      if (text[i] === '}') { i++; return o }
      for (;;) {
        ws(); const k = key(); ws(); i++ /* : */; o[k] = value(); ws()
        if (text[i] === ',') { i++; continue }
        i++; return o
      }
    }
    if (c === '[') {
      i++; ws()
      if (/[BIL]/.test(text[i]) && text[i + 1] === ';') i += 2
      const a = []; ws()
      if (text[i] === ']') { i++; return a }
      for (;;) {
        a.push(value()); ws()
        if (text[i] === ',') { i++; continue }
        i++; return a
      }
    }
    if (c === '"' || c === "'") return str()
    const m = text.slice(i).match(/^[^,\]}\s]+/)
    i += m[0].length
    const t = m[0]
    if (/^-?\d+(\.\d+)?([bBsSlLfFdD])?$/.test(t) || /^-?\d*\.\d+([fFdD])?$/.test(t) || /^-?\d+(\.\d+)?[eE][-+]?\d+[fFdD]?$/.test(t)) return Number(t.replace(/[bBsSlLfFdD]$/, ''))
    if (t === 'true') return 1
    if (t === 'false') return 0
    return t
  }
  const key = () => (text[i] === '"' || text[i] === "'") ? str() : (() => { const m = text.slice(i).match(/^[^:\s]+/); i += m[0].length; return m[0] })()
  const str = () => {
    const q = text[i++]; let s = ''
    while (text[i] !== q) { if (text[i] === '\\') i++; s += text[i++] }
    i++; return s
  }
  return value()
}

// ---------------------------------------------------------------- results

export const results = []
export function check(name, ok, detail = '') {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`)
  return ok
}
export function summary() {
  const failed = results.filter(r => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} passed`)
  for (const f of failed) console.log('  FAILED: ' + f.name + (f.detail ? ' (' + f.detail + ')' : ''))
  if (failed.length) process.exitCode = 1
  return failed.length === 0
}
