<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElMessage } from 'element-plus'
import { medtechApi } from '@/shared/api/modules/medtech'
import MedtechStepLayout from '../layouts/MedtechStepLayout.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const id = computed(() => Number(route.query.id || 0))

async function startCheck() {
  if (!id.value) return
  loading.value = true
  try {
    await medtechApi.startCheck(id.value)
    ElMessage.success('已开始检查')
    router.push({ path: '/medtech/check-result', query: { id: String(id.value) } })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 允许直接打开；无 id 则提示返回
})
</script>

<template>
  <MedtechStepLayout
    :step="2"
    :total-steps="3"
    title="开始检查"
    description="第二步：确认后开始检查，进入结果录入。"
    prev-path="/medtech/check-queue"
    next-path="/medtech/check-result"
  >
    <p style="color: var(--color-text-muted)">当前检查申请 ID：{{ id || '-' }}</p>
    <div class="actions">
      <ElButton @click="router.push('/medtech/check-queue')">返回列表</ElButton>
      <ElButton type="primary" :loading="loading" :disabled="!id" @click="startCheck">开始检查</ElButton>
    </div>
  </MedtechStepLayout>
</template>

<style scoped>
.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
}
</style>

