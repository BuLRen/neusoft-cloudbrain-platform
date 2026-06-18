export type VisitStateTone = 'primary' | 'success' | 'warning' | 'neutral'

export const VISIT_STATE = {
  REGISTERED: 1,
  IN_PROGRESS: 2,
  ENDED: 3,
  CANCELLED: 4,
  EXAM_PENDING: 5,
  EXAM_COMPLETED: 6,
} as const

export const VISIT_STATE_LABEL: Record<number, { text: string; tone: VisitStateTone }> = {
  [VISIT_STATE.REGISTERED]: { text: '待接诊', tone: 'warning' },
  [VISIT_STATE.IN_PROGRESS]: { text: '接诊中', tone: 'primary' },
  [VISIT_STATE.EXAM_PENDING]: { text: '检查检验中', tone: 'warning' },
  [VISIT_STATE.EXAM_COMPLETED]: { text: '检查检验完成', tone: 'success' },
}

export function visitStateLabel(visitState: number) {
  return VISIT_STATE_LABEL[visitState] ?? { text: '未知', tone: 'neutral' as VisitStateTone }
}

/** 从队列进入流程时的默认步骤 */
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
