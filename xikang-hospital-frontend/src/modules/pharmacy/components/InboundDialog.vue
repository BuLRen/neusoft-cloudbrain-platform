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
import type { DrugInboundPayload, DrugOption } from '@/shared/types/pharmacy'

const props = defineProps<{
  modelValue: boolean
  drug: DrugOption | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()

const form = ref<DrugInboundPayload>({
  quantity: 0,
  batchNumber: '',
  location: '',
  productionDate: '',
  expiryDate: '',
})
const submitting = ref(false)

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      form.value = {
        quantity: 0,
        batchNumber: '',
        location: '',
        productionDate: '',
        expiryDate: '',
      }
    }
  },
)

async function submit() {
  if (!props.drug) return
  const qty = Number(form.value.quantity)
  if (!Number.isFinite(qty) || qty <= 0) {
    ElMessage.warning('入库数量必须大于 0')
    return
  }
  submitting.value = true
  try {
    await pharmacyApi.inbound(props.drug.id, form.value)
    ElMessage.success(`已入库 ${qty} ${props.drug.unit || '件'}`)
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
    title="药品入库"
    width="520px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElDescriptions v-if="drug" :column="1" border>
      <ElDescriptionsItem label="药品">{{ drug.name }}</ElDescriptionsItem>
      <ElDescriptionsItem label="规格">{{ drug.specification || '-' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="单位">{{ drug.unit || '-' }}</ElDescriptionsItem>
    </ElDescriptions>
    <ElForm class="mt" label-width="92px">
      <ElFormItem label="入库数量" required>
        <ElInputNumber v-model="form.quantity" :min="1" :controls="false" />
      </ElFormItem>
      <ElFormItem label="批号">
        <ElInput v-model="form.batchNumber" placeholder="如 LOT2026-0088" />
      </ElFormItem>
      <ElFormItem label="生产日期">
        <ElInput v-model="form.productionDate" placeholder="YYYY-MM-DD" />
      </ElFormItem>
      <ElFormItem label="失效日期">
        <ElInput v-model="form.expiryDate" placeholder="YYYY-MM-DD" />
      </ElFormItem>
      <ElFormItem label="货位">
        <ElInput v-model="form.location" placeholder="如 A-3-02" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="submit">确认入库</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.mt {
  margin-block-start: var(--space-4);
}
</style>
