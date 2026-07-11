<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus'
import { CircleCheck, DocumentChecked, EditPen, Select, VideoPlay } from '@element-plus/icons-vue'
import {
  physicianApi,
  type Disease,
  type W3Status,
  type W4FallbackSuggestion,
  type W4Output,
  type W4Suggestion,
} from '@/shared/api/modules/physician'
import { mapDiagnosisSuggestionsToW4Output, suggestionDisplayName } from '@/shared/types/w4Result'
import { useEncounterStore } from '@/app/stores/encounter'
import DiseaseSearchSelect from '../components/DiseaseSearchSelect.vue'
import W4DiagnosisPanel from '../components/W4DiagnosisPanel.vue'
import { physicianRoute } from '../constants/visitState'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'

const CURE_MAX = 500
const CAREFUL_MAX = 300

interface DiagnosisSnapshot {
  diagnosis: string
  icdCode: string
  cure: string
  careful: string
  primaryDiseaseId?: number
  differentialDiseaseIds: number[]
}

type LeaveAction = 'stay' | 'discard' | 'draft' | 'submit'

const router = useRouter()
const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const loading = ref(false)
const leaveDialogVisible = ref(false)
let leaveResolver: ((proceed: boolean) => void) | null = null
const medicalRecordId = ref<number | undefined>()
const primaryDisease = ref<Disease | null>(null)
const differentialDiseases = ref<Disease[]>([])
const w3Status = ref<W3Status | null>(null)
const w4Output = ref<W4Output | null>(null)
const historySuggestions = ref<W4Suggestion[]>([])
const showW3Detail = ref(false)

const diagnosisForm = reactive({
  diagnosis: '',
  icdCode: '',
  cure: '',
  careful: '',
  primaryDiseaseId: undefined as number | undefined,
  differentialDiseaseIds: [] as number[],
})

const savedSnapshot = ref<DiagnosisSnapshot>({
  diagnosis: '',
  icdCode: '',
  cure: '',
  careful: '',
  primaryDiseaseId: undefined,
  differentialDiseaseIds: [],
})

function sortedDiseaseIds(ids: number[]) {
  return [...ids].sort((a, b) => a - b)
}

function syncSavedSnapshot() {
  savedSnapshot.value = {
    diagnosis: diagnosisForm.diagnosis,
    icdCode: diagnosisForm.icdCode,
    cure: diagnosisForm.cure,
    careful: diagnosisForm.careful,
    primaryDiseaseId: diagnosisForm.primaryDiseaseId,
    differentialDiseaseIds: sortedDiseaseIds(diagnosisForm.differentialDiseaseIds),
  }
}

const diagnosisDirty = computed(() => {
  const saved = savedSnapshot.value
  return (
    diagnosisForm.diagnosis !== saved.diagnosis
    || diagnosisForm.icdCode !== saved.icdCode
    || diagnosisForm.cure !== saved.cure
    || diagnosisForm.careful !== saved.careful
    || diagnosisForm.primaryDiseaseId !== saved.primaryDiseaseId
    || sortedDiseaseIds(diagnosisForm.differentialDiseaseIds).join(',')
      !== saved.differentialDiseaseIds.join(',')
  )
})

const hasDiagnosisDraftContent = computed(
  () =>
    Boolean(diagnosisForm.diagnosis.trim())
    || Boolean(diagnosisForm.cure.trim())
    || Boolean(diagnosisForm.careful.trim())
    || Boolean(diagnosisForm.primaryDiseaseId)
    || diagnosisForm.differentialDiseaseIds.length > 0,
)

const w3Completed = computed(() => Boolean(w3Status.value?.completed))
const w3ClinicalImpression = computed(
  () => w3Status.value?.clinicalImpression || w3Status.value?.w3Output?.clinicalImpression || '',
)
const w3OverallAnalysis = computed(
  () => w3Status.value?.overallAnalysis || w3Status.value?.w3Output?.overallAnalysis || '',
)
const hasW3Summary = computed(() => Boolean(w3ClinicalImpression.value || w3OverallAnalysis.value))
const hasPrimarySelection = computed(() => Boolean(diagnosisForm.primaryDiseaseId && diagnosisForm.diagnosis.trim()))

const primaryDiseaseIds = computed({
  get: () => (diagnosisForm.primaryDiseaseId ? [diagnosisForm.primaryDiseaseId] : []),
  set: (ids: number[]) => {
    diagnosisForm.primaryDiseaseId = ids[0]
  },
})

function resetDiagnosisForm() {
  diagnosisForm.diagnosis = ''
  diagnosisForm.icdCode = ''
  diagnosisForm.cure = ''
  diagnosisForm.careful = ''
  diagnosisForm.primaryDiseaseId = undefined
  diagnosisForm.differentialDiseaseIds = []
  primaryDisease.value = null
  differentialDiseases.value = []
  syncSavedSnapshot()
}

function buildDiagnosisPayload() {
  const diseaseIds = [
    ...(diagnosisForm.primaryDiseaseId ? [diagnosisForm.primaryDiseaseId] : []),
    ...diagnosisForm.differentialDiseaseIds,
  ]
  return {
    registerId: registerId.value,
    medicalRecordId: medicalRecordId.value,
    diagnosis: diagnosisForm.diagnosis,
    cure: diagnosisForm.cure,
    careful: diagnosisForm.careful,
    diseaseIds,
  }
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
    const diseases = record.diseases ?? []
    if (diseases.length) {
      primaryDisease.value = diseases[0]
      differentialDiseases.value = diseases.slice(1)
      diagnosisForm.primaryDiseaseId = diseases[0].id
      diagnosisForm.icdCode = diseases[0].diseaseIcd || ''
      diagnosisForm.differentialDiseaseIds = diseases.slice(1).map((item) => item.id)
    } else {
      primaryDisease.value = null
      differentialDiseases.value = []
      diagnosisForm.primaryDiseaseId = undefined
      diagnosisForm.icdCode = ''
      diagnosisForm.differentialDiseaseIds = []
    }
    syncSavedSnapshot()
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

async function loadW4OutputFromServer(id: number) {
  try {
    const status = await physicianApi.w4Status(id)
    if (status.completed && status.w4Output) {
      w4Output.value = status.w4Output
      historySuggestions.value = status.w4Output.suggestions ?? []
      return
    }
  } catch {
    // 兼容尚未部署 w4/status 的后端
  }
  await loadHistorySuggestions()
  w4Output.value = mapDiagnosisSuggestionsToW4Output(historySuggestions.value)
}

function onPrimarySelect(diseases: Disease[]) {
  const selected = diseases[0] ?? null
  primaryDisease.value = selected
  diagnosisForm.primaryDiseaseId = selected?.id
  if (selected) {
    diagnosisForm.diagnosis = selected.diseaseName
    diagnosisForm.icdCode = selected.diseaseIcd || ''
  }
}

function onDifferentialSelect(diseases: Disease[]) {
  differentialDiseases.value = diseases
  diagnosisForm.differentialDiseaseIds = diseases.map((item) => item.id)
}

function diseaseFromSuggestion(item: W4Suggestion | W4FallbackSuggestion): Disease | null {
  const suggestion = item as W4Suggestion
  const diseaseId = Number(suggestion.diseaseId)
  if (!diseaseId) return null
  return {
    id: diseaseId,
    diseaseName: suggestionDisplayName(suggestion),
    diseaseIcd: suggestion.recommendIcd,
  }
}

function adoptAsPrimary(item: W4Suggestion | W4FallbackSuggestion) {
  diagnosisForm.diagnosis = suggestionDisplayName(item)
  const treatment = (item as W4Suggestion).treatmentDirection
  if (treatment) {
    diagnosisForm.cure = treatment
  }
  const icd = (item as W4Suggestion).recommendIcd || (item as W4FallbackSuggestion).estimatedIcdPrefix
  if (icd) {
    diagnosisForm.icdCode = icd
  }
  const disease = diseaseFromSuggestion(item)
  if (disease) {
    primaryDisease.value = disease
    diagnosisForm.primaryDiseaseId = disease.id
  }
  ElMessage.success('已采纳为主诊断，请核对后保存')
}

function addToDifferential(item: W4Suggestion | W4FallbackSuggestion) {
  const disease = diseaseFromSuggestion(item)
  if (!disease) {
    ElMessage.warning('该建议未关联疾病库条目，请手动搜索添加')
    return
  }
  if (diagnosisForm.primaryDiseaseId === disease.id) {
    ElMessage.warning('该诊断已是主诊断')
    return
  }
  if (diagnosisForm.differentialDiseaseIds.includes(disease.id)) {
    ElMessage.info('该诊断已在鉴别诊断中')
    return
  }
  differentialDiseases.value = [...differentialDiseases.value, disease]
  diagnosisForm.differentialDiseaseIds = [...diagnosisForm.differentialDiseaseIds, disease.id]
  ElMessage.success('已加入鉴别诊断')
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
      historySuggestions.value = w4Output.value?.suggestions ?? []
    }
    ElMessage.success('W4 诊断建议已生成')
  } finally {
    loading.value = false
  }
}

async function persistDiagnosis(): Promise<boolean> {
  if (!registerId.value) return false
  if (!medicalRecordId.value) {
    ElMessage.warning('请先完成病历书写后再保存确诊')
    return false
  }
  if (!diagnosisForm.diagnosis.trim()) {
    ElMessage.warning('请填写确诊病名')
    return false
  }
  const payload = buildDiagnosisPayload()
  if (!payload.diseaseIds.length) {
    ElMessage.warning('建议从疾病库搜索并选择对应疾病，以便规范编码')
  }
  loading.value = true
  try {
    await physicianApi.submitDiagnosis(payload)
    syncSavedSnapshot()
    return true
  } catch (err) {
    const msg = err instanceof Error ? err.message : '保存确诊失败'
    ElMessage.error(msg)
    return false
  } finally {
    loading.value = false
  }
}

async function persistDiagnosisDraft(): Promise<boolean> {
  if (!registerId.value) return false
  if (!medicalRecordId.value) {
    ElMessage.warning('请先完成病历书写后再保存草稿')
    return false
  }
  if (!hasDiagnosisDraftContent.value) {
    ElMessage.warning('请至少填写一项确诊信息后再保存草稿')
    return false
  }
  loading.value = true
  try {
    await physicianApi.saveDiagnosisDraft(buildDiagnosisPayload())
    syncSavedSnapshot()
    return true
  } catch (err) {
    const msg = err instanceof Error ? err.message : '保存草稿失败'
    ElMessage.error(msg)
    return false
  } finally {
    loading.value = false
  }
}

async function promptNextStepAfterSave() {
  try {
    await ElMessageBox.confirm(
      '确诊已保存。下一步可前往「处方开立」为患者开具处方。',
      '保存成功',
      {
        confirmButtonText: '前往处方开立',
        cancelButtonText: '留在当前页',
        distinguishCancelAndClose: true,
        type: 'success',
      },
    )
    await router.push(physicianRoute('/physician/prescription', registerId.value))
  } catch {
    ElMessage.success('确诊已保存')
  }
}

async function submitDiagnosis() {
  const saved = await persistDiagnosis()
  if (saved) {
    await loadMedicalRecord()
    await promptNextStepAfterSave()
  }
}

async function saveDiagnosisDraft() {
  const saved = await persistDiagnosisDraft()
  if (saved) {
    ElMessage.success('确诊草稿已保存')
  }
}

function confirmLeaveBeforeNavigate(): Promise<boolean> {
  if (!diagnosisDirty.value) return Promise.resolve(true)
  leaveDialogVisible.value = true
  return new Promise((resolve) => {
    leaveResolver = resolve
  })
}

function onLeaveDialogClosed() {
  if (leaveResolver) {
    leaveResolver(false)
    leaveResolver = null
  }
}

async function resolveLeave(action: LeaveAction) {
  leaveDialogVisible.value = false
  if (action === 'stay') {
    leaveResolver?.(false)
    leaveResolver = null
    return
  }
  if (action === 'discard') {
    leaveResolver?.(true)
    leaveResolver = null
    return
  }
  const saved = action === 'draft' ? await persistDiagnosisDraft() : await persistDiagnosis()
  if (saved) {
    ElMessage.success(action === 'draft' ? '确诊草稿已保存' : '确诊已保存')
  }
  leaveResolver?.(saved)
  leaveResolver = null
}

onBeforeRouteLeave((_to, _from, next) => {
  void confirmLeaveBeforeNavigate().then((proceed) => {
    next(proceed)
  })
})

watch(registerId, (id) => {
  w4Output.value = null
  historySuggestions.value = []
  showW3Detail.value = false
  void loadMedicalRecord()
  void loadW3Status()
  if (id) void loadW4OutputFromServer(id)
})

onMounted(() => {
  void loadMedicalRecord()
  void loadW3Status()
  if (registerId.value) {
    void loadW4OutputFromServer(registerId.value)
  }
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="门诊确诊"
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

    <header v-if="w3Completed && hasW3Summary" class="w3-top-bar">
      <div class="w3-top-bar__content">
        <div class="w3-top-bar__icon" aria-hidden="true">
          <ElIcon :size="22"><DocumentChecked /></ElIcon>
        </div>
        <div class="w3-top-bar__text">
          <strong class="w3-top-bar__title">检查检验 结果解读摘要</strong>
          <p class="w3-top-bar__impression">{{ w3ClinicalImpression || w3OverallAnalysis }}</p>
          <button
            v-if="w3OverallAnalysis && w3OverallAnalysis !== w3ClinicalImpression"
            type="button"
            class="w3-top-bar__link"
            @click="showW3Detail = !showW3Detail"
          >
            {{ showW3Detail ? '收起综合解读' : '查看综合解读' }} ›
          </button>
          <p v-if="showW3Detail" class="w3-top-bar__detail">{{ w3OverallAnalysis }}</p>
        </div>
      </div>
      <div class="w3-top-bar__actions">
        <ElButton :loading="loading" @click="runW4" type="success">
          <ElIcon><VideoPlay /></ElIcon>
          运行 AI 诊断建议工作流
        </ElButton>
        <ElButton :loading="loading" @click="saveDiagnosisDraft">
          <ElIcon><EditPen /></ElIcon>
          保存草稿
        </ElButton>
        <ElButton type="primary" :loading="loading" @click="submitDiagnosis">
          <ElIcon><Select /></ElIcon>
          保存确诊
        </ElButton>
      </div>
    </header>

    <header v-else class="w3-top-bar w3-top-bar--actions-only">
      <div class="w3-top-bar__actions">
        <ElButton :loading="loading" @click="runW4">
          <ElIcon><VideoPlay /></ElIcon>
          运行 AI 诊断建议工作流
        </ElButton>
        <ElButton :loading="loading" @click="saveDiagnosisDraft">
          <ElIcon><EditPen /></ElIcon>
          保存草稿
        </ElButton>
        <ElButton type="primary" :loading="loading" @click="submitDiagnosis">
          <ElIcon><Select /></ElIcon>
          保存确诊
        </ElButton>
      </div>
    </header>

    <W4DiagnosisPanel
      :live-output="w4Output"
      :saved-suggestions="historySuggestions"
      @adopt-primary="adoptAsPrimary"
      @adopt-differential="addToDifferential"
    />

    <section class="doctor-confirm">
      <div class="doctor-confirm__head">
        <div class="doctor-confirm__title-row">
          <h3 class="doctor-confirm__title">医生确认</h3>
          <ElTag v-if="diagnosisDirty" type="warning" effect="plain" size="small">有未保存修改</ElTag>
        </div>
        <p class="doctor-confirm__subtitle">请确认最终诊断并完善诊疗信息。可先保存草稿，稍后再正式保存确诊。</p>
      </div>
      <ElForm label-position="top" class="diagnosis-form">
        <div class="diagnosis-form__col">
          <ElFormItem label="最终确诊" required>
            <div class="diagnosis-form__select-wrap" :class="{ 'is-confirmed': hasPrimarySelection }">
              <DiseaseSearchSelect
                v-model="primaryDiseaseIds"
                :multiple="false"
                :initial-diseases="primaryDisease ? [primaryDisease] : []"
                placeholder="搜索并选择最终确诊疾病"
                @select="onPrimarySelect"
              />
              <ElIcon v-if="hasPrimarySelection" class="diagnosis-form__check"><CircleCheck /></ElIcon>
            </div>
          </ElFormItem>
          <ElFormItem label="ICD 编码" required>
            <ElInput v-model="diagnosisForm.icdCode" placeholder="选择疾病后自动填入，也可手动修改" />
          </ElFormItem>
          <ElFormItem label="确诊病名" required>
            <ElInput
              v-model="diagnosisForm.diagnosis"
              placeholder="可从上方疾病库选择后自动填入，也可手动修改"
            />
          </ElFormItem>
        </div>
        <div class="diagnosis-form__col">
          <ElFormItem label="鉴别诊断（可选）">
            <DiseaseSearchSelect
              v-model="diagnosisForm.differentialDiseaseIds"
              :initial-diseases="differentialDiseases"
              placeholder="搜索并添加鉴别诊断"
              @select="onDifferentialSelect"
            />
          </ElFormItem>
          <ElFormItem label="治疗方向" required>
            <ElInput
              v-model="diagnosisForm.cure"
              type="textarea"
              :rows="4"
              :maxlength="CURE_MAX"
              show-word-limit
              placeholder="填写治疗方向与用药建议"
            />
          </ElFormItem>
          <ElFormItem label="注意事项（可选）">
            <ElInput
              v-model="diagnosisForm.careful"
              type="textarea"
              :rows="3"
              :maxlength="CAREFUL_MAX"
              show-word-limit
              placeholder="填写随访、禁忌或患者告知事项"
            />
          </ElFormItem>
        </div>
      </ElForm>
      <p class="doctor-confirm__footer">AI 建议仅供参考，最终诊断由医生确认并保存。</p>
      <div class="doctor-confirm__actions">
        <ElButton :loading="loading" @click="saveDiagnosisDraft">
          <ElIcon><EditPen /></ElIcon>
          保存草稿
        </ElButton>
        <ElButton type="primary" :loading="loading" @click="submitDiagnosis">
          <ElIcon><Select /></ElIcon>
          保存确诊
        </ElButton>
      </div>
    </section>
  </PhysicianStepLayout>

  <ElDialog
    v-model="leaveDialogVisible"
    title="未保存的确诊信息"
    width="480px"
    align-center
    :close-on-click-modal="false"
    @closed="onLeaveDialogClosed"
  >
    <p class="leave-dialog__text">您有未保存的修改，离开前请选择处理方式。</p>
    <template #footer>
      <div class="leave-dialog__footer">
        <ElButton @click="resolveLeave('stay')">继续编辑</ElButton>
        <ElButton @click="resolveLeave('discard')">不保存</ElButton>
        <ElButton type="warning" :loading="loading" @click="resolveLeave('draft')">保存草稿</ElButton>
        <ElButton type="primary" :loading="loading" @click="resolveLeave('submit')">保存确诊</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.diagnosis-alert {
  margin-block-end: var(--space-4);
}

.w3-top-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-block-end: var(--space-5);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
}

.w3-top-bar--actions-only {
  justify-content: flex-end;
}

.w3-top-bar__content {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  flex: 1;
  min-width: 0;
}

.w3-top-bar__icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}

.w3-top-bar__title {
  display: block;
  font-size: 15px;
}

.w3-top-bar__impression {
  margin: var(--space-2) 0 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.7;
}

.w3-top-bar__link {
  margin-block-start: var(--space-2);
  padding: 0;
  border: none;
  color: var(--color-primary-strong);
  font-size: 13px;
  background: none;
  cursor: pointer;
}

.w3-top-bar__link:hover {
  text-decoration: underline;
}

.w3-top-bar__detail {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.8;
}

.w3-top-bar__actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.doctor-confirm {
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.95);
  box-shadow: var(--shadow-sm);
}

.doctor-confirm__head {
  margin-block-end: var(--space-4);
}

.doctor-confirm__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.doctor-confirm__title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.doctor-confirm__subtitle {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.diagnosis-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5);
}

.diagnosis-form__select-wrap {
  position: relative;
  width: 100%;
}

.diagnosis-form__select-wrap.is-confirmed :deep(.el-input__wrapper) {
  padding-inline-end: 36px;
  box-shadow: inset 0 0 0 1px rgba(32, 180, 134, 0.35);
}

.diagnosis-form__check {
  position: absolute;
  inset-block-start: 50%;
  inset-inline-end: 12px;
  transform: translateY(-50%);
  color: var(--color-success);
  pointer-events: none;
}

.doctor-confirm__footer {
  margin: var(--space-5) 0 0;
  color: var(--color-ai);
  font-size: 13px;
  text-align: center;
}

.doctor-confirm__actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
}

.leave-dialog__text {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.leave-dialog__footer {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-2);
}

@media (max-width: 900px) {
  .w3-top-bar {
    flex-direction: column;
  }

  .w3-top-bar__actions {
    width: 100%;
    justify-content: flex-end;
  }

  .diagnosis-form {
    grid-template-columns: 1fr;
  }
}
</style>
