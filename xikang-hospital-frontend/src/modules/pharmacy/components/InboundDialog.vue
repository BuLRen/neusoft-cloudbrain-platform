<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  ElButton,
  ElDatePicker,
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

const formRef = ref()

const form = ref<DrugInboundPayload>({
  quantity: 0,
  batchNumber: '',
  location: '',
  productionDate: '',
  expiryDate: '',
})
const submitting = ref(false)

const rules = {
  quantity: [
    { required: true, message: '请输入入库数量', trigger: 'blur' },
    {
      validator: (_: unknown, v: number, cb: (e?: Error) => void) => {
        if (!Number.isFinite(Number(v)) || Number(v) <= 0) {
          cb(new Error('入库数量必须大于 0'))
        } else {
          cb()
        }
      },
      trigger: 'blur',
    },
  ],
  batchNumber: [{ required: true, message: '请输入批号', trigger: 'blur' }],
  productionDate: [{ required: true, message: '请选择生产日期', trigger: 'change' }],
  expiryDate: [
    { required: true, message: '请选择失效日期', trigger: 'change' },
    {
      validator: (_: unknown, v: string, cb: (e?: Error) => void) => {
        if (form.value.productionDate && v && v <= form.value.productionDate) {
          cb(new Error('失效日期必须晚于生产日期'))
        } else {
          cb()
        }
      },
      trigger: 'change',
    },
  ],
  location: [{ required: true, message: '请输入货位', trigger: 'blur' }],
}

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
      // 清空校验状态
      formRef.value?.clearValidate()
    }
  },
)

async function submit() {
  if (!props.drug) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请完整填写入库信息')
    return
  }
  submitting.value = true
  try {
    await pharmacyApi.inbound(props.drug.id, form.value)
    ElMessage.success(`已入库 ${form.value.quantity} ${props.drug.unit || '件'}`)
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
    align-center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElDescriptions v-if="drug" :column="1" border size="small">
      <ElDescriptionsItem label="药品">{{ drug.name }}</ElDescriptionsItem>
      <ElDescriptionsItem label="规格">{{ drug.specification || '-' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="单位">{{ drug.unit || '-' }}</ElDescriptionsItem>
    </ElDescriptions>
    <ElForm
      ref="formRef"
      class="mt"
      :model="form"
      :rules="rules"
      label-width="92px"
    >
      <ElFormItem label="入库数量" prop="quantity" required>
        <ElInputNumber v-model="form.quantity" :min="1" :controls="false" style="width: 100%" />
      </ElFormItem>
      <ElFormItem label="批号" prop="batchNumber" required>
        <ElInput v-model="form.batchNumber" placeholder="如 LOT2026-0088" />
      </ElFormItem>
      <ElFormItem label="生产日期" prop="productionDate" required>
        <ElDatePicker
          v-model="form.productionDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择生产日期"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="失效日期" prop="expiryDate" required>
        <ElDatePicker
          v-model="form.expiryDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择失效日期"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="货位" prop="location" required>
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
