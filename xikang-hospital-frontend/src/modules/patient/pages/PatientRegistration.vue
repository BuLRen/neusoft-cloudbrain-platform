<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import StatusTag from '@/shared/components/StatusTag.vue'
import { aiApi } from '@/shared/api/modules/ai'
import { registrationApi, scheduleApi, type DoctorInfo } from '@/shared/api/modules/registration'
import type { RegistrationRecord } from '@/shared/types/registration'
import { useAuthStore } from '@/app/stores/auth'
import { Warning } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const route = useRoute()

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
    ElMessage.error('录音启动失败: ' + (err as Error).message)
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
    ElMessage.error('识别请求失败: ' + (err.message || '网络错误'))
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
const previsitLoading = ref(false)
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

async function loadRegistrations() {
  const patientId = authStore.currentPatientId || authStore.currentPatient?.patientId
  if (!patientId) return
  registrationLoading.value = true
  try {
    registrations.value = await registrationApi.registrationsByPatient(patientId)
  } catch (err) {
    console.error('加载我的挂号失败:', err)
    ElMessage.error('加载我的挂号失败')
  } finally {
    registrationLoading.value = false
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
  if (record.status === 3) return 'danger'
  if (record.payStatus === 1 || record.status === 1 || record.status === 2) return 'success'
  return 'warning'
}

function registrationStatusText(record: RegistrationRecord) {
  return record.statusName || record.payStatusName || (record.payStatus === 1 ? '已缴费' : '待缴费')
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

  const scheduleId = getQueryNumber(route.query.scheduleId)
  if (scheduleId) {
    const matched = availableSchedules.value.find(item => item.id === scheduleId)
    if (matched) selectSchedule(matched)
  }
}

onMounted(startFromDepartmentQuery)

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

    triageResult.value = result
    ElMessage.success('AI 导诊结果已生成')
  } catch (err: any) {
    console.error('[AI导诊] API 调用失败:', err?.message)
    triageResult.value = {
      recommendedDepartment: '消化内科',
      recommendedDoctors: [
        { id: 1, name: '李明华', title: '主任医师' },
      ],
      riskLevel: 'low',
      aiAnalysis: {
        selfCareAdvice: '建议清淡饮食，注意休息。',
        possibleConditions: ['急性胃炎', '消化不良'],
        suggestedExaminations: ['胃镜检查', '血常规'],
      },
    }
    ElMessage.warning('AI 导诊结果已生成（模拟数据）')
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
    ElMessage.warning('AI 导诊未返回科室ID，暂时无法加载排班')
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
async function runPrevisit() {
  if (!previsitForm.value.chiefComplaint.trim()) {
    return
  }
  previsitLoading.value = true
  await new Promise(resolve => setTimeout(resolve, 1500))
  previsitCompleted.value = true
  previsitLoading.value = false
}

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

      <div v-if="registrationLoading" class="empty-state">正在加载挂号记录...</div>
      <div v-else-if="registrations.length === 0" class="empty-state rich-empty">
        <strong>暂无挂号记录</strong>
        <span>完成预约后，挂号单会出现在这里，后续医生开具的病历、检查、处方也可以继续从电子病历入口查看。</span>
        <button class="btn-primary" @click="startRegistration">去预约挂号</button>
      </div>
      <div v-else class="registration-record-list">
        <div v-for="record in registrations" :key="record.id" class="registration-record-card">
          <div class="record-main">
            <div class="record-title-row">
              <strong>{{ record.departmentName || '未分配科室' }}</strong>
              <StatusTag :tone="registrationStatusTone(record)">{{ registrationStatusText(record) }}</StatusTag>
            </div>
            <div class="record-meta">
              <span>挂号单号：{{ record.id }}</span>
              <span>就诊时间：{{ formatVisitTime(record) }}</span>
              <span>医生：{{ record.physicianName || '待分配' }}</span>
              <span>挂号级别：{{ record.registLevelName || '-' }}</span>
            </div>
            <p v-if="record.complaint" class="record-complaint">主诉：{{ record.complaint }}</p>
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

        <div v-if="triageResult" class="triage-result">
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
          <!-- 导诊结果出来后按钮变成"下一步"，点击跳到下一页 -->
          <button
            v-if="triageResult"
            class="btn-primary"
            @click="nextStep"
          >
            下一步
          </button>
          <!-- 导诊结果未生成时显示"开始导诊" -->
          <button
            v-else
            class="btn-primary"
            :disabled="!triageForm.symptoms.trim() || triageLoading"
            @click="runTriage"
          >
            <span v-if="triageLoading" class="loading-dots">分析中</span>
            <span v-else>开始导诊</span>
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

        <div v-if="!previsitCompleted" class="previsit-section">
          <div class="previsit-divider">
            <span>或</span>
          </div>
          <h3 class="previsit-title">💬 进行AI预问诊（可选）</h3>
          <p class="previsit-desc">提前采集病史信息，让医生接诊更高效</p>

          <div class="form-grid">
            <div class="form-item">
              <label>主诉（本次就诊最主要的不适）</label>
              <textarea
                v-model="previsitForm.chiefComplaint"
                class="form-textarea"
                placeholder="例如：胃痛3天，伴有反酸和嗳气..."
                rows="4"
              ></textarea>
            </div>
            <div class="form-item">
              <label>现病史（症状详细描述）</label>
              <textarea
                v-model="previsitForm.presentIllness"
                class="form-textarea"
                placeholder="请详细描述症状出现时间、频率、加重/缓解因素..."
                rows="4"
              ></textarea>
            </div>
            <div class="form-item">
              <label>既往史（以往患有的疾病）</label>
              <textarea
                v-model="previsitForm.pastHistory"
                class="form-textarea"
                placeholder="例如：高血压3年，否认糖尿病史..."
                rows="3"
              ></textarea>
            </div>
            <div class="form-item">
              <label>过敏史（药物/食物过敏）</label>
              <textarea
                v-model="previsitForm.allergyHistory"
                class="form-textarea"
                placeholder="例如：青霉素过敏..."
                rows="3"
              ></textarea>
            </div>
          </div>

          <div class="step-actions center">
            <button class="btn-outline" @click="backToList">稍后再说，查看我的挂号</button>
            <button
              class="btn-primary"
              :disabled="!previsitForm.chiefComplaint.trim() || previsitLoading"
              @click="runPrevisit"
            >
              <span v-if="previsitLoading" class="loading-dots">采集中</span>
              <span v-else>开始采集病史</span>
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
  </div>
</template>

<style scoped>
.registration-wizard {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  width: 100%;
}

.registration-list-page {
  width: 80%;
  margin: 0 10%;
  padding: var(--space-5);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.list-toolbar h2 {
  margin: 0 0 var(--space-2);
  font-size: 22px;
}

.list-toolbar p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.registration-record-list {
  display: grid;
  gap: var(--space-4);
}

.registration-record-card {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: #fff;
}

.record-main {
  flex: 1;
  display: grid;
  gap: var(--space-3);
}

.record-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.record-title-row strong {
  font-size: 17px;
}

.record-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2) var(--space-4);
  color: var(--color-text-muted);
  font-size: 13px;
}

.record-complaint {
  margin: 0;
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.7;
}

.record-side {
  min-width: 112px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: var(--space-2);
  padding-left: var(--space-4);
  border-left: 1px solid var(--color-border);
}

.record-amount {
  color: var(--color-primary);
  font-size: 24px;
  font-weight: 700;
}

.record-pay {
  color: var(--color-text-muted);
  font-size: 13px;
}

.rich-empty {
  display: grid;
  gap: var(--space-3);
  justify-items: center;
}

.rich-empty span {
  max-width: 560px;
  line-height: 1.7;
}

/* ========== 时间轴进度条 ========== */
.step-timeline {
  position: relative;
  width: 80%;
  margin: 0 10%;
  box-sizing: border-box;
  padding: var(--space-4) var(--space-8);
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
}

.timeline-track {
  position: absolute;
  top: 50%;
  left: 48px;
  right: 48px;
  height: 3px;
  background: var(--color-border);
  border-radius: 2px;
  transform: translateY(-50%);
}

.timeline-progress {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: var(--color-success);
  border-radius: 2px;
  transition: width 0.4s ease;
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
}

.step-node {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface);
  border: 3px solid var(--color-border);
  transition: all 0.3s ease;
}

.timeline-step.is-active .step-node {
  background: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px rgba(31, 140, 255, 0.2);
}

.timeline-step.is-completed .step-node {
  background: var(--color-success);
  border-color: var(--color-success);
}

.node-number {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-muted);
}

.timeline-step.is-active .node-number {
  color: white;
}

.node-check {
  font-size: 16px;
  color: white;
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
  font-size: 16px;
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
  color: var(--color-success);
}

/* ========== 步骤内容 ========== */
.step-card {
  padding: var(--space-4) var(--space-8);
  width: 80%;
  margin: 0 10%;
  box-sizing: border-box;
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
  font-size: 32px;
  line-height: 1;
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
}

.step-header p {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
}

/* ========== 表单布局 ========== */
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
  background: var(--color-surface);
  font-size: 14px;
  line-height: 1.7;
  resize: vertical;
  font-family: inherit;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(31, 140, 255, 0.1);
}

.form-textarea::placeholder {
  color: var(--color-text-soft);
}

/* ========== 导诊表单（单列全宽） ========== */
.triage-form-full {
  width: 100%;
}

.triage-form-full .form-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.triage-form-full .form-item label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

/* ========== 导诊结果 ========== */
.triage-result {
  margin-top: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.result-alert {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background: linear-gradient(135deg, rgba(32, 180, 134, 0.1) 0%, rgba(32, 180, 134, 0.05) 100%);
  border: 1px solid var(--color-success);
  border-radius: var(--radius-lg);
}

.alert-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--color-success);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
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
  padding: var(--space-4);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
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
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
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
  font-size: 13px;
  line-height: 1.7;
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

/* ========== 排班列表 ========== */
.schedule-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.schedule-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4);
  background: var(--color-surface);
  border: 2px solid var(--color-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.2s ease;
}

.schedule-item:hover {
  border-color: var(--color-primary);
}

.schedule-item.is-selected {
  border-color: var(--color-primary);
  background: rgba(31, 140, 255, 0.05);
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
  font-weight: 600;
  font-size: 16px;
}

.schedule-info {
  display: flex;
  gap: var(--space-4);
  font-size: 13px;
  color: var(--color-text-muted);
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
  font-weight: 600;
}

/* 挂号级别卡片 */
.level-card {
  margin-top: var(--space-4);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.level-header {
  padding: var(--space-3) var(--space-4);
  background: var(--color-primary-soft);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}

.level-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4);
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
  font-size: 22px;
  font-weight: 700;
  color: var(--color-primary);
}

/* 患者信息提示栏 */
.patient-info-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  background: var(--color-surface);
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
  font-weight: 600;
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
}

/* ========== 确认信息 ========== */
.confirm-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  overflow: hidden;
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
  font-size: 12px;
  color: var(--color-text-muted);
}

.confirm-item span {
  font-size: 14px;
  font-weight: 500;
}

.confirm-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  background: linear-gradient(135deg, var(--color-primary-soft) 0%, rgba(31, 140, 255, 0.05) 100%);
}

.total-label {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-weight: 600;
}

.total-price {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
}

/* ========== 成功页面 ========== */
.success-card-content {
  max-width: 380px;
  margin: 0 auto var(--space-6);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  overflow: hidden;
  text-align: left;
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
  font-family: 'Monaco', 'Consolas', monospace;
  color: var(--color-primary);
}

.success-row.highlight {
  background: var(--color-primary-soft);
}

/* ========== AI预问诊样式 ========== */
.previsit-section {
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border);
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
  background: var(--color-surface);
  padding: 0 var(--space-4);
  color: var(--color-text-muted);
  font-size: 13px;
}

.previsit-title {
  font-size: 18px;
  font-weight: 600;
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
  border-radius: var(--radius-lg);
  color: var(--color-success);
  font-weight: 600;
  margin-bottom: var(--space-5);
}

.check-icon {
  font-size: 18px;
}

/* ========== 操作按钮 ========== */
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

.btn-primary {
  padding: var(--space-3) var(--space-6);
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-lg);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-primary:hover {
  background: var(--color-primary-dark, #1a77e0);
  transform: translateY(-1px);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.btn-primary.btn-lg {
  padding: var(--space-4) var(--space-8);
  font-size: 15px;
}

.btn-outline {
  padding: var(--space-3) var(--space-5);
  background: transparent;
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-outline:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

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

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .result-cards {
    grid-template-columns: 1fr;
  }

  .confirm-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .step-timeline {
    padding: var(--space-4);
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

  .form-row {
    grid-template-columns: 1fr;
  }
}

.urgency-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: #f0f9ff;
  border: 1px solid #3b82f6;
  border-radius: var(--radius-md);
  margin-bottom: var(--space-4);
  font-size: 14px;
}
.urgency-bar.is-urgent { background: #fff7ed; border-color: #f97316; }
.urgency-advice { color: var(--color-text-muted); }
.info-row { display: flex; flex-wrap: wrap; gap: var(--space-4); margin: var(--space-3) 0; font-size: 13px; }
.red-flag-bar { padding: var(--space-3); background: #fef2f2; border: 1px solid #ef4444; border-radius: var(--radius-md); margin: var(--space-3) 0; color: #dc2626; font-size: 13px; display: flex; flex-direction: column; gap: 4px; }
.red-flag-item { font-size: 13px; }
.no-doctor { color: var(--color-text-muted); font-size: 13px; }

/* 症状快捷分类标签 */
.symptom-categories {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  align-items: center;
}
.category-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
  margin-right: var(--space-1);
}
.category-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.category-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: rgba(26, 119, 224, 0.05);
}
.category-btn.is-active {
  border-color: var(--color-primary);
  background: rgba(26, 119, 224, 0.1);
  color: var(--color-primary);
  font-weight: 600;
}

/* 语音输入按钮 */
.textarea-wrapper {
  position: relative;
}
.form-textarea {
  width: 100%;
  padding-right: 48px;
}
.voice-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  font-size: 20px;
}
.voice-btn:hover {
  border-color: var(--color-primary);
  background: rgba(26, 119, 224, 0.08);
  box-shadow: 0 2px 8px rgba(26, 119, 224, 0.2);
}
.voice-btn.is-recording {
  border-color: #ef4444;
  background: #fef2f2;
  animation: pulse 1s infinite;
}
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}
</style>
