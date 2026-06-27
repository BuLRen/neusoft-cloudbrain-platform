<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElCard, ElCollapse, ElCollapseItem, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import {
  physicianApi,
  type Disease,
  type W3Status,
  type W4FallbackSuggestion,
  type W4Output,
  type W4Suggestion,
} from '@/shared/api/modules/physician'
import { suggestionDisplayName } from '@/shared/types/w4Result'
import { useEncounterStore } from '@/app/stores/encounter'
import DiseaseSearchSelect from '../components/DiseaseSearchSelect.vue'
import W4DiagnosisPanel from '../components/W4DiagnosisPanel.vue'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const loading = ref(false)
const medicalRecordId = ref<number | undefined>()
const selectedDiseases = ref<Disease[]>([])
const w3Status = ref<W3Status | null>(null)
const w4Output = ref<W4Output | null>(null)
const historySuggestions = ref<W4Suggestion[]>([])

const diagnosisForm = reactive({
  diagnosis: '',
  cure: '',
  careful: '',
  diseaseIds: [] as number[],
})

const w3Completed = computed(() => Boolean(w3Status.value?.completed))
const w3ClinicalImpression = computed(
  () => w3Status.value?.clinicalImpression || w3Status.value?.w3Output?.clinicalImpression || '',
)
const w3OverallAnalysis = computed(
  () => w3Status.value?.overallAnalysis || w3Status.value?.w3Output?.overallAnalysis || '',
)
const hasW3Summary = computed(() => Boolean(w3ClinicalImpression.value || w3OverallAnalysis.value))

function resetDiagnosisForm() {
  diagnosisForm.diagnosis = ''
  diagnosisForm.cure = ''
  diagnosisForm.careful = ''
  diagnosisForm.diseaseIds = []
  selectedDiseases.value = []
}

async function loadMedicalRecord() {
  if (!registerId.value) {
    medicalRecordId.value = undefined
    resetDiagnosisForm()
    return
  }
  try {
    const record = await physicianApi.medicalRecord(registerId.value)
    if (!record) {
      medicalRecordId.value = undefined
      resetDiagnosisForm()
      return
    }
    medicalRecordId.value = record.id
    diagnosisForm.diagnosis = record.diagnosis || ''
    diagnosisForm.cure = record.cure || ''
    diagnosisForm.careful = record.careful || ''
    selectedDiseases.value = record.diseases ?? []
    diagnosisForm.diseaseIds = selectedDiseases.value.map((item) => item.id)
  } catch {
    medicalRecordId.value = undefined
  }
}

async function loadW3Status() {
  if (!registerId.value) {
    w3Status.value = null
    return
  }
  try {
    w3Status.value = await physicianApi.w3Status(registerId.value)
  } catch {
    w3Status.value = null
  }
}

async function loadHistorySuggestions() {
  if (!registerId.value) {
    historySuggestions.value = []
    return
  }
  try {
    historySuggestions.value = await physicianApi.diagnosisSuggestions(registerId.value)
  } catch {
    historySuggestions.value = []
  }
}

function onDiseaseSelect(diseases: Disease[]) {
  selectedDiseases.value = diseases
  if (diseases.length && !diagnosisForm.diagnosis.trim()) {
    diagnosisForm.diagnosis = diseases.map((item) => item.diseaseName).join('、')
  }
}

function adoptSuggestion(item: W4Suggestion | W4FallbackSuggestion) {
  diagnosisForm.diagnosis = suggestionDisplayName(item)
  const treatment = (item as W4Suggestion).treatmentDirection
  if (treatment) {
    diagnosisForm.cure = treatment
  }
  const diseaseId = Number((item as W4Suggestion).diseaseId)
  if (diseaseId && !diagnosisForm.diseaseIds.includes(diseaseId)) {
    diagnosisForm.diseaseIds = [...diagnosisForm.diseaseIds, diseaseId]
    const suggestion = item as W4Suggestion
    if (!selectedDiseases.value.some((d) => d.id === diseaseId)) {
      selectedDiseases.value = [
        ...selectedDiseases.value,
        {
          id: diseaseId,
          diseaseName: suggestionDisplayName(suggestion),
          diseaseIcd: suggestion.recommendIcd,
        },
      ]
    }
  }
  ElMessage.success('已填入确诊表单，请核对后保存')
}

async function runW4() {
  if (!registerId.value) return
  if (!w3Completed.value) {
    ElMessage.warning('建议先在「查看结果」完成 W3 结果解读，再运行 W4 以获得更准确的诊断建议')
  }
  loading.value = true
  try {
    w4Output.value = await physicianApi.aiW4(registerId.value)
    if (w4Output.value?.status !== 'fallback') {
      await loadHistorySuggestions()
    }
    ElMessage.success('W4 诊断建议已生成，请点击「采纳到确诊表单」确认')
  } finally {
    loading.value = false
  }
}

async function submitDiagnosis() {
  if (!registerId.value) return
  if (!medicalRecordId.value) {
    ElMessage.warning('请先完成病历书写后再保存确诊')
    return
  }
  if (!diagnosisForm.diagnosis.trim()) {
    ElMessage.warning('请填写确诊病名')
    return
  }
  if (!diagnosisForm.diseaseIds.length) {
    ElMessage.warning('建议从疾病库搜索并选择对应疾病，以便规范编码')
  }
  loading.value = true
  try {
    await physicianApi.submitDiagnosis({
      registerId: registerId.value,
      medicalRecordId: medicalRecordId.value,
      diagnosis: diagnosisForm.diagnosis,
      cure: diagnosisForm.cure,
      careful: diagnosisForm.careful,
      diseaseIds: diagnosisForm.diseaseIds,
    })
    ElMessage.success('确诊已保存')
    await loadMedicalRecord()
  } finally {
    loading.value = false
  }
}

watch(registerId, () => {
  w4Output.value = null
  void loadMedicalRecord()
  void loadW3Status()
  void loadHistorySuggestions()
})

onMounted(() => {
  void loadMedicalRecord()
  void loadW3Status()
  void loadHistorySuggestions()
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="门诊确诊"
    description="先查看 W4 AI 诊断参考，再从疾病库搜索并确认最终确诊。"
    prev-path="/physician/results"
    next-path="/physician/prescription"
  >
    <ElAlert
      v-if="!w3Completed"
      class="diagnosis-alert"
      type="warning"
      :closable="false"
      show-icon
      title="建议先完成 W3 结果解读"
      description="W4 会综合 W3 解读与检查检验结果给出诊断建议。"
    />

    <ElCard v-else-if="hasW3Summary" class="w3-summary-card" shadow="never">
      <strong class="w3-summary-card__title">W3 结果解读摘要</strong>
      <p v-if="w3ClinicalImpression" class="w3-summary-card__impression">{{ w3ClinicalImpression }}</p>
      <ElCollapse v-if="w3OverallAnalysis && w3OverallAnalysis !== w3ClinicalImpression" class="w3-summary-card__collapse">
        <ElCollapseItem title="查看综合解读" name="overall">
          <p class="w3-summary-card__text">{{ w3OverallAnalysis }}</p>
        </ElCollapseItem>
      </ElCollapse>
      <p v-else-if="w3OverallAnalysis" class="w3-summary-card__text">{{ w3OverallAnalysis }}</p>
      <p class="w3-summary-card__hint">以上为结果解读，非最终诊断。请结合临床判断运行 W4。</p>
    </ElCard>

    <div class="diagnosis-toolbar">
      <ElButton :loading="loading" @click="runW4">运行 W4（AI 诊断建议）</ElButton>
      <ElButton type="primary" :loading="loading" @click="submitDiagnosis">保存确诊</ElButton>
    </div>

    <W4DiagnosisPanel
      :live-output="w4Output"
      :saved-suggestions="historySuggestions"
      @adopt="adoptSuggestion"
    />

    <section class="doctor-confirm">
      <h3 class="doctor-confirm__title">医生确认</h3>
      <ElForm label-position="top" class="diagnosis-form">
        <ElFormItem label="确诊疾病（库内搜索）">
          <DiseaseSearchSelect
            v-model="diagnosisForm.diseaseIds"
            :initial-diseases="selectedDiseases"
            @select="onDiseaseSelect"
          />
        </ElFormItem>
        <ElFormItem label="确诊病名">
          <ElInput
            v-model="diagnosisForm.diagnosis"
            placeholder="可从上方疾病库选择后自动填入，也可手动修改"
          />
        </ElFormItem>
        <ElFormItem label="治疗方向">
          <ElInput v-model="diagnosisForm.cure" type="textarea" :rows="2" />
        </ElFormItem>
        <ElFormItem label="注意事项">
          <ElInput v-model="diagnosisForm.careful" type="textarea" :rows="2" />
        </ElFormItem>
      </ElForm>
    </section>
  </PhysicianStepLayout>
</template>

<style scoped>
.diagnosis-alert {
  margin-block-end: var(--space-4);
}

.w3-summary-card {
  margin-block-end: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 251, 255, 0.88));
}

.w3-summary-card__title {
  display: block;
  font-size: 15px;
}

.w3-summary-card__impression {
  margin: var(--space-2) 0 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.7;
}

.w3-summary-card__collapse {
  margin-block-start: var(--space-2);
  border: none;
  background: transparent;
}

.w3-summary-card__collapse :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 36px;
  border: none;
  background: transparent;
  color: var(--color-primary-strong);
  font-size: 13px;
}

.w3-summary-card__collapse :deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

.w3-summary-card__text,
.w3-summary-card__hint {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.w3-summary-card__hint {
  font-size: 13px;
  color: var(--color-ai);
}

.diagnosis-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.doctor-confirm {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
}

.doctor-confirm__title {
  margin: 0 0 var(--space-3);
  font-size: 16px;
}

.diagnosis-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.diagnosis-form :first-child {
  grid-column: 1 / -1;
}
</style>
