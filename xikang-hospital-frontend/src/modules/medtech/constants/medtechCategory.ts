export const MEDTECH_CHECK_CATEGORY_OPTIONS = [
  { value: 'general_check', label: '普通检查' },
  { value: 'imaging_ct_chest', label: 'CT 影像 · 胸部' },
  { value: 'imaging_ct_brain', label: 'CT 影像 · 头颅/脑部' },
  { value: 'imaging_ct', label: 'CT 影像 · 通用' },
] as const

export const MEDTECH_INSPECTION_CATEGORY_OPTIONS = [
  { value: 'general_lab', label: '通用检验' },
] as const

export type MedtechAiCategoryCode =
  | (typeof MEDTECH_CHECK_CATEGORY_OPTIONS)[number]['value']
  | (typeof MEDTECH_INSPECTION_CATEGORY_OPTIONS)[number]['value']

export function isCtCategoryCode(code?: string | null): boolean {
  return (code ?? '').startsWith('imaging_ct')
}

export function defaultAiCategoryForTechType(techType: 'check' | 'inspection' | 'disposal'): string | undefined {
  if (techType === 'check') return 'general_check'
  if (techType === 'inspection') return 'general_lab'
  return undefined
}

export function aiCategoryLabel(code?: string | null): string {
  if (!code) return '—'
  const all = [...MEDTECH_CHECK_CATEGORY_OPTIONS, ...MEDTECH_INSPECTION_CATEGORY_OPTIONS]
  return all.find((item) => item.value === code)?.label ?? code
}
