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
  'paper-1.21.11.jar': 'https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar',
  'paper-26.3.jar': 'https://fill-data.papermc.io/v1/objects/9601f9a26a2a52a0f2c807cf1abf691f96ee4fb287878313b601cebc2c51eabf/paper-26.3-244.jar',
  'multiverse-core-5.8.2.jar': 'https://cdn.modrinth.com/data/3wmN97b8/versions/de7TbcZG/multiverse-core-5.8.2.jar',
  'multiverse-inventories-5.3.6.jar': 'https://cdn.modrinth.com/data/qvdtDX3s/versions/86s1EwyC/multiverse-inventories-5.3.6.jar',
  'ViaVersion-5.12.2.jar': 'https://cdn.modrinth.com/data/P1OZGk5p/versions/Fkfcr89K/ViaVersion-5.12.2.jar',
  'ViaBackwards-5.12.2.jar': 'https://cdn.modrinth.com/data/NpvuJQoq/versions/WFO37e8T/ViaBackwards-5.12.2.jar',
  'worldedit-bukkit-7.4.6.jar': 'https://cdn.modrinth.com/data/1u6JkXh5/versions/YFnqiUlL/worldedit-bukkit-7.4.6.jar',
}

for (const [name, url] of Object.entries(FILES)) {
  const to = path.join(JARS, name)
  if (existsSync(to)) continue
  const res = await fetch(url, { headers: { 'User-Agent': 'LanternGraves-tests' } })
  if (!res.ok) throw new Error(`${name}: HTTP ${res.status}`)
  await pipeline(Readable.fromWeb(res.body), createWriteStream(to))
  console.log('fetched ' + name)
}
