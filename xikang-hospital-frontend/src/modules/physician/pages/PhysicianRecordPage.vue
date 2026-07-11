<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import {
  ElButton,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElRadio,
  ElRadioGroup,
  ElTag,
} from 'element-plus'
import {
  CirclePlusFilled,
  Clock,
  Cpu,
  DocumentChecked,
  EditPen,
  MagicStick,
  Monitor,
  Pouring,
  Tickets,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import {
  physicianApi,
  type MedicalRecord,
  type PreliminaryAiMeta,
} from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import PreliminaryDiagnosisPanel from '../components/PreliminaryDiagnosisPanel.vue'
import PreliminaryModelDrawer from '../components/PreliminaryModelDrawer.vue'
import { physicianRoute } from '../constants/visitState'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'
import {
  DEFAULT_PRELIMINARY_AI_MODEL,
  findPreliminaryAiModel,
} from '../constants/preliminary-ai-models'

type PreliminaryInputSource = 'current_record' | 'natural_language'

interface RecordFieldConfig {
  key: 'readme' | 'present' | 'presentTreat' | 'history' | 'allergy' | 'physique' | 'proposal'
  label: string
  icon: Component
  placeholder?: string
  rows: number
  fullWidth?: boolean
}

const RECORD_FIELDS: RecordFieldConfig[] = [
  { key: 'readme', label: '主诉', icon: User, rows: 3 },
  { key: 'present', label: '现病史', icon: EditPen, rows: 3 },
  {
    key: 'presentTreat',
    label: '现病治疗情况',
    icon: Monitor,
    placeholder: '请描述目前的治疗情况、用药情况及效果',
    rows: 3,
  },
  { key: 'history', label: '既往史', icon: Clock, rows: 3 },
  { key: 'allergy', label: '过敏史', icon: CirclePlusFilled, rows: 3 },
  {
    key: 'physique',
    label: '体格检查',
    icon: UserFilled,
    placeholder: '请填写体格检查结果',
    rows: 3,
  },
  {
    key: 'proposal',
    label: '检查/检验建议',
    icon: Pouring,
    placeholder: '检查或检验项目建议（非初步诊断）',
    rows: 3,
    fullWidth: true,
  },
]

const router = useRouter()
const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const loading = ref(false)
const preliminaryLoading = ref(false)

const recordForm = reactive({
  id: undefined as number | undefined,
  readme: '',
  present: '',
  presentTreat: '',
  history: '',
  allergy: '',
  physique: '',
  proposal: '',
})

const preliminaryInputSource = ref<PreliminaryInputSource>('current_record')
const naturalLanguageText = ref('')
const modelDrawerVisible = ref(false)
const selectedAiModel = ref(DEFAULT_PRELIMINARY_AI_MODEL)

const selectedAiModelLabel = computed(
  () => findPreliminaryAiModel(selectedAiModel.value)?.label ?? selectedAiModel.value,
)

const preliminaryForm = reactive({
  aiReasoningText: '',
  doctorDiagnosis: '',
})

const aiMeta = ref<PreliminaryAiMeta>({})
const aiSnapshot = ref({ aiReasoningText: '', doctorDiagnosis: '' })

type RecordSnapshot = Pick<
  typeof recordForm,
  'readme' | 'present' | 'presentTreat' | 'history' | 'allergy' | 'physique' | 'proposal'
>

const savedRecordSnapshot = ref<RecordSnapshot>({
  readme: '',
  present: '',
  presentTreat: '',
  history: '',
  allergy: '',
  physique: '',
  proposal: '',
})

const savedPreliminarySnapshot = ref({ doctorDiagnosis: '', aiReasoningText: '' })

function syncSavedSnapshots() {
  savedRecordSnapshot.value = {
    readme: recordForm.readme,
    present: recordForm.present,
    presentTreat: recordForm.presentTreat,
    history: recordForm.history,
    allergy: recordForm.allergy,
    physique: recordForm.physique,
    proposal: recordForm.proposal,
  }
  savedPreliminarySnapshot.value = {
    doctorDiagnosis: preliminaryForm.doctorDiagnosis,
    aiReasoningText: preliminaryForm.aiReasoningText,
  }
  aiSnapshot.value = {
    aiReasoningText: preliminaryForm.aiReasoningText,
    doctorDiagnosis: preliminaryForm.doctorDiagnosis,
  }
}

const recordDirty = computed(() =>
  (Object.keys(savedRecordSnapshot.value) as (keyof RecordSnapshot)[]).some(
    (key) => recordForm[key] !== savedRecordSnapshot.value[key],
  ),
)

const preliminaryDirty = computed(
  () =>
    preliminaryForm.doctorDiagnosis.trim() !== savedPreliminarySnapshot.value.doctorDiagnosis.trim() ||
    preliminaryForm.aiReasoningText.trim() !== savedPreliminarySnapshot.value.aiReasoningText.trim(),
)

const hasUnsavedChanges = computed(() => recordDirty.value || preliminaryDirty.value)

const hasPreliminaryAiResult = computed(
  () =>
    Boolean(aiMeta.value.aiDiagnosis) ||
    Boolean(aiMeta.value.suggestedDiseases?.length) ||
    Boolean(preliminaryForm.aiReasoningText.trim()),
)

const doctorModified = computed(() => {
  // 未跑过 AI 时，只要编辑区已有内容，重新生成前也需确认，避免覆盖手工填写
  if (!aiSnapshot.value.aiReasoningText && !aiSnapshot.value.doctorDiagnosis.trim()) {
    return Boolean(preliminaryForm.doctorDiagnosis.trim())
  }
  const reasoningChanged =
    preliminaryForm.aiReasoningText.trim() !== aiSnapshot.value.aiReasoningText.trim()
  const diagnosisChanged =
    preliminaryForm.doctorDiagnosis.trim() !== aiSnapshot.value.doctorDiagnosis.trim()
  return reasoningChanged || diagnosisChanged
})

function doctorDiagnosisFromMeta(meta: PreliminaryAiMeta | undefined): string {
  if (!meta) return ''
  if (meta.suggestedDiseaseNames?.length) {
    return meta.suggestedDiseaseNames.join('、')
  }
  const fromDiseases = (meta.suggestedDiseases || [])
    .map((item) => item.diseaseName?.trim())
    .filter((name): name is string => Boolean(name))
  return fromDiseases.join('、')
}

function doctorDiagnosisFromAiResult(
  suggestedDiseases: PreliminaryAiMeta['suggestedDiseases'],
): string {
  return (suggestedDiseases || [])
    .map((item) => item.diseaseName?.trim())
    .filter((name): name is string => Boolean(name))
    .join('、')
}

function suggestedDiseaseNamesForSave(text: string): string[] {
  return text
    .split(/[,，、;；\n]+/)
    .map((name) => name.trim())
    .filter(Boolean)
}

function buildRecordText(): string {
  const parts = [
    recordForm.readme && `主诉：${recordForm.readme}`,
    recordForm.present && `现病史：${recordForm.present}`,
    recordForm.presentTreat && `现病治疗：${recordForm.presentTreat}`,
    recordForm.history && `既往史：${recordForm.history}`,
    recordForm.allergy && `过敏史：${recordForm.allergy}`,
    recordForm.physique && `体格检查：${recordForm.physique}`,
  ].filter(Boolean)
  return parts.join('\n')
}

function resolvePreliminaryPayload(): { text: string; preHandle: boolean } | null {
  if (!registerId.value) return null
  if (preliminaryInputSource.value === 'natural_language') {
    const text = naturalLanguageText.value.trim()
    if (!text) {
      ElMessage.warning('请填写患者自然语言描述')
      return null
    }
    return { text, preHandle: false }
  }
  const text = buildRecordText()
  if (!text.trim()) {
    ElMessage.warning('请先填写病历内容')
    return null
  }
  return { text, preHandle: true }
}

function applyMedicalRecord(record: MedicalRecord | null) {
  recordForm.id = record?.id
  recordForm.readme = record?.readme || encounterStore.aiConsultSummary?.chiefComplaint || ''
  recordForm.present = record?.present || encounterStore.aiConsultSummary?.aiSummary || ''
  recordForm.presentTreat = record?.presentTreat || ''
  recordForm.history = record?.history || ''
  recordForm.allergy = record?.allergy || ''
  recordForm.physique = record?.physique || ''
  recordForm.proposal = record?.proposal || ''

  const meta = record?.preliminaryAiMeta
  const savedPreliminary = record?.preliminaryDiagnosis?.trim() || ''
  const aiFull = meta?.aiDiagnosis?.trim() || ''
  preliminaryForm.aiReasoningText = aiFull || (savedPreliminary.length > 120 ? savedPreliminary : '')

  const fromMeta = doctorDiagnosisFromMeta(meta)
  if (fromMeta) {
    preliminaryForm.doctorDiagnosis = fromMeta
  } else if (savedPreliminary && savedPreliminary !== aiFull) {
    preliminaryForm.doctorDiagnosis = savedPreliminary
  } else {
    preliminaryForm.doctorDiagnosis = ''
  }

  aiMeta.value = meta || {}
  syncSavedSnapshots()
}

async function loadContext() {
  if (!registerId.value) return
  loading.value = true
  try {
    const record = await physicianApi.medicalRecord(registerId.value)
    applyMedicalRecord(record)
  } finally {
    loading.value = false
  }
}

async function generatePreliminaryDiagnosis() {
  if (!registerId.value) return
  const payload = resolvePreliminaryPayload()
  if (!payload) return

  if (doctorModified.value) {
    try {
      await ElMessageBox.confirm('当前初步诊断已有手工修改，重新生成将覆盖编辑区内容。是否继续？', '确认重新生成', {
        type: 'warning',
      })
    } catch {
      return
    }
  }

  preliminaryLoading.value = true
  try {
    const result = await physicianApi.aiPreliminaryDiagnosis({
      registerId: registerId.value,
      text: payload.text,
      preHandle: payload.preHandle,
      model: selectedAiModel.value,
    })
    preliminaryForm.aiReasoningText = result.diagnosisText || ''
    preliminaryForm.doctorDiagnosis =
      result.primaryDiagnosis?.trim() || doctorDiagnosisFromAiResult(result.suggestedDiseases)
    const suggestedDiseaseNames = suggestedDiseaseNamesForSave(preliminaryForm.doctorDiagnosis)
    aiMeta.value = {
      aiDiagnosis: result.diagnosisText,
      clinicalSummary: result.clinicalSummary,
      primaryDiagnosis: result.primaryDiagnosis,
      diagnosisBasis: result.diagnosisBasis,
      knowledgeBaseRecall: result.knowledgeBaseRecall,
      isRecalled: result.isRecalled,
      confidence: result.confidence,
      modelId: result.modelId,
      llmModel: result.llmModel ?? selectedAiModel.value,
      suggestedDiseases: result.suggestedDiseases,
      suggestedDiseaseNames,
      excludedDiagnoses: result.excludedDiagnoses,
      redFlags: result.redFlags,
      preHandle: result.preHandle,
    }
    aiSnapshot.value = {
      aiReasoningText: result.diagnosisText || '',
      doctorDiagnosis: preliminaryForm.doctorDiagnosis,
    }
    ElMessage.success('初步诊断已生成，请审核后保存')
  } finally {
    preliminaryLoading.value = false
  }
}

async function persistMedicalRecord(): Promise<boolean> {
  if (!registerId.value) return false
  const payload = {
    registerId: registerId.value,
    readme: recordForm.readme,
    present: recordForm.present,
    presentTreat: recordForm.presentTreat,
    history: recordForm.history,
    allergy: recordForm.allergy,
    physique: recordForm.physique,
    proposal: recordForm.proposal,
  }

  try {
    if (recordForm.id) {
      await physicianApi.updateMedicalRecord(recordForm.id, payload)
    } else {
      const result = await physicianApi.createMedicalRecord(payload)
      recordForm.id = result.id
    }
    syncSavedSnapshots()
    return true
  } catch {
    return false
  }
}

async function saveMedicalRecord() {
  const wasCreate = !recordForm.id
  const saved = await persistMedicalRecord()
  if (saved) {
    ElMessage.success(wasCreate ? '病历已创建' : '病历已更新')
  }
}

async function persistPreliminaryDiagnosis(): Promise<boolean> {
  if (!registerId.value) return false
  const confirmed = preliminaryForm.doctorDiagnosis.trim()
  if (!confirmed) {
    ElMessage.warning('请填写医生确认的初步诊断')
    return false
  }
  preliminaryLoading.value = true
  try {
    await physicianApi.savePreliminaryDiagnosis({
      registerId: registerId.value,
      preliminaryDiagnosis: confirmed,
      suggestedDiseaseNames: suggestedDiseaseNamesForSave(confirmed),
    })
    await loadContext()
    return true
  } catch {
    return false
  } finally {
    preliminaryLoading.value = false
  }
}

async function promptNextStepAfterSave() {
  try {
    await ElMessageBox.confirm(
      '初步诊断已保存。下一步可前往「开立检查检验」为患者开具检查/检验申请。',
      '保存成功',
      {
        confirmButtonText: '前往开立检查检验',
        cancelButtonText: '留在当前页',
        distinguishCancelAndClose: true,
        type: 'success',
      },
    )
    await router.push(physicianRoute('/physician/orders', registerId.value))
  } catch {
    ElMessage.success('初步诊断已保存')
  }
}

async function savePreliminaryDiagnosis() {
  const saved = await persistPreliminaryDiagnosis()
  if (saved) {
    await promptNextStepAfterSave()
  }
}

function unsavedLeaveMessage(): string {
  const parts: string[] = []
  if (recordDirty.value) parts.push('病历')
  if (preliminaryDirty.value) parts.push('初步诊断')
  return `您有未保存的${parts.join('、')}，离开前是否保存？`
}

async function persistUnsavedChanges(): Promise<boolean> {
  if (recordDirty.value) {
    const saved = await persistMedicalRecord()
    if (!saved) return false
  }
  if (preliminaryDirty.value) {
    const saved = await persistPreliminaryDiagnosis()
    if (!saved) return false
  }
  return true
}

async function confirmLeaveBeforeNavigate(): Promise<boolean> {
  if (!hasUnsavedChanges.value) return true

  try {
    await ElMessageBox.confirm(unsavedLeaveMessage(), '未保存的更改', {
      distinguishCancelAndClose: true,
      confirmButtonText: '保存并离开',
      cancelButtonText: '不保存',
      type: 'warning',
    })
    return persistUnsavedChanges()
  } catch (action) {
    if (action === 'cancel') return true
    return false
  }
}

onBeforeRouteLeave((_to, _from, next) => {
  void confirmLeaveBeforeNavigate().then((proceed) => {
    next(proceed)
  })
})

watch(registerId, () => {
  void loadContext()
})

onMounted(() => {
  void loadContext()
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="病历与初步诊断"
    prev-path="/physician/queue"
    next-path="/physician/orders"
    content-variant="split"
  >
    <div class="record-page">
      <section class="record-card patient-record">
        <header class="patient-record__header">
          <div class="patient-record__intro">
            <div class="patient-record__logo" aria-hidden="true">
              <ElIcon :size="24"><Tickets /></ElIcon>
            </div>
            <div>
              <h2 class="patient-record__title">患者病历</h2>
              <p class="patient-record__subtitle">详细记录患者病情信息</p>
            </div>
          </div>
          <ElButton
            type="primary"
            class="patient-record__save"
            :loading="loading"
            @click="saveMedicalRecord"
          >
            <ElIcon class="patient-record__save-icon"><DocumentChecked /></ElIcon>
            保存病历
          </ElButton>
        </header>

        <div class="patient-record__grid">
          <section
            v-for="field in RECORD_FIELDS"
            :key="field.key"
            class="patient-record__field"
            :class="{ 'patient-record__field--full': field.fullWidth }"
          >
            <label class="patient-record__label" :for="`record-${field.key}`">
              <ElIcon class="patient-record__label-icon" :size="16">
                <component :is="field.icon" />
              </ElIcon>
              <span>{{ field.label }}</span>
            </label>
            <ElInput
              :id="`record-${field.key}`"
              v-model="recordForm[field.key]"
              type="textarea"
              :rows="field.rows"
              :placeholder="field.placeholder"
              resize="vertical"
              class="patient-record__input"
            />
          </section>
        </div>
      </section>

      <section class="record-card ai-panel" :class="{ 'ai-panel--has-results': hasPreliminaryAiResult }">
        <div class="ai-panel__controls">
          <header class="ai-panel__header">
            <div class="ai-panel__intro">
              <div class="ai-panel__logo" aria-hidden="true">
                <ElIcon :size="22"><Cpu /></ElIcon>
              </div>
              <h2 class="ai-panel__title">AI 初步诊断</h2>
            </div>
          </header>



          <div class="ai-panel__source">
            <ElRadioGroup v-model="preliminaryInputSource" class="ai-panel__radios">
              <ElRadio value="current_record">当前病历</ElRadio>
              <ElRadio value="natural_language">患者自然语言</ElRadio>
            </ElRadioGroup>
          </div>

          <ElInput
            v-if="preliminaryInputSource === 'natural_language'"
            v-model="naturalLanguageText"
            type="textarea"
            :rows="3"
            placeholder="粘贴患者口述或语音转写……"
            class="ai-panel__long-text"
          />

          <div class="ai-panel__toolbar">
            <ElButton class="ai-panel__model-btn" @click="modelDrawerVisible = true">
              {{ selectedAiModelLabel }}
            </ElButton>
            <div class="ai-panel__toolbar-btns">
              <ElButton
                type="primary"
                class="ai-panel__generate-btn"
                :loading="preliminaryLoading"
                @click="generatePreliminaryDiagnosis"
              >
                <ElIcon><MagicStick /></ElIcon>
                生成初步诊断
              </ElButton>
              <ElButton
                class="ai-panel__save-btn"
                :loading="preliminaryLoading"
                @click="savePreliminaryDiagnosis"
              >
                保存初步诊断
              </ElButton>
            </div>
          </div>
        </div>

        <PreliminaryModelDrawer v-model:visible="modelDrawerVisible" v-model:model="selectedAiModel" />

        <div class="ai-panel__body">
          <PreliminaryDiagnosisPanel
            v-model:doctor-diagnosis="preliminaryForm.doctorDiagnosis"
            v-model:ai-reasoning-text="preliminaryForm.aiReasoningText"
            :ai-meta="aiMeta"
            :has-ai-result="hasPreliminaryAiResult"
          />
        </div>
      </section>
    </div>
  </PhysicianStepLayout>
</template>

<style scoped>
.record-page {
  --record-sticky-top: calc(var(--header-height) + var(--space-6));
  --record-panel-max-height: calc(100vh - var(--record-sticky-top) - 120px);
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: var(--space-5);
  align-items: start;
}

.record-card {
  padding: var(--space-5) var(--space-6);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: #fff;
  box-shadow: 0 2px 12px rgba(31, 73, 125, 0.06);
}

.patient-record {
  position: sticky;
  top: var(--record-sticky-top);
  align-self: start;
  max-height: var(--record-panel-max-height);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.ai-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.ai-panel--has-results {
  max-height: var(--record-panel-max-height);
}

.ai-panel__controls {
  flex-shrink: 0;
}

.ai-panel__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.patient-record__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-block-end: var(--space-5);
  padding-block-end: var(--space-5);
  border-block-end: 1px solid #e8edf3;
}

.patient-record__intro {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  min-width: 0;
}

.patient-record__logo {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  inline-size: 48px;
  block-size: 48px;
  border-radius: 12px;
  background: #e8f8ef;
  color: #52c41a;
}

.patient-record__title {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-text);
}

.patient-record__subtitle {
  margin: var(--space-1) 0 0;
  font-size: 0.8125rem;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.patient-record__save {
  flex-shrink: 0;
  padding-inline: var(--space-5);
  border-radius: 8px;
  background: #2f73f6;
  border-color: #2f73f6;
  box-shadow: none;
}

.patient-record__save:hover,
.patient-record__save:focus {
  background: #2563eb;
  border-color: #2563eb;
}

.patient-record__save-icon {
  margin-inline-end: var(--space-1);
}

.patient-record__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5) var(--space-6);
}

.patient-record__field--full {
  grid-column: 1 / -1;
}

.patient-record__label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-block-end: var(--space-2);
  font-size: 0.875rem;
  font-weight: 600;
  color: #2f73f6;
  cursor: default;
}

.patient-record__label-icon {
  flex-shrink: 0;
}

.patient-record__input :deep(.el-textarea__inner) {
  padding: var(--space-3) var(--space-4);
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  font-size: 0.875rem;
  line-height: 1.65;
  color: var(--color-text);
  box-shadow: none;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.patient-record__input :deep(.el-textarea__inner::placeholder) {
  color: #a0aec0;
}

.patient-record__input :deep(.el-textarea__inner:hover) {
  border-color: #cbd5e1;
}

.patient-record__input :deep(.el-textarea__inner:focus) {
  border-color: #2f73f6;
  box-shadow: 0 0 0 3px rgba(47, 115, 246, 0.12);
}

.ai-panel__header {
  margin-block-end: var(--space-4);
}

.ai-panel__intro {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.ai-panel__logo {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  inline-size: 44px;
  block-size: 44px;
  border-radius: 12px;
  background: #eef4ff;
  color: #2f73f6;
}

.ai-panel__title {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--color-text);
}

.ai-panel__alert {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: 8px;
  font-size: 0.8125rem;
  line-height: 1.55;
  color: #b45309;
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.ai-panel__alert-icon {
  flex-shrink: 0;
  margin-block-start: 2px;
  color: #f59e0b;
}

.ai-panel__source {
  margin-block-end: var(--space-4);
}

.ai-panel__radios {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
}

.ai-panel__radios :deep(.el-radio__label) {
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

.ai-panel__radios :deep(.el-radio__input.is-checked + .el-radio__label) {
  color: #2f73f6;
  font-weight: 600;
}

.ai-panel__long-text {
  margin-block-end: var(--space-4);
}

.ai-panel__long-text :deep(.el-textarea__inner) {
  border-radius: 8px;
  border-color: #e2e8f0;
}

.ai-panel__toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-block-end: 0;
  padding-block-end: var(--space-4);
  border-block-end: 1px solid #e8edf3;
}

.ai-panel__model-btn {
  width: 100%;
  justify-content: flex-start;
  border-radius: 8px;
  border-color: #e2e8f0;
  color: var(--color-text);
  font-size: 0.8125rem;
}

.ai-panel__toolbar-btns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.ai-panel__toolbar-btns :deep(.el-button) {
  width: 100%;
  margin: 0;
}

.ai-panel__generate-btn {
  border-radius: 8px;
  background: #2f73f6;
  border-color: #2f73f6;
  box-shadow: none;
}

.ai-panel__generate-btn:hover,
.ai-panel__generate-btn:focus {
  background: #2563eb;
  border-color: #2563eb;
}

.ai-panel__save-btn {
  border-radius: 8px;
  border-color: #2f73f6;
  color: #2f73f6;
  background: #fff;
}

.ai-panel__save-btn:hover,
.ai-panel__save-btn:focus {
  color: #2563eb;
  border-color: #2563eb;
  background: #f0f7ff;
}

.ai-panel__tags {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

@media (max-width: 1100px) {
  .record-page {
    grid-template-columns: 1fr;
  }

  .patient-record {
    position: static;
    max-height: none;
    overflow: visible;
  }

  .ai-panel--has-results {
    max-height: min(72vh, 720px);
  }
}

@media (max-width: 900px) {
  .patient-record__grid {
    grid-template-columns: 1fr;
  }

  .patient-record__field--full {
    grid-column: auto;
  }
}
</style>
