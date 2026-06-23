<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  ElButton,
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
import { DOSAGE_FORMS } from '@/shared/constants/pharmacy'
import type { DrugOption } from '@/shared/types/pharmacy'

const props = defineProps<{
  modelValue: boolean
  /** 传入药品为编辑模式；null 为新增模式 */
  drug: DrugOption | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved', drug: DrugOption): void
}>()

const isEdit = ref(false)
const submitting = ref(false)
const form = ref<DrugOption>(makeEmpty())

function makeEmpty(): DrugOption {
  return {
    id: 0,
    name: '',
    genericName: '',
    brandName: '',
    specification: '',
    dosageForm: '',
    category: '',
    unit: '盒',
    manufacturer: '',
    approvalNumber: '',
    price: 0,
    stockQuantity: 0,
    lowStockThreshold: 10,
    storageConditions: '',
    instructions: '',
    contraindications: '',
    adverseReactions: '',
    status: 1,
  }
}

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    if (props.drug) {
      isEdit.value = true
      form.value = { ...props.drug }
    } else {
      isEdit.value = false
      form.value = makeEmpty()
    }
  },
)

async function submit() {
  if (!form.value.name?.trim()) {
    ElMessage.warning('请填写药品名称')
    return
  }
  if (form.value.price == null || form.value.price < 0) {
    ElMessage.warning('单价必须 ≥ 0')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value && form.value.id) {
      await pharmacyApi.updateDrug(form.value.id, form.value)
      ElMessage.success('药品已更新')
    } else {
      const created = await pharmacyApi.createDrug(form.value)
      ElMessage.success('药品已新增')
      form.value.id = created.id || form.value.id
    }
    emit('saved', form.value)
    emit('update:modelValue', false)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="isEdit ? '编辑药品' : '新增药品'"
    width="720px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElForm :label-width="`92px`" label-position="right">
      <div class="form-grid">
        <ElFormItem label="药品名称" required>
          <ElInput v-model="form.name" placeholder="如：阿司匹林肠溶片" />
        </ElFormItem>
        <ElFormItem label="通用名">
          <ElInput v-model="form.genericName" placeholder="如：Aspirin" />
        </ElFormItem>
        <ElFormItem label="商品品牌">
          <ElInput v-model="form.brandName" />
        </ElFormItem>
        <ElFormItem label="剂型">
          <ElSelect v-model="form.dosageForm" placeholder="选择剂型" clearable>
            <ElOption v-for="f in DOSAGE_FORMS" :key="f" :label="f" :value="f" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="分类">
          <ElInput v-model="form.category" placeholder="如：西药 / 中成药 / OTC" />
        </ElFormItem>
        <ElFormItem label="规格">
          <ElInput v-model="form.specification" placeholder="如：100mg*30片/盒" />
        </ElFormItem>
        <ElFormItem label="单位">
          <ElInput v-model="form.unit" placeholder="盒 / 瓶 / 支" />
        </ElFormItem>
        <ElFormItem label="单价" required>
          <ElInputNumber v-model="form.price" :min="0" :precision="2" :controls="false" />
        </ElFormItem>
        <ElFormItem label="预警阈值">
          <ElInputNumber v-model="form.lowStockThreshold" :min="0" :controls="false" />
        </ElFormItem>
        <ElFormItem label="批准文号">
          <ElInput v-model="form.approvalNumber" placeholder="国药准字 Hxxxxxxxx" />
        </ElFormItem>
        <ElFormItem label="生产厂家" class="span-2">
          <ElInput v-model="form.manufacturer" />
        </ElFormItem>
        <ElFormItem label="储存条件" class="span-2">
          <ElInput v-model="form.storageConditions" placeholder="如：常温 / 避光 / 2-8℃ 冷藏" />
        </ElFormItem>
        <ElFormItem label="用药指导" class="span-2">
          <ElInput v-model="form.instructions" type="textarea" :rows="2" />
        </ElFormItem>
        <ElFormItem label="禁忌" class="span-2">
          <ElInput v-model="form.contraindications" type="textarea" :rows="2" />
        </ElFormItem>
        <ElFormItem label="不良反应" class="span-2">
          <ElInput v-model="form.adverseReactions" type="textarea" :rows="2" />
        </ElFormItem>
      </div>
    </ElForm>

    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="submit">
        {{ isEdit ? '保存修改' : '确认新增' }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 var(--space-4);
}

.span-2 {
  grid-column: span 2;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .span-2 {
    grid-column: span 1;
  }
}
</style>
