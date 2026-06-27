<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElCard, ElCollapse, ElCollapseItem, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect, ElTag } from 'element-plus'
import { physicianApi, type Disease, type W3Status, type W4FallbackSuggestion, type W4Output, type W4Suggestion } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const loading = ref(false)
const diseases = ref<Disease[]>([])
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
const isFallbackStatus = computed(() => w4Output.value?.status === 'fallback')
const displaySuggestions = computed(() => {
  if (w4Output.value?.suggestions?.length) {
    return w4Output.value.suggestions
  }
  return historySuggestions.value
})
const hasW4Content = computed(() => Boolean(
  w4Output.value
  || displaySuggestions.value.length
  || historySuggestions.value.length,
))

function suggestionName(item: W4Suggestion | W4FallbackSuggestion) {
  return item.diagnosisName || (item as W4Suggestion).diseaseName || '-'
}

function formatProbability(value?: number) {
  if (value == null || Number.isNaN(value)) return '-'
  const num = value <= 1 ? value * 100 : value
  return `${Math.round(num * 10) / 10}%`
}

async function loadDiseases() {
  diseases.value = await physicianApi.diseases()
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

function adoptSuggestion(item: W4Suggestion | W4FallbackSuggestion) {
  diagnosisForm.diagnosis = suggestionName(item)
  const treatment = (item as W4Suggestion).treatmentDirection
  if (treatment) {
    diagnosisForm.cure = treatment
  }
  const diseaseId = Number((item as W4Suggestion).diseaseId)
  if (diseaseId && !diagnosisForm.diseaseIds.includes(diseaseId)) {
    diagnosisForm.diseaseIds.push(diseaseId)
  }
}

async function runW4() {
  if (!registerId.value) return
  if (!w3Completed.value) {
    ElMessage.warning('建议先在「查看结果」完成 W3 结果解读，再运行 W4 以获得更准确的诊断建议')
  }
  loading.value = true
  try {
    w4Output.value = await physicianApi.aiW4(registerId.value)
    const first = w4Output.value?.suggestions?.[0]
    if (first && w4Output.value?.status !== 'fallback') {
      adoptSuggestion(first)
    }
    if (w4Output.value?.status !== 'fallback') {
      await loadHistorySuggestions()
    }
    ElMessage.success('W4 诊断建议已生成')
  } finally {
    loading.value = false
  }
}

async function submitDiagnosis() {
  if (!registerId.value) return
  const payload = {
    registerId: registerId.value,
    diagnosis: diagnosisForm.diagnosis,
    cure: diagnosisForm.cure,
    careful: diagnosisForm.careful,
    diseaseIds: diagnosisForm.diseaseIds,
  }
  await physicianApi.submitDiagnosis(payload)
  ElMessage.success('确诊已保存')
}

watch(registerId, () => {
  w4Output.value = null
  void loadW3Status()
  void loadHistorySuggestions()
})

onMounted(() => {
  void loadDiseases()
  void loadW3Status()
  void loadHistorySuggestions()
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="门诊确诊"
    description="录入确诊信息并运行 W4 获取疾病诊断建议。W4 会综合病历与 W3 结果解读，最终诊断仍由医生确认。"
    prev-path="/physician/results"
    next-path="/physician/prescription"
  >
    <ElAlert
      v-if="!w3Completed"
      class="diagnosis-alert"
      type="warning"
      :closable="false"
      show-icon
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

    <ElForm label-position="top" class="diagnosis-form">
      <ElFormItem label="确诊病名">
        <ElInput v-model="diagnosisForm.diagnosis" placeholder="例如：急性上呼吸道感染" />
      </ElFormItem>
      <ElFormItem label="治疗方向">
        <ElInput v-model="diagnosisForm.cure" type="textarea" :rows="2" />
      </ElFormItem>
      <ElFormItem label="注意事项">
        <ElInput v-model="diagnosisForm.careful" type="textarea" :rows="2" />
      </ElFormItem>
      <ElFormItem label="疾病编码">
        <ElSelect v-model="diagnosisForm.diseaseIds" multiple filterable placeholder="选择疾病">
          <ElOption v-for="item in diseases" :key="item.id" :label="`${item.diseaseName} ${item.diseaseIcd || ''}`" :value="item.id" />
        </ElSelect>
      </ElFormItem>
    </ElForm>

    <h3 class="w4-section-title">W4 诊断建议</h3>

    <ElAlert
      v-if="isFallbackStatus"
      class="w4-fallback-alert"
      type="warning"
      :closable="false"
      show-icon
      title="疾病库未匹配到候选，以下为 AI 兜底建议"
      :description="w4Output?.searchAdvice || '请手动搜索疾病库并确认诊断。'"
    />

    <div v-if="w4Output?.clinicalSummaryForDoctor" class="w4-summary-block">
      <strong>临床摘要</strong>
      <p>{{ w4Output.clinicalSummaryForDoctor }}</p>
    </div>

    <div v-if="w4Output?.warningSigns?.length" class="w4-summary-block">
      <strong>警示征象</strong>
      <ul class="w4-list">
        <li v-for="(sign, idx) in w4Output.warningSigns" :key="`warn-${idx}`">{{ sign }}</li>
      </ul>
    </div>

    <div v-if="w4Output?.differentialDiagnosis?.length" class="w4-grid">
      <ElCard v-for="(item, idx) in w4Output.differentialDiagnosis" :key="`ddx-${idx}`" class="mini-card">
        <strong>鉴别诊断</strong>
        <p>{{ item.diagnosisName || '-' }}</p>
        <p v-if="item.reason">理由：{{ item.reason }}</p>
      </ElCard>
    </div>

    <div v-if="isFallbackStatus && w4Output?.fallbackSuggestions?.length" class="w4-grid">
      <ElCard v-for="(item, idx) in w4Output.fallbackSuggestions" :key="`fb-${idx}`" class="mini-card mini-card--fallback">
        <div class="mini-card__header">
          <strong>{{ suggestionName(item) }}</strong>
          <ElTag size="small" type="warning">兜底</ElTag>
        </div>
        <p v-if="item.estimatedIcdPrefix">ICD 前缀：{{ item.estimatedIcdPrefix }}</p>
        <p>概率：{{ formatProbability(item.probability) }}</p>
        <p v-if="item.diagnosisBasis">依据：{{ item.diagnosisBasis }}</p>
        <p v-if="item.note">{{ item.note }}</p>
        <ElButton size="small" @click="adoptSuggestion(item)">采纳</ElButton>
      </ElCard>
    </div>

    <div v-else-if="displaySuggestions.length" class="w4-grid">
      <ElCard v-for="(item, idx) in displaySuggestions" :key="`sug-${item.id ?? idx}`" class="mini-card">
        <div class="mini-card__header">
          <strong>{{ suggestionName(item) }}</strong>
          <ElTag v-if="item.riskLevel" size="small">{{ item.riskLevel }}</ElTag>
        </div>
        <p v-if="item.recommendIcd">ICD：{{ item.recommendIcd }}</p>
        <p>概率：{{ formatProbability(item.probability) }}</p>
        <p v-if="item.diagnosisBasis">依据：{{ item.diagnosisBasis }}</p>
        <p v-if="item.treatmentDirection">治疗方向：{{ item.treatmentDirection }}</p>
        <ElButton size="small" @click="adoptSuggestion(item)">采纳</ElButton>
      </ElCard>
    </div>

    <div v-else-if="!hasW4Content">
      <p class="w4-empty">暂无 W4 输出，可运行 W4 获取疾病诊断建议。</p>
    </div>
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

.diagnosis-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.w4-section-title {
  margin-top: var(--space-4);
}

.w4-fallback-alert {
  margin-block-end: var(--space-4);
}

.w4-summary-block {
  margin-block-end: var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(247, 251, 255, 0.6);
}

.w4-summary-block p,
.w4-list {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.w4-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.w4-empty {
  color: var(--color-text-muted);
}

.mini-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.mini-card p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.8;
}

.mini-card--fallback {
  border-color: rgba(230, 162, 60, 0.35);
}
</style>
