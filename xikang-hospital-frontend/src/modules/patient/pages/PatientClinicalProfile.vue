<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GlassCard from '@/shared/components/GlassCard.vue'
import { clinicalRecordApi, type PatientClinicalProfile } from '@/shared/api/modules/clinicalRecord'
import { useAuthStore } from '@/app/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const profile = ref<PatientClinicalProfile | null>(null)

const patientId = computed(() => authStore.currentPatientId || authStore.currentPatient?.patientId)

async function loadProfile() {
  if (!patientId.value) return
  loading.value = true
  try {
    profile.value = await clinicalRecordApi.patientProfile(patientId.value)
  } catch (error) {
    console.warn('加载长期档案失败:', error)
  } finally {
    loading.value = false
  }
}

function formatTime(value?: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(loadProfile)
</script>

<template>
  <div class="clinical-profile">
    <GlassCard class="clinical-profile__card">
      <button class="back-link" @click="router.push('/patient/records')">← 返回电子病历</button>
      <h2>长期健康档案</h2>
      <p class="subtitle">汇总跨就诊的过敏史、慢病与历次确诊摘要。</p>

      <div v-if="loading" class="empty">加载中...</div>
      <template v-else-if="profile">
        <section class="profile-section">
          <h3>过敏史</h3>
          <p>{{ profile.allergySummary || '暂无记录' }}</p>
        </section>
        <section class="profile-section">
          <h3>慢病 / 长期问题</h3>
          <p>{{ profile.chronicConditions || '暂无记录' }}</p>
        </section>
        <section class="profile-section">
          <h3>历次确诊摘要</h3>
          <pre class="profile-pre">{{ profile.pastDiagnosisSummary || '医生归档就诊后会自动追加摘要。' }}</pre>
        </section>
        <p class="meta">最近就诊：{{ formatTime(profile.lastVisitAt) }}</p>
      </template>
    </GlassCard>
  </div>
</template>

<style scoped>
.clinical-profile__card {
  padding: var(--space-5);
}

.back-link {
  border: none;
  background: none;
  color: var(--color-primary);
  cursor: pointer;
  margin-block-end: var(--space-4);
}

.subtitle {
  margin: 0 0 var(--space-5);
  color: var(--color-text-muted);
}

.profile-section {
  margin-block-end: var(--space-5);
}

.profile-section h3 {
  margin: 0 0 var(--space-2);
  font-size: 16px;
}

.profile-section p,
.profile-pre {
  margin: 0;
  line-height: 1.7;
  color: var(--color-text);
}

.profile-pre {
  white-space: pre-wrap;
  font-family: inherit;
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: rgba(31, 140, 255, 0.06);
}

.meta {
  color: var(--color-text-soft);
  font-size: 13px;
}

.empty {
  color: var(--color-text-muted);
}
</style>
