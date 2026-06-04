<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElProgress,
  ElResult,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { aiApi } from '@/shared/api/modules/ai'
import { registrationApi, type DoctorInfo } from '@/shared/api/modules/registration'
import type { FollowUpPlan, PrevisitResult, TriageAnalysisResult } from '@/shared/types/ai'

// Mock data for demonstration
const mockRegistration = reactive({
  id: 10001,
  department: '消化内科',
  doctor: '李明华',
  doctorTitle: '主任医师',
  date: '2026-05-29',
  time: '09:30',
  status: 'registered',
  queuePosition: 5,
  estimatedWait: 25,
})

const mockVisitRecord = reactive({
  id: 9001,
  date: '2026-05-28',
  department: '消化内科',
  doctor: '李明华',
  diagnosis: '急性胃炎',
  prescription: '奥美拉唑肠溶胶囊 20mg x7天\n铝碳酸镁片 0.5g x14天',
  notes: '注意饮食清淡，忌辛辣刺激食物',
})

const mockFollowupPlans: FollowUpPlan[] = [
  { id: 1, planType: '用药随访', startDate: '2026-05-29', endDate: '2026-06-12', status: 'pending', frequency: '每日一次' },
  { id: 2, planType: '复诊提醒', startDate: '2026-06-12', endDate: '2026-06-12', status: 'pending', frequency: '一次性' },
]

// Step definitions
const steps = [
  { key: 'overview', title: '就诊概览', icon: '📋' },
  { key: 'triage', title: 'AI导诊', icon: '🤖' },
  { key: 'registration', title: '挂号', icon: '📝' },
  { key: 'previsit', title: 'AI预问诊', icon: '💬' },
  { key: 'records', title: '就诊记录', icon: '📁' },
  { key: 'followup', title: '随访', icon: '📅' },
]

// State
const authStore = useAuthStore()
const currentStep = ref(0)
const hasRegistration = ref(false)
const inferredPatientId = computed(() => {
  const parsed = Number(authStore.userId)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
})
const patientId = computed<number | undefined>(() => inferredPatientId.value)
const patientName = ref(inferredPatientId.value ? `患者${inferredPatientId.value}` : '张三')

// Triage state
const triageLoading = ref(false)
const triageSymptoms = ref('')
const triageResult = ref<TriageAnalysisResult | null>(null)
const isRecording = ref(false)
const symptomCategories = [
  { key: 'digestive', label: '消化系统', icon: '🔴', placeholder: '请输入您的主要症状，例如：胃痛3天，伴有反酸和嗳气，进食后加重...' },
  { key: 'respiratory', label: '呼吸系统', icon: '🫁', placeholder: '请输入您的主要症状，例如：咳嗽3天，伴有发热和咽痛...' },
  { key: 'neurological', label: '神经系统', icon: '🧠', placeholder: '请输入您的主要症状，例如：头痛、头晕、失眠...' },
  { key: 'orthopedic', label: '骨科', icon: '🦴', placeholder: '请输入您的主要症状，例如：腰痛3天，久坐后加重...' },
  { key: 'cardiovascular', label: '心血管', icon: '❤️', placeholder: '请输入您的主要症状，例如：胸闷、心悸...' },
  { key: 'dermatology', label: '皮肤科', icon: '🧴', placeholder: '请输入您的主要症状，例如：皮肤瘙痒、皮疹...' },
]
const currentCategory = ref<typeof symptomCategories[0] | null>(null)

// Previsit state
const previsitLoading = ref(false)
const previsitResult = ref<PrevisitResult | null>(null)

// Followup state
const feedbackForm = reactive({
  medicationCompliance: 'good',
  symptomFeedback: '',
  sideEffects: '',
  recoveryStatus: '',
})

// Computed
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const currentStatus = computed(() => {
  if (!hasRegistration.value) {
    return { text: '待导诊', tone: 'warning' as const }
  }
  const statusMap: Record<string, { text: string; tone: 'primary' | 'success' | 'warning' | 'ai' }> = {
    overview: { text: '就诊准备中', tone: 'primary' },
    triage: { text: '待导诊', tone: 'warning' },
    registration: { text: '已挂号', tone: 'success' },
    previsit: { text: '待预问诊', tone: 'ai' },
    records: { text: '待就诊', tone: 'primary' },
    followup: { text: '待随访', tone: 'ai' },
  }
  return statusMap[steps[currentStep.value].key] || { text: '进行中', tone: 'primary' }
})

const nextStepHint = computed(() => {
  const hints: Record<string, string> = {
    overview: hasRegistration.value ? '您已完成挂号，建议继续填写 AI 预问诊，方便医生接诊' : '请先进行 AI 导诊，系统将为您推荐合适的科室与医生',
    triage: triageResult.value ? '导诊完成后，可以进入挂号环节' : '请先描述症状，系统会为您生成导诊建议',
    registration: hasRegistration.value ? '挂号完成后，下一步建议进入 AI 预问诊' : '请根据导诊结果完成挂号',
    previsit: '完成预问诊后，医生将更快了解您的主诉和病史',
    records: '就诊结束后，可在这里查看病历、处方和检查结果',
    followup: '根据医嘱完成随访与反馈，便于后续康复管理',
  }
  return hints[steps[currentStep.value].key] || ''
})

const progressPercent = computed(() => {
  return Math.round((currentStep.value / (steps.length - 1)) * 100)
})

const pendingTodoCount = computed(() => {
  return [!triageResult.value, !hasRegistration.value, !previsitResult.value].filter(Boolean).length
})

// Methods
function riskTone(level?: string) {
  if (level === 'critical') return 'danger'
  if (level === 'urgent') return 'warning'
  if (level === 'medium') return 'warning'
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

function selectCategory(cat: typeof symptomCategories[0]) {
  currentCategory.value = cat
  triageSymptoms.value = ''
}

function toggleVoice() {
  if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
    ElMessage.warning('当前浏览器不支持语音输入，请使用 Chrome 或 Edge')
    return
  }
  const SpeechRecognition = (window as any).webkitSpeechRecognition || (window as any).SpeechRecognition

  if (isRecording.value) {
    isRecording.value = false
    if ((window as any).__speechRecognition__) {
      ;(window as any).__speechRecognition__.abort()
    }
    ElMessage.success('语音输入已停止')
    return
  }

  isRecording.value = true
  ElMessage.info('开始语音输入，请说话...')
  const recognition = new SpeechRecognition()
  recognition.lang = 'zh-CN'
  recognition.continuous = false
  recognition.interimResults = false
  ;(window as any).__speechRecognition__ = recognition

  recognition.onresult = (event: any) => {
    if (event.results[0].isFinal) {
      triageSymptoms.value = event.results[0][0].transcript
    }
  }
  recognition.onend = () => { isRecording.value = false }
  recognition.onerror = (event: any) => {
    isRecording.value = false
    if (event.error !== 'no-speech' && event.error !== 'aborted') ElMessage.error('语音识别出错')
  }
  recognition.start()
}

function planTone(status?: FollowUpPlan['status']) {
  if (status === 2 || status === 'completed') return 'success'
  if (status === 3 || status === 'cancelled') return 'danger'
  if (status === 1 || status === 'pending') return 'primary'
  if (status === 0 || status === 'created') return 'warning'
  return 'ai'
}

function planStatusLabel(status?: FollowUpPlan['status']) {
  if (status === 0 || status === 'created') return '待执行'
  if (status === 1 || status === 'pending') return '进行中'
  if (status === 2 || status === 'completed') return '已完成'
  if (status === 3 || status === 'cancelled') return '已取消'
  return typeof status === 'string' && status ? status : '未知'
}

function goToStep(index: number) {
  const isRestricted = !hasRegistration.value && index > 2
  if (isRestricted) {
    ElMessage.warning('当前尚未挂号，请先完成导诊和挂号流程')
    return
  }
  currentStep.value = index
}

async function runTriage() {
  if (!triageSymptoms.value.trim()) {
    ElMessage.warning('请先填写症状描述')
    return
  }
  triageLoading.value = true
  console.log('[AI导诊] patientId=', patientId.value, 'symptoms=', triageSymptoms.value)
  try {
    console.log('[AI导诊] 正在调用 API...')
    const result = await aiApi.triageAnalyze({
      patientId: patientId.value,
      symptoms: triageSymptoms.value,
    })

    // 如果 AI 没有返回推荐医生，根据 AI 推荐的挂号级别获取医生
    const hasDoctors = result.recommendedDoctors && result.recommendedDoctors.length > 0
    const hasDeptId = result.recommendedDepartmentId && result.recommendedDepartmentId > 0

    if (!hasDoctors && hasDeptId) {
      console.log('[AI导诊] 准备获取医生，科室ID:', result.recommendedDepartmentId, '挂号级别:', result.recommendedRegistLevelId)
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
          console.log('[AI导诊] 成功获取医生:', result.recommendedDoctors)
        }
      } catch (e) {
        console.error('[AI导诊] 获取医生异常:', e)
      }
    }

    triageResult.value = result
    console.log('[AI导诊] API 成功返回:', result)
    ElMessage.success('AI 导诊结果已生成')
  } catch (err: any) {
    console.error('[AI导诊] API 调用失败:', err?.message, err)
    // API 调用失败时 fallback 到 mock
    triageResult.value = {
      recommendedDepartment: '消化内科',
      recommendedDoctors: [
        { id: 101, name: '李明华', title: '主任医师' },
        { id: 102, name: '王建国', title: '副主任医师' },
      ],
      riskLevel: 'low',
      aiAnalysis: {
        selfCareAdvice: '建议清淡饮食，避免辛辣刺激性食物，多饮水，注意休息。',
        possibleConditions: ['急性胃炎', '胃痉挛', '消化不良'],
        suggestedExaminations: ['胃镜检查', '血常规', '幽门螺杆菌检测'],
      },
    }
    ElMessage.warning('AI 导诊结果已生成（模拟数据）')
  } finally {
    triageLoading.value = false
  }
}

async function goToRegistration() {
  if (!triageResult.value) {
    ElMessage.warning('请先生成导诊结果')
    return
  }
  // Simulate registration
  hasRegistration.value = true
  currentStep.value = 2
  ElMessage.success('已为您挂号，请按时就诊')
}

async function runPrevisit() {
  if (!hasRegistration.value) {
    ElMessage.warning('请先完成挂号')
    return
  }
  previsitLoading.value = true
  try {
    // Mock previsit result
    previsitResult.value = {
      chiefComplaint: '上腹部疼痛3天，加重1天',
      presentIllness: '患者3天前开始出现上腹部隐痛，呈阵发性发作，进食后加重，伴有反酸、嗳气。1天前疼痛加重，呈持续性胀痛。',
      pastHistory: '既往体健，无高血压、糖尿病病史。无手术史。',
      allergyHistory: '否认药物过敏史。',
      physicalExamination: '腹部平坦，上腹部压痛明显，无反跳痛，肝脾肋下未触及。',
      preliminaryDiagnosis: '急性胃炎',
    }
    ElMessage.success('AI 预问诊已完成')
  } finally {
    previsitLoading.value = false
  }
}

async function submitFeedback() {
  if (!feedbackForm.symptomFeedback.trim()) {
    ElMessage.warning('请填写症状反馈')
    return
  }
  ElMessage.success('随访反馈已提交，感谢您的配合')
  // Reset form
  feedbackForm.symptomFeedback = ''
  feedbackForm.sideEffects = ''
  feedbackForm.recoveryStatus = ''
}

function syncCurrentStep() {
  if (hasRegistration.value) {
    currentStep.value = previsitResult.value ? 3 : 2
    return
  }
  currentStep.value = triageResult.value ? 1 : 0
}

onMounted(() => {
  syncCurrentStep()
})
</script>

<template>
  <div class="patient-workspace">
    <!-- Top Status Bar -->
    <GlassCard class="status-bar">
      <div class="status-bar__content">
        <div class="status-bar__left">
          <div class="status-indicator" :class="currentStatus.tone">
            <span class="status-dot"></span>
            <span class="status-text">{{ currentStatus.text }}</span>
          </div>
          <h2 class="status-title">{{ greeting }}，患者</h2>
        </div>
        <div class="status-bar__right">
          <span class="progress-label">就诊进度</span>
          <ElProgress :percentage="progressPercent" :stroke-width="8" :show-text="false" class="progress-bar" />
          <span class="progress-text">{{ progressPercent }}%</span>
        </div>
      </div>
      <p class="status-hint">{{ nextStepHint }}</p>
    </GlassCard>

    <div class="workspace-layout">
      <!-- Left Sidebar: Step Navigation -->
      <GlassCard class="step-sidebar">
        <div class="patient-info">
          <div class="patient-avatar">
            <span>{{ patientName ? patientName[0] : '?' }}</span>
          </div>
          <div class="patient-details">
            <span class="patient-name">{{ patientName || '未填写姓名' }}</span>
            <StatusTag :tone="patientId ? 'success' : 'warning'">
              {{ patientId ? `ID: ${patientId}` : '未绑定' }}
            </StatusTag>
          </div>
        </div>

        <div class="step-divider"></div>

        <div class="step-nav">
          <h3 class="step-nav__title">就诊流程</h3>
          <div
            v-for="(step, index) in steps"
            :key="step.key"
            class="step-item"
            :class="{
              'is-active': currentStep === index,
              'is-completed': currentStep > index,
              'is-disabled': index > currentStep + 1 && !hasRegistration && index > 1,
            }"
            @click="goToStep(index)"
          >
            <div class="step-item__indicator">
              <span v-if="currentStep > index" class="step-check">✓</span>
              <span v-else class="step-number">{{ index + 1 }}</span>
            </div>
            <div class="step-item__content">
              <span class="step-icon">{{ step.icon }}</span>
              <span class="step-title">{{ step.title }}</span>
            </div>
          </div>
        </div>
      </GlassCard>

      <!-- Right Content Area -->
      <div class="content-area">
        <!-- Step 1: 就诊概览 -->
        <GlassCard v-show="currentStep === 0" class="step-content">
          <div class="step-header">
            <span class="step-icon-lg">📋</span>
            <h2>就诊概览</h2>
            <p>查看您的就诊信息和待办事项</p>
          </div>

          <div class="overview-grid">
            <ElCard class="overview-card" shadow="hover">
              <template #header>
                <div class="card-header">
                  <span class="card-icon">🧭</span>
                  <span>当前就诊状态</span>
                  <StatusTag :tone="hasRegistration ? 'success' : 'warning'">
                    {{ hasRegistration ? '已挂号' : '待挂号' }}
                  </StatusTag>
                </div>
              </template>
              <template v-if="hasRegistration">
                <ElDescriptions :column="2" border size="small">
                  <ElDescriptionsItem label="科室">{{ mockRegistration.department }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="医生">{{ mockRegistration.doctor }} ({{ mockRegistration.doctorTitle }})</ElDescriptionsItem>
                  <ElDescriptionsItem label="就诊日期">{{ mockRegistration.date }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="就诊时间">{{ mockRegistration.time }}</ElDescriptionsItem>
                </ElDescriptions>
                <div class="queue-info">
                  <ElProgress :percentage="65" :stroke-width="6" color="#1f8cff" />
                  <p>当前排队：第 {{ mockRegistration.queuePosition }} 位，预计等待 {{ mockRegistration.estimatedWait }} 分钟</p>
                </div>
              </template>
              <ElResult v-else icon="info" title="当前还没有挂号记录" sub-title="建议先完成 AI 导诊，再根据推荐科室进入挂号流程。" />
            </ElCard>

            <ElCard class="overview-card" shadow="hover">
              <template #header>
                <div class="card-header">
                  <span class="card-icon">📋</span>
                  <span>待办事项</span>
                  <StatusTag :tone="pendingTodoCount ? 'warning' : 'success'">{{ pendingTodoCount }} 项</StatusTag>
                </div>
              </template>
              <div class="todo-list">
                <div class="todo-item" :class="{ 'is-done': Boolean(triageResult) }">
                  <span class="todo-icon">{{ triageResult ? '✓' : '○' }}</span>
                  <span>完成 AI 导诊</span>
                </div>
                <div class="todo-item" :class="{ 'is-done': hasRegistration }">
                  <span class="todo-icon">{{ hasRegistration ? '✓' : '○' }}</span>
                  <span>完成挂号</span>
                </div>
                <div class="todo-item" :class="{ 'is-done': Boolean(previsitResult) }">
                  <span class="todo-icon">{{ previsitResult ? '✓' : '○' }}</span>
                  <span>就诊前完成 AI 预问诊</span>
                </div>
              </div>
            </ElCard>

            <ElCard class="overview-card" shadow="hover">
              <template #header>
                <div class="card-header">
                  <span class="card-icon">➡️</span>
                  <span>下一步建议</span>
                </div>
              </template>
              <div class="next-step-panel">
                <strong>{{ !triageResult ? '先做 AI 导诊' : !hasRegistration ? '根据导诊结果完成挂号' : !previsitResult ? '进入 AI 预问诊' : '等待医生接诊' }}</strong>
                <p>
                  {{ !triageResult
                    ? '填写症状后，系统会推荐更合适的科室和医生。'
                    : !hasRegistration
                      ? '挂号完成后，医生端才能接收到本次就诊流程。'
                      : !previsitResult
                        ? '补充主诉、病史与过敏史，方便医生快速了解病情。'
                        : '当前前置准备已完成，可等待医生进一步处理。' }}
                </p>
              </div>
            </ElCard>

            <ElCard class="overview-card" shadow="hover">
              <template #header>
                <div class="card-header">
                  <span class="card-icon">📁</span>
                  <span>历史参考</span>
                </div>
              </template>
              <ElDescriptions :column="1" border size="small">
                <ElDescriptionsItem label="最近就诊日期">{{ mockVisitRecord.date }}</ElDescriptionsItem>
                <ElDescriptionsItem label="最近科室">{{ mockVisitRecord.department }}</ElDescriptionsItem>
                <ElDescriptionsItem label="最近诊断">{{ mockVisitRecord.diagnosis }}</ElDescriptionsItem>
              </ElDescriptions>
            </ElCard>
          </div>

          <div class="action-buttons">
            <ElButton v-if="!hasRegistration" type="primary" @click="goToStep(1)">
              {{ triageResult ? '查看导诊结果' : '开始导诊' }}
            </ElButton>
            <template v-else>
              <ElButton @click="goToStep(2)">查看挂号信息</ElButton>
              <ElButton type="primary" @click="goToStep(3)">填写预问诊</ElButton>
            </template>
          </div>
        </GlassCard>

        <!-- Step 2: AI导诊 -->
        <GlassCard v-show="currentStep === 1" class="step-content">
          <div class="step-header">
            <span class="step-icon-lg">🤖</span>
            <h2>AI导诊</h2>
            <p>描述您的症状，AI将为您推荐合适的科室和医生</p>
          </div>

          <div class="triage-section">
            <!-- 症状快捷分类 -->
            <div class="symptom-categories">
              <span class="category-label">快捷选择：</span>
              <button
                v-for="cat in symptomCategories"
                :key="cat.key"
                class="category-btn"
                :class="{ 'is-active': currentCategory?.key === cat.key }"
                @click="selectCategory(cat)"
              >{{ cat.icon }} {{ cat.label }}</button>
            </div>

            <ElForm label-position="top" class="triage-form">
              <ElFormItem label="症状描述">
                <div class="textarea-wrapper">
                  <ElInput
                    v-model="triageSymptoms"
                    type="textarea"
                    :rows="5"
                    :placeholder="currentCategory?.placeholder || '请输入您的主要症状，例如：胃痛3天，伴有反酸和嗳气，进食后加重...'"
                  />
                  <button class="voice-btn" :class="{ 'is-recording': isRecording }" @click="toggleVoice">
                    {{ isRecording ? '🔴' : '🎤' }}
                  </button>
                </div>
              </ElFormItem>
            </ElForm>
            <div class="form-actions">
              <ElButton type="primary" :loading="triageLoading" @click="runTriage">开始AI分析</ElButton>
            </div>
          </div>

          <div v-if="triageResult" class="triage-result">
            <!-- 紧迫性提示 -->
            <ElAlert :type="triageResult.urgencyLevel === 'I' ? 'error' : triageResult.urgencyLevel === 'II' ? 'warning' : 'info'" :closable="false">
              <template #title>
                <span>
                  紧迫等级：
                  <StatusTag :tone="urgencyTone(triageResult.urgencyLevel)">
                    {{ urgencyLabel(triageResult.urgencyLevel) }}
                  </StatusTag>
                </span>
                <span style="margin-left: 16px">{{ triageResult.urgencyAdvice }}</span>
              </template>
            </ElAlert>

            <!-- 分诊结果 -->
            <ElAlert type="success" :closable="false" style="margin-top: 12px">
              <template #title>
                <strong>推荐科室：{{ triageResult.recommendedDepartment }}</strong>
                <span v-if="triageResult.departmentReason" style="margin-left: 12px; font-size: 13px; font-weight: normal; color: #666;">
                  {{ triageResult.departmentReason }}
                </span>
              </template>
            </ElAlert>

            <!-- 备选科室 + 可信度 -->
            <div v-if="triageResult.alternativeDepartments?.length || triageResult.confidenceLevel" class="info-row" style="margin-top: 12px">
              <span v-if="triageResult.alternativeDepartments?.length" class="info-item">
                <strong>备选科室：</strong>
                <StatusTag v-for="d in triageResult.alternativeDepartments" :key="d" tone="primary">{{ d }}</StatusTag>
              </span>
              <span v-if="triageResult.confidenceLevel" class="info-item">
                <strong>可信度：</strong>
                <StatusTag :tone="triageResult.confidenceLevel === 'high' ? 'success' : triageResult.confidenceLevel === 'medium' ? 'primary' : 'warning'">
                  {{ triageResult.confidenceLevel === 'high' ? '高' : triageResult.confidenceLevel === 'medium' ? '中' : '低' }}
                </StatusTag>
              </span>
            </div>

            <!-- 红旗征提醒 -->
            <ElAlert v-if="triageResult.redFlags?.length" type="error" :closable="false" style="margin-top: 12px">
              <template #title>⚠️ 如出现以下症状，请立即去急诊或拨打120：</template>
              <template #default>
                <div class="red-flag-list">
                  <span v-for="flag in triageResult.redFlags" :key="flag" class="red-flag-item">• {{ flag }}</span>
                </div>
              </template>
            </ElAlert>

            <!-- 推荐医生 -->
            <ElCard class="result-card" style="margin-top: 12px">
              <template #header>
                <span>推荐医生</span>
                <StatusTag :tone="riskTone(triageResult.riskLevel)">
                  {{ triageResult.riskLevel === 'critical' ? '高风险' : triageResult.riskLevel === 'urgent' ? '中高风险' : '一般' }}
                </StatusTag>
              </template>
              <div v-if="triageResult.recommendedDoctors?.length" class="doctors-list">
                <div v-for="doctor in triageResult.recommendedDoctors" :key="doctor.name" class="doctor-item">
                  <span class="doctor-name">{{ doctor.name }}</span>
                  <span class="doctor-title">{{ doctor.title }}</span>
                </div>
              </div>
              <ElEmpty v-else description="暂无推荐医生信息" :image-size="60" />
            </ElCard>

            <!-- AI分析建议 -->
            <ElCard class="result-card">
              <template #header>
                <span>AI分析建议</span>
              </template>
              <p class="advice-text">{{ triageResult.selfCareAdvice || triageResult.aiAnalysis?.selfCareAdvice || '暂无建议' }}</p>
              <div v-if="triageResult.aiAnalysis?.possibleConditions?.length" class="condition-tags">
                <strong>可疑疾病：</strong>
                <StatusTag v-for="condition in triageResult.aiAnalysis?.possibleConditions" :key="condition" tone="ai">
                  {{ condition }}
                </StatusTag>
              </div>
              <div v-if="triageResult.aiAnalysis?.suggestedExaminations?.length" class="exam-tags">
                <strong>建议检查：</strong>
                <StatusTag v-for="exam in triageResult.aiAnalysis?.suggestedExaminations" :key="exam" tone="primary">
                  {{ exam }}
                </StatusTag>
              </div>
            </ElCard>

            <div class="result-actions">
              <ElButton type="primary" @click="goToRegistration">一键挂号</ElButton>
              <ElButton @click="goToStep(0)">返回概览</ElButton>
            </div>
          </div>

          <ElEmpty v-else-if="!triageLoading" description="请先描述您的症状，AI将为您分析" />
        </GlassCard>

        <!-- Step 3: 挂号 -->
        <GlassCard v-show="currentStep === 2" class="step-content">
          <div class="step-header">
            <span class="step-icon-lg">📝</span>
            <h2>挂号信息</h2>
            <p>查看您的挂号记录和排队状态</p>
          </div>

          <template v-if="hasRegistration">
            <ElCard class="registration-card">
              <template #header>
                <div class="card-header">
                  <span>当前挂号</span>
                  <StatusTag tone="success">已确认</StatusTag>
                </div>
              </template>
              <ElDescriptions :column="2" border>
                <ElDescriptionsItem label="挂号单号">{{ mockRegistration.id }}</ElDescriptionsItem>
                <ElDescriptionsItem label="挂号日期">{{ mockRegistration.date }}</ElDescriptionsItem>
                <ElDescriptionsItem label="就诊科室">{{ mockRegistration.department }}</ElDescriptionsItem>
                <ElDescriptionsItem label="就诊医生">{{ mockRegistration.doctor }} ({{ mockRegistration.doctorTitle }})</ElDescriptionsItem>
                <ElDescriptionsItem label="就诊时间">{{ mockRegistration.time }}</ElDescriptionsItem>
                <ElDescriptionsItem label="当前排队">第 {{ mockRegistration.queuePosition }} 位</ElDescriptionsItem>
              </ElDescriptions>
            </ElCard>

            <ElCard class="queue-card">
              <template #header>
                <span>排队进度</span>
              </template>
              <div class="queue-visual">
                <ElProgress type="circle" :percentage="65" :width="120" :stroke-width="10">
                  <template #default>
                    <div class="queue-number">{{ mockRegistration.queuePosition }}</div>
                    <div class="queue-label">当前排队</div>
                  </template>
                </ElProgress>
                <div class="queue-info">
                  <p><strong>预计等待时间：</strong>{{ mockRegistration.estimatedWait }} 分钟</p>
                  <p><strong>状态：</strong><StatusTag tone="primary">候诊中</StatusTag></p>
                </div>
              </div>
            </ElCard>

            <ElTimeline class="visit-timeline">
              <ElTimelineItem timestamp="08:30" placement="top">
                <p>系统开始叫号</p>
              </ElTimelineItem>
              <ElTimelineItem timestamp="09:00" placement="top">
                <p>您已到达候诊区</p>
              </ElTimelineItem>
              <ElTimelineItem timestamp="09:15" placement="top" type="primary">
                <p><strong>当前 - 候诊中</strong></p>
              </ElTimelineItem>
              <ElTimelineItem timestamp="09:30" placement="top">
                <p>预计就诊时间</p>
              </ElTimelineItem>
            </ElTimeline>
          </template>

          <template v-else>
            <ElResult title="暂无挂号记录" sub-title="请先完成AI导诊，然后进行挂号">
              <template #extra>
                <ElButton type="primary" @click="goToStep(1)">前往AI导诊</ElButton>
              </template>
            </ElResult>
          </template>

          <div class="action-buttons">
            <ElButton @click="goToStep(1)">返回导诊</ElButton>
            <ElButton v-if="hasRegistration" type="primary" @click="goToStep(3)">前往AI预问诊</ElButton>
          </div>
        </GlassCard>

        <!-- Step 4: AI预问诊 -->
        <GlassCard v-show="currentStep === 3" class="step-content">
          <div class="step-header">
            <span class="step-icon-lg">💬</span>
            <h2>AI预问诊</h2>
            <p>在就诊前与AI对话，采集您的病史信息，帮助医生更好地了解您的病情</p>
          </div>

          <div class="previsit-section">
            <ElAlert type="info" :closable="false" title="温馨提醒">
              请尽可能详细地描述您的症状和病史，这将有助于医生做出更准确的诊断。
            </ElAlert>

            <div class="previsit-actions">
              <ElButton type="primary" :loading="previsitLoading" @click="runPrevisit">
                开始AI预问诊对话
              </ElButton>
            </div>

            <div v-if="previsitResult" class="previsit-result">
              <ElCard class="result-card">
                <template #header>
                  <span>预问诊摘要</span>
                </template>
                <ElDescriptions :column="1" border>
                  <ElDescriptionsItem label="主诉">{{ previsitResult.chiefComplaint }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="现病史">{{ previsitResult.presentIllness }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="既往史">{{ previsitResult.pastHistory }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="过敏史">{{ previsitResult.allergyHistory }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="体格检查">{{ previsitResult.physicalExamination }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="初步诊断">
                    <StatusTag tone="warning">{{ previsitResult.preliminaryDiagnosis }}</StatusTag>
                  </ElDescriptionsItem>
                </ElDescriptions>
              </ElCard>
            </div>

            <ElEmpty v-else description="点击上方按钮开始预问诊" />
          </div>

          <div class="action-buttons">
            <ElButton @click="goToStep(2)">返回挂号</ElButton>
            <ElButton type="primary" @click="goToStep(4)">查看就诊记录</ElButton>
          </div>
        </GlassCard>

        <!-- Step 5: 就诊记录 -->
        <GlassCard v-show="currentStep === 4" class="step-content">
          <div class="step-header">
            <span class="step-icon-lg">📁</span>
            <h2>就诊记录</h2>
            <p>查看您的历史就诊信息、病历和处方</p>
          </div>

          <ElCard class="record-card">
            <template #header>
              <div class="card-header">
                <span>最近就诊</span>
                <StatusTag tone="success">已完成</StatusTag>
              </div>
            </template>
            <ElDescriptions :column="2" border>
              <ElDescriptionsItem label="就诊日期">{{ mockVisitRecord.date }}</ElDescriptionsItem>
              <ElDescriptionsItem label="就诊科室">{{ mockVisitRecord.department }}</ElDescriptionsItem>
              <ElDescriptionsItem label="接诊医生">{{ mockVisitRecord.doctor }}</ElDescriptionsItem>
              <ElDescriptionsItem label="诊断结果">
                <StatusTag tone="warning">{{ mockVisitRecord.diagnosis }}</StatusTag>
              </ElDescriptionsItem>
            </ElDescriptions>
          </ElCard>

          <ElCard class="record-card">
            <template #header>
              <span>处方信息</span>
            </template>
            <pre class="prescription-text">{{ mockVisitRecord.prescription }}</pre>
          </ElCard>

          <ElCard class="record-card">
            <template #header>
              <span>医嘱</span>
            </template>
            <p class="notes-text">{{ mockVisitRecord.notes }}</p>
          </ElCard>

          <ElCard class="record-card">
            <template #header>
              <span>检查报告</span>
            </template>
            <ElEmpty description="暂无检查报告" />
          </ElCard>

          <div class="action-buttons">
            <ElButton @click="goToStep(3)">返回预问诊</ElButton>
            <ElButton type="primary" @click="goToStep(5)">前往随访</ElButton>
          </div>
        </GlassCard>

        <!-- Step 6: 随访 -->
        <GlassCard v-show="currentStep === 5" class="step-content">
          <div class="step-header">
            <span class="step-icon-lg">📅</span>
            <h2>随访管理</h2>
            <p>查看您的随访计划和填写反馈</p>
          </div>

          <div class="followup-section">
            <div class="followup-plans">
              <h3>随访计划</h3>
              <div class="plans-grid">
                <ElCard v-for="plan in mockFollowupPlans" :key="plan.id" class="plan-card" shadow="hover">
                  <template #header>
                    <div class="card-header">
                      <span>{{ plan.planType }}</span>
                      <StatusTag :tone="planTone(plan.status)">{{ planStatusLabel(plan.status) }}</StatusTag>
                    </div>
                  </template>
                  <ElDescriptions :column="1" border size="small">
                    <ElDescriptionsItem label="开始日期">{{ plan.startDate }}</ElDescriptionsItem>
                    <ElDescriptionsItem label="结束日期">{{ plan.endDate }}</ElDescriptionsItem>
                    <ElDescriptionsItem label="频率">{{ plan.frequency }}</ElDescriptionsItem>
                  </ElDescriptions>
                </ElCard>
              </div>
            </div>

            <ElCard class="feedback-card">
              <template #header>
                <span>用药反馈</span>
              </template>
              <ElForm label-position="top">
                <ElFormItem label="服药依从性">
                  <ElInput v-model="feedbackForm.medicationCompliance" placeholder="good / average / poor" />
                </ElFormItem>
                <ElFormItem label="症状反馈">
                  <ElInput v-model="feedbackForm.symptomFeedback" type="textarea" :rows="3" placeholder="请描述您目前的症状改善情况..." />
                </ElFormItem>
                <ElFormItem label="不良反应">
                  <ElInput v-model="feedbackForm.sideEffects" type="textarea" :rows="2" placeholder="如有不良反应请描述..." />
                </ElFormItem>
                <ElFormItem label="恢复情况">
                  <ElInput v-model="feedbackForm.recoveryStatus" type="textarea" :rows="2" placeholder="请描述您的整体恢复情况..." />
                </ElFormItem>
              </ElForm>
              <div class="form-actions">
                <ElButton type="primary" @click="submitFeedback">提交反馈</ElButton>
              </div>
            </ElCard>
          </div>

          <div class="action-buttons">
            <ElButton @click="goToStep(4)">返回就诊记录</ElButton>
            <ElButton type="primary" @click="goToStep(0)">返回概览</ElButton>
          </div>
        </GlassCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.patient-workspace {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

/* Status Bar */
.status-bar {
  padding: var(--space-5);
}

.status-bar__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.status-bar__left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
}

.status-indicator.primary {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.status-indicator.success {
  background: rgba(32, 180, 134, 0.14);
  color: var(--color-success);
}

.status-indicator.warning {
  background: rgba(245, 159, 0, 0.16);
  color: var(--color-warning);
}

.status-indicator.ai {
  background: rgba(124, 92, 255, 0.14);
  color: var(--color-ai);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.status-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.status-bar__right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.progress-label {
  color: var(--color-text-muted);
  font-size: 13px;
}

.progress-bar {
  width: 120px;
}

.progress-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}

.status-hint {
  margin: var(--space-3) 0 0;
  color: var(--color-text-muted);
  font-size: 14px;
}

/* Workspace Layout */
.workspace-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--space-5);
  min-height: 600px;
}

/* Step Sidebar */
.step-sidebar {
  padding: var(--space-5);
  position: sticky;
  top: var(--space-5);
  height: fit-content;
}

.patient-info {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding-bottom: var(--space-4);
}

.patient-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: 600;
}

.patient-details {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.patient-name {
  font-weight: 600;
  font-size: 15px;
}

.step-divider {
  height: 1px;
  background: var(--color-border);
  margin: var(--space-4) 0;
}

.step-nav__title {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0 0 var(--space-4);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.step-nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.step-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.step-item:hover {
  background: var(--color-menu-hover);
}

.step-item.is-active {
  background: var(--color-primary-soft);
}

.step-item.is-completed {
  opacity: 0.7;
}

.step-item.is-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.step-item__indicator {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  background: var(--color-control);
  color: var(--color-text-muted);
}

.step-item.is-active .step-item__indicator {
  background: var(--color-primary);
  color: white;
}

.step-item.is-completed .step-item__indicator {
  background: var(--color-success);
  color: white;
}

.step-check {
  font-size: 14px;
}

.step-item__content {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.step-icon {
  font-size: 16px;
}

.step-title {
  font-size: 14px;
  font-weight: 500;
}

.step-item.is-active .step-title {
  color: var(--color-primary);
  font-weight: 600;
}

/* Content Area */
.content-area {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.step-content {
  padding: var(--space-5);
}

.step-header {
  margin-bottom: var(--space-5);
}

.step-icon-lg {
  font-size: 32px;
  margin-bottom: var(--space-2);
  display: block;
}

.step-header h2 {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 var(--space-2);
  letter-spacing: -0.02em;
}

.step-header p {
  color: var(--color-text-muted);
  margin: 0;
}

/* Overview Grid */
.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.overview-card {
  transition: transform var(--duration-base) var(--ease-standard);
}

.overview-card:hover {
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 600;
}

.card-icon {
  font-size: 18px;
}

.queue-info {
  margin-top: var(--space-4);
}

.queue-info p {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.todo-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  transition: all var(--duration-base) var(--ease-standard);
}

.todo-item.is-done {
  opacity: 0.6;
}

.todo-item.is-done .todo-icon {
  color: var(--color-success);
}

.todo-icon {
  font-size: 16px;
}

.followup-preview {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.followup-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.followup-date {
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 600;
  min-width: 50px;
  text-align: center;
}

.followup-content strong {
  display: block;
  font-size: 14px;
}

.followup-content p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.next-step-panel {
  display: grid;
  gap: var(--space-2);
}

.next-step-panel strong {
  font-size: 16px;
}

.next-step-panel p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

/* Triage Section */
.triage-section {
  margin-bottom: var(--space-5);
}

.triage-form {
  margin-bottom: var(--space-4);
}

.form-actions {
  display: flex;
  gap: var(--space-3);
}

.triage-result {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.result-card {
  margin-top: var(--space-4);
}

.doctors-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.doctor-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.doctor-name {
  font-weight: 600;
}

.doctor-title {
  color: var(--color-text-muted);
  font-size: 13px;
}

.advice-text {
  line-height: 1.8;
  margin: 0 0 var(--space-4);
}

.condition-tags,
.exam-tags {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-top: var(--space-3);
}

.condition-tags strong,
.exam-tags strong {
  color: var(--color-text-muted);
}

.result-actions {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

/* Registration Section */
.registration-card,
.queue-card {
  margin-bottom: var(--space-4);
}

.queue-visual {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  padding: var(--space-4) 0;
}

.queue-number {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
}

.queue-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.queue-info p {
  margin: var(--space-2) 0;
}

.visit-timeline {
  margin-top: var(--space-4);
}

/* Previsit Section */
.previsit-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.previsit-actions {
  display: flex;
  gap: var(--space-3);
}

.previsit-result {
  margin-top: var(--space-4);
}

/* Record Section */
.record-card {
  margin-bottom: var(--space-4);
}

.prescription-text,
.notes-text {
  margin: 0;
  line-height: 1.8;
  white-space: pre-wrap;
}

.notes-text {
  color: var(--color-text-muted);
}

/* Followup Section */
.followup-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.followup-plans h3 {
  margin: 0 0 var(--space-4);
  font-size: 16px;
}

.plans-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-4);
}

.plan-card {
  transition: transform var(--duration-base) var(--ease-standard);
}

.plan-card:hover {
  transform: translateY(-2px);
}

.feedback-card {
  margin-top: var(--space-4);
}

/* Action Buttons */
.action-buttons {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}

/* Responsive */
@media (max-width: 1024px) {
  .workspace-layout {
    grid-template-columns: 1fr;
  }

  .step-sidebar {
    position: static;
  }

  .step-nav {
    flex-direction: row;
    flex-wrap: wrap;
    gap: var(--space-2);
  }

  .step-item {
    flex: 0 0 auto;
    padding: var(--space-2) var(--space-3);
  }

  .overview-grid,
  .plans-grid {
    grid-template-columns: 1fr;
  }

  .status-bar__content {
    flex-direction: column;
    align-items: flex-start;
  }
}

.symptom-categories { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-bottom: var(--space-3); align-items: center; }
.category-label { font-size: 13px; font-weight: 600; color: var(--color-text-muted); }
.category-btn { display: inline-flex; align-items: center; gap: 4px; padding: 5px 12px; border: 1px solid var(--color-border); border-radius: var(--radius-lg); background: var(--color-surface); color: var(--color-text); font-size: 13px; cursor: pointer; transition: all 0.2s; }
.category-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }
.category-btn.is-active { border-color: var(--color-primary); background: rgba(26,119,224,0.1); color: var(--color-primary); font-weight: 600; }
.textarea-wrapper { position: relative; }
.textarea-wrapper :deep(.el-textarea__inner) { padding-right: 44px; }
.voice-btn { position: absolute; right: 12px; bottom: 10px; width: 40px; height: 40px; border-radius: 50%; border: 1px solid var(--color-border); background: var(--color-surface); cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 18px; transition: all 0.2s; z-index: 1; }
.voice-btn:hover { border-color: var(--color-primary); box-shadow: 0 2px 8px rgba(26,119,224,0.2); }
.voice-btn.is-recording { border-color: #ef4444; background: #fef2f2; animation: pulse 1s infinite; }
@keyframes pulse { 0%,100%{transform:scale(1)}50%{transform:scale(1.1)} }
</style>