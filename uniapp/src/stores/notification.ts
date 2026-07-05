import { ref } from 'vue'
import { medicalApi } from '../api/medical'
import { currentPatient } from './session'

export const unreadMessageCount = ref(0)

export async function refreshUnreadMessageCount() {
  const patientId = currentPatient.value?.patientId
  if (!patientId) {
    unreadMessageCount.value = 0
    return
  }
  try {
    const data = await medicalApi.unreadCount(patientId)
    unreadMessageCount.value = Math.max(0, Number(data.count || 0))
  } catch {
    // 导航栏的辅助请求失败时不打断当前页面
  }
}

export function markOneMessageReadLocally() {
  unreadMessageCount.value = Math.max(0, unreadMessageCount.value - 1)
}
