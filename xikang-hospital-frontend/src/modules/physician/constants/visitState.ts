export type VisitStateTone = 'primary' | 'success' | 'warning' | 'neutral'

export const VISIT_STATE = {
  REGISTERED: 1,
  IN_PROGRESS: 2,
  ENDED: 3,
  CANCELLED: 4,
  EXAM_PENDING: 5,
  EXAM_COMPLETED: 6,
} as const

export const PHYSICIAN_QUEUE = '/physician/queue'

export const PHYSICIAN_ASSISTANT = '/physician/assistant'

export const PHYSICIAN_STEP_PATHS = [
  '/physician/record',
  '/physician/orders',
  '/physician/results',
  '/physician/diagnosis',
  '/physician/prescription',
] as const

export const PHYSICIAN_ENCOUNTER_PATHS = [
  ...PHYSICIAN_STEP_PATHS,
  PHYSICIAN_ASSISTANT,
] as const

export const PHYSICIAN_PATH_TITLES: Record<string, string> = {
  [PHYSICIAN_QUEUE]: '待诊接诊',
  '/physician/record': '病历与初步诊断',
  '/physician/orders': '开立检查检验',
  '/physician/results': '查看结果',
  '/physician/diagnosis': '门诊确诊',
  '/physician/prescription': '开立处方',
  [PHYSICIAN_ASSISTANT]: 'AI 助手',
}

export const VISIT_STATE_LABEL: Record<number, { text: string; tone: VisitStateTone }> = {
  [VISIT_STATE.REGISTERED]: { text: '待接诊', tone: 'warning' },
  [VISIT_STATE.IN_PROGRESS]: { text: '接诊中', tone: 'primary' },
  [VISIT_STATE.EXAM_PENDING]: { text: '检查检验中', tone: 'warning' },
  [VISIT_STATE.EXAM_COMPLETED]: { text: '检查检验完成', tone: 'success' },
}

export function visitStateLabel(visitState: number) {
  return VISIT_STATE_LABEL[visitState] ?? { text: '未知', tone: 'neutral' as VisitStateTone }
}

export function isPhysicianEncounterPath(path: string) {
  return (PHYSICIAN_ENCOUNTER_PATHS as readonly string[]).includes(path)
}

export function isPhysicianStepPath(path: string) {
  return (PHYSICIAN_STEP_PATHS as readonly string[]).includes(path)
}

export function resumePathForVisitState(visitState: number): string {
  if (visitState === VISIT_STATE.EXAM_PENDING || visitState === VISIT_STATE.EXAM_COMPLETED) {
    return '/physician/results'
  }
  return '/physician/record'
}

export function physicianRoute(path: string, registerId?: number | null) {
  if (!registerId) return path
  return { path, query: { registerId: String(registerId) } }
}

export function physicianPathTitle(path: string) {
  return PHYSICIAN_PATH_TITLES[path] ?? '门诊诊疗'
}
