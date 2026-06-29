<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElCheckbox, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'
import { physicianApi, type Drug, type PrescriptionItem, type W5Output, type W5Suggestion } from '@/shared/api/modules/physician'
import { clinicalRecordApi } from '@/shared/api/modules/clinicalRecord'
import { useEncounterStore } from '@/app/stores/encounter'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'
import W5PrescriptionPanel from '../components/W5PrescriptionPanel.vue'

interface PrescriptionDraft {
  drugId?: number
  drugUsage: string
  drugNumber: number
}

const router = useRouter()
const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)
const endingVisit = ref(false)
const archiveOnFinish = ref(true)

const loading = ref(false)
const w5Loading = ref(false)
const drugKeyword = ref('')
const drugs = ref<Drug[]>([])

const confirmedDiagnosis = ref('')
const w5Output = ref<W5Output | null>(null)
const savedW5Suggestions = ref<W5Suggestion[]>([])
const prescriptionDraft = reactive<PrescriptionDraft>({ drugUsage: '', drugNumber: 1 })
const prescriptionBasket = ref<Array<PrescriptionDraft & { drugName: string; drugPrice: number }>>([])
const prescriptions = ref<PrescriptionItem[]>([])

async function searchDrugs() {
  drugs.value = await physicianApi.drugs(drugKeyword.value)
}

const hasConfirmedDiagnosis = computed(() => confirmedDiagnosis.value.trim().length > 0)

function addDrugToBasket() {
  const drug = drugs.value.find((item) => item.id === prescriptionDraft.drugId)
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
  prescriptionDraft.drugId = undefined
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
    if (w5Output.value?.status !== 'fallback') {
      await loadSavedW5Suggestions()
    }
    ElMessage.success('W5 用药建议已生成')
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
  const cached = drugs.value.find((d) => d.id === item.drugId)
  if (cached) {
    drugName = cached.drugName
    drugPrice = cached.drugPrice
  } else {
    try {
      const detail = await physicianApi.drug(item.drugId)
      drugName = detail.drugName
      drugPrice = detail.drugPrice
    } catch {
      ElMessage.warning('无法加载药品详情，请手动搜索添加')
      return
    }
  }
  prescriptionBasket.value.push({
    drugId: item.drugId,
    drugName,
    drugPrice,
    drugUsage: item.recommendUsage || '',
    drugNumber: item.recommendQuantity && item.recommendQuantity > 0 ? item.recommendQuantity : 1,
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
  void Promise.all([searchDrugs(), loadPrescriptions(), loadConfirmedDiagnosis(), loadSavedW5Suggestions()])
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="开立处方"
    description="选择药品并提交处方；可先运行 W5 获取 AI 用药推荐。"
    prev-path="/physician/diagnosis"
  >
    <div class="rx-grid">
      <section>
        <div class="inline-tools">
          <ElInput v-model="drugKeyword" placeholder="搜索药品" @keyup.enter="searchDrugs" />
          <ElButton @click="searchDrugs">搜索</ElButton>
        </div>

        <ElForm label-position="top">
          <ElFormItem label="药品">
            <ElSelect v-model="prescriptionDraft.drugId" filterable placeholder="选择药品">
              <ElOption v-for="item in drugs" :key="item.id" :label="`${item.drugName} / ${item.drugPrice}元`" :value="item.id" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="用法用量">
            <ElInput v-model="prescriptionDraft.drugUsage" placeholder="例如：口服，一日三次" />
          </ElFormItem>
          <ElFormItem label="数量">
            <ElInputNumber v-model="prescriptionDraft.drugNumber" :min="1" />
          </ElFormItem>
        </ElForm>

        <div class="actions">
          <ElCheckbox v-model="archiveOnFinish">结束看诊时归档并发布给患者</ElCheckbox>
          <ElButton @click="addDrugToBasket">加入处方篮</ElButton>
          <ElButton :loading="endingVisit" @click="endVisitWithoutPrescription">结束看诊（无处方）</ElButton>
          <ElButton type="primary" :loading="loading" @click="submitPrescription">提交处方</ElButton>
        </div>

        <ElTable v-if="prescriptionBasket.length" :data="prescriptionBasket" style="margin-top: var(--space-4)">
          <ElTableColumn prop="drugName" label="药品" />
          <ElTableColumn prop="drugUsage" label="用法" />
          <ElTableColumn prop="drugNumber" label="数量" />
          <ElTableColumn label="操作" width="80">
            <template #default="{ $index }">
              <ElButton link type="danger" @click="removeBasketItem($index)">移除</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </section>

      <section>
        <ElForm label-position="top">
          <ElFormItem label="确诊病名（用于处方）" required>
            <ElInput v-model="confirmedDiagnosis" placeholder="来自确诊页面或 W4 输出" />
          </ElFormItem>
        </ElForm>

        <div class="w5-actions">
          <ElButton
            :loading="w5Loading"
            :disabled="!hasConfirmedDiagnosis"
            @click="runW5"
          >
            <VideoPlay style="margin-right: 4px" />
            运行 W5 智能荐药
          </ElButton>
          <p v-if="!hasConfirmedDiagnosis" class="w5-hint">请先完成门诊确诊并填写确诊病名</p>
        </div>

        <W5PrescriptionPanel
          :live-output="w5Output"
          :saved-suggestions="savedW5Suggestions"
          :disabled="loading"
          @adopt="adoptW5Suggestion"
        />

        <h3>已提交处方</h3>
        <ElTable v-if="prescriptions.length" :data="prescriptions">
          <ElTableColumn prop="drugName" label="药品" />
          <ElTableColumn prop="drugUsage" label="用法" />
          <ElTableColumn prop="drugState" label="状态" />
          <ElTableColumn label="操作" width="120">
            <template #default="{ row }">
              <ElButton link type="danger" :disabled="loading" @click="removePrescriptionItem(row.id)">删除</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <p v-else style="color: var(--color-text-muted)">暂无处方记录。</p>
      </section>
    </div>
  </PhysicianStepLayout>
</template>

<style scoped>
.rx-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: var(--space-4);
  align-items: start;
}

.inline-tools {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-start: var(--space-3);
}

.w5-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.w5-hint {
  margin: 0;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}
</style>

