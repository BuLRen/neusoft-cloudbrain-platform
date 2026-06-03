<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTag,
} from 'element-plus'
import {
  physicianApi,
  type MedicalRecord,
  type PreliminaryAiMeta,
  type StructuredRecord,
} from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import GlassCard from '@/shared/components/GlassCard.vue'
import PreliminaryDiagnosisPanel from '../components/PreliminaryDiagnosisPanel.vue'
import PreliminaryModelDrawer from '../components/PreliminaryModelDrawer.vue'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'
import {
  DEFAULT_PRELIMINARY_AI_MODEL,
  findPreliminaryAiModel,
} from '../constants/preliminary-ai-models'

type PreliminaryInputSource = 'current_record' | 'pre_consultation' | 'natural_language'

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const loading = ref(false)
const preliminaryLoading = ref(false)
const structuredRecord = ref<StructuredRecord | null>(null)
const w1InputMode = ref<'pre_consultation' | 'long_text' | 'doctor_form'>('pre_consultation')
const w1LongText = ref('')

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

const hasPreliminaryAiResult = computed(
  () =>
    Boolean(aiMeta.value.aiDiagnosis) ||
    Boolean(aiMeta.value.suggestedDiseases?.length) ||
    Boolean(preliminaryForm.aiReasoningText.trim()),
)

const hasPreConsultation = computed(
  () =>
    Boolean(
      encounterStore.aiConsultSummary?.aiSummary ||
        encounterStore.aiConsultSummary?.chiefComplaint,
    ),
)

const doctorModified = computed(() => {
  if (!aiSnapshot.value.aiReasoningText && !aiSnapshot.value.doctorDiagnosis.trim()) {
    return false
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

function buildPreConsultationText(): string {
  const summary = encounterStore.aiConsultSummary
  if (!summary) return ''
  return [
    summary.chiefComplaint && `主诉：${summary.chiefComplaint}`,
    summary.aiSummary && `摘要：${summary.aiSummary}`,
  ]
    .filter(Boolean)
    .join('\n')
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
  if (preliminaryInputSource.value === 'pre_consultation') {
    const text = buildPreConsultationText()
    if (!text) {
      ElMessage.warning('当前患者暂无预问诊摘要')
      return null
    }
    return { text, preHandle: true }
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
  aiSnapshot.value = {
    aiReasoningText: preliminaryForm.aiReasoningText,
    doctorDiagnosis: preliminaryForm.doctorDiagnosis,
  }
}

function applyStructuredToRecordForm(record: StructuredRecord) {
  recordForm.readme = record.chiefComplaint || recordForm.readme
  recordForm.present = record.presentIllness || recordForm.present
  recordForm.presentTreat = record.presentTreat || recordForm.presentTreat
  recordForm.history = record.history || recordForm.history
  recordForm.allergy = record.allergy || recordForm.allergy
  recordForm.physique = record.physique || recordForm.physique
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

async function runW1() {
  if (!registerId.value) return
  const payload: Record<string, unknown> = {
    registerId: registerId.value,
    inputMode: w1InputMode.value,
    patientInfoFromRegister: encounterStore.patientSummary || undefined,
  }
  if (w1InputMode.value === 'long_text') {
    payload.longText = w1LongText.value
  } else if (w1InputMode.value === 'doctor_form') {
    payload.doctorForm = { ...recordForm }
  }
  structuredRecord.value = await physicianApi.aiW1(payload)
  applyStructuredToRecordForm(structuredRecord.value)
  ElMessage.success('W1 病历字段已结构化；如需更新初步诊断请单独生成')
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

async function saveMedicalRecord() {
  if (!registerId.value) return
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

  if (recordForm.id) {
    await physicianApi.updateMedicalRecord(recordForm.id, payload)
    ElMessage.success('病历已更新')
  } else {
    const result = await physicianApi.createMedicalRecord(payload)
    recordForm.id = result.id
    ElMessage.success('病历已创建')
  }
}

async function savePreliminaryDiagnosis() {
  if (!registerId.value) return
  const confirmed = preliminaryForm.doctorDiagnosis.trim()
  if (!confirmed) {
    ElMessage.warning('请填写医生确认的初步诊断')
    return
  }
  preliminaryLoading.value = true
  try {
    await physicianApi.savePreliminaryDiagnosis({
      registerId: registerId.value,
      preliminaryDiagnosis: confirmed,
      suggestedDiseaseNames: suggestedDiseaseNamesForSave(confirmed),
    })
    ElMessage.success('初步诊断已保存')
    await loadContext()
  } finally {
    preliminaryLoading.value = false
  }
}

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
    :step="2"
    :total-steps="6"
    title="病历与初步诊断"
    description="第二步：书写病历；在下方独立区域生成并确认 AI 初步诊断。"
    prev-path="/physician/queue"
    next-path="/physician/orders"
  >
    <div class="record-page">
      <GlassCard class="record-page__card">
        <h2 class="record-page__title">病历书写</h2>
        <div class="record-page__toolbar">
          <ElSelect v-model="w1InputMode" style="max-width: 220px">
            <ElOption label="使用预问诊" value="pre_consultation" />
            <ElOption label="患者长文本/语音转写" value="long_text" />
            <ElOption label="医生按字段填写" value="doctor_form" />
          </ElSelect>
          <ElButton :loading="loading" @click="runW1">运行 W1</ElButton>
          <ElButton type="primary" :loading="loading" @click="saveMedicalRecord">保存病历</ElButton>
        </div>

        <ElInput
          v-if="w1InputMode === 'long_text'"
          v-model="w1LongText"
          type="textarea"
          :rows="3"
          placeholder="粘贴患者口述或语音转写长文本……"
          class="record-page__long-text"
        />

        <ElForm label-position="top" class="form-grid">
          <ElFormItem label="主诉">
            <ElInput v-model="recordForm.readme" />
          </ElFormItem>
          <ElFormItem label="现病史">
            <ElInput v-model="recordForm.present" type="textarea" :rows="3" />
          </ElFormItem>
          <ElFormItem label="现病治疗情况">
            <ElInput v-model="recordForm.presentTreat" type="textarea" :rows="2" />
          </ElFormItem>
          <ElFormItem label="既往史">
            <ElInput v-model="recordForm.history" type="textarea" :rows="2" />
          </ElFormItem>
          <ElFormItem label="过敏史">
            <ElInput v-model="recordForm.allergy" />
          </ElFormItem>
          <ElFormItem label="体格检查">
            <ElInput v-model="recordForm.physique" type="textarea" :rows="2" />
          </ElFormItem>
          <ElFormItem label="检查/检验建议" class="form-grid__full">
            <ElInput v-model="recordForm.proposal" type="textarea" :rows="2" placeholder="检查或检验项目建议（非初步诊断）" />
          </ElFormItem>
        </ElForm>
      </GlassCard>

      <GlassCard class="record-page__card record-page__card--preliminary">
        <h2 class="record-page__title">AI 初步诊断</h2>
        <ElAlert
          type="warning"
          :closable="false"
          show-icon
          title="AI 初步诊断为辅助参考，非最终确诊，须医生审核后保存。"
          class="record-page__alert"
        />

        <div class="record-page__toolbar">
          <ElRadioGroup v-model="preliminaryInputSource">
            <ElRadio value="current_record">当前病历</ElRadio>
            <ElRadio value="pre_consultation" :disabled="!hasPreConsultation">预问诊摘要</ElRadio>
            <ElRadio value="natural_language">患者自然语言</ElRadio>
          </ElRadioGroup>
        </div>

        <ElInput
          v-if="preliminaryInputSource === 'natural_language'"
          v-model="naturalLanguageText"
          type="textarea"
          :rows="3"
          placeholder="粘贴患者口述或语音转写……"
          class="record-page__long-text"
        />

        <div class="record-page__toolbar record-page__toolbar--actions">
          <ElButton @click="modelDrawerVisible = true">
            模型：{{ selectedAiModelLabel }}
          </ElButton>
          <ElButton type="primary" :loading="preliminaryLoading" @click="generatePreliminaryDiagnosis">
            生成初步诊断
          </ElButton>
          <ElButton :loading="preliminaryLoading" @click="savePreliminaryDiagnosis">保存初步诊断</ElButton>
          <ElTag v-if="doctorModified" type="warning">医生已修改</ElTag>
          <ElTag v-else-if="aiMeta.aiDiagnosis" type="info">AI 生成</ElTag>
        </div>

        <PreliminaryModelDrawer v-model:visible="modelDrawerVisible" v-model:model="selectedAiModel" />

        <PreliminaryDiagnosisPanel
          v-model:doctor-diagnosis="preliminaryForm.doctorDiagnosis"
          v-model:ai-reasoning-text="preliminaryForm.aiReasoningText"
          :ai-meta="aiMeta"
          :has-ai-result="hasPreliminaryAiResult"
        />
      </GlassCard>
    </div>
  </PhysicianStepLayout>
</template>

<style scoped>
.record-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.record-page__card {
  padding: var(--space-4);
}

.record-page__title {
  margin: 0 0 var(--space-4);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.record-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  margin-block-end: var(--space-3);
}

.record-page__alert {
  margin-block-end: var(--space-4);
}

.record-page__long-text {
  margin-block-end: var(--space-3);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.form-grid__full {
  grid-column: 1 / -1;
}

.record-page__toolbar--actions {
  align-items: center;
}

.record-page__toolbar--actions :deep(.el-button--primary) {
  background: var(--el-color-primary);
  box-shadow: none;
}

.record-page__toolbar--actions :deep(.el-button:hover:not(.is-disabled)) {
  transform: none;
}
</style>
