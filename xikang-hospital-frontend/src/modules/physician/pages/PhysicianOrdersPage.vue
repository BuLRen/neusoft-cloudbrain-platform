<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import { physicianApi, type MedicalTechnology, type W2Output } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'

type TechType = MedicalTechnology['techType']

interface RequestDraft {
  medicalTechnologyId?: number
  info: string
  position: string
  remark: string
}

interface BasketItem extends RequestDraft {
  techName: string
  techType: TechType
}

const TECH_TYPE_LABEL: Record<TechType, string> = {
  check: '检查',
  inspection: '检验',
  disposal: '处置',
}

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const technologyKeyword = ref('')
const technologies = ref<MedicalTechnology[]>([])
const requestDraft = reactive<RequestDraft>({ info: '', position: '', remark: '' })
const requestBasket = ref<BasketItem[]>([])

const aiLoading = ref(false)
const w2Output = ref<W2Output | null>(null)

function technologyOptionLabel(item: MedicalTechnology) {
  return `${item.techName} / ${TECH_TYPE_LABEL[item.techType]} / ${item.techPrice}元`
}

async function searchTechnologies() {
  technologies.value = await physicianApi.medicalTechnologies(undefined, technologyKeyword.value || undefined)
}

function addTechnologyToBasket() {
  const technology = technologies.value.find((item) => item.id === requestDraft.medicalTechnologyId)
  if (!technology) return
  requestBasket.value.push({
    medicalTechnologyId: technology.id,
    techName: technology.techName,
    techType: technology.techType,
    info: requestDraft.info,
    position: requestDraft.position,
    remark: requestDraft.remark,
  })
  requestDraft.medicalTechnologyId = undefined
  requestDraft.info = ''
  requestDraft.position = ''
  requestDraft.remark = ''
}

function toRequestItems(items: BasketItem[], techType: TechType) {
  return items.map((item) => {
    const base = { medicalTechnologyId: item.medicalTechnologyId }
    if (techType === 'check') {
      return { ...base, checkInfo: item.info, checkPosition: item.position, checkRemark: item.remark }
    }
    if (techType === 'inspection') {
      return { ...base, inspectionInfo: item.info, inspectionPosition: item.position, inspectionRemark: item.remark }
    }
    return { ...base, disposalInfo: item.info, disposalPosition: item.position, disposalRemark: item.remark }
  })
}

async function submitTechnologyRequest() {
  if (!registerId.value) return
  if (requestBasket.value.length === 0) return

  const register = registerId.value
  const byType = (type: TechType) => requestBasket.value.filter((item) => item.techType === type)

  const checkItems = byType('check')
  const inspectionItems = byType('inspection')
  const disposalItems = byType('disposal')

  if (checkItems.length) {
    await physicianApi.createCheckRequest({ registerId: register, items: toRequestItems(checkItems, 'check') })
  }
  if (inspectionItems.length) {
    await physicianApi.createInspectionRequest({ registerId: register, items: toRequestItems(inspectionItems, 'inspection') })
  }
  if (disposalItems.length) {
    await physicianApi.createDisposalRequest({ registerId: register, items: toRequestItems(disposalItems, 'disposal') })
  }

  requestBasket.value = []
  ElMessage.success('申请已提交')
}

async function runW2() {
  if (!registerId.value) return
  aiLoading.value = true
  try {
    w2Output.value = await physicianApi.aiW2(registerId.value)
    ElMessage.success('W2 检查推荐已生成')
  } finally {
    aiLoading.value = false
  }
}

onMounted(() => {
  void searchTechnologies()
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    :step="3"
    :total-steps="6"
    title="开立检查检验"
    description="第三步：开立检查/检验/处置申请，可运行 W2 生成 AI 推荐。"
    prev-path="/physician/record"
    next-path="/physician/results"
  >
    <div class="orders-grid">
      <section class="orders-panel orders-panel--form">
        <div class="inline-tools">
          <ElInput v-model="technologyKeyword" placeholder="搜索项目（名称或编码）" @keyup.enter="searchTechnologies" />
          <ElButton @click="searchTechnologies">搜索</ElButton>
        </div>

        <ElForm label-position="top">
          <ElFormItem label="项目">
            <ElSelect v-model="requestDraft.medicalTechnologyId" filterable placeholder="选择医技项目">
              <ElOption v-for="item in technologies" :key="item.id" :label="technologyOptionLabel(item)" :value="item.id" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="目的要求">
            <ElInput v-model="requestDraft.info" />
          </ElFormItem>
          <ElFormItem label="部位/标本">
            <ElInput v-model="requestDraft.position" />
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput v-model="requestDraft.remark" />
          </ElFormItem>
        </ElForm>

        <div class="actions">
          <ElButton @click="addTechnologyToBasket">加入申请篮</ElButton>
          <ElButton type="primary" @click="submitTechnologyRequest">提交申请</ElButton>
        </div>

        <ElTable v-if="requestBasket.length" :data="requestBasket" style="margin-top: var(--space-4)">
          <ElTableColumn prop="techName" label="项目" />
          <ElTableColumn label="类型" width="80">
            <template #default="{ row }">{{ TECH_TYPE_LABEL[row.techType as TechType] }}</template>
          </ElTableColumn>
          <ElTableColumn prop="position" label="部位/标本" />
          <ElTableColumn prop="info" label="目的" />
        </ElTable>
      </section>

      <section class="orders-panel orders-panel--ai">
        <div class="ai-toolbar">
          <h3 class="orders-panel__title">AI 辅助</h3>
          <ElButton :loading="aiLoading" @click="runW2">运行 W2（AI 推荐）</ElButton>
        </div>

        <h3 class="orders-subtitle">W2 初步判断</h3>
        <ElAlert v-if="w2Output?.preliminaryAssessment" type="info" :closable="false" :title="w2Output.preliminaryAssessment" />

        <h3 class="orders-subtitle">AI 推荐</h3>
        <ElEmpty v-if="!w2Output?.recommendedExaminations?.length" description="暂无 AI 推荐，可运行 W2" />
        <ElCard v-for="item in w2Output?.recommendedExaminations || []" :key="`w2-${item.techId}`" class="mini-card">
          <strong>{{ item.techName }}</strong>
          <p>{{ item.reason }}</p>
        </ElCard>
      </section>
    </div>
  </PhysicianStepLayout>
</template>

<style scoped>
.orders-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
  gap: var(--space-6);
  align-items: start;
}

.orders-panel {
  min-width: 0;
}

.orders-panel--ai {
  padding-inline-start: var(--space-5);
  border-inline-start: 1px solid var(--color-border);
}

.orders-panel__title {
  margin: 0;
  font-size: 15px;
  letter-spacing: -0.02em;
}

.orders-subtitle {
  margin: var(--space-4) 0 var(--space-3);
  font-size: 14px;
  font-weight: 650;
  color: var(--color-text-muted);
}

.inline-tools {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  flex-wrap: wrap;
  margin-block-end: var(--space-4);
}

.actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}

.ai-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-block-end: var(--space-2);
}

.mini-card {
  margin-block-start: var(--space-3);
}

.mini-card p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.8;
}
</style>

