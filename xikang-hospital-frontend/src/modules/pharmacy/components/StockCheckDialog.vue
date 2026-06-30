<script setup lang="ts">
import { ref, watch } from 'vue'
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
} from 'element-plus'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import type { DrugOption } from '@/shared/types/pharmacy'

const props = defineProps<{
  modelValue: boolean
  drug: DrugOption | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()

const form = ref<{ actualQuantity: number; reason: string }>({
  actualQuantity: 0,
  reason: '库存盘点',
})
const submitting = ref(false)

watch(
  () => props.modelValue,
  (v) => {
    if (v && props.drug) {
      form.value = {
        actualQuantity: props.drug.stockQuantity ?? 0,
        reason: '库存盘点',
      }
    }
  },
)

async function submit() {
  if (!props.drug) return
  const actual = Number(form.value.actualQuantity)
  if (!Number.isFinite(actual) || actual < 0) {
    ElMessage.warning('实际数量必须 ≥ 0')
    return
  }
  const before = props.drug.stockQuantity ?? 0
  const diff = actual - before
  submitting.value = true
  try {
    await pharmacyApi.updateStock(props.drug.id, {
      quantity: actual,
      reason: form.value.reason,
    })
    ElMessage.success(`盘点完成：${before} → ${actual}（${diff >= 0 ? '+' : ''}${diff}）`)
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
    title="库存盘点"
    width="480px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElDescriptions v-if="drug" :column="1" border>
      <ElDescriptionsItem label="药品">{{ drug.drugName }}</ElDescriptionsItem>
      <ElDescriptionsItem label="账面库存">
        {{ drug.stockQuantity ?? 0 }} {{ drug.drugUnit || '' }}
      </ElDescriptionsItem>
    </ElDescriptions>
    <ElForm class="mt" label-width="92px">
      <ElFormItem label="实际数量" required>
        <ElInputNumber v-model="form.actualQuantity" :min="0" :controls="false" />
      </ElFormItem>
      <ElFormItem label="盘点原因">
        <ElInput v-model="form.reason" placeholder="如：季度盘点 / 报损" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="submit">确认盘点</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.mt {
  margin-block-start: var(--space-4);
}
</style>
