import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import XLSX from 'xlsx'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const outputDir = path.resolve(__dirname, '../public/samples/personnel')

function writeWorkbook(filename, sheetName, headers, rows) {
  const worksheet = XLSX.utils.aoa_to_sheet([headers, ...rows])
  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, worksheet, sheetName)
  const buffer = XLSX.write(workbook, { type: 'buffer', bookType: 'xlsx' })
  return writeFile(path.join(outputDir, filename), buffer)
}

await mkdir(outputDir, { recursive: true })

await writeWorkbook(
  'physician_import_sample.xlsx',
  '导入数据',
  ['姓名', '科室', '挂号级别'],
  [
    ['张三', '内科', '专家号'],
    ['李测试', '外科', '普通号'],
  ],
)

await writeWorkbook(
  'medtech_import_sample.xlsx',
  '导入数据',
  ['姓名', '医技科室'],
  [
    ['王检验', '检验科'],
    ['赵放射', '放射科'],
  ],
)

console.log('Generated sample files in', outputDir)
