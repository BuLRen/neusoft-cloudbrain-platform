<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElCheckbox,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import {
  Document,
  EditPen,
  MagicStick,
  Promotion,
  ShoppingCart,
  VideoPlay,
} from '@element-plus/icons-vue'
import { physicianApi, type Drug, type PrescriptionItem, type W5Output, type W5Suggestion } from '@/shared/api/modules/physician'
import { clinicalRecordApi } from '@/shared/api/modules/clinicalRecord'
import { useEncounterStore } from '@/app/stores/encounter'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'
import DrugSearchPicker from '../components/DrugSearchPicker.vue'
import W5PrescriptionPanel from '../components/W5PrescriptionPanel.vue'

interface PrescriptionDraftForm {
  drugUsage: string
  drugNumber: number
}

interface PrescriptionBasketItem extends PrescriptionDraftForm {
  drugId: number
  drugName: string
  drugPrice: number
}

const router = useRouter()
const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)
const endingVisit = ref(false)
const archiveOnFinish = ref(true)

const loading = ref(false)
const w5Loading = ref(false)
const selectedDrugId = ref<number | undefined>()
const selectedDrug = ref<Drug | null>(null)
const drugPickerRef = ref<InstanceType<typeof DrugSearchPicker> | null>(null)

const confirmedDiagnosis = ref('')
const w5Output = ref<W5Output | null>(null)
const savedW5Suggestions = ref<W5Suggestion[]>([])
const prescriptionDraft = reactive<PrescriptionDraftForm>({ drugUsage: '', drugNumber: 1 })
const prescriptionBasket = ref<PrescriptionBasketItem[]>([])
const prescriptions = ref<PrescriptionItem[]>([])

const basketDrugCount = computed(() => prescriptionBasket.value.length)
const basketTotalQuantity = computed(() =>
  prescriptionBasket.value.reduce((sum, item) => sum + item.drugNumber, 0),
)

const hasConfirmedDiagnosis = computed(() => confirmedDiagnosis.value.trim().length > 0)

function onDrugSelect(drug: Drug) {
  selectedDrug.value = drug
}

function addDrugToBasket() {
  const drug = selectedDrug.value
  if (!drug) {
    ElMessage.warning('请选择药品')
    return
  }
  if (!prescriptionDraft.drugUsage.trim()) {
    ElMessage.warning('请填写用法用量')
    return
  }
  if (prescriptionDraft.drugNumber < 1) {
    ElMessage.warning('数量至少为 1')
    return
  }
  prescriptionBasket.value.push({
    drugId: drug.id,
    drugName: drug.drugName,
    drugPrice: drug.drugPrice,
    drugUsage: prescriptionDraft.drugUsage,
    drugNumber: prescriptionDraft.drugNumber,
  })
  selectedDrug.value = null
  selectedDrugId.value = undefined
  drugPickerRef.value?.reset()
  prescriptionDraft.drugUsage = ''
  prescriptionDraft.drugNumber = 1
}

async function loadPrescriptions() {
  if (!registerId.value) return
  prescriptions.value = await physicianApi.prescriptions(registerId.value)
}

async function loadSavedW5Suggestions() {
  if (!registerId.value) {
    savedW5Suggestions.value = []
    return
  }
  try {
    savedW5Suggestions.value = await physicianApi.w5Suggestions(registerId.value)
  } catch {
    savedW5Suggestions.value = []
  }
}

async function runW5() {
  if (!registerId.value) return
  if (!hasConfirmedDiagnosis.value) {
    ElMessage.warning('请先完成门诊确诊并填写确诊病名')
    return
  }
  w5Loading.value = true
  try {
    w5Output.value = await physicianApi.aiW5(registerId.value)
    if (w5Output.value?.status === 'fallback') {
      ElMessage.warning(w5Output.value.searchAdvice || '药品库未匹配到候选，请手动搜索选药')
    } else {
      await loadSavedW5Suggestions()
      ElMessage.success('W5 用药建议已生成')
    }
  } catch {
    w5Output.value = null
  } finally {
    w5Loading.value = false
  }
}

async function adoptW5Suggestion(item: W5Suggestion) {
  if (!item.drugId) {
    ElMessage.warning('该建议无药品库 ID，请手动搜索选药')
    return
  }
  const existing = prescriptionBasket.value.find((row) => row.drugId === item.drugId)
  if (existing) {
    ElMessage.info('该药品已在处方篮中')
    return
  }
  let drugName = item.drugName || ''
  let drugPrice = 0
  let stockQuantity = 0
  try {
    const detail = await physicianApi.drug(item.drugId)
    drugName = detail.drugName
    drugPrice = detail.drugPrice
    stockQuantity = detail.stockQuantity ?? 0
  } catch {
    ElMessage.warning('无法加载药品详情，请手动搜索添加')
    return
  }
  if (stockQuantity <= 0) {
    ElMessage.warning(`「${drugName}」当前无库存，无法采纳，请换用其他药品或手动搜索`)
    return
  }
  const requestedQty = item.recommendQuantity && item.recommendQuantity > 0 ? item.recommendQuantity : 1
  const drugNumber = Math.min(requestedQty, stockQuantity)
  if (drugNumber < requestedQty) {
    ElMessage.warning(`「${drugName}」库存仅 ${stockQuantity}，已按可用数量 ${drugNumber} 加入处方篮`)
  }
  prescriptionBasket.value.push({
    drugId: item.drugId,
    drugName,
    drugPrice,
    drugUsage: item.recommendUsage || '',
    drugNumber,
  })
  if (item.id) {
    try {
      await physicianApi.adoptW5Suggestion(item.id)
    } catch {
      // non-blocking
    }
  }
  ElMessage.success(`已将「${drugName}」加入处方篮`)
}

async function loadConfirmedDiagnosis() {
  if (!registerId.value) {
    confirmedDiagnosis.value = ''
    return
  }
  try {
    const record = await physicianApi.medicalRecord(registerId.value)
    confirmedDiagnosis.value = record?.diagnosis || ''
  } catch {
    confirmedDiagnosis.value = ''
  }
}

async function maybeArchiveVisit(id: number) {
  if (!archiveOnFinish.value) return
  try {
    await clinicalRecordApi.physicianArchive(id)
    ElMessage.success('病历已归档，患者可在电子病历中查看')
  } catch (error) {
    console.warn('归档病历失败:', error)
    ElMessage.warning('看诊已结束，但病历归档失败，请稍后重试')
  }
}

function removeBasketItem(index: number) {
  prescriptionBasket.value.splice(index, 1)
}

async function submitPrescription() {
  if (!registerId.value) return
  if (!prescriptionBasket.value.length) {
    ElMessage.warning('处方篮为空，请先添加药品')
    return
  }
  if (!confirmedDiagnosis.value.trim()) {
    ElMessage.warning('请填写确诊病名后再提交处方')
    return
  }
  const invalidUsage = prescriptionBasket.value.find((item) => !item.drugUsage.trim())
  if (invalidUsage) {
    ElMessage.warning(`请为「${invalidUsage.drugName}」填写用法用量`)
    return
  }
  const currentRegisterId = registerId.value
  const payload = {
    registerId: currentRegisterId,
    confirmedDiagnosis: confirmedDiagnosis.value,
    items: prescriptionBasket.value.map((item) => ({
      drugId: item.drugId,
      drugUsage: item.drugUsage,
      drugNumber: item.drugNumber,
    })),
  }
  loading.value = true
  try {
    await physicianApi.createPrescription(payload)
    await maybeArchiveVisit(currentRegisterId)
    prescriptionBasket.value = []
    await loadPrescriptions()
    encounterStore.clearEncounter()
    ElMessage.success('处方已提交，看诊已结束')
    await router.push('/physician/queue')
  } finally {
    loading.value = false
  }
}

async function endVisitWithoutPrescription() {
  if (!registerId.value) return
  const currentRegisterId = registerId.value
  const archiveHint = archiveOnFinish.value
    ? '同时将归档并发布病历给患者。'
    : '病历暂不发布给患者。'
  try {
    await ElMessageBox.confirm(`确认结束本次看诊？${archiveHint}`, '结束看诊', { type: 'warning' })
  } catch {
    return
  }
  endingVisit.value = true
  try {
    await physicianApi.endVisit(currentRegisterId)
    await maybeArchiveVisit(currentRegisterId)
    encounterStore.clearEncounter()
    ElMessage.success('看诊已结束')
    await router.push('/physician/queue')
  } finally {
    endingVisit.value = false
  }
}

async function removePrescriptionItem(id: number) {
  loading.value = true
  try {
    await physicianApi.deletePrescription(id)
    await loadPrescriptions()
    ElMessage.success('已删除处方药品')
  } finally {
    loading.value = false
  }
}

watch(registerId, () => {
  w5Output.value = null
  void loadPrescriptions()
  void loadConfirmedDiagnosis()
  void loadSavedW5Suggestions()
})

onMounted(() => {
  void Promise.all([loadPrescriptions(), loadConfirmedDiagnosis(), loadSavedW5Suggestions()])
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="处方开立"
    prev-path="/physician/diagnosis"
  >
    <div class="rx-grid">
      <div class="rx-col rx-col--manual">
        <article class="rx-card">
          <header class="rx-card__head">
            <div class="rx-card__title-group">
              <span class="rx-card__icon rx-card__icon--primary" aria-hidden="true">
                <ElIcon :size="18"><EditPen /></ElIcon>
              </span>
              <h3 class="rx-card__title">手动处方区</h3>
            </div>
          </header>

          <ElForm class="rx-form" label-position="left" label-width="88px">
            <ElFormItem label="药品">
              <DrugSearchPicker
                ref="drugPickerRef"
                v-model="selectedDrugId"
                class="rx-form__control"
                @select="onDrugSelect"
              />
            </ElFormItem>
            <ElFormItem label="用法用量">
              <ElInput
                v-model="prescriptionDraft.drugUsage"
                class="rx-form__control"
                placeholder="例如：口服，一日三次，每次 1 片"
              />
            </ElFormItem>
            <ElFormItem label="数量">
              <ElInputNumber v-model="prescriptionDraft.drugNumber" :min="1" controls-position="right" />
            </ElFormItem>
          </ElForm>

          <div class="rx-actions">
            <ElCheckbox v-model="archiveOnFinish" class="rx-actions__checkbox">
              结束看诊时归档并发布给患者
            </ElCheckbox>
            <div class="rx-actions__buttons">
              <ElButton class="rx-btn-outline" @click="addDrugToBasket">
                <ElIcon><ShoppingCart /></ElIcon>
                加入处方篮
              </ElButton>
              <ElButton :loading="endingVisit" @click="endVisitWithoutPrescription">
                结束看诊（无处方）
              </ElButton>
              <ElButton type="primary" :loading="loading" @click="submitPrescription">
                <ElIcon><Promotion /></ElIcon>
                提交处方
              </ElButton>
            </div>
          </div>
        </article>

        <article class="rx-card rx-card--basket">
          <header class="rx-card__head">
            <div class="rx-card__title-group">
              <span class="rx-card__icon rx-card__icon--primary" aria-hidden="true">
                <ElIcon :size="18"><ShoppingCart /></ElIcon>
              </span>
              <h3 class="rx-card__title">处方篮 / 待提交药品</h3>
            </div>
          </header>

          <ElTable
            v-if="prescriptionBasket.length"
            :data="prescriptionBasket"
            class="rx-basket-table"
            :header-cell-style="{ background: 'var(--color-table-header)', color: 'var(--color-text-soft)', fontWeight: 600 }"
          >
            <ElTableColumn prop="drugName" label="药品" min-width="100" />
            <ElTableColumn label="用法" min-width="180">
              <template #default="{ row }">
                <ElInput
                  v-model="row.drugUsage"
                  size="small"
                  placeholder="用法用量"
                />
              </template>
            </ElTableColumn>
            <ElTableColumn label="数量" width="120" align="center">
              <template #default="{ row }">
                <ElInputNumber
                  v-model="row.drugNumber"
                  size="small"
                  :min="1"
                  controls-position="right"
                />
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="72" align="center">
              <template #default="{ $index }">
                <ElButton link type="danger" @click="removeBasketItem($index)">移除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <p v-else class="rx-empty">处方篮为空，请添加药品或从 AI 推荐采纳。</p>

          <footer v-if="prescriptionBasket.length" class="rx-basket-footer">
            <span>共 {{ basketDrugCount }} 种药品</span>
            <span>合计数量：{{ basketTotalQuantity }}</span>
          </footer>
        </article>
      </div>

      <div class="rx-col rx-col--ai">
        <article class="rx-card rx-card--ai">
          <header class="rx-card__head rx-card__head--ai">
            <div class="rx-card__title-group">
              <span class="rx-card__icon rx-card__icon--ai" aria-hidden="true">
                <ElIcon :size="18"><MagicStick /></ElIcon>
              </span>
              <h3 class="rx-card__title">AI 用药推荐区</h3>
            </div>
            <ElTag class="rx-ai-tag" size="small" effect="light" round>辅助参考</ElTag>
          </header>

          <ElForm class="rx-form rx-form--ai" label-position="top">
            <ElFormItem label="确诊病名（用于处方）" required>
              <ElInput v-model="confirmedDiagnosis" placeholder="来自确诊页面或 W4 输出" />
            </ElFormItem>
          </ElForm>

          <div class="rx-w5-trigger">
            <ElButton
              class="rx-w5-btn"
              :loading="w5Loading"
              :disabled="!hasConfirmedDiagnosis"
              @click="runW5"
            >
              <ElIcon><VideoPlay /></ElIcon>
              运行 智能荐药 工作流
            </ElButton>
            <p v-if="!hasConfirmedDiagnosis" class="rx-w5-hint">请先完成门诊确诊并填写确诊病名</p>
          </div>

          <W5PrescriptionPanel
            :live-output="w5Output"
            :saved-suggestions="savedW5Suggestions"
            :disabled="loading"
            @adopt="adoptW5Suggestion"
          />
        </article>

        <article class="rx-card rx-card--submitted">
          <header class="rx-card__head">
            <div class="rx-card__title-group">
              <span class="rx-card__icon rx-card__icon--primary" aria-hidden="true">
                <ElIcon :size="18"><Document /></ElIcon>
              </span>
              <h3 class="rx-card__title">已提交处方</h3>
            </div>
          </header>

          <ElTable
            v-if="prescriptions.length"
            :data="prescriptions"
            class="rx-submitted-table"
            :header-cell-style="{ background: 'var(--color-table-header)', color: 'var(--color-text-soft)', fontWeight: 600 }"
          >
            <ElTableColumn prop="drugName" label="药品" />
            <ElTableColumn prop="drugUsage" label="用法" show-overflow-tooltip />
            <ElTableColumn prop="drugState" label="状态" width="88" />
            <ElTableColumn label="操作" width="72" align="center">
              <template #default="{ row }">
                <ElButton link type="danger" :disabled="loading" @click="removePrescriptionItem(row.id)">
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <p v-else class="rx-empty">暂无处方记录。</p>
        </article>
      </div>
    </div>
  </PhysicianStepLayout>
</template>

<style scoped>
.rx-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: var(--space-5);
  align-items: start;
}

.rx-col {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  min-width: 0;
}

.rx-card {
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--shadow-sm);
}

.rx-card--ai {
  border-color: rgba(32, 180, 134, 0.22);
  background: linear-gradient(180deg, rgba(32, 180, 134, 0.04) 0%, rgba(255, 255, 255, 0.98) 120px);
}

.rx-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-5);
}

.rx-card__head--ai {
  margin-block-end: var(--space-4);
}

.rx-card__title-group {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.rx-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.rx-card__icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.rx-card__icon--primary {
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}

.rx-card__icon--ai {
  color: var(--color-success);
  background: rgba(32, 180, 134, 0.14);
}

.rx-ai-tag {
  flex-shrink: 0;
  --el-tag-bg-color: rgba(32, 180, 134, 0.1);
  --el-tag-border-color: rgba(32, 180, 134, 0.24);
  --el-tag-text-color: var(--color-success);
}

.rx-form :deep(.el-form-item) {
  margin-block-end: var(--space-4);
}

.rx-form :deep(.el-form-item__label) {
  color: var(--color-text-muted);
  font-weight: 500;
}

.rx-form__control {
  width: 100%;
}

.rx-form--ai :deep(.el-form-item__label) {
  color: var(--color-text);
  font-weight: 600;
}

.rx-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-block-start: var(--space-2);
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}

.rx-actions__checkbox {
  margin: 0;
}

.rx-actions__buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-3);
}

.rx-btn-outline {
  --el-button-text-color: var(--color-primary-strong);
  --el-button-border-color: var(--color-primary);
  --el-button-bg-color: #fff;
  --el-button-hover-text-color: var(--color-primary-strong);
  --el-button-hover-border-color: var(--color-primary-strong);
  --el-button-hover-bg-color: var(--color-primary-soft);
}

.rx-basket-table,
.rx-submitted-table {
  width: 100%;
}

.rx-basket-table :deep(.el-input),
.rx-basket-table :deep(.el-input-number) {
  width: 100%;
}

.rx-basket-table :deep(.el-input-number .el-input__wrapper) {
  padding-inline: 8px;
}

.rx-basket-table :deep(.el-table__inner-wrapper::before),
.rx-submitted-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.rx-basket-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-start: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  font-size: 13px;
  background: var(--color-table-header);
}

.rx-w5-trigger {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.rx-w5-btn {
  width: 100%;
  min-height: 40px;
  --el-button-text-color: var(--color-success);
  --el-button-border-color: rgba(32, 180, 134, 0.45);
  --el-button-bg-color: #fff;
  --el-button-hover-text-color: #178f68;
  --el-button-hover-border-color: var(--color-success);
  --el-button-hover-bg-color: rgba(32, 180, 134, 0.08);
}

.rx-w5-hint {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 12px;
  text-align: center;
}

.rx-empty {
  margin: 0;
  padding: var(--space-5) var(--space-3);
  color: var(--color-text-soft);
  font-size: 13px;
  text-align: center;
}

@media (max-width: 1024px) {
  .rx-grid {
    grid-template-columns: 1fr;
  }

  .rx-actions__buttons {
    justify-content: stretch;
  }

  .rx-actions__buttons .el-button {
    flex: 1 1 auto;
  }
}
</style>
