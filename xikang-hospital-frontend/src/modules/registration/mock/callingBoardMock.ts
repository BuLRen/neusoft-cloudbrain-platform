import type {
  CallItem,
  CallingBoardActiveResponse,
  CallingBoardHubResponse,
  DeptBoardItem,
} from '@/shared/types/calling'

const MOCK_POOL: Omit<CallItem, 'registerId' | 'calledTime' | 'queueNumber'>[] = [
  { patientName: '陈晓明', departmentId: 1, departmentName: '内科', doctorId: 101, doctorName: '李建国', clinicRoom: '内科3诊室', callStatus: 1, waitingCount: 11, nextQueueNumber: 13 },
  { patientName: '王芳', departmentId: 1, departmentName: '内科', doctorId: 102, doctorName: '张丽华', clinicRoom: '内科5诊室', callStatus: 1, waitingCount: 6, nextQueueNumber: 8 },
  { patientName: '刘志强', departmentId: 2, departmentName: '外科', doctorId: 201, doctorName: '赵伟', clinicRoom: '外科1诊室', callStatus: 1, waitingCount: 4, nextQueueNumber: 5 },
  { patientName: '赵敏', departmentId: 2, departmentName: '外科', doctorId: 202, doctorName: '孙海涛', clinicRoom: '外科2诊室', callStatus: 1, waitingCount: 9, nextQueueNumber: 10 },
  { patientName: '周子轩', departmentId: 3, departmentName: '儿科', doctorId: 301, doctorName: '吴晓燕', clinicRoom: '儿科特需诊室', callStatus: 1, waitingCount: 15, nextQueueNumber: 16 },
  { patientName: '林小雨', departmentId: 4, departmentName: '骨科', doctorId: 401, doctorName: '郑国强', clinicRoom: '骨科4诊室', callStatus: 1, waitingCount: 3, nextQueueNumber: 4 },
  { patientName: '黄俊杰', departmentId: 5, departmentName: '眼科', doctorId: 501, doctorName: '钱慧敏', clinicRoom: '眼科检查室', callStatus: 1, waitingCount: 7, nextQueueNumber: 9 },
  { patientName: '徐婷婷', departmentId: 6, departmentName: '耳鼻喉科', doctorId: 601, doctorName: '冯志刚', clinicRoom: '耳鼻喉2诊室', callStatus: 1, waitingCount: 2, nextQueueNumber: 3 },
  { patientName: '何佳怡', departmentId: 7, departmentName: '皮肤科', doctorId: 701, doctorName: '曹雪梅', clinicRoom: '皮肤激光室', callStatus: 1, waitingCount: 5, nextQueueNumber: 6 },
  { patientName: '马文博', departmentId: 8, departmentName: '口腔科', doctorId: 801, doctorName: '谢俊杰', clinicRoom: '口腔修复室', callStatus: 1, waitingCount: 8, nextQueueNumber: 10 },
  { patientName: '杨思琪', departmentId: 9, departmentName: '妇科', doctorId: 901, doctorName: '韩雅琴', clinicRoom: '妇科1诊室', callStatus: 1, waitingCount: 12, nextQueueNumber: 14 },
  { patientName: '罗浩然', departmentId: 10, departmentName: '中医科', doctorId: 1001, doctorName: '唐明远', clinicRoom: '中医针灸室', callStatus: 1, waitingCount: 4, nextQueueNumber: 5 },
  { patientName: '邓丽华', departmentId: 11, departmentName: '康复科', doctorId: 1101, doctorName: '许建平', clinicRoom: '康复理疗区', callStatus: 1, waitingCount: 6, nextQueueNumber: 7 },
  { patientName: '蔡宇航', departmentId: 12, departmentName: '心内科', doctorId: 1201, doctorName: '潘志刚', clinicRoom: '心内专家室', callStatus: 1, waitingCount: 10, nextQueueNumber: 11 },
]

const IDLE_DEPARTMENTS: Pick<DeptBoardItem, 'departmentId' | 'departmentName'>[] = [
  { departmentId: 13, departmentName: '泌尿科' },
  { departmentId: 14, departmentName: '肿瘤科' },
]

const SIMULATION_NEW_CALLS: CallItem[] = [
  { registerId: 9901, patientName: '孙丽娟', queueNumber: 18, departmentId: 1, departmentName: '内科', doctorName: '李建国', clinicRoom: '内科3诊室', callStatus: 1, waitingCount: 12, nextQueueNumber: 19 },
  { registerId: 9902, patientName: '吴天宇', queueNumber: 7, departmentId: 3, departmentName: '儿科', doctorName: '吴晓燕', clinicRoom: '儿科特需诊室', callStatus: 1, waitingCount: 16, nextQueueNumber: 17 },
  { registerId: 9903, patientName: '郑美玲', queueNumber: 3, departmentId: 5, departmentName: '眼科', doctorName: '钱慧敏', clinicRoom: '眼科检查室', callStatus: 1, waitingCount: 8, nextQueueNumber: 9 },
  { registerId: 9904, patientName: '胡志强', queueNumber: 22, departmentId: 2, departmentName: '外科', doctorName: '赵伟', clinicRoom: '外科1诊室', callStatus: 1, waitingCount: 5, nextQueueNumber: 6 },
]

function isoMinutesAgo(minutes: number): string {
  const d = new Date()
  d.setMinutes(d.getMinutes() - minutes)
  return d.toISOString()
}

function toCallItem(
  base: (typeof MOCK_POOL)[number],
  registerId: number,
  queueNumber: number,
  minutesAgo: number,
): CallItem {
  return {
    ...base,
    registerId,
    queueNumber,
    calledTime: isoMinutesAgo(minutesAgo),
    callStatus: 1,
  }
}

function getAllMockCallItems(): CallItem[] {
  const queueNumbers = [12, 8, 5, 10, 16, 4, 9, 3, 6, 10, 14, 5, 7, 11]
  return MOCK_POOL.map((base, i) =>
    toCallItem(base, 9000 + i, queueNumbers[i], MOCK_POOL.length - i),
  )
}

function buildDeptBoardItem(deptId: number, deptName: string, calls: CallItem[]): DeptBoardItem {
  const sorted = [...calls].sort((a, b) => (b.calledTime ?? '').localeCompare(a.calledTime ?? ''))
  const waitingCount = calls.reduce((sum, c) => sum + (c.waitingCount ?? 0), 0)
  const first = sorted[0]
  return {
    departmentId: deptId,
    departmentName: deptName,
    calling: sorted,
    callingCount: sorted.length,
    waitingCount,
    currentCalling: first ?? null,
    nextWaiting: first?.nextQueueNumber != null
      ? { queueNumber: first.nextQueueNumber }
      : null,
  }
}

export function isCallingBoardMockMode(query: Record<string, unknown>): boolean {
  const v = query.mock
  if (v === '1' || v === 'true') return true
  if (Array.isArray(v) && (v[0] === '1' || v[0] === 'true')) return true
  return false
}

export function createMockHubData(): CallingBoardHubResponse {
  const allCalls = getAllMockCallItems()
  const deptMap = new Map<number, CallItem[]>()

  for (const call of allCalls) {
    const id = call.departmentId!
    if (!deptMap.has(id)) deptMap.set(id, [])
    deptMap.get(id)!.push(call)
  }

  const departments: DeptBoardItem[] = []
  for (const [deptId, calls] of deptMap) {
    departments.push(buildDeptBoardItem(deptId, calls[0].departmentName!, calls))
  }

  for (const idle of IDLE_DEPARTMENTS) {
    departments.push({
      departmentId: idle.departmentId,
      departmentName: idle.departmentName,
      calling: [],
      callingCount: 0,
      waitingCount: 0,
      currentCalling: null,
      nextWaiting: null,
    })
  }

  departments.sort((a, b) => a.departmentId - b.departmentId)

  const recent = [...allCalls]
    .sort((a, b) => (b.calledTime ?? '').localeCompare(a.calledTime ?? ''))
    .slice(0, 5)

  return { departments, recent }
}

export function createMockCallingBoardData(deptIds: number[] = []): CallingBoardActiveResponse {
  let items = getAllMockCallItems()

  if (deptIds.length) {
    items = items.filter(r => r.departmentId != null && deptIds.includes(r.departmentId))
  }

  items.sort((a, b) => (b.calledTime ?? '').localeCompare(a.calledTime ?? ''))

  const recent = items.slice(0, 5)
  const totalWaiting = items.reduce((sum, r) => sum + (r.waitingCount ?? 0), 0)

  return {
    active: items,
    recent,
    stats: {
      totalWaiting: deptIds.length ? totalWaiting : 127,
      activeCalling: items.length,
    },
  }
}

export function applyMockCallToHub(hub: CallingBoardHubResponse, call: CallItem): CallingBoardHubResponse {
  const departments = hub.departments.map((dept) => {
    if (dept.departmentId !== call.departmentId) return dept
    const calling = [call, ...dept.calling.filter(c => c.registerId !== call.registerId)]
    return {
      ...dept,
      calling,
      callingCount: calling.length,
      waitingCount: dept.waitingCount + 1,
      currentCalling: call,
      nextWaiting: call.nextQueueNumber != null ? { queueNumber: call.nextQueueNumber } : dept.nextWaiting,
    }
  })

  const recent = [call, ...hub.recent.filter(r => r.registerId !== call.registerId)].slice(0, 5)
  return { departments, recent }
}

export interface MockSimulationHandlers {
  onCalled: (item: CallItem) => void
  onRefresh: () => CallingBoardActiveResponse
}

export interface MockHubSimulationHandlers {
  onCalled: (item: CallItem) => void
  onHubUpdate: (hub: CallingBoardHubResponse) => void
  getHub: () => CallingBoardHubResponse
}

export function startCallingBoardMockSimulation(
  handlers: MockSimulationHandlers,
  getActiveRows: () => CallItem[],
  setData: (data: CallingBoardActiveResponse) => void,
): () => void {
  let simIndex = 0
  let nextRegisterId = 9950

  function fireNewCall(template: CallItem) {
    const newCall: CallItem = {
      ...template,
      registerId: nextRegisterId++,
      calledTime: new Date().toISOString(),
      callStatus: 1,
    }

    const current = getActiveRows()
    const merged = [newCall, ...current.filter(r => r.registerId !== newCall.registerId)].slice(0, 16)
    const recent = merged.slice(0, 5)
    const totalWaiting = (handlers.onRefresh().stats.totalWaiting ?? 127) + 1

    setData({
      active: merged,
      recent,
      stats: { totalWaiting, activeCalling: merged.length },
    })
    handlers.onCalled(newCall)
  }

  const timer = setInterval(() => {
    fireNewCall(SIMULATION_NEW_CALLS[simIndex % SIMULATION_NEW_CALLS.length])
    simIndex++
  }, 12_000)

  const firstCallTimer = setTimeout(() => {
    fireNewCall(SIMULATION_NEW_CALLS[0])
    simIndex = 1
  }, 3_000)

  return () => {
    clearInterval(timer)
    clearTimeout(firstCallTimer)
  }
}

export function startCallingBoardHubMockSimulation(
  handlers: MockHubSimulationHandlers,
): () => void {
  let simIndex = 0
  let nextRegisterId = 9950

  function fireNewCall(template: CallItem) {
    const newCall: CallItem = {
      ...template,
      registerId: nextRegisterId++,
      calledTime: new Date().toISOString(),
      callStatus: 1,
    }
    const updated = applyMockCallToHub(handlers.getHub(), newCall)
    handlers.onHubUpdate(updated)
    handlers.onCalled(newCall)
  }

  const timer = setInterval(() => {
    fireNewCall(SIMULATION_NEW_CALLS[simIndex % SIMULATION_NEW_CALLS.length])
    simIndex++
  }, 12_000)

  const firstCallTimer = setTimeout(() => {
    fireNewCall(SIMULATION_NEW_CALLS[0])
    simIndex = 1
  }, 3_000)

  return () => {
    clearInterval(timer)
    clearTimeout(firstCallTimer)
  }
}
