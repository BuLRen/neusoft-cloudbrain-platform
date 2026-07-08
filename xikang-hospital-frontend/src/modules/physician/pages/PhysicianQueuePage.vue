<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElCheckboxGroup, ElEmpty, ElIcon, ElInput, ElMessage, ElPopover, ElTag } from 'element-plus'
import {
  ArrowRight,
  Calendar,
  Document,
  Filter,
  Search,
  Tickets,
  User,
} from '@element-plus/icons-vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { physicianApi, callingApi, type MedicalRecord, type PhysicianPatient, type CallingResult } from '@/shared/api/modules/physician'
import { useAuthStore } from '@/app/stores/auth'
import { useEncounterStore } from '@/app/stores/encounter'
import ClinicalRecordDrawer from '../components/ClinicalRecordDrawer.vue'
import EncounterProgressCard from '../components/EncounterProgressCard.vue'
import MedicalRecordSummaryCard from '../components/MedicalRecordSummaryCard.vue'
import PhysicianWaitingQueue from '../components/PhysicianWaitingQueue.vue'
import { physicianRoute, resumePathForVisitState, visitStateLabel, VISIT_STATE } from '../constants/visitState'

const VISIT_STATE_FILTER_OPTIONS = [
  { value: VISIT_STATE.REGISTERED, label: '待接诊' },
  { value: VISIT_STATE.IN_PROGRESS, label: '接诊中' },
  { value: VISIT_STATE.EXAM_PENDING, label: '检查检验中' },
  { value: VISIT_STATE.EXAM_COMPLETED, label: '检查检验完成' },
] as const

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const encounterStore = useEncounterStore()

const showNeedEncounterHint = ref(false)
const loading = ref(false)
const allPatients = ref<PhysicianPatient[]>([])
const selectedPatient = ref<PhysicianPatient | null>(null)
const keyword = ref('')
const visitStateFilters = ref<number[]>([])
const draftVisitStateFilters = ref<number[]>([])
const filterPopoverVisible = ref(false)
const stats = reactive({ totalVisited: 0, totalWaiting: 0 })
const notebookDrawerVisible = ref(false)
const medicalRecord = ref<MedicalRecord | null>(null)
const recordLoading = ref(false)
let recordLoadSeq = 0
const waitingQueueRef = ref<InstanceType<typeof PhysicianWaitingQueue> | null>(null)

const selectedRegisterId = computed(() => selectedPatient.value?.registerId)

const filteredPatients = computed(() => {
  if (!visitStateFilters.value.length) return allPatients.value
  const allowed = new Set(visitStateFilters.value)
  return allPatients.value.filter((patient) => allowed.has(patient.visitState))
})

const hasActiveFilters = computed(() => visitStateFilters.value.length > 0)

const activeFilterTags = computed(() =>
  visitStateFilters.value.flatMap((state) => {
    const option = VISIT_STATE_FILTER_OPTIONS.find((item) => item.value === state)
    return option ? [{ value: option.value, label: option.label }] : []
  }),
)

const patientBadge = computed(() => {
  const patient = selectedPatient.value
  if (!patient) return ''
  const parts = [patient.gender, patient.age != null ? `${patient.age}岁` : ''].filter(Boolean)
  return parts.join(' / ')
})

const visitStateInfo = computed(() => {
  const state = selectedPatient.value?.visitState
  if (state == null) return { text: '未知', tone: 'neutral' as const }
  return visitStateLabel(state)
})

const enterButtonLabel = computed(() => {
  const state = selectedPatient.value?.visitState
  if (state === 5 || state === 6) return '继续诊疗'
  return '进入流程（下一步）'
})

const listFooterText = computed(() => {
  const shown = filteredPatients.value.length
  const loaded = allPatients.value.length
  if (!shown) {
    return hasActiveFilters.value ? '无符合筛选条件的患者' : '暂无患者'
  }
  if (hasActiveFilters.value) {
    return `筛选结果 ${shown} 人（已加载 ${loaded} 人）`
  }
  return `显示 ${shown} 人`
})

function syncSelectedPatientAfterFilter() {
  const list = filteredPatients.value
  if (!list.length) {
    selectedPatient.value = null
    return
  }
  if (!selectedPatient.value || !list.some((patient) => patient.registerId === selectedPatient.value?.registerId)) {
    selectedPatient.value = list[0]
  }
}

function openFilterPopover() {
  draftVisitStateFilters.value = [...visitStateFilters.value]
}

function applyFilters() {
  visitStateFilters.value = [...draftVisitStateFilters.value]
  filterPopoverVisible.value = false
  syncSelectedPatientAfterFilter()
}

function resetDraftFilters() {
  draftVisitStateFilters.value = []
}

function removeVisitStateFilter(state: number) {
  visitStateFilters.value = visitStateFilters.value.filter((value) => value !== state)
  syncSelectedPatientAfterFilter()
}

function clearFilters() {
  visitStateFilters.value = []
  draftVisitStateFilters.value = []
  filterPopoverVisible.value = false
  syncSelectedPatientAfterFilter()
}

function formatVisitDate(value?: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function patientProgressPercent(patient: PhysicianPatient) {
  switch (patient.visitState) {
    case VISIT_STATE.REGISTERED:
      return 12
    case VISIT_STATE.IN_PROGRESS:
      return 36
    case VISIT_STATE.EXAM_PENDING:
      return 68
    case VISIT_STATE.EXAM_COMPLETED:
      return 92
    default:
      return 8
  }
}

function patientHintText(patient: PhysicianPatient) {
  if (patient.visitState === VISIT_STATE.EXAM_COMPLETED) {
    return '结果已出，可继续确诊开方'
  }
  if (patient.visitState === VISIT_STATE.EXAM_PENDING) {
    return '检查检验进行中'
  }
  if (patient.visitState === VISIT_STATE.IN_PROGRESS) {
    return '接诊中，可继续填写病历'
  }
  return ''
}

async function loadMedicalRecord(registerId: number) {
  const seq = ++recordLoadSeq
  recordLoading.value = true
  try {
    const record = await physicianApi.medicalRecord(registerId)
    if (seq !== recordLoadSeq) return
    medicalRecord.value = record
  } catch (error) {
    if (seq !== recordLoadSeq) return
    console.warn('加载病历失败:', error)
    medicalRecord.value = null
  } finally {
    if (seq === recordLoadSeq) {
      recordLoading.value = false
    }
  }
}

async function loadPatients() {
  loading.value = true
  try {
    const [patientPage, patientStats] = await Promise.all([
      physicianApi.patients({ keyword: keyword.value, page: 1, size: 20 }),
      physicianApi.patientStats(),
    ])
    allPatients.value = patientPage.records
    stats.totalVisited = patientStats.totalVisited || 0
    stats.totalWaiting = patientStats.totalWaiting || 0
    syncSelectedPatientAfterFilter()
    if (!selectedPatient.value && filteredPatients.value.length > 0) {
      selectedPatient.value = filteredPatients.value[0]
    } else if (selectedPatient.value) {
      const refreshed = allPatients.value.find((p) => p.registerId === selectedPatient.value?.registerId)
      if (refreshed) selectedPatient.value = refreshed
      syncSelectedPatientAfterFilter()
    }
    await refreshWaitingQueue()
  } finally {
    loading.value = false
  }
}

async function enterEncounter() {
  if (!selectedPatient.value || !selectedRegisterId.value) return
  const patient = selectedPatient.value
  const { visitState } = await physicianApi.startEncounter(patient.registerId)
  encounterStore.applyPatient({ ...patient, visitState })
  const path = resumePathForVisitState(visitState)
  await router.push(physicianRoute(path, patient.registerId))
}

// ====== 叫号系统（设计文档 §6.2）======
const currentCalling = ref<CallingResult | null>(null)
const callingBusy = ref(false)
let callingRefreshTimer: ReturnType<typeof setInterval> | null = null
let callingEventSource: EventSource | null = null

async function refreshWaitingQueue() {
  if (authStore.role !== 'physician') return
  await waitingQueueRef.value?.refresh()
}

function onQueueSelect(registerId: number) {
  const patient = allPatients.value.find((p) => p.registerId === registerId)
  if (patient) {
    selectedPatient.value = patient
  }
}

async function refreshCurrentCalling() {
  try {
    currentCalling.value = await callingApi.currentCalling()
  } catch {
    // 静默失败
  }
}

// 订阅本医生的 SSE 频道，收到任意叫号事件立即拉一次 currentCalling，
// 比纯 15s 轮询快很多（多医生场景：别人帮叫/患者应答/超时过号 → 本工作站秒级感知）。
function connectCallingStream() {
  disconnectCallingStream()
  // admin 等无 employeeId 的角色不订阅（也调不了 /call/current 之外的接口）
  if (authStore.role !== 'physician') return
  const doctorId = authStore.employeeId
  if (!doctorId) return
  // /calling/stream/ 在 gateway 白名单内，无需 JWT
  const url = `/api/registration/calling/stream/doctor/${doctorId}`
  callingEventSource = new EventSource(url)
  const onEvent = () => { void refreshCurrentCalling() }
  callingEventSource.addEventListener('CALLED', onEvent)
  callingEventSource.addEventListener('ANSWERED', onEvent)
  callingEventSource.addEventListener('PASSED', onEvent)
  callingEventSource.onerror = () => {
    // EventSource 会自动重连；这里不做事，自动重连后事件会继续到达
  }
}

function disconnectCallingStream() {
  callingEventSource?.close()
  callingEventSource = null
}

async function callNext() {
  if (callingBusy.value) return
  callingBusy.value = true
  try {
    const result = await callingApi.callNext()
    currentCalling.value = result
    ElMessage.success(`已叫：${result.patientName || ''}（${result.queueNumber ?? '-'}号）`)
    await loadPatients()
    await refreshWaitingQueue()
  } catch (e: any) {
    ElMessage.error(e?.message || '叫号失败')
  } finally {
    callingBusy.value = false
  }
}

async function callSpecific(registerId: number) {
  if (callingBusy.value) return
  callingBusy.value = true
  try {
    const result = await callingApi.callSpecific(registerId)
    currentCalling.value = result
    ElMessage.success(`已叫：${result.patientName || ''}`)
    await loadPatients()
    await refreshWaitingQueue()
  } catch (e: any) {
    ElMessage.error(e?.message || '叫号失败')
  } finally {
    callingBusy.value = false
  }
}

async function answerCurrent(registerId: number) {
  if (callingBusy.value) return
  callingBusy.value = true
  try {
    await callingApi.answer(registerId)
    currentCalling.value = null
    ElMessage.success('已应答，进入接诊')
    await loadPatients()
    await refreshWaitingQueue()
  } catch (e: any) {
    ElMessage.error(e?.message || '应答失败')
  } finally {
    callingBusy.value = false
  }
}

async function passCurrent(registerId: number) {
  if (callingBusy.value) return
  callingBusy.value = true
  try {
    await callingApi.pass(registerId)
    currentCalling.value = null
    ElMessage.info('已标记过号')
    await loadPatients()
    await refreshWaitingQueue()
  } catch (e: any) {
    ElMessage.error(e?.message || '过号失败')
  } finally {
    callingBusy.value = false
  }
}

function openNotebookDrawer() {
  if (!selectedRegisterId.value) return
  notebookDrawerVisible.value = true
}

function dismissNeedEncounterHint() {
  showNeedEncounterHint.value = false
  const { needEncounter: _removed, ...rest } = route.query
  void router.replace({ path: route.path, query: rest })
}

watch(
  () => route.query.needEncounter,
  (value) => {
    showNeedEncounterHint.value = value === '1'
  },
  { immediate: true },
)

watch(
  selectedRegisterId,
  (registerId) => {
    medicalRecord.value = null
    if (!registerId) return
    void loadMedicalRecord(registerId)
  },
  { immediate: true },
)

onMounted(() => {
  void loadPatients()
  void refreshCurrentCalling()
  // SSE 实时推送：医生本人频道的叫号/应答/过号事件秒级到达
  connectCallingStream()
  // 15 秒轮询兜底（SSE 重连间隙/网络抖动时保证最终一致）
  callingRefreshTimer = setInterval(refreshCurrentCalling, 15_000)
})

onUnmounted(() => {
  if (callingRefreshTimer) clearInterval(callingRefreshTimer)
  disconnectCallingStream()
})
</script>

<template>
  <div class="physician-queue u-page-grid">
    <ElAlert
      v-if="showNeedEncounterHint"
      type="warning"
      :closable="true"
      show-icon
      title="请先选择患者"
      description="请从下方列表选择患者并进入流程，或通过侧栏进入各诊疗步骤时选择患者。"
      class="queue-hint"
      @close="dismissNeedEncounterHint"
    />

    <PageHeader
      :title="authStore.role === 'physician'
        ? `${authStore.realName || '医生'} · 待诊接诊`
        : authStore.role === 'admin'
          ? '管理员 · 全部患者'
          : '待诊接诊'"
      :description="authStore.role === 'physician'
        ? `当前账号待诊 ${stats.totalWaiting} 人，已看诊 ${stats.totalVisited} 人`
        : authStore.role === 'admin'
          ? `管理员可查看全部待诊患者（${stats.totalWaiting} 人待诊，${stats.totalVisited} 人已看诊）`
          : '选择待诊、接诊中或检查/检验进行中的患者，进入后续诊疗流程'"
      eyebrow="门诊诊疗"
    >
      <template #actions>
        <ElButton type="primary" @click="loadPatients">刷新患者</ElButton>
      </template>
    </PageHeader>

    <section class="queue-grid">
      <aside class="queue-sidebar">
        <PhysicianWaitingQueue
          v-if="authStore.role === 'physician'"
          ref="waitingQueueRef"
          :selected-register-id="selectedRegisterId"
          @select="onQueueSelect"
        />
        <GlassCard class="patient-panel">
        <div class="panel-heading">
          <h2>待诊 / 进行中患者</h2>
          <p>待诊及检查检验中 {{ stats.totalWaiting }} 人，已完成 {{ stats.totalVisited }} 人</p>
        </div>

        <div class="patient-search">
          <ElInput
            v-model="keyword"
            placeholder="搜索病历号或姓名"
            clearable
            class="patient-search__input"
            @keyup.enter="loadPatients"
          >
            <template #prefix>
              <ElIcon><Search /></ElIcon>
            </template>
          </ElInput>
          <ElPopover
            v-model:visible="filterPopoverVisible"
            placement="bottom-end"
            :width="300"
            trigger="click"
            popper-class="queue-filter-popover"
            @show="openFilterPopover"
          >
            <template #reference>
              <ElButton
                class="patient-search__filter"
                :type="hasActiveFilters ? 'primary' : 'default'"
              >
                <ElIcon><Filter /></ElIcon>
                筛选
                <span v-if="hasActiveFilters" class="patient-search__filter-count">
                  {{ visitStateFilters.length }}
                </span>
              </ElButton>
            </template>

            <div class="queue-filter">
              <p class="queue-filter__title">按就诊状态筛选</p>
              <p class="queue-filter__hint">可多选；不选则显示全部已加载患者</p>
              <ElCheckboxGroup v-model="draftVisitStateFilters" class="queue-filter__group">
                <ElCheckbox
                  v-for="option in VISIT_STATE_FILTER_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </ElCheckbox>
              </ElCheckboxGroup>
              <div class="queue-filter__actions">
                <ElButton size="small" @click="resetDraftFilters">重置</ElButton>
                <ElButton size="small" type="primary" @click="applyFilters">应用</ElButton>
              </div>
            </div>
          </ElPopover>
        </div>

        <div v-if="hasActiveFilters" class="patient-filter-tags">
          <span class="patient-filter-tags__label">已筛选</span>
          <ElTag
            v-for="tag in activeFilterTags"
            :key="tag.value"
            size="small"
            closable
            round
            @close="removeVisitStateFilter(tag.value)"
          >
            {{ tag.label }}
          </ElTag>
          <ElButton text type="primary" size="small" @click="clearFilters">清除全部</ElButton>
        </div>

        <div class="patient-list">
          <ElAlert v-if="loading" type="info" :closable="false" title="正在加载患者列表" />
          <button
            v-for="patient in filteredPatients"
            :key="patient.registerId"
            class="patient-item"
            :class="{ 'is-active': patient.registerId === selectedRegisterId }"
            type="button"
            @click="selectedPatient = patient"
          >
            <span class="patient-item__avatar" aria-hidden="true">
              <ElIcon :size="18"><User /></ElIcon>
            </span>
            <span class="patient-item__body">
              <span class="patient-item__top">
                <strong>{{ patient.realName }}</strong>
                <StatusTag :tone="visitStateLabel(patient.visitState).tone">
                  {{ visitStateLabel(patient.visitState).text }}
                </StatusTag>
              </span>
              <span class="patient-item__meta">{{ patient.caseNumber }}</span>
              <span class="patient-item__progress" aria-hidden="true">
                <span
                  class="patient-item__progress-bar"
                  :style="{ width: `${patientProgressPercent(patient)}%` }"
                />
              </span>
              <span v-if="patientHintText(patient)" class="patient-item__hint">
                {{ patientHintText(patient) }}
              </span>
            </span>
            <ElIcon
              v-if="patient.registerId === selectedRegisterId"
              class="patient-item__chevron"
              aria-hidden="true"
            >
              <ArrowRight />
            </ElIcon>
          </button>
          <ElEmpty
            v-if="!loading && filteredPatients.length === 0"
            :description="hasActiveFilters ? '无符合筛选条件的患者' : '暂无待诊或进行中的患者'"
          />
        </div>

        <p v-if="allPatients.length || hasActiveFilters" class="patient-panel__footer">{{ listFooterText }}</p>
        </GlassCard>
      </aside>

      <main class="work-panel">
        <template v-if="selectedPatient">
          <div class="preview-stack">
            <GlassCard class="preview-card preview-card--header">
              <div class="patient-profile">
                <div class="patient-profile__main">
                  <div class="patient-profile__avatar" aria-hidden="true">
                    <ElIcon :size="30"><User /></ElIcon>
                  </div>
                  <div class="patient-profile__identity">
                    <div class="patient-profile__name-row">
                      <h2 class="patient-profile__name">{{ selectedPatient.realName }}</h2>
                      <span v-if="patientBadge" class="patient-profile__badge">{{ patientBadge }}</span>
                      <StatusTag :tone="visitStateInfo.tone">{{ visitStateInfo.text }}</StatusTag>
                    </div>
                    <p v-if="selectedPatient.visitState === 6" class="patient-profile__notice">
                      结果已出，可继续确诊开方
                    </p>
                  </div>
                </div>
                <div class="patient-profile__stats">
                  <div class="patient-profile__stat">
                    <span class="patient-profile__stat-icon" aria-hidden="true">
                      <ElIcon><Tickets /></ElIcon>
                    </span>
                    <div>
                      <span class="patient-profile__stat-label">病历号</span>
                      <strong class="patient-profile__stat-value">{{ selectedPatient.caseNumber || '-' }}</strong>
                    </div>
                  </div>
                  <div class="patient-profile__stat">
                    <span class="patient-profile__stat-icon" aria-hidden="true">
                      <ElIcon><User /></ElIcon>
                    </span>
                    <div>
                      <span class="patient-profile__stat-label">性别</span>
                      <strong class="patient-profile__stat-value">{{ selectedPatient.gender || '-' }}</strong>
                    </div>
                  </div>
                  <div class="patient-profile__stat">
                    <span class="patient-profile__stat-icon" aria-hidden="true">
                      <ElIcon><Calendar /></ElIcon>
                    </span>
                    <div>
                      <span class="patient-profile__stat-label">就诊时间</span>
                      <strong class="patient-profile__stat-value">{{ formatVisitDate(selectedPatient.visitDate) }}</strong>
                    </div>
                  </div>
                </div>
              </div>
            </GlassCard>

            <GlassCard class="preview-card preview-card--flush">
              <MedicalRecordSummaryCard
                :record="medicalRecord"
                :loading="recordLoading"
              />
            </GlassCard>

            <GlassCard class="preview-card preview-card--flush">
              <EncounterProgressCard
                :register-id="selectedRegisterId"
                :visit-state="selectedPatient.visitState"
              />
            </GlassCard>

            <div class="preview-actions">
              <ElButton class="preview-actions__secondary" @click="openNotebookDrawer">
                <ElIcon><Document /></ElIcon>
                查看本次病历本
              </ElButton>
              <ElButton type="primary" class="preview-actions__primary" @click="enterEncounter">
                {{ enterButtonLabel }}
                <ElIcon><ArrowRight /></ElIcon>
              </ElButton>
            </div>

            <!-- 叫号操作区（设计文档 §6.2） -->
            <GlassCard class="calling-card" :class="{ 'calling-card--active': currentCalling?.hasCalling }">
              <!-- 左：当前叫号状态 -->
              <div class="calling-stage">
                <div class="calling-stage__label">
                  <span class="calling-stage__dot" aria-hidden="true"></span>
                  <span v-if="currentCalling?.hasCalling">正在呼叫</span>
                  <span v-else>空闲</span>
                </div>
                <div v-if="currentCalling?.hasCalling" class="calling-stage__num">
                  {{ currentCalling.queueNumber ?? '-' }}<span class="calling-stage__unit">号</span>
                </div>
                <div v-if="currentCalling?.hasCalling" class="calling-stage__name">
                  {{ currentCalling.patientName }}
                </div>
                <div v-if="currentCalling?.hasCalling" class="calling-stage__meta">
                  第 {{ currentCalling.callRound }} 次呼叫
                </div>
                <div v-else class="calling-stage__hint">点击右侧"叫下一个"开始接诊</div>
              </div>

              <!-- 右：操作矩阵 -->
              <div class="calling-pad">
                <button
                  class="calling-btn calling-btn--primary"
                  :disabled="callingBusy"
                  @click="callNext"
                >
                  <span class="calling-btn__icon" aria-hidden="true">▶</span>
                  <span class="calling-btn__text">叫下一个</span>
                </button>
                <button
                  class="calling-btn calling-btn--ghost"
                  :disabled="callingBusy || !currentCalling?.hasCalling || !currentCalling?.registerId"
                  @click="callSpecific(currentCalling!.registerId!)"
                >
                  <span class="calling-btn__text">重呼当前</span>
                </button>
                <button
                  v-if="currentCalling?.hasCalling"
                  class="calling-btn calling-btn--answer"
                  :disabled="callingBusy"
                  @click="answerCurrent(currentCalling.registerId!)"
                >
                  <span class="calling-btn__icon" aria-hidden="true">✓</span>
                  <span class="calling-btn__text">患者应答</span>
                </button>
                <button
                  v-if="currentCalling?.hasCalling"
                  class="calling-btn calling-btn--pass"
                  :disabled="callingBusy"
                  @click="passCurrent(currentCalling.registerId!)"
                >
                  <span class="calling-btn__text">标记过号</span>
                </button>
              </div>
            </GlassCard>
          </div>
        </template>

        <GlassCard v-else class="work-panel-card">
          <ElEmpty description="请选择左侧待诊患者" />
        </GlassCard>
      </main>
    </section>

    <ClinicalRecordDrawer
      v-model:visible="notebookDrawerVisible"
      :register-id="selectedRegisterId ?? null"
      mode="physician"
      subtitle="各环节保存后将自动汇总"
    />
  </div>
</template>

<style scoped>
.queue-hint {
  margin-block-end: var(--space-4);
}

.queue-grid {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: stretch;
}

.queue-sidebar {
  min-width: 0;
  min-height: 0;
}

.patient-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100%;
  position: sticky;
  top: var(--space-3);
  max-height: calc(100vh - var(--shell-gap) * 2 - var(--space-3));
  overflow: hidden;
}
.patient-panel,
.preview-card,
.work-panel-card {
  padding: var(--space-5);
}

.preview-card--flush {
  padding: 0;
  overflow: hidden;
}

.panel-heading h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.panel-heading {
  flex-shrink: 0;
}

.panel-heading p {
  margin-block-start: 6px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.patient-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
  flex-shrink: 0;
}

.patient-search__filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.patient-search__filter-count {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  background: rgba(255, 255, 255, 0.28);
}

.patient-filter-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-block-start: var(--space-3);
}

.patient-filter-tags__label {
  font-size: 12px;
  color: var(--color-text-soft);
}

.queue-filter__title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
}

.queue-filter__hint {
  margin: 6px 0 var(--space-3);
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.queue-filter__group {
  display: grid;
  gap: 8px;
}

.queue-filter__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
  padding-block-start: var(--space-3);
  border-block-start: 1px solid var(--color-border);
}

.patient-list {
  margin-block-start: var(--space-4);
  display: grid;
  gap: var(--space-2);
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-inline-end: 2px;
  align-content: start;
}

.patient-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: center;
  padding: var(--space-3) var(--space-3) var(--space-3) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  text-align: start;
  background: rgba(255, 255, 255, 0.55);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.patient-item:hover {
  border-color: rgba(31, 140, 255, 0.28);
  background: rgba(255, 255, 255, 0.92);
}

.patient-item.is-active {
  border-color: rgba(31, 140, 255, 0.45);
  background: linear-gradient(90deg, rgba(31, 140, 255, 0.1), rgba(255, 255, 255, 0.95));
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.patient-item__avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}

.patient-item__body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.patient-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.patient-item__top strong {
  font-size: 15px;
}

.patient-item__meta {
  color: var(--color-text-muted);
  font-size: 12px;
}

.patient-item__progress {
  display: block;
  height: 4px;
  margin-block-start: 2px;
  border-radius: 999px;
  background: #e8edf3;
  overflow: hidden;
}

.patient-item__progress-bar {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-primary), #5eb3ff);
  transition: width 0.25s ease;
}

.patient-item__hint {
  font-size: 12px;
  color: var(--color-success);
}

.patient-item__chevron {
  color: var(--color-primary);
}

.patient-panel__footer {
  margin: var(--space-3) 0 0;
  margin-block-start: auto;
  padding-block-start: var(--space-3);
  text-align: center;
  color: var(--color-text-soft);
  font-size: 12px;
  flex-shrink: 0;
}

.preview-stack {
  display: grid;
  gap: var(--space-4);
}

.preview-card--header {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
}

.patient-profile {
  display: grid;
  gap: var(--space-5);
}

.patient-profile__main {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.patient-profile__avatar {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  color: var(--color-primary-strong);
  background: linear-gradient(145deg, rgba(31, 140, 255, 0.18), rgba(47, 216, 196, 0.12));
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.12);
}

.patient-profile__identity {
  flex: 1 1 220px;
  min-width: 0;
}

.patient-profile__name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.patient-profile__name {
  margin: 0;
  font-size: 30px;
  line-height: 1.1;
  letter-spacing: -0.03em;
}

.patient-profile__badge {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 var(--space-3);
  border-radius: 999px;
  color: var(--color-primary-strong);
  font-size: 13px;
  font-weight: 600;
  background: var(--color-primary-soft);
}

.patient-profile__notice {
  margin: var(--space-2) 0 0;
  font-size: 13px;
  color: var(--color-success);
}

.patient-profile__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.patient-profile__stat {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.patient-profile__stat-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.patient-profile__stat-label {
  display: block;
  color: var(--color-text-soft);
  font-size: 12px;
}

.patient-profile__stat-value {
  display: block;
  margin-block-start: 2px;
  font-size: 15px;
  font-weight: 700;
  word-break: break-all;
}

.preview-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}

.preview-actions__secondary,
.preview-actions__primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.preview-actions__primary {
  min-width: 168px;
  padding-inline: var(--space-5);
}

@media (max-width: 1100px) {
  .patient-profile__stats {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .queue-grid {
    grid-template-columns: 1fr;
  }

  .patient-panel {
    position: static;
    max-height: none;
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .patient-list {
    flex: none;
    min-height: 0;
    max-height: none;
  }

  .patient-panel__footer {
    margin-block-start: var(--space-3);
  }
}

/* ===== 叫号卡片（设计文档 §6.2 重设计版） ===== */
/* 设计思路：
   - 左状态 + 右操作的指挥台布局，状态先于操作
   - "正在呼叫"用品牌渐变 + 大字号承担视觉重量
   - 空闲态刻意做"扁、静"，让医生明显感知到"还有号没叫"
   - 按钮按业务语义分层：主操作（叫下一个）= 视觉中心
     应答 = 绿色 confirm；过号 = 弱化 ghost；重呼 = ghost
*/
.calling-card {
  margin-block-start: var(--space-3);
  padding: var(--space-5) !important;
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(220px, 1.3fr);
  gap: var(--space-5);
  align-items: stretch;
  position: relative;
  overflow: hidden;
  transition: border-color var(--duration-base) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard);
}

/* 正在呼叫时：整张卡片左侧加一条 4px 渐变条，作为"有进行中任务"的视觉锚点 */
.calling-card--active::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--gradient-primary);
}

/* ===== 左：状态台 ===== */
.calling-stage {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--space-2);
  padding-inline-start: var(--space-2);
}
.calling-stage__label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--color-text-muted);
  text-transform: uppercase;
}
.calling-stage__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-text-soft);
  flex-shrink: 0;
}
.calling-card--active .calling-stage__dot {
  background: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-soft);
  animation: calling-pulse 1.6s ease-in-out infinite;
}
@keyframes calling-pulse {
  0%, 100% { box-shadow: 0 0 0 4px var(--color-primary-soft); }
  50%      { box-shadow: 0 0 0 8px rgba(31, 140, 255, 0.06); }
}

.calling-stage__num {
  font-size: 56px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.calling-stage__unit {
  font-size: 22px;
  font-weight: 600;
  margin-inline-start: 4px;
  -webkit-text-fill-color: var(--color-text-muted);
}
.calling-stage__name {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
}
.calling-stage__meta {
  font-size: 12px;
  color: var(--color-text-soft);
}
.calling-stage__hint {
  font-size: 13px;
  color: var(--color-text-soft);
  line-height: 1.5;
  margin-block-start: var(--space-1);
}

/* ===== 右：按钮矩阵 ===== */
.calling-pad {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}
.calling-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-3);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-standard),
              background var(--duration-fast) var(--ease-standard),
              box-shadow var(--duration-fast) var(--ease-standard),
              border-color var(--duration-fast) var(--ease-standard);
  min-height: 52px;
}
.calling-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.calling-btn:not(:disabled):active {
  transform: translateY(1px);
}
.calling-btn__icon {
  font-size: 12px;
  display: inline-flex;
  align-items: center;
}

/* 主操作：品牌渐变 + 阴影，占据视觉中心 */
.calling-btn--primary {
  background: var(--gradient-primary);
  color: #fff;
  box-shadow: 0 6px 16px rgba(31, 140, 255, 0.32);
}
.calling-btn--primary:not(:disabled):hover {
  box-shadow: 0 10px 24px rgba(31, 140, 255, 0.42);
  transform: translateY(-1px);
}

/* 应答：绿色 confirm，第二权重 */
.calling-btn--answer {
  background: var(--color-success);
  color: #fff;
  box-shadow: 0 4px 12px rgba(32, 180, 134, 0.28);
}
.calling-btn--answer:not(:disabled):hover {
  box-shadow: 0 8px 18px rgba(32, 180, 134, 0.38);
  transform: translateY(-1px);
}

/* Ghost：弱化操作，玻璃描边 */
.calling-btn--ghost,
.calling-btn--pass {
  background: var(--color-control);
  color: var(--color-text);
  border-color: var(--color-border);
  backdrop-filter: blur(8px);
}
.calling-btn--ghost:not(:disabled):hover,
.calling-btn--pass:not(:disabled):hover {
  background: var(--color-control-hover);
  border-color: var(--color-border-strong);
}

/* 过号：刻意弱化（不该频繁用），警告色仅作 hover */
.calling-btn--pass:not(:disabled):hover {
  color: var(--color-warning-strong);
  border-color: var(--color-warning);
}

/* ===== 响应式 ===== */
@media (max-width: 720px) {
  .calling-card {
    grid-template-columns: 1fr;
  }
  .calling-stage__num {
    font-size: 44px;
  }
}

/* 尊重用户的 reduced-motion 设置 */
@media (prefers-reduced-motion: reduce) {
  .calling-card--active .calling-stage__dot {
    animation: none;
  }
  .calling-btn {
    transition: none;
  }
  .calling-btn:not(:disabled):active {
    transform: none;
  }
}
</style>
