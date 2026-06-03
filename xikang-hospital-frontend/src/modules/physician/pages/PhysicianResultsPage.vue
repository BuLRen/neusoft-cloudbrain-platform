<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElCard, ElEmpty, ElMessage, ElTable, ElTableColumn } from 'element-plus'
import { physicianApi, type CheckResult, type InspectionResult, type W3Output } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const loading = ref(false)
const checkResults = ref<CheckResult[]>([])
const inspectionResults = ref<InspectionResult[]>([])
const w3Output = ref<W3Output | null>(null)

async function loadResults() {
  if (!registerId.value) return
  loading.value = true
  try {
    const [checks, inspections] = await Promise.all([
      physicianApi.checkResults(registerId.value),
      physicianApi.inspectionResults(registerId.value),
    ])
    checkResults.value = checks
    inspectionResults.value = inspections
  } finally {
    loading.value = false
  }
}

async function runW3() {
  if (!registerId.value) return
  loading.value = true
  try {
    w3Output.value = await physicianApi.aiW3(registerId.value)
    ElMessage.success('W3 结果分析已完成')
  } finally {
    loading.value = false
  }
}

watch(registerId, () => {
  void loadResults()
})

onMounted(() => {
  void loadResults()
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    :step="4"
    :total-steps="6"
    title="查看结果"
    description="第四步：查看检查/检验结果，可运行 W3 生成结构化分析（非最终诊断）。"
    prev-path="/physician/orders"
    next-path="/physician/diagnosis"
  >
    <div class="results-toolbar">
      <ElButton :loading="loading" @click="loadResults">刷新结果</ElButton>
      <ElButton type="primary" :loading="loading" @click="runW3">运行 W3（AI 分析）</ElButton>
    </div>

    <h3>检查结果</h3>
    <ElEmpty v-if="!checkResults.length" description="暂无检查结果" />
    <ElTable v-else :data="checkResults">
      <ElTableColumn prop="techName" label="项目" />
      <ElTableColumn prop="checkState" label="状态" />
      <ElTableColumn prop="checkResult" label="结果" />
      <ElTableColumn label="AI 分析">
        <template #default="{ row }">
          <span>{{ row.aiAnalysis?.analysisReport || '-' }}</span>
        </template>
      </ElTableColumn>
    </ElTable>

    <h3 style="margin-top: var(--space-4)">检验结果</h3>
    <ElEmpty v-if="!inspectionResults.length" description="暂无检验结果" />
    <ElTable v-else :data="inspectionResults">
      <ElTableColumn prop="techName" label="项目" />
      <ElTableColumn prop="inspectionState" label="状态" />
      <ElTableColumn prop="inspectionResult" label="结果" />
      <ElTableColumn label="AI 分析">
        <template #default="{ row }">
          <span>{{ row.aiAnalysis?.analysisReport || '-' }}</span>
        </template>
      </ElTableColumn>
    </ElTable>

    <h3 style="margin-top: var(--space-4)">W3 汇总</h3>
    <ElEmpty v-if="!w3Output" description="暂无 W3 输出，可运行 W3" />
    <div v-else class="w3-grid">
      <ElCard v-for="item in w3Output.examSummaries || []" :key="item.techName" class="mini-card">
        <strong>{{ item.techName }}</strong>
        <p v-if="item.interpretation">{{ item.interpretation }}</p>
        <p v-if="item.riskLevel">风险等级：{{ item.riskLevel }}</p>
      </ElCard>
      <ElCard v-if="w3Output.overallAnalysis" class="mini-card">
        <strong>总体分析</strong>
        <p>{{ w3Output.overallAnalysis }}</p>
      </ElCard>
    </div>
  </PhysicianStepLayout>
</template>

<style scoped>
.results-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.w3-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.mini-card p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.8;
}
</style>

