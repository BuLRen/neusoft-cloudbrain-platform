import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminPaymentApi, type AdminPatientOption } from '@/shared/api/modules/adminPayment'
import type { OrderStatusFilter } from '@/shared/types/payment'

const STORAGE_KEY = 'admin-payment-bill-filters'

export interface PaymentBillFilters {
  searchInput: string
  patientId?: number
  patientName?: string
  listKeyword?: string
  status?: OrderStatusFilter
  dateRange: string[]
  page: number
  size: number
}

export function loadStoredFilters(): Partial<PaymentBillFilters> {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    return JSON.parse(raw) as Partial<PaymentBillFilters>
  } catch {
    return {}
  }
}

export function saveFilters(filters: PaymentBillFilters) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(filters))
  } catch {
    // ignore quota errors
  }
}

export function useAdminPaymentBillSearch() {
  const searchLoading = ref(false)
  const patientOptions = ref<AdminPatientOption[]>([])
  const selectedPatient = ref<AdminPatientOption | null>(null)
  const listKeyword = ref<string | undefined>(undefined)

  async function resolveSearch(input: string): Promise<{ patientId?: number; keyword?: string }> {
    const trimmed = input.trim()
    if (!trimmed) {
      patientOptions.value = []
      selectedPatient.value = null
      listKeyword.value = undefined
      return {}
    }

    if (/^\d+$/.test(trimmed)) {
      patientOptions.value = []
      selectedPatient.value = null
      listKeyword.value = trimmed
      return { keyword: trimmed }
    }

    searchLoading.value = true
    try {
      const patients = await adminPaymentApi.searchPatients(trimmed)
      if (patients.length === 0) {
        patientOptions.value = []
        selectedPatient.value = null
        listKeyword.value = trimmed
        return { keyword: trimmed }
      }
      if (patients.length === 1) {
        patientOptions.value = []
        selectedPatient.value = patients[0]
        listKeyword.value = undefined
        return { patientId: patients[0].id }
      }
      patientOptions.value = patients
      selectedPatient.value = null
      listKeyword.value = undefined
      return {}
    } catch (e) {
      patientOptions.value = []
      selectedPatient.value = null
      listKeyword.value = trimmed
      ElMessage.error(e instanceof Error ? e.message : '患者查询失败')
      return { keyword: trimmed }
    } finally {
      searchLoading.value = false
    }
  }

  function selectPatient(patient: AdminPatientOption) {
    selectedPatient.value = patient
    patientOptions.value = []
    listKeyword.value = undefined
  }

  function clearPatientSelection() {
    selectedPatient.value = null
    patientOptions.value = []
    listKeyword.value = undefined
  }

  function listQueryParams(filters: {
    searchInput: string
    status?: OrderStatusFilter
    dateRange: string[]
    page: number
    size: number
  }) {
    const [startDate, endDate] = filters.dateRange.length === 2 ? filters.dateRange : [undefined, undefined]
    return {
      keyword: listKeyword.value,
      patientId: selectedPatient.value?.id,
      status: filters.status,
      startDate,
      endDate,
      page: filters.page,
      size: filters.size,
    }
  }

  return {
    searchLoading,
    patientOptions,
    selectedPatient,
    listKeyword,
    resolveSearch,
    selectPatient,
    clearPatientSelection,
    listQueryParams,
  }
}
