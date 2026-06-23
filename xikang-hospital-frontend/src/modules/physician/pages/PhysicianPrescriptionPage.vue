<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElCheckbox, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import { physicianApi, type Drug, type PrescriptionItem } from '@/shared/api/modules/physician'
import { clinicalRecordApi } from '@/shared/api/modules/clinicalRecord'
import { useEncounterStore } from '@/app/stores/encounter'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'

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
const drugKeyword = ref('')
const drugs = ref<Drug[]>([])

const confirmedDiagnosis = ref('')
const prescriptionDraft = reactive<PrescriptionDraft>({ drugUsage: '', drugNumber: 1 })
const prescriptionBasket = ref<Array<PrescriptionDraft & { drugName: string; drugPrice: number }>>([])
const prescriptions = ref<PrescriptionItem[]>([])

async function searchDrugs() {
  drugs.value = await physicianApi.drugs(drugKeyword.value)
}

function addDrugToBasket() {
  const drug = drugs.value.find((item) => item.id === prescriptionDraft.drugId)
  if (!drug) return
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

async function submitPrescription() {
  if (!registerId.value) return
  if (!prescriptionBasket.value.length) return
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
  void loadPrescriptions()
})

onMounted(() => {
  void Promise.all([searchDrugs(), loadPrescriptions()])
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="开立处方"
    description="选择药品并提交处方（审方由人员B负责）。"
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
        </ElTable>
      </section>

      <section>
        <ElForm label-position="top">
          <ElFormItem label="确诊病名（用于处方）">
            <ElInput v-model="confirmedDiagnosis" placeholder="来自确诊页面或 W4 输出" />
          </ElFormItem>
        </ElForm>

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
</style>

