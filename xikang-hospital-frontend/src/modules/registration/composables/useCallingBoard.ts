import { http } from '@/shared/api'
import type {
  CallItem,
  CallingBoardActiveResponse,
  CallingBoardHubResponse,
} from '@/shared/types/calling'

export const ROWS_PER_PAGE = 10
export const PAGE_ROTATE_MS = 8_000

export function maskName(name?: string) {
  if (!name) return '—'
  if (name.length <= 1) return name
  if (name.length === 2) return name[0] + '*'
  return name[0] + '*'.repeat(name.length - 2) + name[name.length - 1]
}

export function formatClinicRoom(item: CallItem): string {
  if (item.clinicRoom) return item.clinicRoom
  if (item.doctorName) return `${item.doctorName}诊室`
  return '—'
}

export function formatWaiting(row: CallItem): string {
  const count = row.waitingCount ?? 0
  if (row.nextQueueNumber != null) {
    return `${count}人 / 下${row.nextQueueNumber}号`
  }
  return `${count}人`
}

export function formatTime(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export async function fetchHubBoard(): Promise<CallingBoardHubResponse | null> {
  return http<CallingBoardHubResponse>({
    url: '/registration/calling/board/all',
    method: 'GET',
    params: { includeIdle: true },
    skipAuthHandling: true,
    skipErrorMessage: true,
  })
}

export async function fetchDeptBoard(departmentId: number): Promise<CallingBoardActiveResponse | null> {
  return http<CallingBoardActiveResponse>({
    url: '/registration/calling/board/active',
    method: 'GET',
    params: { deptIds: String(departmentId), limit: 50 },
    skipAuthHandling: true,
    skipErrorMessage: true,
  })
}

export function buildSpeakText(payload: CallItem, includeDept = true): string {
  const room = formatClinicRoom(payload)
  const parts: string[] = []
  if (includeDept && payload.departmentName) parts.push(payload.departmentName)
  if (payload.doctorName) parts.push(`${payload.doctorName}医生`)
  if (room && room !== '—') parts.push(room)
  const dest = parts.join('')
  return `请${maskName(payload.patientName)}到${dest}就诊`
}
