<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElButton, ElDialog, ElEmpty, ElInput, ElMessage } from 'element-plus'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import type { DiagnosisSuggestionItem, DrugSuggestionItem } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  visible: boolean
  registerId?: number
  mode: 'drug' | 'diagnosis'
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  select: [messageType: 'drug_card' | 'diagnosis_card', payload: Record<string, unknown>]
}>()

const loading = ref(false)
const keyword = ref('')
const drugs = ref<DrugSuggestionItem[]>([])
const diagnoses = ref<DiagnosisSuggestionItem[]>([])

async function load() {
  if (!props.registerId) return
  loading.value = true
  try {
    if (props.mode === 'drug') {
      drugs.value = await medtechFollowUpApi.suggestDrugs(props.registerId, keyword.value || undefined)
    } else {
      diagnoses.value = await medtechFollowUpApi.suggestDiagnoses(props.registerId)
    }
  } catch {
    ElMessage.error('加载推荐数据失败')
  } finally {
    loading.value = false
  }
}

function close() {
  emit('update:visible', false)
}

function pickDrug(item: DrugSuggestionItem) {
  emit('select', 'drug_card', { ...item })
  close()
}

function pickDiagnosis(item: DiagnosisSuggestionItem) {
  emit('select', 'diagnosis_card', { ...item })
  close()
}

watch(
  () => [props.visible, props.registerId, props.mode],
  ([visible]) => {
    if (visible) void load()
  },
)
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="mode === 'drug' ? '发送荐药卡片' : '发送病况卡片'"
    width="560px"
    @close="close"
  >
    <div v-if="mode === 'drug'" class="picker">
      <ElInput v-model="keyword" placeholder="搜索药品名称/助记码" clearable @keyup.enter="load" />
      <ElButton :loading="loading" @click="load">搜索</ElButton>
      <div v-if="drugs.length" class="picker__list">
        <button v-for="item in drugs" :key="`${item.drugId}-${item.drugName}`" type="button" class="picker__item" @click="pickDrug(item)">
          <strong>{{ item.drugName }}</strong>
          <span>{{ item.drugUsage || item.drugFormat || '点击查看详情' }}</span>
        </button>
      </div>
      <ElEmpty v-else description="暂无可推荐药品" />
    </div>
    <div v-else class="picker">
      <div v-if="diagnoses.length" class="picker__list">
        <button
          v-for="(item, index) in diagnoses"
          :key="`${item.diseaseId ?? index}-${item.diseaseName}`"
          type="button"
          class="picker__item"
          @click="pickDiagnosis(item)"
        >
          <strong>{{ item.diseaseName ?? item.diagnosisText }}</strong>
          <span>{{ item.treatmentDirection || item.diseaseIcd || '点击查看详情' }}</span>
        </button>
      </div>
      <ElEmpty v-else description="暂无关联病况" />
    </div>
  </ElDialog>
</template>

<style scoped>
.picker {
  display: grid;
  gap: var(--space-3);
}

.picker__list {
  display: grid;
  gap: var(--space-2);
  max-height: 360px;
  overflow-y: auto;
}

.picker__item {
  display: grid;
  gap: var(--space-1);
  text-align: left;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  cursor: pointer;
}

.picker__item:hover {
  border-color: var(--color-primary);
}

.picker__item span {
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
