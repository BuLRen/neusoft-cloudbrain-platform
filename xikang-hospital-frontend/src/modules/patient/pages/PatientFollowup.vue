<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'

const activeTab = ref('plans')

// 随访计划（待后端实现后通过 API 获取）
const followupPlans = ref<any[]>([])

// 用药提醒（待后端实现后通过 API 获取）
const medications = ref<any[]>([])

// 反馈表单
const feedbackForm = ref({
  symptom: '',
  feedback: '',
  rating: 5,
})
const feedbackLoading = ref(false)

function statusTone(status: string) {
  if (status === 'completed') return 'success'
  if (status === 'pending') return 'warning'
  return 'neutral'
}

function statusText(status: string) {
  if (status === 'completed') return '已完成'
  if (status === 'pending') return '待完成'
  return '进行中'
}

async function submitFeedback() {
  if (!feedbackForm.value.symptom.trim()) {
    ElMessage.warning('请填写症状描述')
    return
  }
  feedbackLoading.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 800))
    ElMessage.success('反馈提交成功')
    feedbackForm.value = { symptom: '', feedback: '', rating: 5 }
  } finally {
    feedbackLoading.value = false
  }
}

function markComplete(planId: number) {
  const plan = followupPlans.value.find(p => p.id === planId)
  if (plan) {
    plan.status = 'completed'
    ElMessage.success('已标记为完成')
  }
}
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
      </div>
    </GlassCard>

    <!-- 随访计划 -->
    <GlassCard v-if="activeTab === 'plans'" class="plans-card">
      <div class="section-header">
        <h3>随访计划</h3>
        <StatusTag tone="neutral">{{ followupPlans.length }} 项计划</StatusTag>
      </div>
      <div class="plans-list">
        <div v-for="plan in followupPlans" :key="plan.id" class="plan-item">
          <div class="plan-status">
            <StatusTag :tone="statusTone(plan.status)">
              {{ statusText(plan.status) }}
            </StatusTag>
          </div>
          <div class="plan-info">
            <div class="plan-main">
              <strong class="plan-title">{{ plan.title }}</strong>
              <span class="plan-doctor">{{ plan.doctor }}</span>
            </div>
            <div class="plan-meta">
              <span>📅 {{ plan.dueDate }}</span>
              <span v-if="plan.notes">📝 {{ plan.notes }}</span>
            </div>
          </div>
          <div class="plan-actions">
            <button
              v-if="plan.status === 'pending'"
              class="btn-outline"
              @click="markComplete(plan.id)"
            >
              标记完成
            </button>
            <button class="btn-outline">详情</button>
          </div>
        </div>
        <div v-if="!followupPlans.length" class="empty-state">
          <p>暂无随访计划</p>
        </div>
      </div>
    </GlassCard>

    <!-- 用药提醒 -->
    <GlassCard v-if="activeTab === 'medications'" class="medications-card">
      <div class="section-header">
        <h3>当前用药</h3>
        <StatusTag tone="warning">{{ medications.length }} 种药物</StatusTag>
      </div>
      <div class="medications-list">
        <div v-for="med in medications" :key="med.id" class="medication-item">
          <div class="med-header">
            <strong class="med-name">{{ med.name }}</strong>
            <span class="med-dosage">{{ med.dosage }}</span>
          </div>
          <div class="med-details">
            <div class="med-row">
              <span class="med-label">用法：</span>
              <span>{{ med.frequency }}</span>
            </div>
            <div class="med-row">
              <span class="med-label">疗程：</span>
              <span>{{ med.duration }}</span>
            </div>
            <div class="med-row">
              <span class="med-label">开始日期：</span>
              <span>{{ med.startDate }}</span>
            </div>
            <div class="med-row">
              <span class="med-label">提醒时间：</span>
              <div class="reminder-times">
                <StatusTag
                  v-for="time in med.reminderTimes"
                  :key="time"
                  tone="primary"
                >
                  {{ time }}
                </StatusTag>
              </div>
            </div>
          </div>
          <div class="med-actions">
            <button class="btn-primary">查看用药指导</button>
          </div>
        </div>
        <div v-if="!medications.length" class="empty-state">
          <p>暂无用药提醒</p>
        </div>
      </div>
    </GlassCard>

    <!-- 健康反馈 -->
    <GlassCard v-if="activeTab === 'feedback'" class="feedback-card">
      <div class="section-header">
        <h3>提交健康反馈</h3>
        <p class="feedback-tip">定期反馈有助于医生了解您的康复情况</p>
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
.feedback-card {
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

/* 计划列表 */
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

.plan-actions {
  display: flex;
  gap: var(--space-2);
}

/* 药物列表 */
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
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.med-name {
  font-size: 18px;
  font-weight: 600;
}

.med-dosage {
  color: var(--color-primary);
  font-weight: 600;
}

.med-details {
  display: grid;
  gap: var(--space-3);
}

.med-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

.med-label {
  color: var(--color-text-muted);
  font-size: 13px;
  min-width: 70px;
}

.reminder-times {
  display: flex;
  gap: var(--space-2);
}

.med-actions {
  margin-top: var(--space-4);
}

/* 反馈表单 */
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

.form-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
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
  transition: color var(--duration-fast);
}

.star-btn.active {
  color: #f59e0b;
}

.star-btn:hover {
  color: #f59e0b;
}

.form-actions {
  display: flex;
  gap: var(--space-3);
}

/* 共享样式 */
.btn-primary {
  padding: var(--space-3) var(--space-5);
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.btn-primary:hover {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-outline {
  padding: var(--space-2) var(--space-4);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.btn-outline:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.empty-state {
  text-align: center;
  padding: var(--space-8);
  color: var(--color-text-muted);
}
</style>