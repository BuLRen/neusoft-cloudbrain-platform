<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
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
    // 拦截器统一报错
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
        <ElDescriptionsItem label="商品名">{{ drug.name }}</ElDescriptionsItem>
        <ElDescriptionsItem label="通用名">{{ drug.genericName || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="品牌">{{ drug.brandName || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="剂型">{{ drug.dosageForm || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="分类">
          <ElTag v-if="drug.category" size="small">{{ drug.category }}</ElTag>
          <span v-else>-</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="规格">{{ drug.specification || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="厂家">{{ drug.manufacturer || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="批准文号">{{ drug.approvalNumber || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="单价">{{ drug.price ?? '-' }} 元</ElDescriptionsItem>
        <ElDescriptionsItem label="库存">
          <span :class="{ 'stock-low': (drug.stockQuantity ?? 0) <= (drug.lowStockThreshold ?? 0) }">
            {{ drug.stockQuantity }} {{ drug.unit }}
          </span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="储存条件" :span="2">{{ drug.storageConditions || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="用药指导" :span="2">{{ drug.instructions || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="禁忌" :span="2">{{ drug.contraindications || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="不良反应" :span="2">{{ drug.adverseReactions || '-' }}</ElDescriptionsItem>
      </ElDescriptions>

      <div class="detail-actions">
        <ElButton type="primary" :loading="guideLoading" @click="generateGuide">
          生成 AI 用药指导
        </ElButton>
        <ElButton @click="emit('check', drug)">库存盘点</ElButton>
      </div>

      <div v-if="guide" class="guide-result">
        <h4>AI 用药指导</h4>
        <ElDescriptions :column="1" border>
          <ElDescriptionsItem v-if="guide.usage" label="用法">{{ guide.usage }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="guide.dosage" label="剂量">{{ guide.dosage }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="guide.frequency" label="频次">{{ guide.frequency }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="guide.precautions" label="注意事项">{{ guide.precautions }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="guide.sideEffects" label="不良反应">{{ guide.sideEffects }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="guide.storage" label="储存">{{ guide.storage }}</ElDescriptionsItem>
        </ElDescriptions>
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
  color: var(--color-danger, #f56c6c);
  font-weight: 600;
}
</style>
