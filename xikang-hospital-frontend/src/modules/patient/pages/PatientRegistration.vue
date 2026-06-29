<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElDatePicker, ElMessage, ElMessageBox, ElRadio, ElRadioButton, ElRadioGroup } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import StatusTag from '@/shared/components/StatusTag.vue'
import CheckInQRCode from '@/shared/components/CheckInQRCode.vue'
import { aiApi } from '@/shared/api/modules/ai'
import { registrationApi, scheduleApi, type DoctorInfo } from '@/shared/api/modules/registration'
import type { RegistrationRecord } from '@/shared/types/registration'
import type { ExpenseRecordSortBy, ExpenseRecordSortDir } from '@/shared/types/registration'
import { useAuthStore } from '@/app/stores/auth'
import { Warning } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

// 开发环境标记（模板里不能用 import.meta.env，必须先在 script 暴露）
const isDev = import.meta.env.DEV

// ========== 预问诊状态映射 ==========
type PreConsultBtnState = { text: string; type: 'primary' | 'warning' | 'default'; state: 'none' | 'in_progress' | 'completed' } | null
const preConsultStates = ref<Record<number, PreConsultBtnState>>({})

// 步骤定义 - 按时间顺序：导诊 → 选择排班 → 确认挂号 → 预问诊
const steps = [
  { key: 'triage', title: 'AI导诊', icon: '🤖', desc: '分析症状推荐科室' },
  { key: 'schedule', title: '选择排班', icon: '📅', desc: '选择医生和时间' },
  { key: 'confirm', title: '确认挂号', icon: '✅', desc: '确认并提交' },
  { key: 'previsit', title: 'AI预问诊', icon: '💬', desc: '采集病史信息（可选）' },
]

const pageMode = ref<'list' | 'wizard'>('list')
const registrations = ref<RegistrationRecord[]>([])
const registrationLoading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const actionLoading = ref(false)
const selectedRegistration = ref<RegistrationRecord | null>(null)

// 当前步骤索引
const currentStep = ref(0)

// ========== Step 1: AI导诊 ==========
const triageForm = ref({
  symptoms: '',
})
const triageLoading = ref(false)
const triageResult = ref<any>(null)
const isRecording = ref(false)
const voiceLoading = ref(false)

// ========== 百度ASR HTTP 短语音识别 ==========
let audioContext: AudioContext | null = null
let audioProcessor: ScriptProcessorNode | null = null
let sourceNode: MediaStreamAudioSourceNode | null = null
let audioBuffer: Int16Array[] = []  // 收集 PCM 数据

async function toggleVoice() {
  if (isRecording.value) {
    stopRecording()
    return
  }

  // 检查麦克风权限
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    stream.getTracks().forEach(t => t.stop())
  } catch {
    ElMessage.error('无法访问麦克风，请检查权限设置')
    return
  }

  isRecording.value = true
  audioBuffer = []
  ElMessage.info('开始录音，请说话...')

  startMediaRecorder()
}

function startMediaRecorder() {
  navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
    // 创建 AudioContext（使用设备默认采样率，通常是 44100 或 48000）
    audioContext = new AudioContext()
    sourceNode = audioContext.createMediaStreamSource(stream)

    // ScriptProcessorNode 处理音频并重采样到 16kHz
    const bufferSize = 4096
    audioProcessor = audioContext.createScriptProcessor(bufferSize, 1, 1)
    sourceNode.connect(audioProcessor)
    audioProcessor.connect(audioContext.destination)

    const deviceSampleRate = audioContext.sampleRate
    const targetSampleRate = 16000
    const ratio = deviceSampleRate / targetSampleRate

    audioProcessor.onaudioprocess = (event) => {
      const inputData = event.inputBuffer.getChannelData(0)

      // 重采样: 从设备采样率转换到 16kHz
      const resampledLength = Math.round(inputData.length / ratio)
      const resampledData = new Float32Array(resampledLength)
      for (let i = 0; i < resampledLength; i++) {
        const srcIndex = i * ratio
        const srcIndexFloor = Math.floor(srcIndex)
        const srcIndexCeil = Math.min(srcIndexFloor + 1, inputData.length - 1)
        const frac = srcIndex - srcIndexFloor
        resampledData[i] = inputData[srcIndexFloor] * (1 - frac) + inputData[srcIndexCeil] * frac
      }

      // 转换为 Int16 并存入缓冲区
      const pcmData = convertFloat32ToInt16(resampledData)
      audioBuffer.push(pcmData)
    }
  }).catch(err => {
    console.error('[Voice] 录音失败:', err)
    ElMessage.error('录音启动失败，请检查麦克风权限或使用文字输入')
    isRecording.value = false
  })
}

/**
 * 停止录音并发送识别请求
 */
async function stopRecording() {
  if (!isRecording.value) return

  isRecording.value = false

  // 清理音频处理
  if (audioProcessor) {
    audioProcessor.disconnect()
    audioProcessor = null
  }
  if (sourceNode) {
    sourceNode.disconnect()
    sourceNode = null
  }
  if (audioContext) {
    await audioContext.close()
    audioContext = null
  }

  // 合并所有 PCM 数据
  if (audioBuffer.length === 0) {
    ElMessage.warning('没有采集到音频')
    return
  }

  const totalLength = audioBuffer.reduce((sum, arr) => sum + arr.length, 0)
  const mergedPcm = new Int16Array(totalLength)
  let offset = 0
  for (const arr of audioBuffer) {
    mergedPcm.set(arr, offset)
    offset += arr.length
  }
  audioBuffer = []

  // 发送识别请求
  await sendForRecognition(mergedPcm)
}

/**
 * 发送音频到后端进行识别
 */
async function sendForRecognition(pcmData: Int16Array) {
  voiceLoading.value = true
  ElMessage.info('正在识别...')

  try {
    // 转换为字节数组
    const byteArray = new Uint8Array(pcmData.buffer)

    // 获取 API 地址（走网关8080，有CORS配置）
    const apiUrl = import.meta.env.DEV
      ? 'http://localhost:8080/api/voice/recognize'
      : '/api/voice/recognize'

    const response = await fetch(apiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/octet-stream',
      },
      body: byteArray.buffer as ArrayBuffer,
    })

    const result = await response.json()

    if (result.success && result.text) {
      triageForm.value.symptoms = result.text
      ElMessage.success('语音识别成功')
    } else {
      ElMessage.error(result.error || '语音识别失败')
    }
  } catch (err: any) {
    console.error('[Voice] 识别请求失败:', err)
    ElMessage.error('语音识别失败，请检查网络或改用文字输入')
  } finally {
    voiceLoading.value = false
  }
}

/**
 * 将 Float32Array 音频数据转换为 Int16Array PCM 格式
 */
function convertFloat32ToInt16(float32Array: Float32Array): Int16Array {
  const int16Array = new Int16Array(float32Array.length)
  for (let i = 0; i < float32Array.length; i++) {
    const s = Math.max(-1, Math.min(1, float32Array[i]))
    int16Array[i] = s < 0 ? s * 0x8000 : s * 0x7FFF
  }
  return int16Array
}

// 常见症状快捷标签
const symptomCategories = [
  {
    key: 'digestive',
    label: '消化系统',
    icon: '🔴',
    placeholder: '请输入您的主要症状，例如：胃痛3天，伴有反酸和嗳气，进食后加重...',
  },
  {
    key: 'respiratory',
    label: '呼吸系统',
    icon: '🫁',
    placeholder: '请输入您的主要症状，例如：咳嗽3天，伴有发热和咽痛...',
  },
  {
    key: 'neurological',
    label: '神经系统',
    icon: '🧠',
    placeholder: '请输入您的主要症状，例如：头痛、头晕、失眠、记忆力下降...',
  },
  {
    key: 'orthopedic',
    label: '骨科',
    icon: '🦴',
    placeholder: '请输入您的主要症状，例如：腰痛3天，久坐后加重，伴有腿部麻木...',
  },
  {
    key: 'cardiovascular',
    label: '心血管',
    icon: '❤️',
    placeholder: '请输入您的主要症状，例如：胸闷、心悸、活动后呼吸困难...',
  },
  {
    key: 'dermatology',
    label: '皮肤科',
    icon: '🧴',
    placeholder: '请输入您的主要症状，例如：皮肤瘙痒、皮疹、红肿...',
  },
]

const currentCategory = ref<typeof symptomCategories[0] | null>(null)

function selectCategory(cat: typeof symptomCategories[0]) {
  currentCategory.value = cat
  triageForm.value.symptoms = ''
  // 聚焦到 textarea
  const el = document.querySelector('.form-textarea') as HTMLTextAreaElement
  if (el) el.focus()
}

// ========== Step 2: 选择排班 ==========
const selectedSchedule = ref<any>(null)
const selectedLevel = ref<any>(null)
const availableSchedules = ref<any[]>([])
const scheduleLoading = ref(false)
const scheduleDate = ref(formatDate(new Date()))
const defaultSettleCategoryId = ref<number>()

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// ========== Step 3: 确认挂号 ==========
const submitting = ref(false)
const registrationResult = ref<any>(null)

// ========== Step 4: AI预问诊 ==========
const previsitForm = ref({
  chiefComplaint: '',
  presentIllness: '',
  pastHistory: '',
  allergyHistory: '',
})
const previsitCompleted = ref(false)

// 步骤完成状态
const stepStatus = computed(() => {
  return steps.map((_, index) => {
    if (index < currentStep.value) return 'completed'
    if (index === currentStep.value) return 'active'
    return 'pending'
  })
})

async function nextStep() {
  // 领域护栏：话题外输入禁止跳到下一步
  if (triageResult.value?.isOutOfScope) {
    ElMessage.warning('请先描述您的症状，再进行下一步')
    return
  }
  if (currentStep.value < steps.length - 1) {
    currentStep.value++
    if (currentStep.value === 1) {
      await loadAvailableSchedules()
    }
  }
}

function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

function goToStep(index: number) {
  if (index <= currentStep.value) {
    currentStep.value = index
  }
}

// ========== 列表筛选 / 排序 ==========
const visitDateRange = ref<[string, string] | null>(null)
const sortBy = ref<ExpenseRecordSortBy>('payTime')
const sortDir = ref<ExpenseRecordSortDir>('desc')

// 状态 tab：全部 / 进行中(1,2,5,6) / 已完成(3) / 已取消(4,7)
type RegTab = 'all' | 'ongoing' | 'done' | 'cancelled'
const activeTab = ref<RegTab>('all')

function tabFilter(status?: number): boolean {
  switch (activeTab.value) {
    case 'ongoing':   return [1, 2, 5, 6].includes(status ?? 0)
    case 'done':      return status === 3
    case 'cancelled': return [4, 7].includes(status ?? 0)
    default:          return true
  }
}

// 状态优先级：待缴费 → 已缴费 → 已退号/爽约（垫底）
function registrationStatusRank(record: RegistrationRecord): number {
  if (record.status === 4 || record.status === 7) return 2
  if (record.payStatus === 0) return 0
  if (record.payStatus === 1) return 1
  return 1
}

function parseRegistrationTime(record: RegistrationRecord, key: ExpenseRecordSortBy): number {
  const value = key === 'createTime' ? record.createTime : key === 'refundTime' ? record.refundTime : (record as any).payTime
  if (!value) return Number.POSITIVE_INFINITY
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : Number.POSITIVE_INFINITY
}

const sortedRegistrations = computed(() => {
  const list = registrations.value.slice()
  const dir = sortDir.value === 'asc' ? 1 : -1
  const key = sortBy.value
  return list.sort((a, b) => {
    const rankDiff = registrationStatusRank(a) - registrationStatusRank(b)
    if (rankDiff !== 0) return rankDiff
    const timeDiff = (parseRegistrationTime(a, key) - parseRegistrationTime(b, key)) * dir
    if (timeDiff !== 0) return timeDiff
    return a.id - b.id
  })
})

function visitDateOf(record: RegistrationRecord): string {
  if (record.visitDate) return record.visitDate.slice(0, 10)
  if (record.createTime) return record.createTime.slice(0, 10)
  return ''
}

const filteredRegistrations = computed(() => {
  // 第一层：tab 状态过滤
  const byTab = sortedRegistrations.value.filter((r) => tabFilter(r.status))
  // 第二层：日期范围过滤
  const range = visitDateRange.value
  if (!range || !range[0] || !range[1]) return byTab
  const [start, end] = range
  return byTab.filter((record) => {
    const day = visitDateOf(record)
    if (!day) return false
    return day >= start && day <= end
  })
})

function clearFilters() {
  activeTab.value = 'all'
  visitDateRange.value = null
}

async function loadRegistrations() {
  const patientId = authStore.currentPatientId || authStore.currentPatient?.patientId
  if (!patientId) return
  registrationLoading.value = true
  try {
    registrations.value = await registrationApi.registrationsByPatient(patientId)
    await loadPreConsultStates()
  } catch (err) {
    console.error('加载我的挂号失败:', err)
    ElMessage.error('加载我的挂号失败')
  } finally {
    registrationLoading.value = false
  }
}

// ========== 加载每个挂号的预问诊状态 ==========
async function loadPreConsultStates() {
  // 已缴费、未退号的挂号都查；爽约(5)的挂号也查，但只为了能"查看"历史预问诊
  const targets = registrations.value.filter(
    r => r.payStatus === 1 && r.status !== 4,
  )
  const results: Record<number, PreConsultBtnState> = {}
  await Promise.all(
    targets.map(async r => {
      try {
        const session = await aiApi.previsitSession(r.id)
        // 爽约挂号只保留"已完成"的查看入口，去掉"去预问诊/继续预问诊"等动作入口
        if (r.status === 7) {
          if (session && session.exists && session.state === 'completed') {
            results[r.id] = { text: '查看预问诊', type: 'default', state: 'completed' }
          }
          return
        }
        if (!session || !session.exists) {
          results[r.id] = { text: '去预问诊', type: 'primary', state: 'none' }
        } else if (session.state === 'completed') {
          results[r.id] = { text: '查看预问诊', type: 'default', state: 'completed' }
        } else {
          results[r.id] = { text: '继续预问诊', type: 'warning', state: 'in_progress' }
        }
      } catch {
        if (r.status !== 5) {
          results[r.id] = { text: '去预问诊', type: 'primary', state: 'none' }
        }
      }
    }),
  )
  preConsultStates.value = results
}

function preConsultBtnFor(record: RegistrationRecord): PreConsultBtnState {
  if (record.payStatus !== 1) return null
  // 退号：不显示任何预问诊入口
  if (record.status === 4) return null
  // 爽约：仅保留"查看预问诊"（已完成的历史会话），不显示"去/继续"等动作入口
  if (record.status === 7) {
    const state = preConsultStates.value[record.id]
    return state && state.state === 'completed' ? state : null
  }
  return preConsultStates.value[record.id] || { text: '去预问诊', type: 'primary', state: 'none' }
}

function goPrevisit(record: RegistrationRecord) {
  const patientId = authStore.currentPatientId || authStore.currentPatient?.patientId
  router.push({
    path: '/patient/previsit',
    query: { registerId: record.id, patientId },
  })
}

function goPrevisitById(registerId: number) {
  const patientId = authStore.currentPatientId || authStore.currentPatient?.patientId
  router.push({
    path: '/patient/previsit',
    query: { registerId, patientId },
  })
}

function formatMoney(value?: number | string | null) {
  return Number(value || 0).toFixed(2)
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const text = String(value).trim()
  if (!text) return '-'
  const isoMatch = text.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$/)
  if (isoMatch) {
    const [, y, m, d, hh, mm] = isoMatch
    return `${y}-${m}-${d} ${hh}:${mm}`
  }
  const fallback = new Date(text)
  if (!Number.isNaN(fallback.getTime())) {
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${fallback.getFullYear()}-${pad(fallback.getMonth() + 1)}-${pad(fallback.getDate())} ${pad(fallback.getHours())}:${pad(fallback.getMinutes())}`
  }
  return text
}

function getCurrentPatientId() {
  return authStore.currentPatientId || authStore.currentPatient?.patientId || selectedRegistration.value?.patientId
}

function syncPatientBalance(accountBalance?: number) {
  const patientId = getCurrentPatientId()
  if (patientId && typeof accountBalance === 'number') {
    authStore.setPatientBalance(patientId, accountBalance)
  }
}

async function openRegistrationDetail(record: RegistrationRecord) {
  detailVisible.value = true
  selectedRegistration.value = record
  await loadRegistrationDetail(record.id)
}

async function loadRegistrationDetail(id: number) {
  detailLoading.value = true
  try {
    selectedRegistration.value = await registrationApi.registration(id)
  } catch (err: any) {
    console.error('加载挂号详情失败:', err)
    ElMessage.error(err?.message || '加载挂号详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function refreshRegistrationState(targetId?: number) {
  await loadRegistrations()
  const detailId = targetId || selectedRegistration.value?.id
  if (detailVisible.value && detailId) {
    await loadRegistrationDetail(detailId)
  }
}

function canPay(record: RegistrationRecord) {
  return record.status !== 4 && record.status !== 5 && record.payStatus === 0
}

function canCancel(record: RegistrationRecord) {
  return record.status === 1 || record.status === 2
}

// 是否可以报到：仅已缴费、未报到、未退号、未爽约的挂号可以报到
function canCheckIn(record: RegistrationRecord) {
  if (record.payStatus !== 1) return false
  if (record.status === 4 || record.status === 7) return false
  if (record.checkedIn) return false
  // 必须是就诊当天
  if (!record.visitDate) return false
  const visitDay = record.visitDate.slice(0, 10)
  const today = new Date().toISOString().slice(0, 10)
  return visitDay === today
}

// 患者端"模拟报到"按钮：暂绕过就诊当日限制，方便跑通流程。
// 真实报到由报到机扫码触发后无需此按钮。
function canCheckInSimulate(record: RegistrationRecord) {
  if (record.payStatus !== 1) return false
  if (record.status === 4 || record.status === 7) return false
  if (record.checkedIn) return false
  return true
}

// 详情弹窗里是否显示二维码：只过滤终态(退号/爽约/已报到)
// 已缴费的挂号都展示二维码，方便患者事后回看
function canShowDetailQr(record: RegistrationRecord) {
  if (record.payStatus !== 1) return false
  if (record.status === 4 || record.status === 7) return false
  if (record.checkedIn) return false
  return true
}

function paymentStatusTone(payStatus?: number): 'success' | 'warning' | 'danger' {
  if (payStatus === 1) return 'success'
  if (payStatus === 2) return 'danger'
  return 'warning'
}

async function handlePay(record: RegistrationRecord) {
  const amount = Number(record.amount || 0)
  const balance = Number(authStore.currentPatient?.accountBalance || 0)
  const nextBalance = balance - amount
  try {
    await ElMessageBox.confirm(
      `确认支付该挂号单？\n\n支付金额：¥${formatMoney(amount)}\n当前余额：¥${formatMoney(balance)}\n支付后余额：¥${formatMoney(nextBalance)}`,
      '挂号缴费确认',
      {
        confirmButtonText: '确认支付',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  actionLoading.value = true
  try {
    const result = await registrationApi.payRegistration(record.id)
    syncPatientBalance(result.accountBalance)
    ElMessage.success(result.paymentMessage || '缴费成功')
    await refreshRegistrationState(record.id)
  } catch (err: any) {
    console.error('挂号缴费失败:', err)
    ElMessage.error(err?.message || '挂号缴费失败')
  } finally {
    actionLoading.value = false
  }
}

async function handleCancel(record: RegistrationRecord) {
  const paidHint = record.payStatus === 1 ? '\n\n该挂号已缴费，取消后将原路退回患者余额。' : ''
  try {
    await ElMessageBox.confirm(
      `确认取消该挂号吗？${paidHint}`,
      '取消挂号确认',
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '返回',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  actionLoading.value = true
  try {
    const result = await registrationApi.cancelRegistration(record.id)
    syncPatientBalance(result.accountBalance)
    ElMessage.success(result.paymentMessage || '取消挂号成功')
    await refreshRegistrationState(record.id)
  } catch (err: any) {
    console.error('取消挂号失败:', err)
    ElMessage.error(err?.message || '取消挂号失败')
  } finally {
    actionLoading.value = false
  }
}

async function handleCheckIn(record: RegistrationRecord) {
  actionLoading.value = true
  try {
    const result = await registrationApi.checkIn(record.id)
    ElMessage.success(result.message || '报到成功')
    await refreshRegistrationState(record.id)
  } catch (err: any) {
    console.error('报到失败:', err)
    ElMessage.error(err?.message || '报到失败')
  } finally {
    actionLoading.value = false
  }
}

function startRegistration() {
  pageMode.value = 'wizard'
  restart()
}

function backToList() {
  pageMode.value = 'list'
  loadRegistrations()
}

function registrationStatusTone(record: RegistrationRecord): 'success' | 'warning' | 'danger' {
  if (record.status === 4 || record.status === 7) return 'danger'
  if (record.status === 3 || record.status === 6 || record.payStatus === 1 || record.status === 1 || record.status === 2) return 'success'
  return 'warning'
}

// 前端兜底：保证 5/6/7 在患者端始终显示正确中文（不依赖后端 statusName）。
function registrationStatusLabel(record: RegistrationRecord): string {
  switch (record.status) {
    case 1: return '已挂号'
    case 2: return '医生接诊'
    case 3: return '看诊结束'
    case 4: return '已退号'
    case 5: return '检查检验中'
    case 6: return '检查检验完成'
    case 7: return '爽约'
    default: return record.statusName || '未知状态'
  }
}

function formatVisitTime(record: RegistrationRecord) {
  return [record.visitDate, record.visitTime].filter(Boolean).join(' ') || record.createTime || '-'
}

function getQueryNumber(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

function getQueryString(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  return typeof raw === 'string' ? raw : undefined
}

async function startFromDepartmentQuery() {
  const departmentId = getQueryNumber(route.query.departmentId)
  if (!departmentId) {
    await loadRegistrations()
    return
  }

  pageMode.value = 'wizard'
  restart()
  pageMode.value = 'wizard'
  currentStep.value = 1
  scheduleDate.value = getQueryString(route.query.date) || formatDate(new Date())
  triageResult.value = {
    recommendedDepartmentId: departmentId,
    recommendedDepartment: getQueryString(route.query.departmentName) || '所选科室',
  }

  await loadAvailableSchedules()

  // 优先按 query.scheduleId 精确选中（外部深链接场景）
  const scheduleId = getQueryNumber(route.query.scheduleId)
  if (scheduleId) {
    const matched = availableSchedules.value.find(item => item.id === scheduleId)
    if (matched) selectSchedule(matched)
    return
  }

  // AI 导诊跳转：按 doctorId 匹配该医生的排班
  const doctorId = getQueryNumber(route.query.doctorId)
  if (doctorId) {
    const matched = availableSchedules.value.find(item => item.physicianId === doctorId)
    if (matched) {
      selectSchedule(matched)
      return
    }
  }

  // 仅有挂号级别：选该级别的第一个可用排班
  const registLevelId = getQueryNumber(route.query.registLevelId)
  if (registLevelId) {
    const matched = availableSchedules.value.find(item => item.registLevelId === registLevelId)
    if (matched) selectSchedule(matched)
  }
}

onMounted(startFromDepartmentQuery)

// 组件卸载兜底：用户在请求中途切走页面，防止 loading 状态遗留为 true
onUnmounted(() => {
  triageLoading.value = false
})

// ========== Step 1: AI导诊 ==========
async function runTriage() {
  if (!triageForm.value.symptoms.trim()) {
    ElMessage.warning('请先描述症状')
    return
  }
  triageLoading.value = true
  try {
    const result = await aiApi.triageAnalyze({
      symptoms: triageForm.value.symptoms,
      patientId: getCurrentPatientId(),
    })

    // 如果 AI 没有返回推荐医生，根据 AI 推荐的挂号级别获取医生
    const hasDoctors = result.recommendedDoctors && result.recommendedDoctors.length > 0
    const hasDeptId = result.recommendedDepartmentId && result.recommendedDepartmentId > 0

    if (!hasDoctors && hasDeptId) {
      try {
        let doctors: DoctorInfo[] = []

        // 优先按 AI 推荐的挂号级别获取医生
        if (result.recommendedRegistLevelId) {
          doctors = await registrationApi.getDoctorsByDepartmentAndLevel(
            result.recommendedDepartmentId!,
            result.recommendedRegistLevelId
          )
        }

        // 如果该级别没有医生，获取该科室所有医生
        if (!doctors.length) {
          doctors = await registrationApi.getDoctorsByDepartment(result.recommendedDepartmentId!)
        }

        if (doctors.length > 0) {
          result.recommendedDoctors = doctors.map((d: DoctorInfo) => ({
            id: d.id,
            name: d.realname,
            title: d.registName || '医生',
          }))
        }
      } catch {
        // 忽略获取医生失败
      }
    }

    // 领域护栏：用户输入与医疗无关时弹窗提示，关闭后用户直接在原输入框里改
    // 注意：不要给 triageResult 赋值，否则按钮会从"开始导诊"切换掉
    if (result?.isOutOfScope) {
      const message = result.outOfScopeMessage
        || '我是医疗分诊助手，请告诉我您的症状，我来帮您推荐合适的科室。'
      triageLoading.value = false
      triageResult.value = null
      await ElMessageBox.alert(message, '请描述您的症状', {
        type: 'info',
        confirmButtonText: '我知道了',
      }).catch(() => {
        // 用户关闭弹窗，静默处理
      })
      return
    }
    triageResult.value = result
    ElMessage.success('AI 导诊结果已生成')
  } catch (err: any) {
    console.error('[AI导诊] API 调用失败:', err?.message)
    // 网络/超时/服务端异常：弹窗提示用户，并清掉 mock fallback（避免显示假数据误导）
    triageLoading.value = false
    triageResult.value = null
    try {
      await ElMessageBox.alert(
        '上次导诊请求未能完成，请检查网络后重新输入症状再试一次。',
        '导诊未完成',
        {
          type: 'warning',
          confirmButtonText: '我知道了',
          callback: () => {
            triageForm.value.symptoms = ''
          },
        },
      )
    } catch {
      // 用户关闭弹窗
    }
  }
  triageLoading.value = false
}

// ========== Step 2: 选择排班 ==========
async function loadAvailableSchedules() {
  const departmentId = triageResult.value?.recommendedDepartmentId
  selectedSchedule.value = null
  selectedLevel.value = null
  availableSchedules.value = []
  if (!departmentId) {
    ElMessage.warning('暂时无法加载排班，请稍后重试或返回导诊重新选择科室')
    return
  }
  scheduleLoading.value = true
  try {
    const schedules = await scheduleApi.schedulingOptions(departmentId, scheduleDate.value)
    availableSchedules.value = schedules.filter(item => item.status === 1 && (item.availableQuota || 0) > 0)
  } catch (err: any) {
    console.error('加载排班失败:', err)
    ElMessage.error(err?.message || '加载排班失败')
  } finally {
    scheduleLoading.value = false
  }
}

async function onScheduleDateChange() {
  await loadAvailableSchedules()
}

function selectSchedule(schedule: any) {
  if (!schedule.registLevelId) {
    ElMessage.warning('该排班缺少挂号级别，暂不能选择')
    return
  }
  selectedSchedule.value = schedule
  selectedLevel.value = {
    id: schedule.registLevelId,
    name: schedule.registLevelName || '挂号',
    price: schedule.price || 0,
  }
}

async function ensureDefaultSettleCategory() {
  if (defaultSettleCategoryId.value) return defaultSettleCategoryId.value
  const categories = await registrationApi.settleCategories()
  const matched = categories.find(item => item.name?.includes('自费') || item.name?.includes('普通')) || categories[0]
  if (!matched?.id) {
    throw new Error('未配置结算类别，无法挂号')
  }
  defaultSettleCategoryId.value = matched.id
  return matched.id
}

// ========== Step 3: 确认挂号 ==========
async function submitRegistration() {
  if (!selectedSchedule.value || !selectedLevel.value) {
    return
  }
  submitting.value = true
  try {
    // 调用真实的挂号接口
    const patientId = authStore.currentPatientId || authStore.currentPatient?.patientId
    if (!patientId) {
      ElMessage.error('请先选择就诊人')
      return
    }
    const currentPatient = authStore.currentPatient
    const settleCategoryId = await ensureDefaultSettleCategory()
    const result = await registrationApi.createRegistration({
      patientId,
      patientName: currentPatient?.realName,
      gender: currentPatient?.gender,
      idCard: currentPatient?.idCard,
      cardNumber: currentPatient?.idCard,
      birthdate: currentPatient?.birthdate,
      homeAddress: currentPatient?.homeAddress,
      departmentId: selectedSchedule.value.departmentId,
      physicianId: selectedSchedule.value.physicianId,
      schedulingId: selectedSchedule.value.id,
      visitDate: selectedSchedule.value.workDate,
      visitTime: selectedSchedule.value.timeSlot,
      registLevelId: selectedLevel.value.id,
      settleCategoryId,
      complaint: triageForm.value.symptoms,
      aiTriageResult: triageResult.value,
    })

    registrationResult.value = {
      id: result.id,
      departmentName: selectedSchedule.value.departmentName,
      physicianName: selectedSchedule.value.physicianName,
      visitDate: selectedSchedule.value.workDate,
      visitTime: selectedSchedule.value.timeSlot,
      amount: result.amount ?? selectedLevel.value.price,
      statusName: result.payStatusName || result.statusName || '待缴费',
      paymentMessage: result.paymentMessage,
      payStatus: result.payStatus,
      accountBalance: result.accountBalance,
    }
    if (typeof result.accountBalance === 'number') {
      authStore.setPatientBalance(patientId, result.accountBalance)
    }
    ElMessage.success(result.paymentMessage || '挂号成功')
    await loadRegistrations()
    await nextStep()
  } catch (err: any) {
    console.error('挂号失败:', err)
    ElMessage.error(err?.message || '挂号失败，请重试')
  } finally {
    submitting.value = false
  }
}

// ========== Step 4: AI预问诊 ==========
function riskTone(level?: string) {
  if (level === 'critical') return 'danger'
  if (level === 'urgent' || level === 'medium') return 'warning'
  return 'success'
}

function urgencyLabel(level?: string) {
  const map: Record<string, string> = {
    I: '急危 - 立即拨打120/去急诊', II: '急重 - 15-60分钟内处理', III: '紧急 - 2-4小时内处理',
    IV: '次紧急 - 48小时内就诊', V: '非紧急 - 择期处理',
  }
  return map[level || ''] || level || ''
}

function urgencyTone(level?: string) {
  if (level === 'I') return 'danger'
  if (level === 'II') return 'warning'
  if (level === 'III') return 'primary'
  if (level === 'IV') return 'success'
  return 'ai'
}

function restart() {
  // 停止录音
  if (audioProcessor) {
    audioProcessor.disconnect()
    audioProcessor = null
  }
  if (sourceNode) {
    sourceNode.disconnect()
    sourceNode = null
  }
  if (audioContext) {
    audioContext.close()
    audioContext = null
  }
  audioBuffer = []
  isRecording.value = false

  currentStep.value = 0
  triageForm.value = { symptoms: '' }
  triageResult.value = null
  selectedSchedule.value = null
  selectedLevel.value = null
  availableSchedules.value = []
  scheduleDate.value = formatDate(new Date())
  registrationResult.value = null
  previsitForm.value = { chiefComplaint: '', presentIllness: '', pastHistory: '', allergyHistory: '' }
  previsitCompleted.value = false
  currentCategory.value = null
}

const currentBalance = computed(() => Number(authStore.currentPatient?.accountBalance || 0))
const selectedFee = computed(() => Number(selectedLevel.value?.price || 0))
const balanceEnough = computed(() => currentBalance.value >= selectedFee.value)

// 计算属性判断是否显示挂号成功卡片
const showSuccessCard = computed(() => {
  return currentStep.value === 3 || (currentStep.value === 2 && registrationResult.value)
})
</script>

<template>
  <div class="registration-wizard">
    <div v-if="pageMode === 'list'" class="registration-list-page">
      <div class="list-toolbar">
        <div>
          <h2>我的挂号</h2>
          <p>这里展示当前就诊人的挂号记录，就诊时间、科室医生、费用状态都会集中显示。</p>
        </div>
        <button class="btn-primary" @click="startRegistration">预约新挂号</button>
      </div>

      <div class="list-filters">
        <div class="filter-field filter-field--tabs">
          <label>挂号状态</label>
          <ElRadioGroup v-model="activeTab" class="tab-filter">
            <ElRadioButton value="all">全部</ElRadioButton>
            <ElRadioButton value="ongoing">进行中</ElRadioButton>
            <ElRadioButton value="done">已完成</ElRadioButton>
            <ElRadioButton value="cancelled">已取消</ElRadioButton>
          </ElRadioGroup>
        </div>
        <div class="filter-field">
          <label>就诊日期</label>
          <ElDatePicker
            v-model="visitDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            clearable
            class="field"
          />
        </div>
        <div class="filter-field">
          <label>排序字段</label>
          <select v-model="sortBy" class="form-input">
            <option value="payTime">就诊时间</option>
            <option value="createTime">开单时间</option>
            <option value="refundTime">退号时间</option>
          </select>
        </div>
        <div class="filter-field">
          <label>排序方向</label>
          <ElRadioGroup v-model="sortDir">
            <ElRadio value="desc">降序</ElRadio>
            <ElRadio value="asc">升序</ElRadio>
          </ElRadioGroup>
        </div>
        <div class="filter-field filter-summary">
          <span>已显示 {{ filteredRegistrations.length }} / {{ registrations.length }} 条</span>
        </div>
      </div>

      <div v-if="registrationLoading" class="empty-state">正在加载挂号记录...</div>
      <div v-else-if="registrations.length === 0" class="empty-state rich-empty">
        <strong>暂无挂号记录</strong>
        <span>完成预约后，挂号单会出现在这里，后续医生开具的病历、检查、处方也可以继续从电子病历入口查看。</span>
        <button class="btn-primary" @click="startRegistration">去预约挂号</button>
      </div>
      <div v-else-if="filteredRegistrations.length === 0" class="empty-state">
        当前筛选条件下没有挂号记录，<button class="link-btn" @click="clearFilters">清空筛选</button>
      </div>
      <div v-else class="registration-record-list">
        <div v-for="record in filteredRegistrations" :key="record.id" class="registration-record-card">
          <div class="record-main">
            <div class="record-title-row">
              <strong>{{ record.departmentName || '未分配科室' }}</strong>
              <StatusTag :tone="registrationStatusTone(record)">{{ registrationStatusLabel(record) }}</StatusTag>
              <StatusTag :tone="paymentStatusTone(record.payStatus)">{{ record.payStatusName || (record.payStatus === 1 ? '已缴费' : '待缴费') }}</StatusTag>
              <StatusTag v-if="record.checkedIn" tone="success">✅ 已报到</StatusTag>
              <StatusTag v-else-if="canCheckIn(record)" tone="warning">待报到</StatusTag>
            </div>
            <div class="record-meta">
              <span>挂号单号：{{ record.id }}</span>
              <span>就诊时间：{{ formatVisitTime(record) }}</span>
              <span>医生：{{ record.physicianName || '待分配' }}</span>
              <span>挂号级别：{{ record.registLevelName || '-' }}</span>
            </div>
            <p v-if="record.complaint" class="record-complaint">主诉：{{ record.complaint }}</p>
            <div class="record-actions">
              <button class="btn-outline btn-sm" @click="openRegistrationDetail(record)">查看详情</button>
              <button v-if="canPay(record)" class="btn-primary btn-sm" :disabled="actionLoading" @click="handlePay(record)">去缴费</button>
              <button v-if="canCheckInSimulate(record) && isDev" class="btn-primary btn-sm" :disabled="actionLoading" @click="handleCheckIn(record)">📍 模拟报到</button>
              <button v-if="canCancel(record)" class="btn-outline btn-sm btn-danger" :disabled="actionLoading" @click="handleCancel(record)">取消挂号</button>
              <button
                v-if="preConsultBtnFor(record)"
                class="btn-sm"
                :class="preConsultBtnFor(record)?.type === 'primary' ? 'btn-primary' : preConsultBtnFor(record)?.type === 'warning' ? 'btn-warning' : 'btn-outline'"
                @click="goPrevisit(record)"
              >💬 {{ preConsultBtnFor(record)?.text }}</button>
            </div>
          </div>
          <div class="record-side">
            <span class="record-amount">¥{{ Number(record.amount || 0).toFixed(2) }}</span>
            <span class="record-pay">{{ record.payStatusName || (record.payStatus === 1 ? '已缴费' : '待缴费') }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 步骤进度条 -->
    <div v-if="pageMode === 'wizard'" class="step-timeline">
      <div class="timeline-track">
        <div
          class="timeline-progress"
          :style="{ width: ((currentStep / (steps.length - 1)) * 100) + '%' }"
        ></div>
      </div>
      <div class="timeline-steps">
        <div
          v-for="(step, index) in steps"
          :key="step.key"
          class="timeline-step"
          :class="{ 'is-active': stepStatus[index] === 'active', 'is-completed': stepStatus[index] === 'completed' }"
          @click="goToStep(index)"
        >
          <div class="step-node">
            <span v-if="stepStatus[index] === 'completed'" class="node-check">✓</span>
            <span v-else class="node-number">{{ index + 1 }}</span>
          </div>
          <div class="step-content">
            <span class="step-icon">{{ step.icon }}</span>
            <span class="step-title">{{ step.title }}</span>
            <span class="step-desc">{{ step.desc }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 步骤内容区域 -->
    <div v-if="pageMode === 'wizard'" class="step-content">

      <!-- Step 1: AI导诊 -->
      <div v-if="currentStep === 0" class="step-card">
        <div class="step-header">
          <span class="step-icon-lg">🤖</span>
          <div class="header-text">
            <h2>AI 智能导诊</h2>
            <p>描述您的症状，AI将为您推荐合适的科室和医生</p>
          </div>
        </div>

        <div class="triage-form-full">
          <!-- 常见症状快捷分类 -->
          <div class="symptom-categories">
            <span class="category-label">快捷选择：</span>
            <button
              v-for="cat in symptomCategories"
              :key="cat.key"
              class="category-btn"
              :class="{ 'is-active': currentCategory?.key === cat.key }"
              @click="selectCategory(cat)"
            >
              <span>{{ cat.icon }}</span>
              <span>{{ cat.label }}</span>
            </button>
          </div>

          <div class="form-item">
            <label>症状描述</label>
            <div class="textarea-wrapper">
              <textarea
                v-model="triageForm.symptoms"
                class="form-textarea"
                :placeholder="currentCategory?.placeholder || '请输入您的主要症状，例如：胃痛3天，伴有反酸和嗳气，进食后加重...'"
                rows="6"
              ></textarea>
              <!-- 语音输入按钮 -->
              <button
                class="voice-btn"
                :class="{ 'is-recording': isRecording, 'is-loading': voiceLoading }"
                :title="isRecording ? '停止录音' : '语音输入'"
                :disabled="voiceLoading"
                @click="toggleVoice"
              >
                <span>{{ isRecording ? '🔴' : voiceLoading ? '⏳' : '🎤' }}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 领域护栏：话题外时由 runTriage() 弹窗提示，此处无需展示卡片 -->

        <div v-if="triageResult && !triageResult.isOutOfScope" class="triage-result">
          <!-- 紧迫性提示 -->
          <div class="urgency-bar" :class="{ 'is-urgent': triageResult.urgencyLevel === 'I' || triageResult.urgencyLevel === 'II' }">
            <StatusTag :tone="urgencyTone(triageResult.urgencyLevel)">
              {{ urgencyLabel(triageResult.urgencyLevel) }}
            </StatusTag>
            <span class="urgency-advice">{{ triageResult.urgencyAdvice }}</span>
          </div>

          <div class="result-alert">
            <div class="alert-icon">✓</div>
            <div class="alert-content">
              <strong>推荐科室：{{ triageResult.recommendedDepartment }}</strong>
              <p v-if="triageResult.departmentReason">{{ triageResult.departmentReason }}</p>
              <p v-else>根据您的症状，AI为您推荐以下科室和医生</p>
            </div>
          </div>

          <!-- 备选科室 + 可信度 -->
          <div v-if="triageResult.alternativeDepartments?.length || triageResult.confidenceLevel" class="info-row">
            <span v-if="triageResult.alternativeDepartments?.length">
              <strong>备选：</strong>
              <StatusTag v-for="d in triageResult.alternativeDepartments" :key="d" tone="primary">{{ d }}</StatusTag>
            </span>
            <span v-if="triageResult.confidenceLevel">
              <strong>可信度：</strong>
              <StatusTag :tone="triageResult.confidenceLevel === 'high' ? 'success' : 'warning'">
                {{ triageResult.confidenceLevel === 'high' ? '高' : triageResult.confidenceLevel === 'medium' ? '中' : '低' }}
              </StatusTag>
            </span>
          </div>

          <!-- 红旗征 -->
          <div v-if="triageResult.redFlags?.length" class="red-flag-bar">
            <strong>⚠️ 出现以下症状请立即去急诊或拨打120：</strong>
            <span v-for="flag in triageResult.redFlags" :key="flag" class="red-flag-item">• {{ flag }}</span>
          </div>

          <div class="result-cards">
            <div class="result-card">
              <div class="card-header">
                <span>推荐医生</span>
                <StatusTag :tone="riskTone(triageResult.riskLevel)">
                  {{ triageResult.riskLevel === 'critical' ? '高风险' : triageResult.riskLevel === 'urgent' ? '中高风险' : '一般' }}
                </StatusTag>
              </div>
              <div class="doctors-list">
                <div
                  v-for="doctor in (triageResult.recommendedDoctors || [])"
                  :key="doctor.id"
                  class="doctor-chip"
                >
                  <span class="doctor-name">{{ doctor.name }}</span>
                  <span class="doctor-title">{{ doctor.title }}</span>
                </div>
                <span v-if="!triageResult.recommendedDoctors?.length" class="no-doctor">暂无推荐医生信息</span>
              </div>
            </div>

            <div class="result-card">
              <div class="card-header">
                <span>AI 分析建议</span>
              </div>
              <p class="advice-text">{{ triageResult.selfCareAdvice || triageResult.aiAnalysis?.selfCareAdvice || '暂无建议' }}</p>
              <div v-if="triageResult.aiAnalysis?.possibleConditions?.length" class="tags-section">
                <div class="tags-row">
                  <strong>可疑疾病：</strong>
                  <div class="tag-list">
                    <StatusTag v-for="condition in triageResult.aiAnalysis?.possibleConditions" :key="condition" tone="ai">
                      {{ condition }}
                    </StatusTag>
                  </div>
                </div>
                <div v-if="triageResult.aiAnalysis?.suggestedExaminations?.length" class="tags-row">
                  <strong>建议检查：</strong>
                  <div class="tag-list">
                    <StatusTag v-for="exam in triageResult.aiAnalysis?.suggestedExaminations" :key="exam" tone="primary">
                      {{ exam }}
                    </StatusTag>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="step-actions">
          <button class="btn-outline" @click="backToList">返回我的挂号</button>
          <!-- 未生成导诊结果 → "开始导诊" -->
          <button
            v-if="!triageResult"
            class="btn-primary"
            :disabled="!triageForm.symptoms.trim() || triageLoading"
            @click="runTriage"
          >
            <span v-if="triageLoading" class="loading-dots">分析中</span>
            <span v-else>开始导诊</span>
          </button>
          <!-- 正常导诊结果出来后 → "下一步" -->
          <button
            v-else
            class="btn-primary"
            @click="nextStep"
          >
            下一步
          </button>
        </div>
      </div>

      <!-- Step 2: 选择排班 -->
      <div v-if="currentStep === 1" class="step-card">
        <div class="step-header">
          <span class="step-icon-lg">📅</span>
          <div class="header-text">
            <h2>选择医生和时间</h2>
            <p>根据导诊推荐，选择可用的排班进行挂号</p>
          </div>
        </div>

        <div class="schedule-filter">
          <label>就诊日期</label>
          <input v-model="scheduleDate" class="form-input" type="date" @change="onScheduleDateChange" />
          <button class="btn-outline" :disabled="scheduleLoading" @click="loadAvailableSchedules">
            {{ scheduleLoading ? '加载中...' : '刷新排班' }}
          </button>
        </div>

        <div v-if="scheduleLoading" class="empty-state">正在加载可用排班...</div>
        <div v-else-if="availableSchedules.length === 0" class="empty-state">该科室当前日期暂无可挂号排班，请切换日期或返回重新导诊</div>

        <div v-else class="schedule-list">
          <div
            v-for="schedule in availableSchedules"
            :key="schedule.id"
            class="schedule-item"
            :class="{ 'is-selected': selectedSchedule?.id === schedule.id }"
            @click="selectSchedule(schedule)"
          >
            <div class="schedule-main">
              <div class="schedule-doctor">
                <span class="doctor-name">{{ schedule.physicianName }}</span>
                <StatusTag tone="primary">{{ schedule.physicianTitle || schedule.registLevelName || '医生' }}</StatusTag>
              </div>
              <div class="schedule-info">
                <span>📅 {{ schedule.workDate }} {{ schedule.timeSlot }}</span>
                <span>🏥 {{ schedule.departmentName }}</span>
                <span>💳 {{ schedule.registLevelName || '挂号' }} ¥{{ schedule.price || 0 }}</span>
              </div>
            </div>
            <div class="schedule-status">
              <StatusTag :tone="schedule.availableQuota > 10 ? 'success' : 'warning'">
                剩余 {{ schedule.availableQuota }} 个号
              </StatusTag>
              <span v-if="selectedSchedule?.id === schedule.id" class="selected-badge">已选</span>
            </div>
          </div>
        </div>

        <div v-if="selectedSchedule" class="level-card">
          <div class="level-header">
            <span>挂号级别</span>
          </div>
          <div class="level-content">
            <div class="level-info">
              <span class="level-name">{{ selectedLevel.name }}</span>
              <span class="level-price">¥{{ selectedLevel.price }}</span>
            </div>
            <StatusTag tone="success">已选</StatusTag>
          </div>
        </div>

        <div class="step-actions">
          <button class="btn-outline" @click="prevStep">上一步</button>
          <button
            class="btn-primary"
            :disabled="!selectedSchedule || !selectedLevel"
            @click="nextStep"
          >
            下一步
          </button>
        </div>
      </div>

      <!-- Step 3: 确认挂号 -->
      <div v-if="currentStep === 2 && !registrationResult" class="step-card">
        <div class="step-header">
          <span class="step-icon-lg">✅</span>
          <div class="header-text">
            <h2>确认挂号信息</h2>
            <p>请核对以下信息，确认无误后提交挂号</p>
          </div>
        </div>

        <!-- 患者信息提示 -->
        <div class="patient-info-bar">
          <div class="patient-info-left">
            <span class="patient-info-label">就诊人：</span>
            <span class="patient-info-value">{{ authStore.currentPatient?.realName }}</span>
            <el-tag v-if="authStore.currentPatient?.relation" size="small" type="info">
              {{ authStore.currentPatient.relation }}
            </el-tag>
          </div>
          <div v-if="authStore.currentPatient?.allergyHistory" class="patient-info-right">
            <el-icon><Warning /></el-icon>
            <span class="allergy-warning">过敏史：{{ authStore.currentPatient.allergyHistory }}</span>
          </div>
        </div>

        <div class="confirm-card">
          <div class="confirm-row">
            <div class="confirm-item">
              <label>就诊科室</label>
              <span>{{ selectedSchedule?.departmentName }}</span>
            </div>
            <div class="confirm-item">
              <label>就诊医生</label>
              <span>{{ selectedSchedule?.physicianName }} ({{ selectedSchedule?.physicianTitle }})</span>
            </div>
          </div>
          <div class="confirm-row">
            <div class="confirm-item">
              <label>就诊时间</label>
              <span>{{ selectedSchedule?.workDate }} {{ selectedSchedule?.timeSlot }}</span>
            </div>
            <div class="confirm-item">
              <label>挂号级别</label>
              <span>{{ selectedLevel?.name }}</span>
            </div>
          </div>
          <div class="confirm-total">
            <div class="total-label">
              <span>挂号费用</span>
              <StatusTag :tone="balanceEnough ? 'success' : 'warning'">
                {{ balanceEnough ? '余额自动支付' : '余额不足，待缴费' }}
              </StatusTag>
            </div>
            <span class="total-price">¥{{ selectedLevel?.price }}</span>
          </div>
          <div class="confirm-row">
            <div class="confirm-item">
              <label>账户余额</label>
              <span>¥{{ currentBalance.toFixed(2) }}</span>
            </div>
            <div class="confirm-item">
              <label>支付提示</label>
              <span>{{ balanceEnough ? '确认后将直接从当前就诊人账户扣费' : '确认后生成待缴费记录，可充值后缴费' }}</span>
            </div>
          </div>
        </div>

        <div class="step-actions">
          <button class="btn-outline" @click="prevStep">返回修改</button>
          <button
            class="btn-primary btn-lg"
            :disabled="submitting"
            @click="submitRegistration"
          >
            {{ submitting ? '提交中...' : '确认挂号' }}
          </button>
        </div>
      </div>

      <!-- Step 4: 挂号成功 & AI预问诊 -->
      <div v-if="showSuccessCard" class="step-card">
        <div class="step-header">
          <span class="step-icon-lg">🎉</span>
          <div class="header-text">
            <h2>挂号成功</h2>
            <p>请按时前往就诊。您现在可以继续进行AI预问诊，帮助医生更快了解您的病情</p>
          </div>
        </div>

        <div class="success-card-content">
          <div class="success-row">
            <label>挂号单号</label>
            <span class="mono">{{ registrationResult?.id }}</span>
          </div>
          <div class="success-row">
            <label>就诊科室</label>
            <span>{{ registrationResult?.departmentName }}</span>
          </div>
          <div class="success-row">
            <label>就诊医生</label>
            <span>{{ registrationResult?.physicianName }}</span>
          </div>
          <div class="success-row">
            <label>就诊时间</label>
            <span>{{ registrationResult?.visitDate }} {{ registrationResult?.visitTime }}</span>
          </div>
          <div class="success-row highlight">
            <label>费用状态</label>
            <StatusTag :tone="registrationResult?.payStatus === 1 ? 'success' : 'warning'">{{ registrationResult?.statusName }}</StatusTag>
          </div>
          <div v-if="registrationResult?.paymentMessage" class="success-row">
            <label>支付提示</label>
            <span>{{ registrationResult.paymentMessage }}</span>
          </div>
          <div v-if="registrationResult?.accountBalance !== undefined" class="success-row">
            <label>账户余额</label>
            <span>¥{{ Number(registrationResult.accountBalance).toFixed(2) }}</span>
          </div>
        </div>

        <div v-if="registrationResult && registrationResult.payStatus === 1" class="checkin-section">
          <div class="previsit-divider">
            <span>到院报到</span>
          </div>
          <h3 class="checkin-title">📱 请保存您的报到二维码</h3>
          <p class="checkin-desc">到院后请在报到机上扫描此二维码报到，进入候诊队列</p>
          <CheckInQRCode :register-id="registrationResult.id" />
        </div>

        <div v-if="!previsitCompleted" class="previsit-section">
          <div class="previsit-divider">
            <span>或</span>
          </div>
          <h3 class="previsit-title">💬 进行AI预问诊（可选）</h3>
          <p class="previsit-desc">和 AI 聊几句，提前采集病史信息，让医生接诊更高效</p>

          <div class="previsit-cta-card">
            <div class="previsit-cta-icon">🤖</div>
            <div class="previsit-cta-content">
              <h4>对话式预问诊</h4>
              <p>AI 会像医生秘书一样依次问您：哪里不舒服→持续多久→伴随症状→既往史→过敏史。完成后自动生成结构化病历给医生。</p>
              <p class="previsit-cta-hint">您也可以稍后从「我的挂号」继续</p>
            </div>
          </div>

          <div class="step-actions center">
            <button class="btn-outline" @click="backToList">稍后再说，查看我的挂号</button>
            <button
              v-if="registrationResult"
              class="btn-primary"
              @click="goPrevisitById(registrationResult.id)"
            >
              <span>💬 立即开始对话</span>
            </button>
          </div>
        </div>

        <div v-else class="previsit-completed">
          <div class="completed-badge">
            <span class="check-icon">✓</span>
            <span>AI预问诊已完成</span>
          </div>
          <div class="step-actions center">
            <button class="btn-outline" @click="restart">继续挂号</button>
            <button class="btn-primary" @click="backToList">查看我的挂号</button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="detailVisible"
      title="挂号详情"
      width="720px"
      class="registration-detail-dialog"
      destroy-on-close
    >
      <div v-if="detailLoading" class="empty-state">正在加载挂号详情...</div>
      <div v-else-if="selectedRegistration" class="detail-dialog">
        <div class="detail-header">
          <div class="detail-header-main">
            <span class="detail-department">{{ selectedRegistration.departmentName || '未分配科室' }}</span>
            <span class="detail-doctor">{{ selectedRegistration.physicianName || '待分配医生' }}</span>
          </div>
          <div class="detail-header-side">
            <div class="detail-amount">
              <span class="detail-amount-label">挂号金额</span>
              <span class="detail-amount-value">¥{{ formatMoney(selectedRegistration.amount) }}</span>
            </div>
            <div class="detail-tags">
              <StatusTag :tone="registrationStatusTone(selectedRegistration)">
                {{ registrationStatusLabel(selectedRegistration) }}
              </StatusTag>
              <StatusTag :tone="paymentStatusTone(selectedRegistration.payStatus)">
                {{ selectedRegistration.payStatusName || (selectedRegistration.payStatus === 1 ? '已缴费' : '待缴费') }}
              </StatusTag>
              <StatusTag v-if="selectedRegistration.checkedIn" tone="success">✅ 已报到</StatusTag>
              <StatusTag v-else-if="canCheckIn(selectedRegistration)" tone="warning">未报到</StatusTag>
            </div>
          </div>
        </div>

        <div
          v-if="canShowDetailQr(selectedRegistration)"
          class="checkin-section"
        >
          <h3 class="checkin-title">📱 报到二维码</h3>
          <p class="checkin-desc">到院后请在报到机上扫描此二维码</p>
          <CheckInQRCode :register-id="selectedRegistration.id" />
          <p v-if="selectedRegistration.checkInTime" class="checkin-time">
            报到时间：{{ formatDateTime(selectedRegistration.checkInTime) }}
          </p>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">基本信息</div>
          <div class="detail-grid">
            <div class="detail-item">
              <label>挂号单号</label>
              <span class="mono">{{ selectedRegistration.id }}</span>
            </div>
            <div class="detail-item">
              <label>病历号</label>
              <span>{{ selectedRegistration.caseNumber || '-' }}</span>
            </div>
            <div class="detail-item">
              <label>就诊时间</label>
              <span>{{ formatVisitTime(selectedRegistration) }}</span>
            </div>
            <div class="detail-item">
              <label>挂号级别</label>
              <span>{{ selectedRegistration.registLevelName || '-' }}</span>
            </div>
            <div class="detail-item">
              <label>结算类别</label>
              <span>{{ selectedRegistration.settleCategoryName || '-' }}</span>
            </div>
            <div class="detail-item">
              <label>支付时间</label>
              <span>{{ formatDateTime(selectedRegistration.payTime) }}</span>
            </div>
            <div class="detail-item">
              <label>退费时间</label>
              <span>{{ formatDateTime(selectedRegistration.refundTime) }}</span>
            </div>
            <div class="detail-item">
              <label>建卡时间</label>
              <span>{{ formatDateTime(selectedRegistration.createTime) }}</span>
            </div>
          </div>
        </div>

        <div v-if="selectedRegistration.complaint" class="detail-section">
          <div class="detail-section-title">主诉</div>
          <p class="detail-complaint">{{ selectedRegistration.complaint }}</p>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">
            费用明细
            <span class="detail-section-meta" v-if="selectedRegistration.expenseRecords?.length">
              共 {{ selectedRegistration.expenseRecords.length }} 条
            </span>
          </div>
          <div v-if="selectedRegistration.expenseRecords?.length" class="expense-table-wrapper">
            <table class="expense-table">
              <thead>
                <tr>
                  <th>项目</th>
                  <th>类别</th>
                  <th>数量</th>
                  <th>金额</th>
                  <th>状态</th>
                  <th>支付/退费时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in selectedRegistration.expenseRecords" :key="item.id">
                  <td>{{ item.itemName || '-' }}</td>
                  <td>{{ item.categoryName || '-' }}</td>
                  <td>{{ item.quantity || 0 }}</td>
                  <td>¥{{ formatMoney(item.totalAmount) }}</td>
                  <td>
                    <StatusTag :tone="paymentStatusTone(item.status)">{{ item.statusName || '-' }}</StatusTag>
                  </td>
                  <td>{{ formatDateTime(item.payTime) || formatDateTime(item.refundTime) || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state small">暂无费用明细</div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-actions">
          <button
            v-if="selectedRegistration && canCancel(selectedRegistration)"
            class="btn-outline btn-danger"
            :disabled="actionLoading"
            @click="handleCancel(selectedRegistration)"
          >
            取消挂号
          </button>
          <button
            v-if="selectedRegistration && canCheckInSimulate(selectedRegistration) && isDev"
            class="btn-primary"
            :disabled="actionLoading"
            @click="handleCheckIn(selectedRegistration)"
          >
            📍 模拟报到
          </button>
          <button
            v-if="selectedRegistration && canPay(selectedRegistration)"
            class="btn-primary"
            :disabled="actionLoading"
            @click="handlePay(selectedRegistration)"
          >
            去缴费
          </button>
          <button class="btn-outline" @click="detailVisible = false">关闭</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ====================================================================
   PatientRegistration — 重新设计的玻璃拟态风格
   严守项目 tokens.css，结构/交互保持不变
   ==================================================================== */

.registration-wizard {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  width: 100%;
}

/* ===================== 列表页 ===================== */
.registration-list-page {
  width: 88%;
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--space-8);
  background: var(--color-surface);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
}

.list-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-5);
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--color-border);
}

.list-toolbar h2 {
  margin: 0 0 var(--space-2);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.01em;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.list-toolbar p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
  font-size: 13.5px;
  max-width: 640px;
}

/* —— 筛选条 —— */
.list-filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  align-items: flex-end;
  margin-bottom: var(--space-5);
  padding: var(--space-4) var(--space-5);
  background: var(--color-control);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 168px;
}

.filter-field label {
  font-size: 11.5px;
  color: var(--color-text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.filter-field .form-input,
.filter-field :deep(.el-date-editor) {
  height: 36px;
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  font-size: 13px;
  font-family: inherit;
  transition: border-color var(--duration-base) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard);
}

.filter-field .form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.filter-field .field {
  width: 100%;
}

/* tab 顶层筛选：占满整行，区别于下方次级筛选 */
/* tab 字段：作为筛选行的一个普通列，与其他字段并排，宽度不够时由 flex-wrap 自然换行 */
.filter-field--tabs {
  min-width: auto;
}

.filter-summary {
  min-width: auto;
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 12px;
  padding-bottom: 8px;
}

.link-btn {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  font-size: inherit;
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* —— 挂号记录卡片 —— */
.registration-record-list {
  display: grid;
  gap: var(--space-4);
}

.registration-record-card {
  position: relative;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface-strong);
  box-shadow: var(--shadow-sm);
  transition: transform var(--duration-base) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard),
              border-color var(--duration-base) var(--ease-standard);
  overflow: hidden;
}

.registration-record-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: var(--gradient-primary);
  opacity: 0.85;
}

.registration-record-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--color-border-strong);
}

.record-main {
  flex: 1;
  display: grid;
  gap: var(--space-3);
  padding-left: var(--space-2);
}

/* 操作按钮区：拉开间距 + 危险操作视觉隔离 */
.record-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

/* 主操作（去缴费）和次要操作之间留出呼吸距离 */
.record-actions .btn-primary.btn-sm {
  margin-right: var(--space-1);
}

/* 危险操作（取消挂号）和其它按钮之间加分隔，避免误点 */
.record-actions .btn-outline.btn-sm.btn-danger {
  margin-left: var(--space-3);
  position: relative;
}

.record-actions .btn-outline.btn-sm.btn-danger::before {
  content: '';
  position: absolute;
  left: calc(var(--space-3) * -1);
  top: 50%;
  width: 1px;
  height: 18px;
  background: var(--color-border);
  transform: translateY(-50%);
}

.record-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.record-title-row strong {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.record-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2) var(--space-5);
  color: var(--color-text-muted);
  font-size: 13px;
}

.record-complaint {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  background: var(--color-primary-soft);
  border-left: 3px solid var(--color-primary);
  border-radius: var(--radius-sm);
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.7;
}

.record-side {
  min-width: 124px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: var(--color-control);
  border-radius: var(--radius-lg);
}

.record-amount {
  color: var(--color-primary);
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1;
}

.record-pay {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 500;
}

/* —— 空状态 —— */
.empty-state {
  padding: var(--space-8) var(--space-4);
  color: var(--color-text-muted);
  font-size: 14px;
  text-align: center;
  background: var(--color-control);
  border-radius: var(--radius-lg);
}

.rich-empty {
  display: grid;
  gap: var(--space-3);
  justify-items: center;
  padding: var(--space-8);
}

.rich-empty strong {
  font-size: 18px;
  color: var(--color-text);
}

.rich-empty span {
  max-width: 560px;
  line-height: 1.7;
  color: var(--color-text-muted);
  font-size: 13.5px;
  text-align: center;
}

.empty-state.small {
  padding: var(--space-4);
  font-size: 13px;
}

/* ===================== 按钮 ===================== */
.btn-primary,
.btn-outline,
.btn-warning {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-family: inherit;
  font-weight: 600;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: transform var(--duration-fast) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard),
              background var(--duration-base) var(--ease-standard),
              border-color var(--duration-base) var(--ease-standard),
              color var(--duration-base) var(--ease-standard);
  white-space: nowrap;
  user-select: none;
}

.btn-primary {
  padding: 10px 22px;
  background: var(--gradient-primary);
  color: #fff;
  border: none;
  font-size: 14px;
  box-shadow: 0 6px 18px rgba(31, 140, 255, 0.28);
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 26px rgba(31, 140, 255, 0.36);
}

.btn-primary:active:not(:disabled) {
  transform: translateY(0);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

.btn-primary.btn-lg {
  padding: var(--space-4) var(--space-8);
  font-size: 15px;
}

.btn-outline {
  padding: 10px 20px;
  background: var(--color-surface-strong);
  color: var(--color-text);
  border: 1px solid var(--color-border-strong);
  font-size: 14px;
}

.btn-outline:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.btn-outline.btn-danger {
  color: var(--color-danger);
  border-color: rgba(239, 77, 90, 0.32);
}

.btn-outline.btn-danger:hover {
  background: rgba(239, 77, 90, 0.08);
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.btn-warning {
  padding: 8px 16px;
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
  border: 1px solid rgba(245, 159, 0, 0.42);
  font-size: 13px;
  font-weight: 600;
  box-shadow: none;
}

.btn-warning:hover:not(:disabled) {
  background: rgba(245, 159, 0, 0.18);
  border-color: var(--color-warning);
  transform: translateY(-1px);
}

.btn-warning:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 小号按钮（列表行内动作） */
.btn-sm {
  padding: 7px 14px;
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
  border: 1px solid transparent;
  background: transparent;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.btn-sm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-outline.btn-sm {
  background: var(--color-surface-strong);
  color: var(--color-text);
  border-color: var(--color-border);
}

.btn-outline.btn-sm:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.btn-outline.btn-sm.btn-danger {
  color: var(--color-danger);
  border-color: rgba(239, 77, 90, 0.3);
}

.btn-outline.btn-sm.btn-danger:hover {
  background: rgba(239, 77, 90, 0.06);
  border-color: var(--color-danger);
}

.btn-primary.btn-sm {
  background: var(--gradient-primary);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 3px 10px rgba(31, 140, 255, 0.24);
}

.btn-primary.btn-sm:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(31, 140, 255, 0.34);
}

/* 加载点动画 */
.loading-dots::after {
  content: '';
  animation: dots 1.5s infinite;
}

@keyframes dots {
  0%, 20% { content: ''; }
  40% { content: '.'; }
  60% { content: '..'; }
  80%, 100% { content: '...'; }
}

/* ===================== 步骤时间轴 ===================== */
.step-timeline {
  position: relative;
  width: 88%;
  max-width: 1280px;
  margin: 0 auto;
  box-sizing: border-box;
  padding: var(--space-5) var(--space-8);
  background: var(--color-surface);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-sm);
}

.timeline-track {
  position: absolute;
  top: calc(var(--space-5) + 18px);
  left: calc(var(--space-8) + 18px);
  right: calc(var(--space-8) + 18px);
  height: 3px;
  background: var(--color-border);
  border-radius: 2px;
}

.timeline-progress {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: var(--gradient-primary);
  border-radius: 2px;
  transition: width 0.5s var(--ease-standard);
}

.timeline-steps {
  position: relative;
  display: flex;
  justify-content: space-between;
  width: 100%;
  box-sizing: border-box;
}

.timeline-step {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  z-index: 1;
  cursor: pointer;
  flex: 1;
  transition: transform var(--duration-fast) var(--ease-standard);
}

.timeline-step:hover {
  transform: translateY(-2px);
}

.step-node {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface-strong);
  border: 2px solid var(--color-border);
  transition: all 0.35s var(--ease-standard);
  box-shadow: 0 2px 8px rgba(31, 73, 125, 0.06);
}

.timeline-step.is-active .step-node {
  background: var(--gradient-primary);
  border-color: transparent;
  box-shadow: 0 0 0 6px var(--color-primary-soft),
              0 8px 20px rgba(31, 140, 255, 0.32);
}

.timeline-step.is-completed .step-node {
  background: var(--gradient-primary);
  border-color: transparent;
  box-shadow: 0 6px 14px rgba(31, 140, 255, 0.28);
}

.node-number {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-muted);
}

.timeline-step.is-active .node-number,
.timeline-step.is-completed .node-number {
  color: #fff;
}

.node-check {
  font-size: 16px;
  color: #fff;
  font-weight: 700;
}

.step-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  text-align: center;
}

.step-icon {
  font-size: 15px;
}

.step-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.step-desc {
  font-size: 11px;
  color: var(--color-text-muted);
}

.timeline-step.is-active .step-title {
  color: var(--color-primary);
}

.timeline-step.is-completed .step-title {
  color: var(--color-primary);
}

/* ===================== 步骤卡片通用 ===================== */
.step-card {
  padding: var(--space-5) var(--space-8);
  width: 88%;
  max-width: 1280px;
  margin: 0 auto;
  box-sizing: border-box;
  background: var(--color-surface);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
}

.step-header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--color-border);
}

.step-icon-lg {
  font-size: 34px;
  line-height: 1;
  filter: drop-shadow(0 4px 10px rgba(31, 140, 255, 0.18));
}

.header-text {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.step-header h2 {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
  letter-spacing: -0.02em;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.step-header p {
  font-size: 13.5px;
  color: var(--color-text-muted);
  margin: 0;
}

/* ===================== 表单 ===================== */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5);
  align-items: start;
  width: 100%;
  box-sizing: border-box;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form-item label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.form-textarea {
  width: 100%;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
  font-size: 14px;
  line-height: 1.75;
  resize: vertical;
  font-family: inherit;
  color: var(--color-text);
  transition: border-color var(--duration-base) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard);
}

.form-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-soft);
}

.form-textarea::placeholder {
  color: var(--color-text-soft);
}

/* ===================== 导诊 ===================== */
.triage-form-full {
  width: 100%;
}

.triage-form-full .form-item label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.triage-result {
  margin-top: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.result-alert {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5);
  background: linear-gradient(135deg, rgba(32, 180, 134, 0.14) 0%, rgba(32, 180, 134, 0.04) 100%);
  border: 1px solid rgba(32, 180, 134, 0.35);
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 24px rgba(32, 180, 134, 0.12);
}

.alert-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--color-success);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 6px 14px rgba(32, 180, 134, 0.32);
}

.alert-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.alert-content strong {
  font-size: 16px;
  color: var(--color-success);
}

.alert-content p {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
}

.result-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

.result-card {
  padding: var(--space-5);
  background: var(--color-surface-strong);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.result-card .card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
  font-weight: 600;
  font-size: 14px;
}

.doctors-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.doctor-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--color-control);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  transition: all var(--duration-base) var(--ease-standard);
}

.doctor-chip:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.doctor-name {
  font-weight: 600;
  font-size: 14px;
}

.doctor-title {
  color: var(--color-text-muted);
  font-size: 12px;
}

.advice-text {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--color-text);
  margin: 0 0 var(--space-3);
}

.tags-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.tags-row {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.tags-row strong {
  font-size: 12px;
  color: var(--color-text-muted);
  min-width: 70px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.urgency-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--color-control);
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-primary);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-4);
  font-size: 14px;
}

.urgency-bar.is-urgent {
  background: rgba(245, 159, 0, 0.08);
  border-left-color: var(--color-warning);
}

.urgency-advice {
  color: var(--color-text-muted);
}

.info-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin: var(--space-3) 0;
  font-size: 13px;
}

.red-flag-bar {
  padding: var(--space-3) var(--space-4);
  background: rgba(239, 77, 90, 0.06);
  border: 1px solid rgba(239, 77, 90, 0.28);
  border-left: 4px solid var(--color-danger);
  border-radius: var(--radius-md);
  margin: var(--space-3) 0;
  color: var(--color-danger);
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.red-flag-item {
  font-size: 13px;
  padding-left: var(--space-2);
}

.no-doctor {
  color: var(--color-text-muted);
  font-size: 13px;
}

/* 症状快捷分类 */
.symptom-categories {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  align-items: center;
}

.category-label {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-text-muted);
  margin-right: var(--space-1);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.category-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-strong);
  color: var(--color-text);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.category-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft);
  transform: translateY(-1px);
}

.category-btn.is-active {
  border-color: transparent;
  background: var(--gradient-primary);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(31, 140, 255, 0.24);
}

/* 语音输入 */
.textarea-wrapper {
  position: relative;
}

.form-textarea {
  width: 100%;
  padding-right: 64px;
}

.voice-btn {
  position: absolute;
  right: 14px;
  bottom: 14px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid var(--color-border);
  background: var(--color-surface-strong);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-base) var(--ease-standard);
  font-size: 20px;
  box-shadow: var(--shadow-sm);
}

.voice-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  transform: scale(1.05);
}

.voice-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.voice-btn.is-recording {
  border-color: var(--color-danger);
  background: rgba(239, 77, 90, 0.08);
  animation: pulse 1.2s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(239, 77, 90, 0.6); }
  50% { transform: scale(1.08); box-shadow: 0 0 0 12px rgba(239, 77, 90, 0); }
}

/* ===================== 排班列表 ===================== */
.schedule-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
  padding: var(--space-3) var(--space-4);
  background: var(--color-control);
  border-radius: var(--radius-md);
}

.schedule-filter label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.schedule-filter .form-input {
  height: 36px;
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  font-size: 13px;
  font-family: inherit;
  color: var(--color-text);
  transition: border-color var(--duration-base) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard);
}

.schedule-filter .form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.schedule-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.schedule-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  background: var(--color-surface-strong);
  border: 2px solid var(--color-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
  box-shadow: var(--shadow-sm);
}

.schedule-item:hover {
  border-color: var(--color-border-strong);
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.schedule-item.is-selected {
  border-color: transparent;
  background: linear-gradient(135deg, rgba(31, 140, 255, 0.06) 0%, rgba(47, 216, 196, 0.04) 100%);
  box-shadow: 0 0 0 2px var(--color-primary), 0 8px 20px rgba(31, 140, 255, 0.18);
}

.schedule-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.schedule-doctor {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.schedule-doctor .doctor-name {
  font-weight: 700;
  font-size: 16px;
}

.schedule-info {
  display: flex;
  gap: var(--space-4);
  font-size: 13px;
  color: var(--color-text-muted);
  flex-wrap: wrap;
}

.schedule-status {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-2);
}

.selected-badge {
  font-size: 12px;
  color: var(--color-primary);
  font-weight: 700;
  letter-spacing: 0.04em;
}

/* 挂号级别卡片 */
.level-card {
  margin-top: var(--space-4);
  background: var(--color-surface-strong);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.level-header {
  padding: var(--space-3) var(--space-4);
  background: var(--color-primary-soft);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.level-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
}

.level-info {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
}

.level-name {
  font-weight: 600;
  font-size: 15px;
}

.level-price {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: -0.02em;
}

/* 患者信息栏 */
.patient-info-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  background: var(--color-control);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-4);
}

.patient-info-left {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.patient-info-label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.patient-info-value {
  font-size: 14px;
  font-weight: 700;
}

.patient-info-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-danger);
  font-size: 13px;
}

.allergy-warning {
  color: var(--color-danger);
  font-weight: 600;
}

/* ===================== 确认信息 ===================== */
.confirm-card {
  background: var(--color-surface-strong);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.confirm-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border);
}

.confirm-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.confirm-item label {
  font-size: 11.5px;
  color: var(--color-text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.confirm-item span {
  font-size: 14.5px;
  font-weight: 500;
}

.confirm-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-5);
  background: linear-gradient(135deg, var(--color-primary-soft) 0%, rgba(47, 216, 196, 0.06) 100%);
}

.total-label {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-weight: 600;
  font-size: 15px;
}

.total-price {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: -0.02em;
  line-height: 1;
}

/* ===================== 成功页面 ===================== */
.success-card-content {
  max-width: 420px;
  margin: 0 auto var(--space-6);
  background: var(--color-surface-strong);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  overflow: hidden;
  text-align: left;
  box-shadow: var(--shadow-sm);
}

.success-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border);
}

.success-row:last-child {
  border-bottom: none;
}

.success-row label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.success-row span {
  font-size: 14px;
  font-weight: 500;
}

.success-row .mono {
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  color: var(--color-primary);
  letter-spacing: 0.02em;
}

.success-row.highlight {
  background: var(--color-primary-soft);
}

/* ===================== 报到二维码 ===================== */
.checkin-section {
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}

.checkin-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  text-align: center;
}

.checkin-desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-muted);
  text-align: center;
}

.checkin-time {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: var(--color-success);
  text-align: center;
}

/* ===================== AI预问诊 ===================== */
.previsit-section {
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border);
}

.previsit-cta-card {
  display: flex;
  gap: var(--space-4);
  padding: var(--space-5);
  background: linear-gradient(135deg, rgba(124, 92, 255, 0.1) 0%, rgba(31, 140, 255, 0.06) 100%);
  border: 1px solid rgba(124, 92, 255, 0.28);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-4);
  box-shadow: var(--shadow-sm);
}

.previsit-cta-icon {
  font-size: 42px;
  line-height: 1;
  flex-shrink: 0;
  filter: drop-shadow(0 4px 10px rgba(124, 92, 255, 0.28));
}

.previsit-cta-content {
  flex: 1;
}

.previsit-cta-content h4 {
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 var(--space-2);
  color: var(--color-ai);
}

.previsit-cta-content p {
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 var(--space-1);
  color: var(--color-text);
}

.previsit-cta-hint {
  color: var(--color-text-muted) !important;
  font-size: 12px !important;
}

.previsit-divider {
  text-align: center;
  margin-bottom: var(--space-6);
  position: relative;
}

.previsit-divider::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--color-border);
}

.previsit-divider span {
  position: relative;
  background: var(--color-surface-strong);
  padding: 0 var(--space-4);
  color: var(--color-text-muted);
  font-size: 13px;
  letter-spacing: 0.06em;
}

.previsit-title {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 var(--space-2);
  text-align: center;
}

.previsit-desc {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0 0 var(--space-5);
  text-align: center;
}

.previsit-completed {
  margin-top: var(--space-6);
}

.completed-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  background: rgba(32, 180, 134, 0.1);
  border: 1px solid var(--color-success);
  border-radius: 999px;
  color: var(--color-success);
  font-weight: 600;
  margin-bottom: var(--space-5);
}

.check-icon {
  font-size: 18px;
}

/* ===================== 步骤按钮区 ===================== */
.step-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-6);
  padding-top: var(--space-5);
  border-top: 1px solid var(--color-border);
}

.step-actions.center {
  justify-content: center;
}

/* ===================== 详情弹窗 ===================== */
.registration-detail-dialog :deep(.el-dialog) {
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
}

.registration-detail-dialog :deep(.el-dialog__header) {
  padding: 22px 26px 14px;
  margin-right: 0;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface-strong);
}

.registration-detail-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.01em;
}

.registration-detail-dialog :deep(.el-dialog__body) {
  padding: 22px 26px;
  background: var(--color-bg-soft);
}

.registration-detail-dialog :deep(.el-dialog__footer) {
  padding: 14px 26px 20px;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface-strong);
}

.detail-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
  border-radius: var(--radius-xl);
  background: var(--gradient-primary);
  color: #fff;
  box-shadow: 0 12px 28px rgba(31, 140, 255, 0.28);
}

.detail-header :deep(.status-tag) {
  --tag-color: #ffffff;
  --tag-bg: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.38);
}

.detail-header-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.detail-department {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: #fff;
}

.detail-doctor {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.88);
}

.detail-header-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.detail-amount {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}

.detail-amount-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
}

.detail-amount-value {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
}

.detail-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.detail-section {
  background: var(--color-surface-strong);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: var(--shadow-sm);
}

.detail-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: 0.01em;
}

.detail-section-title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 14px;
  margin-right: 10px;
  background: var(--gradient-primary);
  border-radius: 2px;
  vertical-align: -2px;
}

.detail-section-meta {
  font-size: 12px;
  color: var(--color-text-muted);
  font-weight: 400;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 20px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  background: var(--color-control);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}

.detail-item label {
  font-size: 11.5px;
  color: var(--color-text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.detail-item span {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
  word-break: break-all;
}

.detail-item .mono {
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  letter-spacing: 0.02em;
  color: var(--color-primary);
}

.detail-complaint {
  margin: 0;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  border-left: 4px solid var(--color-primary);
  color: var(--color-text);
  font-size: 13.5px;
  line-height: 1.75;
}

.expense-table-wrapper {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-surface-strong);
}

.expense-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.expense-table th {
  text-align: left;
  padding: 12px 14px;
  background: var(--color-table-header);
  color: var(--color-text-muted);
  font-weight: 600;
  font-size: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  border-bottom: 1px solid var(--color-border);
}

.expense-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text);
}

.expense-table tr:last-child td {
  border-bottom: none;
}

.expense-table tr:hover td {
  background: var(--color-primary-soft);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

/* ===================== 响应式 ===================== */
@media (max-width: 1024px) {
  .result-cards {
    grid-template-columns: 1fr;
  }

  .confirm-row {
    grid-template-columns: 1fr;
  }

  .registration-list-page,
  .step-timeline,
  .step-card {
    width: 94%;
  }
}

@media (max-width: 768px) {
  .registration-list-page,
  .step-timeline,
  .step-card {
    width: 100%;
    padding: var(--space-5);
  }

  .timeline-steps {
    flex-wrap: wrap;
    gap: var(--space-4);
  }

  .timeline-step {
    flex: 0 0 calc(50% - var(--space-2));
  }

  .timeline-track {
    display: none;
  }

  .step-content {
    align-items: flex-start;
    text-align: left;
  }

  .form-row,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .record-side {
    min-width: 0;
    padding: var(--space-2);
  }
}
</style>
