<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import type { DrugOption, DrugStock } from '@/shared/types/pharmacy'

const props = defineProps<{
  modelValue: boolean
  drug: DrugOption | null
  /** 可选：从批次弹窗中传入可选批次 */
  batches?: DrugStock[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()

const LOSS_REASONS = [
  '过期失效',
  '破损',
  '变质',
  '包装破损',
  '虫蛀霉变',
  '其他',
] as const

const form = ref<{
  batchId: number | null
  quantity: number
  reason: string
  reasonPreset: string
  operatorName: string
}>({
  batchId: null,
  quantity: 1,
  reason: '',
  reasonPreset: '过期失效',
  operatorName: '',
})

const submitting = ref(false)

const availableBatches = computed(() =>
  (props.batches ?? []).filter((b) => b.status === 1 && (b.quantity ?? 0) > 0),
)

const selectedBatch = computed(() => {
  if (!form.value.batchId) return null
  return availableBatches.value.find((b) => b.id === form.value.batchId) ?? null
})

const maxQuantity = computed(() => selectedBatch.value?.quantity ?? props.drug?.stockQuantity ?? 0)

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      const firstBatch = availableBatches.value[0]
      form.value = {
        batchId: firstBatch?.id ?? null,
        quantity: 1,
        reason: '过期失效',
        reasonPreset: '过期失效',
        operatorName: '',
      }
    }
  },
)

function onPresetChange(v: string) {
  form.value.reasonPreset = v
  form.value.reason = v
}

async function submit() {
  if (!props.drug) return
  const qty = Number(form.value.quantity)
  if (!Number.isFinite(qty) || qty <= 0) {
    ElMessage.warning('报损数量必须大于 0')
    return
  }
  if (qty > maxQuantity.value) {
    ElMessage.warning(
      `报损数量超过最大可报损数（${maxQuantity.value}），请检查库存`,
    )
    return
  }
  if (!form.value.reason.trim()) {
    ElMessage.warning('请填写报损原因')
    return
  }
  submitting.value = true
  try {
    await pharmacyApi.loss(props.drug.id, {
      batchId: form.value.batchId ?? undefined,
      quantity: qty,
      reason: form.value.reason.trim(),
      operatorName: form.value.operatorName.trim() || undefined,
    })
    ElMessage.success(`已报损 ${qty} ${props.drug.unit || '件'}：${form.value.reason}`)
    emit('update:modelValue', false)
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="药品报损"
    width="540px"
    align-center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElDescriptions v-if="drug" :column="1" border size="small">
      <ElDescriptionsItem label="药品">{{ drug.name }}</ElDescriptionsItem>
      <ElDescriptionsItem label="规格">{{ drug.specification || '-' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="当前总库存">
        <strong :class="{ 'stock-low': (drug.stockQuantity ?? 0) <= (drug.lowStockThreshold ?? 0) }">
          {{ drug.stockQuantity ?? 0 }} {{ drug.unit || '件' }}
        </strong>
      </ElDescriptionsItem>
    </ElDescriptions>

    <ElForm class="mt" label-width="92px">
      <ElFormItem v-if="availableBatches.length > 0" label="报损批次">
        <ElSelect
          v-model="form.batchId"
          placeholder="不指定则从最早过期批次扣减"
          clearable
          filterable
          class="full"
        >
          <ElOption
            v-for="b in availableBatches"
            :key="b.id"
            :value="b.id"
            :label="`${b.batchNumber || '无批号'} · 库存 ${b.quantity} · 效期 ${b.expiryDate || '-'}`"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem label="报损数量" required>
        <ElInputNumber
          v-model="form.quantity"
          :min="1"
          :max="maxQuantity || undefined"
          :controls="false"
        />
        <span v-if="selectedBatch" class="hint">批次可报损上限：{{ maxQuantity }}</span>
        <span v-else-if="drug" class="hint">全药可报损上限：{{ maxQuantity }}</span>
      </ElFormItem>

      <ElFormItem label="原因预设">
        <ElSelect v-model="form.reasonPreset" class="full" @change="onPresetChange">
          <ElOption v-for="r in LOSS_REASONS" :key="r" :value="r" :label="r" />
        </ElSelect>
      </ElFormItem>

      <ElFormItem label="具体原因" required>
        <ElInput
          v-model="form.reason"
          type="textarea"
          :rows="2"
          placeholder="可补充详细说明，如：存放期间受潮、整批外包装破损等"
        />
      </ElFormItem>

      <ElFormItem label="操作人">
        <ElInput v-model="form.operatorName" placeholder="留空则使用当前账号" />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">取消</ElButton>
      <ElButton type="danger" :loading="submitting" @click="submit">确认报损</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.mt {
  margin-block-start: var(--space-4);
}

.full {
  width: 100%;
}

.hint {
  margin-left: var(--space-3);
  font-size: 12px;
  color: var(--color-text-muted);
}

.stock-low {
  color: var(--color-danger, #f56c6c);
  font-weight: 600;
}
</style>
