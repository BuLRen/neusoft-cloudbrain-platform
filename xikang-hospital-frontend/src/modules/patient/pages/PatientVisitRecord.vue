<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import ClinicalRecordAffix from '@/modules/physician/components/ClinicalRecordAffix.vue'
import { clinicalRecordApi, type ClinicalVisitDetail } from '@/shared/api/modules/clinicalRecord'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<ClinicalVisitDetail | null>(null)

const registerId = computed(() => Number(route.params.registerId))

const affixSubtitle = computed(() => {
  if (!detail.value) return ''
  return [detail.value.physicianName, detail.value.visitDate ? String(detail.value.visitDate).slice(0, 16) : ''].filter(Boolean).join(' · ')
})

function formatTime(value?: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadDetail() {
  if (!registerId.value) return
  loading.value = true
  try {
    detail.value = await clinicalRecordApi.patientVisitDetail(registerId.value)
  } catch (error) {
    console.warn('加载就诊病历失败:', error)
    detail.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<template>
  <div class="visit-record">
    <GlassCard class="visit-record__header">
      <button class="back-link" @click="router.push('/patient/records')">← 返回就诊列表</button>
      <div v-if="loading" class="empty">加载中...</div>
      <template v-else-if="detail">
        <div class="visit-record__title-row">
          <div>
            <h2>{{ detail.departmentName || '就诊详情' }}</h2>
            <p>{{ detail.physicianName }} · {{ formatTime(detail.visitDate) }}</p>
          </div>
          <StatusTag :tone="detail.archived ? 'success' : 'warning'">
            {{ detail.archived ? '已归档' : '待医生归档' }}
          </StatusTag>
        </div>
        <p v-if="detail.message" class="visit-record__notice">{{ detail.message }}</p>
        <p v-else-if="!detail.archived" class="visit-record__hint">
          点击右下角「病历本」可查看本次就诊进度；完整病历内容需待医生归档后开放。
        </p>
        <p v-else class="visit-record__hint">点击右下角「病历本」查看本次就诊完整时间线。</p>
      </template>
    </GlassCard>

    <ClinicalRecordAffix
      :register-id="registerId || null"
      mode="patient"
      :subtitle="affixSubtitle"
    />
  </div>
</template>

<style scoped>
.visit-record {
  display: grid;
  gap: var(--space-4);
}

.visit-record__header {
  padding: var(--space-5);
}

.back-link {
  border: none;
  background: none;
  color: var(--color-primary);
  cursor: pointer;
  margin-block-end: var(--space-4);
}

.visit-record__title-row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  align-items: flex-start;
}

.visit-record__title-row h2 {
  margin: 0 0 var(--space-2);
}

.visit-record__title-row p {
  margin: 0;
  color: var(--color-text-muted);
}

.visit-record__notice,
.visit-record__hint {
  margin: var(--space-4) 0 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  font-size: 14px;
  line-height: 1.6;
}

.visit-record__notice {
  background: rgba(255, 193, 7, 0.12);
  color: var(--color-text);
}

.visit-record__hint {
  background: rgba(31, 140, 255, 0.08);
  color: var(--color-text-muted);
}

.empty {
  color: var(--color-text-muted);
}
</style>
