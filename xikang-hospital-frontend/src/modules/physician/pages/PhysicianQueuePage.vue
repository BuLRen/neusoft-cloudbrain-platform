<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElCheckboxGroup, ElEmpty, ElIcon, ElInput, ElMessage, ElPagination, ElPopover, ElTag } from 'element-plus'
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
import { apiUrl } from '@/config/api'
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
  { value: VISIT_STATE.ENDED, label: '已结束看诊' },
] as const

const PAGE_SIZE_OPTIONS = [10, 20, 30] as const
const DEFAULT_PAGE_SIZE = 10

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
const currentPage = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const totalPatients = ref(0)
const stats = reactive({ totalVisited: 0, totalWaiting: 0 })
const notebookDrawerVisible = ref(false)
const medicalRecord = ref<MedicalRecord | null>(null)
const recordLoading = ref(false)
let recordLoadSeq = 0
const waitingQueueRef = ref<InstanceType<typeof PhysicianWaitingQueue> | null>(null)

const selectedRegisterId = computed(() => selectedPatient.value?.registerId)

/** 服务端已按筛选分页，当前页直接展示 */
const filteredPatients = computed(() => allPatients.value)

const hasActiveFilters = computed(() => visitStateFilters.value.length > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(totalPatients.value / pageSize.value)))

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
  if (state === VISIT_STATE.ENDED) return '看诊已结束'
  if (state === VISIT_STATE.EXAM_PENDING || state === VISIT_STATE.EXAM_COMPLETED) return '继续诊疗'
  return '进入流程（下一步）'
})

const canEnterEncounter = computed(() => {
  const state = selectedPatient.value?.visitState
  return state != null && state !== VISIT_STATE.ENDED && state !== VISIT_STATE.CANCELLED
})

const listFooterText = computed(() => {
  if (!totalPatients.value) {
    return hasActiveFilters.value ? '无符合筛选条件的患者' : '暂无待诊或进行中的患者'
  }
  return `共 ${totalPatients.value} 人 · 第 ${currentPage.value}/${totalPages.value} 页`
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
  currentPage.value = 1
  void loadPatients()
}

function resetDraftFilters() {
  draftVisitStateFilters.value = []
}

function removeVisitStateFilter(state: number) {
  visitStateFilters.value = visitStateFilters.value.filter((value) => value !== state)
  currentPage.value = 1
  void loadPatients()
}

function clearFilters() {
  visitStateFilters.value = []
  draftVisitStateFilters.value = []
  filterPopoverVisible.value = false
  currentPage.value = 1
  void loadPatients()
}

function onPageChange(page: number) {
  currentPage.value = page
  void loadPatients()
}

function onPageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  void loadPatients()
}

function searchPatients() {
  currentPage.value = 1
  void loadPatients()
}

function formatVisitDate(value?: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
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
    const includeEnded = visitStateFilters.value.includes(VISIT_STATE.ENDED)
    const visitStates = visitStateFilters.value.length
      ? [...visitStateFilters.value]
      : undefined
    const [patientPage, patientStats] = await Promise.all([
      physicianApi.patients({
        keyword: keyword.value,
        page: currentPage.value,
        size: pageSize.value,
        includeEnded,
        visitStates,
      }),
      physicianApi.patientStats(),
    ])
    allPatients.value = patientPage.records
    totalPatients.value = patientPage.total || 0
    if (patientPage.page) currentPage.value = patientPage.page
    stats.totalVisited = patientStats.totalVisited || 0
    stats.totalWaiting = patientStats.totalWaiting || 0
    syncSelectedPatientAfterFilter()
    if (selectedPatient.value) {
      const refreshed = allPatients.value.find((p) => p.registerId === selectedPatient.value?.registerId)
      if (refreshed) selectedPatient.value = refreshed
    }
    await refreshWaitingQueue()
  } finally {
    loading.value = false
  }
}

async function enterEncounter() {
  if (!selectedPatient.value || !selectedRegisterId.value || !canEnterEncounter.value) return
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
  const url = apiUrl(`/registration/calling/stream/doctor/${doctorId}`)
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
          :current-calling="currentCalling"
          :calling-busy="callingBusy"
          @select="onQueueSelect"
          @call-next="callNext"
          @recall-current="callSpecific"
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
            @keyup.enter="searchPatients"
            @clear="searchPatients"
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
              <p class="queue-filter__hint">可多选；不选则默认显示进行中患者（不含已结束）</p>
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
            <span class="patient-item__body">
              <strong class="patient-item__name">{{ patient.realName }}</strong>
              <span class="patient-item__meta">{{ patient.caseNumber || '-' }}</span>
            </span>
            <StatusTag :tone="visitStateLabel(patient.visitState).tone">
              {{ visitStateLabel(patient.visitState).text }}
            </StatusTag>
          </button>
          <ElEmpty
            v-if="!loading && filteredPatients.length === 0"
            :description="hasActiveFilters ? '无符合筛选条件的患者' : '暂无待诊或进行中的患者'"
          />
        </div>

        <div v-if="totalPatients > 0" class="patient-pagination">
          <p class="patient-panel__footer">{{ listFooterText }}</p>
          <ElPagination
            :current-page="currentPage"
            :page-size="pageSize"
            :total="totalPatients"
            :page-sizes="[...PAGE_SIZE_OPTIONS]"
            layout="sizes, prev, pager, next"
            small
            background
            @current-change="onPageChange"
            @size-change="onPageSizeChange"
          />
        </div>
        <p v-else-if="!loading" class="patient-panel__footer">{{ listFooterText }}</p>
        </GlassCard>
      </aside>

      <main class="work-panel">
        <template v-if="selectedPatient">
          <div class="preview-scroll">
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
                      <p v-if="selectedPatient.visitState === VISIT_STATE.EXAM_COMPLETED" class="patient-profile__notice">
                        结果已出，可继续确诊开方
                      </p>
                      <p v-else-if="selectedPatient.visitState === VISIT_STATE.ENDED" class="patient-profile__notice">
                        看诊已结束，可查看本次病历本
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
            </div>
          </div>

          <div class="preview-actions">
            <ElButton class="preview-actions__secondary" @click="openNotebookDrawer">
              <ElIcon><Document /></ElIcon>
              查看本次病历本
            </ElButton>
            <ElButton
              type="primary"
              class="preview-actions__primary"
              :disabled="!canEnterEncounter"
              @click="enterEncounter"
            >
              {{ enterButtonLabel }}
              <ElIcon><ArrowRight /></ElIcon>
            </ElButton>
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
  align-items: start;
}

.queue-sidebar {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

.patient-panel {
  display: flex;
  flex-direction: column;
  /* 高度由左侧内容（约 10 名患者）自然撑开，不再跟随右侧拉伸 */
  height: auto;
  max-height: none;
  position: static;
  overflow: visible;
}

.work-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  align-self: stretch;
  /* 不参与撑高 grid 行；行高由左侧决定，右侧在该高度内滚动 */
  height: 0;
  min-height: 100%;
  overflow: hidden;
}

.preview-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding-inline-end: 2px;
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
  flex: none;
  align-content: start;
}

.patient-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
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

.patient-item__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.patient-item__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 700;
}

.patient-item__meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-muted);
  font-size: 12px;
}

.patient-pagination {
  display: grid;
  gap: var(--space-2);
  margin-block-start: var(--space-3);
  padding-block-start: var(--space-3);
  border-block-start: 1px solid var(--color-border);
  flex-shrink: 0;
}

.patient-pagination :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: center;
  row-gap: var(--space-2);
}

.patient-panel__footer {
  margin: 0;
  text-align: center;
  color: var(--color-text-soft);
  font-size: 12px;
  flex-shrink: 0;
}

.patient-panel > .patient-panel__footer {
  margin-block-start: var(--space-3);
  padding-block-start: var(--space-3);
  border-block-start: 1px solid var(--color-border);
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
  flex-shrink: 0;
  margin-block-start: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.96);
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

  .work-panel {
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .preview-scroll {
    overflow: visible;
    scrollbar-gutter: auto;
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

</style>
