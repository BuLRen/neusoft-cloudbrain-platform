import { reactive, computed } from 'vue'
import { patientApi } from '../api/patient'
import { connectNotification, disconnectNotification } from './notification'

export interface PatientInfo {
  patientId: number
  realName: string
  gender?: string
  relation?: string
  isPrimary?: number
  idCard?: string
  birthdate?: string
  phone?: string
  homeAddress?: string
  allergyHistory?: string
  accountBalance?: number
}

interface SessionData {
  token: string
  refreshToken: string
  userId: string
  username: string
  realName: string
  role: string
  patients: PatientInfo[]
  currentPatientId: number | null
}

const SESSION_KEY = 'xikang_session'
const emptySession = (): SessionData => ({ token:'', refreshToken:'', userId:'', username:'', realName:'', role:'', patients:[], currentPatientId:null })
export const session = reactive<SessionData>(emptySession())
export const isAuthenticated = computed(() => Boolean(session.token))
export const currentPatient = computed(() => session.patients.find(item => item.patientId === session.currentPatientId) || session.patients.find(item => item.isPrimary === 1) || session.patients[0] || null)

export function restoreSession() {
  const cached = uni.getStorageSync(SESSION_KEY) as Partial<SessionData> | ''
  if (cached && typeof cached === 'object') Object.assign(session, emptySession(), cached)
}

export function saveSession(data: Partial<SessionData>) {
  Object.assign(session, data)
  if (!session.currentPatientId) session.currentPatientId = session.patients.find(item => item.isPrimary === 1)?.patientId || session.patients[0]?.patientId || null
  uni.setStorageSync(SESSION_KEY, { ...session })
}

export function switchPatient(patientId: number) {
  if (session.patients.some(item => item.patientId === patientId)) {
    session.currentPatientId = patientId
    uni.setStorageSync(SESSION_KEY, { ...session })
    // 切患者后必须重连 WS，否则收到的是上一个用户的消息，channel 会错乱
    try {
      disconnectNotification()
      connectNotification()
    } catch {
      /* ignore：未登录/未配置时静默 */
    }
  }
}

export function setPatientBalance(patientId: number, accountBalance: number) {
  const target = session.patients.find(item => item.patientId === patientId)
  if (!target) return
  target.accountBalance = accountBalance
  uni.setStorageSync(SESSION_KEY, { ...session })
}

export async function refreshCurrentPatientBalance(): Promise<void> {
  const pid = currentPatient.value?.patientId
  if (!pid) return
  try {
    const res = await patientApi.balance(pid)
    setPatientBalance(pid, res.accountBalance)
  } catch { /* 静默失败：余额刷新失败不打扰用户 */ }
}

export function clearSession() {
  Object.assign(session, emptySession())
  uni.removeStorageSync(SESSION_KEY)
  // 登出后必须断开 WS，避免旧 token 持续上报通知
  try {
    disconnectNotification()
  } catch {
    /* ignore */
  }
}
