<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElCheckboxGroup, ElEmpty, ElIcon, ElInput, ElPopover, ElTag } from 'element-plus'
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
import { physicianApi, type MedicalRecord, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useAuthStore } from '@/app/stores/auth'
import { useEncounterStore } from '@/app/stores/encounter'
import ClinicalRecordDrawer from '../components/ClinicalRecordDrawer.vue'
import EncounterProgressCard from '../components/EncounterProgressCard.vue'
import MedicalRecordSummaryCard from '../components/MedicalRecordSummaryCard.vue'
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
      description="诊疗步骤需从待诊接诊选择患者并进入流程后，方可通过侧边栏访问。"
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
</style>
