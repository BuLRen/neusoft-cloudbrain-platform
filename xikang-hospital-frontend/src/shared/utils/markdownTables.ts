function stripBold(cell: string) {
  return cell.replace(/^\*\*|\*\*$/g, '').trim()
}

function escapeCell(cell: string) {
  return cell.replace(/\|/g, '\\|')
}

function parseRowCells(line: string): string[] {
  const trimmed = line.trim()
  if (!trimmed) return []

  if (trimmed.includes('|')) {
    return trimmed
      .split('|')
      .map((cell) => stripBold(cell))
      .filter(Boolean)
  }

  const byMultiSpace = trimmed.split(/\s{2,}/).map(stripBold).filter(Boolean)
  if (byMultiSpace.length >= 2) return byMultiSpace

  const allBold = [...trimmed.matchAll(/\*\*([^*]+)\*\*/g)].map((match) => match[1].trim())
  if (allBold.length >= 2) return allBold

  return [stripBold(trimmed)]
}

function lineLooksLikeDiffHeader(line: string) {
  const plain = line.replace(/\*\*/g, '')
  return plain.includes('项目') && plain.includes('修改前') && plain.includes('修改后')
}

function toGfmTable(rows: string[][]): string {
  if (!rows.length) return ''

  const colCount = Math.max(...rows.map((row) => row.length))
  const padRow = (row: string[]) => {
    const padded = row.map((cell) => escapeCell(cell))
    while (padded.length < colCount) padded.push('')
    return padded
  }

  const [header, ...body] = rows
  const lines = [
    `| ${padRow(header).join(' | ')} |`,
    `| ${padRow(header).map(() => '---').join(' | ')} |`,
    ...body.map((row) => `| ${padRow(row).join(' | ')} |`),
  ]
  return lines.join('\n')
}

/**
 * Agent 回复里常见「项目 / 修改前 / 修改后」空格分列伪表格，marked 无法识别。
 * 在 Markdown 渲染前将其转为 GFM pipe table。
 */
export function normalizeMarkdownTables(source: string): string {
  if (!source.trim()) return source

  const lines = source.replace(/\r\n/g, '\n').split('\n')
  const output: string[] = []

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]
    if (!lineLooksLikeDiffHeader(line)) {
      output.push(line)
      continue
    }

    const rows: string[][] = [parseRowCells(line)]
    let cursor = index + 1
    while (cursor < lines.length) {
      const nextLine = lines[cursor]
      if (!nextLine.trim() || lineLooksLikeDiffHeader(nextLine)) break

      const cells = parseRowCells(nextLine)
      if (cells.length < 2) break

      rows.push(cells)
      cursor += 1
    }

    if (rows.length >= 2) {
      output.push(toGfmTable(rows))
      index = cursor - 1
      continue
    }

    output.push(line)
  }

  return output.join('\n')
}
