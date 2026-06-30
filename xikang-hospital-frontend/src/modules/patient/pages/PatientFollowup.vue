<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import PatientCommunicationPanel from '@/modules/patient/components/PatientCommunicationPanel.vue'
import GlucoseForecastPanel from '@/modules/medtech/follow-up/components/GlucoseForecastPanel.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { useAuthStore } from '@/app/stores/auth'
import type {
  PatientFollowUpPlanItem,
  PatientFollowUpRecordItem,
  PatientMedicationItem,
} from '@/shared/types/medtechFollowUp'

const authStore = useAuthStore()
const activeTab = ref('plans')
const loading = ref(false)

const followupPlans = ref<PatientFollowUpPlanItem[]>([])
const medications = ref<PatientMedicationItem[]>([])
const feedbackRecords = ref<PatientFollowUpRecordItem[]>([])

const patientId = computed(() => authStore.currentPatientId || authStore.currentPatient?.patientId)

const feedbackForm = ref({
  symptom: '',
  feedback: '',
  rating: 5,
})
const feedbackLoading = ref(false)

function statusTone(status?: string) {
  if (status === 'completed') return 'success'
  if (status === 'pending' || status === 'overdue') return 'warning'
  return 'neutral'
}

function statusText(status?: string) {
  if (status === 'completed') return '已完成'
  if (status === 'pending') return '待完成'
  if (status === 'overdue') return '已逾期'
  if (status === 'cancelled') return '已取消'
  return '进行中'
}

function planTitle(plan: PatientFollowUpPlanItem) {
  const typeMap: Record<string, string> = {
    medication: '用药随访',
    side_effect: '副作用随访',
    recovery: '康复随访',
    revisit: '复诊提醒',
  }
  return typeMap[plan.followUpType ?? ''] ?? '随访计划'
}

async function loadPortalData() {
  if (!patientId.value) return
  loading.value = true
  try {
    const params = { patientId: patientId.value }
    const [plans, meds, records] = await Promise.all([
      medtechFollowUpApi.listPatientPlans(params),
      medtechFollowUpApi.listPatientMedications(params),
      medtechFollowUpApi.listPatientRecords(params),
    ])
    followupPlans.value = plans
    medications.value = meds
    feedbackRecords.value = records
  } catch {
    ElMessage.error('加载随访数据失败')
  } finally {
    loading.value = false
  }
}

async function submitFeedback() {
  if (!feedbackForm.value.symptom.trim()) {
    ElMessage.warning('请填写症状描述')
    return
  }
  const registerId = followupPlans.value[0]?.registerId
  if (!registerId) {
    ElMessage.warning('暂无关联就诊记录，无法提交反馈')
    return
  }
  feedbackLoading.value = true
  try {
    await medtechFollowUpApi.submitPatientFeedback({
      registerId,
      followUpPlanId: followupPlans.value.find((p) => p.planStatus !== 'completed')?.id,
      symptom: feedbackForm.value.symptom,
      feedback: feedbackForm.value.feedback,
      rating: feedbackForm.value.rating,
    })
    ElMessage.success('反馈提交成功')
    feedbackForm.value = { symptom: '', feedback: '', rating: 5 }
    await loadPortalData()
  } catch {
    ElMessage.error('反馈提交失败')
  } finally {
    feedbackLoading.value = false
  }
}

async function markComplete(planId: number) {
  try {
    await medtechFollowUpApi.completePatientPlan(planId)
    ElMessage.success('已标记为完成')
    await loadPortalData()
  } catch {
    ElMessage.error('操作失败')
  }
}

watch(activeTab, (tab) => {
  if (tab !== 'communication') {
    void loadPortalData()
  }
})

onMounted(() => {
  void loadPortalData()
})
</script>

<template>
  <div class="patient-followup">
    <GlassCard class="followup-tabs-card">
      <div class="tabs-header">
        <h2>随访管理</h2>
        <p>管理您的随访计划、用药提醒和健康反馈</p>
      </div>
      <div class="tabs-nav">
        <button
          :class="['tab-btn', { active: activeTab === 'plans' }]"
          @click="activeTab = 'plans'"
        >
          随访计划
        </button>
        <button
          :class="['tab-btn', { active: activeTab === 'medications' }]"
          @click="activeTab = 'medications'"
        >
          用药提醒
        </button>
        <button
          :class="['tab-btn', { active: activeTab === 'feedback' }]"
          @click="activeTab = 'feedback'"
        >
          健康反馈
        </button>
        <button
          :class="['tab-btn', { active: activeTab === 'glucose' }]"
          @click="activeTab = 'glucose'"
        >
          血糖预测
        </button>
        <button
          :class="['tab-btn', { active: activeTab === 'communication' }]"
          @click="activeTab = 'communication'"
        >
          医患沟通
        </button>
      </div>
    </GlassCard>

    <GlassCard v-if="activeTab === 'plans'" class="plans-card" v-loading="loading">
      <div class="section-header">
        <h3>随访计划</h3>
        <StatusTag tone="neutral">{{ followupPlans.length }} 项计划</StatusTag>
      </div>
      <div class="plans-list">
        <div v-for="plan in followupPlans" :key="plan.id" class="plan-item">
          <div class="plan-status">
            <StatusTag :tone="statusTone(plan.planStatus)">
              {{ statusText(plan.planStatus) }}
            </StatusTag>
          </div>
          <div class="plan-info">
            <div class="plan-main">
              <strong class="plan-title">{{ planTitle(plan) }}</strong>
              <span class="plan-doctor">{{ plan.doctorName ?? '随访医生' }}</span>
            </div>
            <div class="plan-meta">
              <span v-if="plan.plannedDate">📅 {{ plan.plannedDate }}</span>
              <span v-if="plan.contentTemplate">📝 含用药/康复指导</span>
            </div>
          </div>
          <div class="plan-actions">
            <button
              v-if="plan.planStatus === 'pending' || plan.planStatus === 'overdue'"
              class="btn-outline"
              @click="markComplete(plan.id)"
            >
              标记完成
            </button>
          </div>
        </div>
        <div v-if="!followupPlans.length" class="empty-state">
          <p>暂无随访计划</p>
        </div>
      </div>
    </GlassCard>

    <GlassCard v-if="activeTab === 'medications'" class="medications-card" v-loading="loading">
      <div class="section-header">
        <h3>当前用药</h3>
        <StatusTag tone="warning">{{ medications.length }} 种药物</StatusTag>
      </div>
      <div class="medications-list">
        <div v-for="med in medications" :key="med.id" class="medication-item">
          <div class="med-header">
            <strong class="med-name">{{ med.drugName ?? '药品' }}</strong>
            <span class="med-dosage">{{ med.drugNumber ?? '—' }}</span>
          </div>
          <div class="med-details">
            <div class="med-row">
              <span class="med-label">用法：</span>
              <span>{{ med.drugUsage ?? '遵医嘱' }}</span>
            </div>
          </div>
        </div>
        <div v-if="!medications.length" class="empty-state">
          <p>暂无用药提醒</p>
        </div>
      </div>
    </GlassCard>

    <GlassCard v-if="activeTab === 'feedback'" class="feedback-card">
      <div class="section-header">
        <h3>提交健康反馈</h3>
        <p class="feedback-tip">定期反馈有助于医生了解您的康复情况</p>
      </div>
      <div v-if="feedbackRecords.length" class="feedback-history">
        <h4>历史反馈</h4>
        <div v-for="record in feedbackRecords.slice(0, 5)" :key="record.id" class="feedback-history-item">
          <span>{{ record.followUpTime?.slice(0, 10) ?? '—' }}</span>
          <p>{{ record.patientFeedback }}</p>
        </div>
      </div>
      <div class="feedback-form">
        <div class="form-group">
          <label class="form-label">症状描述</label>
          <textarea
            v-model="feedbackForm.symptom"
            class="form-textarea"
            placeholder="请描述您目前的症状，如：无不适 / 胃痛减轻但仍有反酸..."
            rows="4"
          ></textarea>
        </div>
        <div class="form-group">
          <label class="form-label">反馈内容（可选）</label>
          <textarea
            v-model="feedbackForm.feedback"
            class="form-textarea"
            placeholder="如有其他需要补充的内容，请在此说明..."
            rows="3"
          ></textarea>
        </div>
        <div class="form-group">
          <label class="form-label">整体评价</label>
          <div class="rating-stars">
            <button
              v-for="star in 5"
              :key="star"
              :class="['star-btn', { active: star <= feedbackForm.rating }]"
              @click="feedbackForm.rating = star"
            >
              {{ star <= feedbackForm.rating ? '★' : '☆' }}
            </button>
          </div>
        </div>
        <div class="form-actions">
          <button
            class="btn-primary"
            :disabled="feedbackLoading"
            @click="submitFeedback"
          >
            {{ feedbackLoading ? '提交中...' : '提交反馈' }}
          </button>
        </div>
      </div>
    </GlassCard>

    <PatientCommunicationPanel v-if="activeTab === 'communication'" />

    <GlassCard v-if="activeTab === 'glucose'" class="glucose-card">
      <GlucoseForecastPanel
        v-if="patientId"
        :patient-id="patientId"
        :register-id="followupPlans[0]?.registerId"
        mode="patient"
        compact
      />
    </GlassCard>
  </div>
</template>

<style scoped>
.patient-followup {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.followup-tabs-card {
  padding: var(--space-5);
}

.tabs-header {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
}

.tabs-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.tabs-header p {
  color: var(--color-text-muted);
  margin: 0;
}

.tabs-nav {
  display: flex;
  gap: var(--space-2);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: var(--space-3);
}

.tab-btn {
  padding: var(--space-2) var(--space-4);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.tab-btn:hover {
  color: var(--color-primary);
}

.tab-btn.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.plans-card,
.medications-card,
.feedback-card,
.glucose-card {
  padding: var(--space-5);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-5);
}

.section-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.feedback-tip {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: var(--space-2) 0 0;
}

.feedback-history {
  margin-bottom: var(--space-5);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.feedback-history h4 {
  margin: 0 0 var(--space-3);
  font-size: 14px;
}

.feedback-history-item {
  padding: var(--space-2) 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

.feedback-history-item p {
  margin: var(--space-1) 0 0;
  color: var(--color-text);
}

.plans-list {
  display: grid;
  gap: var(--space-4);
}

.plan-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

.plan-status {
  flex-shrink: 0;
}

.plan-info {
  flex: 1;
  display: grid;
  gap: var(--space-2);
}

.plan-main {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.plan-title {
  font-size: 16px;
  font-weight: 600;
}

.plan-doctor {
  color: var(--color-text-muted);
  font-size: 13px;
}

.plan-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  font-size: 13px;
  color: var(--color-text-muted);
}

.medications-list {
  display: grid;
  gap: var(--space-4);
}

.medication-item {
  padding: var(--space-5);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

.med-header {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.med-name {
  font-size: 18px;
  font-weight: 600;
}

.med-dosage {
  color: var(--color-primary);
  font-weight: 600;
}

.med-row {
  display: flex;
  gap: var(--space-2);
}

.med-label {
  color: var(--color-text-muted);
  min-width: 48px;
}

.feedback-form {
  display: grid;
  gap: var(--space-5);
}

.form-group {
  display: grid;
  gap: var(--space-2);
}

.form-label {
  font-weight: 500;
  font-size: 14px;
}

.form-textarea {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  font-family: inherit;
}

.rating-stars {
  display: flex;
  gap: var(--space-2);
}

.star-btn {
  font-size: 28px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--color-border);
}

.star-btn.active {
  color: #f59e0b;
}

.btn-primary {
  padding: var(--space-3) var(--space-5);
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.btn-outline {
  padding: var(--space-2) var(--space-4);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.empty-state {
  text-align: center;
  padding: var(--space-8);
  color: var(--color-text-muted);
}
</style>
