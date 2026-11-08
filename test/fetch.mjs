// Downloads the server and plugin jars the tests run against, into $LG_TEST_BASE/jars.
// Pinned so a test run means the same thing next week. Nothing here touches a real server.
import { createWriteStream, existsSync, mkdirSync } from 'node:fs'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import path from 'node:path'

const BASE = process.env.LG_TEST_BASE
if (!BASE) throw new Error('Set LG_TEST_BASE to a throwaway folder')
const JARS = path.join(BASE, 'jars')
mkdirSync(JARS, { recursive: true })

export const FILES = {
  'paper-26.1.2.jar': 'https://fill-data.papermc.io/v1/objects/1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7/paper-26.1.2-74.jar',
  'paper-26.2.jar': 'https://fill-data.papermc.io/v1/objects/ef3dbc68b013deafdbbee3bebf1ba351671ebcda94a8f136809b7532d5ba2fe1/paper-26.2-135.jar',
  'paper-26.3.jar': 'https://fill-data.papermc.io/v1/objects/9601f9a26a2a52a0f2c807cf1abf691f96ee4fb287878313b601cebc2c51eabf/paper-26.3-244.jar',
  'multiverse-core-5.8.2.jar': 'https://cdn.modrinth.com/data/3wmN97b8/versions/de7TbcZG/multiverse-core-5.8.2.jar',
  'multiverse-inventories-5.3.6.jar': 'https://cdn.modrinth.com/data/qvdtDX3s/versions/86s1EwyC/multiverse-inventories-5.3.6.jar',
  'ViaVersion-5.12.2.jar': 'https://cdn.modrinth.com/data/P1OZGk5p/versions/Fkfcr89K/ViaVersion-5.12.2.jar',
  'ViaBackwards-5.12.2.jar': 'https://cdn.modrinth.com/data/NpvuJQoq/versions/WFO37e8T/ViaBackwards-5.12.2.jar',
  'worldedit-bukkit-7.4.6.jar': 'https://cdn.modrinth.com/data/1u6JkXh5/versions/YFnqiUlL/worldedit-bukkit-7.4.6.jar',
  // 16.18.7 lists 26.1 to 26.3; 16.18.8 lists only 26.3.
  'GriefPrevention-16.18.7.jar': 'https://cdn.modrinth.com/data/O4o4mKaq/versions/dGfCZHqk/GriefPrevention.jar',
  'GriefPrevention-16.18.8.jar': 'https://cdn.modrinth.com/data/O4o4mKaq/versions/MtwPHMAS/GriefPrevention.jar',
}

// The SHA-512 Modrinth publishes for these.
const SHA512 = {
  'GriefPrevention-16.18.7.jar': 'c9bc692253ba3860327e5c38767ce3dc66c798264fe650a08b4ae888337ff75bc16e9bd1db7b39a514a275bf2cc2a3f1f8cd95cf080b89ae42a0f684fc2bfc66',
  'GriefPrevention-16.18.8.jar': '248edc681e94fe333aa4262b4e8858e9e8f79e7e4872a76348ff6df213cc56e6069eea9916c5c4e6476b1513575f0d45db76c91526f5dc032e02b8806341b9e9',
}

// Paper publishes a SHA-256 for every build; each Paper jar is checked against it.
async function paperSha(version, build) {
  const r = await fetch(`https://fill.papermc.io/v3/projects/paper/versions/${version}/builds/${build}`, { headers: { 'User-Agent': 'LanternGraves-tests' } })
  return (await r.json()).downloads['server:default'].checksums.sha256
}

for (const [name, url] of Object.entries(FILES)) {
  const to = path.join(JARS, name)
  const paper = url.match(/paper-([\d.]+)-(\d+)\.jar$/)
  if (existsSync(to)) {
    if (paper) await verify(to, await paperSha(paper[1], paper[2]))
    continue
  }
  const res = await fetch(url, { headers: { 'User-Agent': 'LanternGraves-tests' } })
  if (!res.ok) throw new Error(`${name}: HTTP ${res.status}`)
  await pipeline(Readable.fromWeb(res.body), createWriteStream(to))
  if (paper) await verify(to, await paperSha(paper[1], paper[2]))
  if (SHA512[name]) await verify(to, SHA512[name], 'sha512', 'Modrinth')
  console.log('fetched ' + name)
}

async function verify(file, want, algo = 'sha256', who = 'Paper') {
  const { createHash } = await import('node:crypto')
  const { readFileSync, rmSync } = await import('node:fs')
  const got = createHash(algo).update(readFileSync(file)).digest('hex')
  if (got !== want) { rmSync(file); throw new Error(`${path.basename(file)}: checksum ${got} isn't ${who}'s ${want}; deleted`) }
  console.log(`${path.basename(file)}: matches ${who}'s ${algo.toUpperCase()}`)
}
