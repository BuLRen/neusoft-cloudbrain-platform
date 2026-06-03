<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { medtechApi } from '@/shared/api/modules/medtech'
import MedtechStepLayout from '../layouts/MedtechStepLayout.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const id = computed(() => Number(route.query.id || 0))

const form = reactive({
  result: '',
  findings: '',
  conclusion: '',
  impression: '',
})

async function submit() {
  if (!id.value) return
  loading.value = true
  try {
    await medtechApi.submitCheckResult(id.value, {
      result: form.result,
      findings: form.findings || undefined,
      conclusion: form.conclusion || undefined,
      impression: form.impression || undefined,
    })
    ElMessage.success('结果已提交')
    router.push('/medtech/check-queue')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 可选：后续可通过 checkReport 预填展示
})
</script>

<template>
  <MedtechStepLayout
    :step="3"
    :total-steps="3"
    title="结果录入"
    description="第三步：录入检查结果并提交。"
    prev-path="/medtech/check-start"
  >
    <p style="color: var(--color-text-muted)">当前检查申请 ID：{{ id || '-' }}</p>
    <ElForm label-position="top" class="form-grid">
      <ElFormItem label="检查结果（result）">
        <ElInput v-model="form.result" type="textarea" :rows="3" />
      </ElFormItem>
      <ElFormItem label="所见（findings）">
        <ElInput v-model="form.findings" type="textarea" :rows="2" />
      </ElFormItem>
      <ElFormItem label="结论（conclusion）">
        <ElInput v-model="form.conclusion" type="textarea" :rows="2" />
      </ElFormItem>
      <ElFormItem label="印象（impression）">
        <ElInput v-model="form.impression" type="textarea" :rows="2" />
      </ElFormItem>
    </ElForm>
    <div class="actions">
      <ElButton @click="router.push('/medtech/check-queue')">取消</ElButton>
      <ElButton type="primary" :loading="loading" :disabled="!id" @click="submit">结果提交</ElButton>
    </div>
  </MedtechStepLayout>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  margin-block-start: var(--space-4);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
}
</style>

