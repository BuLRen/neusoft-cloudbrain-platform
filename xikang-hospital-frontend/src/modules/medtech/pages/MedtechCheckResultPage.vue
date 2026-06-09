<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElMessage } from 'element-plus'
import { medtechApi } from '@/shared/api/modules/medtech'
import { resultFormApi } from '@/shared/api/modules/resultForm'
import DynamicResultForm from '@/shared/components/DynamicResultForm.vue'
import type { ResultFormSchema } from '@/shared/types/resultForm'
import MedtechStepLayout from '../layouts/MedtechStepLayout.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const schema = ref<ResultFormSchema | null>(null)
const formValues = ref<Record<string, unknown>>({})
const formRef = ref<InstanceType<typeof DynamicResultForm>>()

const id = computed(() => Number(route.query.id || 0))

async function loadSchema() {
  if (!id.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    schema.value = await resultFormApi.resolveCheckForm({ checkRequestId: id.value })
    formValues.value = { ...(schema.value.existingValues ?? {}) }
  } catch {
    schema.value = null
    errorMessage.value = '结果表单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!id.value || !schema.value) return
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    await medtechApi.submitCheckResult(id.value, { values: formValues.value })
    ElMessage.success('结果已提交')
    router.push('/medtech/check-queue')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadSchema()
})
</script>

<template>
  <MedtechStepLayout
    :step="3"
    :total-steps="3"
    title="结果录入"
    description="第三步：按项目配置动态录入检查结果。"
    prev-path="/medtech/check-start"
  >
    <div v-if="schema" class="result-meta">
      <p><strong>检查项目：</strong>{{ schema.techName || '-' }}</p>
      <p><strong>表单分类：</strong>{{ schema.categoryName || schema.categoryCode }}</p>
      <p v-if="schema.extensionFieldCount" class="result-meta__hint">
        含 {{ schema.baseFieldCount }} 个基础字段、{{ schema.extensionFieldCount }} 个项目扩展字段
      </p>
    </div>

    <ElAlert
      v-if="errorMessage"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
      class="error-alert"
    />

    <ElEmpty v-else-if="!loading && !schema" description="未找到结果表单配置" />

    <DynamicResultForm
      v-else-if="schema"
      ref="formRef"
      v-loading="loading"
      v-model="formValues"
      :fields="schema.fields"
      :base-field-count="schema.baseFieldCount"
    />

    <div class="actions">
      <ElButton @click="router.push('/medtech/check-queue')">取消</ElButton>
      <ElButton type="primary" :loading="loading" :disabled="!schema" @click="submit">结果提交</ElButton>
    </div>
  </MedtechStepLayout>
</template>

<style scoped>
.result-meta {
  display: grid;
  gap: var(--space-1);
  margin-block-end: var(--space-4);
  color: var(--color-text-muted);
}

.result-meta__hint {
  font-size: var(--font-size-sm);
}

.error-alert {
  margin-block-end: var(--space-4);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
}
</style>
