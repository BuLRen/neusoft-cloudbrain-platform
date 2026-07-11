import { copyFileSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const target = join(root, 'src/config/api.local.ts')
const example = join(root, 'src/config/api.local.example.ts')

if (!existsSync(target)) {
  copyFileSync(example, target)
  console.log('[api-config] 已从 api.local.example.ts 生成 api.local.ts')
}
