<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElMessage,
  ElTag,
} from 'element-plus'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import type { DrugOption, MedicationGuide } from '@/shared/types/pharmacy'

const props = defineProps<{
  modelValue: boolean
  drug: DrugOption | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'check', drug: DrugOption): void
}>()

const guide = ref<MedicationGuide | null>(null)
const guideLoading = ref(false)

watch(
  () => props.modelValue,
  (v) => {
    if (v) guide.value = null
  },
)

async function generateGuide() {
  if (!props.drug) return
  guideLoading.value = true
  try {
    guide.value = await pharmacyApi.medicationGuide(props.drug.id)
  } catch {
    ElMessage.warning('AI 生成失败，请稍后重试或咨询药师')
  } finally {
    guideLoading.value = false
  }
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="药品详情"
    width="640px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="drug">
      <ElDescriptions :column="2" border>
        <ElDescriptionsItem label="药品名称">{{ drug.drugName }}</ElDescriptionsItem>
        <ElDescriptionsItem label="药品编码">{{ drug.drugCode || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="助记码">{{ drug.mnemonicCode || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="剂型">{{ drug.drugDosage || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="分类">
          <ElTag v-if="drug.drugType" size="small">{{ drug.drugType }}</ElTag>
          <span v-else>-</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="规格">{{ drug.drugFormat || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="厂家">{{ drug.manufacturer || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="单价">{{ drug.drugPrice ?? '-' }} 元</ElDescriptionsItem>
        <ElDescriptionsItem label="库存">
          <span :class="{ 'stock-low': (drug.stockQuantity ?? 0) <= (drug.lowStockThreshold ?? 0) }">
            {{ drug.stockQuantity }} {{ drug.drugUnit }}
          </span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="预警阈值">{{ drug.lowStockThreshold ?? '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem v-if="drug.storageConditions" label="储存条件" :span="2">{{ drug.storageConditions }}</ElDescriptionsItem>
        <ElDescriptionsItem v-if="drug.instructions" label="用药指导" :span="2">{{ drug.instructions }}</ElDescriptionsItem>
        <ElDescriptionsItem v-if="drug.contraindications" label="禁忌" :span="2">{{ drug.contraindications }}</ElDescriptionsItem>
        <ElDescriptionsItem v-if="drug.adverseReactions" label="不良反应" :span="2">{{ drug.adverseReactions }}</ElDescriptionsItem>
      </ElDescriptions>

      <div class="detail-actions">
        <ElButton type="primary" :loading="guideLoading" @click="generateGuide">
          {{ guideLoading ? 'AI 生成中…' : '查看用药说明' }}
        </ElButton>
        <ElButton @click="emit('check', drug)">库存盘点</ElButton>
      </div>

      <div v-if="guide" class="guide-result">
        <h4>用药说明</h4>
        <ElDescriptions :column="1" border>
          <ElDescriptionsItem v-if="guide.usage" label="用法用量">{{ guide.usage }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="guide.precautions" label="注意事项">{{ guide.precautions }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="guide.sideEffects" label="不良反应">{{ guide.sideEffects }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="guide.storage" label="储存建议">{{ guide.storage }}</ElDescriptionsItem>
        </ElDescriptions>
        <p v-if="!guide.usage && !guide.precautions && !guide.sideEffects && !guide.storage" class="guide-empty">
          AI 暂未能生成用药说明，请咨询药师
        </p>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.detail-actions {
  margin-block-start: var(--space-4);
  display: flex;
  gap: var(--space-3);
}

.guide-result {
  margin-block-start: var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.guide-result h4 {
  margin-block-end: var(--space-3);
  color: var(--color-primary);
}

.stock-low {
  color: var(--color-danger);
  font-weight: 600;
}

.guide-empty {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}
</style>
