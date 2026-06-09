<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { ResultFormField } from '@/shared/types/resultForm'

const props = defineProps<{
  fields: ResultFormField[]
  modelValue: Record<string, unknown>
  baseFieldCount?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
}>()

const formRef = ref<FormInstance>()
const localModel = reactive<Record<string, unknown>>({})

const baseFields = computed(() => {
  const count = props.baseFieldCount ?? props.fields.length
  return props.fields.slice(0, count)
})

const extensionFields = computed(() => {
  const count = props.baseFieldCount ?? props.fields.length
  return props.fields.slice(count)
})

const rules = computed<FormRules>(() => {
  const next: FormRules = {}
  for (const field of props.fields) {
    const fieldRules = []
    if (field.required) {
      fieldRules.push({
        required: true,
        message: `请填写${field.fieldLabel}`,
        trigger: field.fieldType === 'number' ? 'change' : 'blur',
      })
    }
    if (field.maxLength && field.fieldType !== 'number') {
      fieldRules.push({
        max: field.maxLength,
        message: `${field.fieldLabel}不能超过 ${field.maxLength} 字`,
        trigger: 'blur',
      })
    }
    if (fieldRules.length) {
      next[field.fieldKey] = fieldRules
    }
  }
  return next
})

watch(
  () => props.modelValue,
  (value) => {
    Object.keys(localModel).forEach((key) => delete localModel[key])
    Object.assign(localModel, value ?? {})
  },
  { immediate: true, deep: true },
)

watch(
  localModel,
  (value) => emit('update:modelValue', { ...value }),
  { deep: true },
)

async function validate() {
  if (!formRef.value) return true
  return formRef.value.validate().then(() => true).catch(() => false)
}

defineExpose({ validate })
</script>

<template>
  <ElForm ref="formRef" :model="localModel" :rules="rules" label-position="top" class="dynamic-result-form">
    <section v-if="baseFields.length" class="dynamic-result-form__section">
      <h4 v-if="extensionFields.length" class="dynamic-result-form__section-title">基础字段</h4>
      <div class="dynamic-result-form__grid">
        <ElFormItem
          v-for="field in baseFields"
          :key="field.fieldKey"
          :label="field.fieldLabel"
          :prop="field.fieldKey"
          :class="field.fieldType === 'textarea' ? 'dynamic-result-form__item--full' : ''"
        >
          <ElInputNumber
            v-if="field.fieldType === 'number'"
            v-model="localModel[field.fieldKey] as number"
            class="dynamic-result-form__control"
            controls-position="right"
          />
          <ElInput
            v-else
            v-model="localModel[field.fieldKey] as string"
            :type="field.fieldType === 'textarea' ? 'textarea' : 'text'"
            :rows="field.fieldType === 'textarea' ? 3 : undefined"
            :placeholder="field.placeholder"
            :maxlength="field.maxLength"
            show-word-limit
            class="dynamic-result-form__control"
          />
        </ElFormItem>
      </div>
    </section>

    <section v-if="extensionFields.length" class="dynamic-result-form__section">
      <h4 class="dynamic-result-form__section-title">项目扩展字段</h4>
      <div class="dynamic-result-form__grid">
        <ElFormItem
          v-for="field in extensionFields"
          :key="field.fieldKey"
          :label="field.fieldLabel"
          :prop="field.fieldKey"
          :class="field.fieldType === 'textarea' ? 'dynamic-result-form__item--full' : ''"
        >
          <ElInputNumber
            v-if="field.fieldType === 'number'"
            v-model="localModel[field.fieldKey] as number"
            class="dynamic-result-form__control"
            controls-position="right"
          />
          <ElInput
            v-else
            v-model="localModel[field.fieldKey] as string"
            :type="field.fieldType === 'textarea' ? 'textarea' : 'text'"
            :rows="field.fieldType === 'textarea' ? 3 : undefined"
            :placeholder="field.placeholder"
            :maxlength="field.maxLength"
            show-word-limit
            class="dynamic-result-form__control"
          />
        </ElFormItem>
      </div>
    </section>
  </ElForm>
</template>

<style scoped>
.dynamic-result-form__section + .dynamic-result-form__section {
  margin-block-start: var(--space-4);
}

.dynamic-result-form__section-title {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-weight: 600;
}

.dynamic-result-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.dynamic-result-form__item--full {
  grid-column: 1 / -1;
}

.dynamic-result-form__control {
  width: 100%;
}
</style>
