import { computed, onMounted, onUnmounted, ref } from 'vue'
import { registrationApi } from '@/shared/api/modules/registration'
import type { CallItem } from '@/shared/types/calling'

export interface DepartmentPatientStat {
  departmentId: number
  departmentName: string
  waitingCount: number
  callingCount: number
  activeCount: number
  todayRegistrations: number
  calling: CallItem[]
}

export interface DepartmentPatientSummary {
  totalActive: number
  totalWaiting: number
  totalCalling: number
  triagePending: number
}

const REFRESH_INTERVAL_MS = 30_000

export function formatLocalDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function todayWorkloadRange(): { startDate: string; endDate: string } {
  const today = new Date()
  const tomorrow = new Date(today)
  tomorrow.setDate(tomorrow.getDate() + 1)
  return { startDate: formatLocalDate(today), endDate: formatLocalDate(tomorrow) }
}

/** 近 N 天统计区间 [startDate, endDate)，endDate 为明天 0 点 */
export function reportPeriodRange(days: number): { startDate: string; endDate: string } {
  const end = new Date()
  const start = new Date(end)
  start.setDate(start.getDate() - Math.max(days - 1, 0))
  const tomorrow = new Date(end)
  tomorrow.setDate(tomorrow.getDate() + 1)
  return { startDate: formatLocalDate(start), endDate: formatLocalDate(tomorrow) }
}

function formatTimestamp(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${formatLocalDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function useDepartmentPatientStats(options?: { autoRefresh?: boolean }) {
  const autoRefresh = options?.autoRefresh ?? true

  const loading = ref(false)
  const departments = ref<DepartmentPatientStat[]>([])
  const lastUpdatedAt = ref('')
  const triagePendingCount = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  const summary = computed<DepartmentPatientSummary>(() => {
    const list = departments.value
    return {
      totalActive: list.reduce((s, d) => s + d.activeCount, 0),
      totalWaiting: list.reduce((s, d) => s + d.waitingCount, 0),
      totalCalling: list.reduce((s, d) => s + d.callingCount, 0),
      triagePending: triagePendingCount.value,
    }
  })

  async function refresh() {
    loading.value = true
    try {
      const { startDate, endDate } = todayWorkloadRange()
      const [board, workload, triagePending] = await Promise.all([
        registrationApi.callingBoardAll(true),
        registrationApi.departmentWorkload({ startDate, endDate }),
        registrationApi.triagePending(),
      ])

      triagePendingCount.value = triagePending.length

      const workloadMap = new Map(workload.map((w) => [w.departmentId, w]))
      const boardMap = new Map(board.departments.map((d) => [d.departmentId, d]))
      const allDeptIds = new Set([...workloadMap.keys(), ...boardMap.keys()])

      const merged: DepartmentPatientStat[] = []
      for (const deptId of allDeptIds) {
        const boardItem = boardMap.get(deptId)
        const workloadItem = workloadMap.get(deptId)
        const waitingCount = boardItem?.waitingCount ?? 0
        const callingCount = boardItem?.callingCount ?? 0

        merged.push({
          departmentId: deptId,
          departmentName: boardItem?.departmentName ?? workloadItem?.departmentName ?? `科室 ${deptId}`,
          waitingCount,
          callingCount,
          activeCount: waitingCount + callingCount,
          todayRegistrations: workloadItem?.registrations ?? 0,
          calling: boardItem?.calling ?? [],
        })
      }

      departments.value = merged.sort(
        (a, b) => b.activeCount - a.activeCount || b.todayRegistrations - a.todayRegistrations,
      )
      lastUpdatedAt.value = formatTimestamp(new Date())
    } finally {
      loading.value = false
    }
  }

  function startAutoRefresh() {
    stopAutoRefresh()
    if (autoRefresh) {
      timer = setInterval(() => {
        void refresh()
      }, REFRESH_INTERVAL_MS)
    }
  }

  function stopAutoRefresh() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  onMounted(() => {
    void refresh()
    startAutoRefresh()
  })

  onUnmounted(stopAutoRefresh)

  return {
    loading,
    departments,
    summary,
    lastUpdatedAt,
    refresh,
    startAutoRefresh,
    stopAutoRefresh,
  }
}
