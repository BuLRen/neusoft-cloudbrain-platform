<script setup lang="ts">
import { ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import {
  FOLLOW_UP_TYPES,
  FOLLOW_UP_TYPE_LABELS,
  MEDICATION_ADHERENCE,
  MEDICATION_ADHERENCE_LABELS,
} from '@/shared/constants/pharmacy'
import type { FollowUpFeedback, FollowUpPlan } from '@/shared/types/pharmacy'

const patientId = ref<number | undefined>()
const plans = ref<FollowUpPlan[]>([])
const loading = ref(false)
const loaded = ref(false)

// 反馈弹窗
const feedbackVisible = ref(false)
const feedbackPlanId = ref<number | undefined>()
const feedbackSubmitting = ref(false)
const feedbackForm = ref<FollowUpFeedback>({
  followUpType: 'MEDICATION_REMINDER',
  medicationAdherence: 'COMPLIANT',
  symptomScoreCurrent: undefined,
  aiAssessment: '',
  sideEffects: [],
})

async function load() {
  if (!patientId.value) {
    ElMessage.warning('请输入患者 ID')
    return
  }
  loading.value = true
  try {
    plans.value = await pharmacyApi.patientFollowUpPlans(patientId.value)
    loaded.value = true
  } finally {
    loading.value = false
  }
}

function statusTone(status?: string) {
  if (!status) return 'primary'
  if (status.includes('完成') || status === 'completed') return 'success'
  if (status.includes('取消') || status.includes('失败')) return 'danger'
  return 'primary'
}

function openFeedback(planId: number) {
  feedbackPlanId.value = planId
  feedbackForm.value = {
    followUpType: 'MEDICATION_REMINDER',
    medicationAdherence: 'COMPLIANT',
    symptomScoreCurrent: undefined,
    aiAssessment: '',
    sideEffects: [],
  }
  feedbackVisible.value = true
}

async function submitFeedback() {
  if (!feedbackPlanId.value) return
  feedbackSubmitting.value = true
  try {
    await pharmacyApi.submitFollowUpFeedback(feedbackPlanId.value, feedbackForm.value)
    ElMessage.success('随访反馈已提交')
    feedbackVisible.value = false
  } finally {
    feedbackSubmitting.value = false
  }
}

async function retryFollowUpCreation(prescriptionId: number) {
  try {
    await pharmacyApi.retryFollowUp(prescriptionId)
    ElMessage.success('随访计划已重新创建')
    if (patientId.value) void load()
  } catch {
    // 拦截器统一报错
  }
}
</script>

<template>
  <div class="followup-page u-page-grid">
    <PageHeader
      title="患者随访"
      description="查看每位患者的 AI 随访计划、录入反馈、重试创建失败的随访。随访在发药后自动产生。"
      eyebrow="Role B / Pharmacy · ④"
    />

    <GlassCard class="fu-card">
      <!-- 按 patientId 查询 -->
      <div class="filter-bar">
        <ElInputNumber
          v-model="patientId"
          :min="1"
          :controls="false"
          placeholder="请输入患者 ID"
          class="field-id"
        />
        <ElButton type="primary" :loading="loading" @click="load">查询随访计划</ElButton>
        <span v-if="loaded" class="filter-summary">
          共 <strong>{{ plans.length }}</strong> 条随访计划
        </span>
      </div>

      <ElEmpty
        v-if="loaded && plans.length === 0"
        description="该患者暂无随访计划（发药后会自动创建）"
      />
      <ElTable
        v-else-if="loaded"
        v-loading="loading"
        :data="plans"
      >
        <ElTableColumn prop="planId" label="计划 ID" min-width="90" />
        <ElTableColumn prop="patientName" label="患者" min-width="100" />
        <ElTableColumn prop="prescriptionId" label="处方 ID" min-width="90" />
        <ElTableColumn label="状态" min-width="110">
          <template #default="{ row }">
            <StatusTag :tone="statusTone(row.status)">{{ row.status || '-' }}</StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="currentStage" label="当前阶段" min-width="140" />
        <ElTableColumn prop="nextFollowUpTime" label="下次随访" min-width="160" />
        <ElTableColumn label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <ElButton link size="small" @click="openFeedback(row.planId || row.id)">
              录入反馈
            </ElButton>
            <ElButton
              v-if="row.prescriptionId"
              link
              size="small"
              @click="retryFollowUpCreation(row.prescriptionId)"
            >重试创建</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElEmpty v-else description="输入患者 ID 后查询随访计划" />
    </GlassCard>

    <!-- 反馈录入弹窗 -->
    <ElDialog v-model="feedbackVisible" title="录入随访反馈" width="560px">
      <ElForm label-width="100px">
        <ElFormItem label="随访类型">
          <ElSelect v-model="feedbackForm.followUpType">
            <ElOption
              v-for="t in FOLLOW_UP_TYPES"
              :key="t"
              :label="FOLLOW_UP_TYPE_LABELS[t]"
              :value="t"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="依从性">
          <ElSelect v-model="feedbackForm.medicationAdherence">
            <ElOption
              v-for="a in MEDICATION_ADHERENCE"
              :key="a"
              :label="MEDICATION_ADHERENCE_LABELS[a]"
              :value="a"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="症状评分">
          <ElInputNumber
            v-model="feedbackForm.symptomScoreCurrent"
            :min="0"
            :max="10"
            :controls="false"
          />
        </ElFormItem>
        <ElFormItem label="AI 评估">
          <ElInput
            v-model="feedbackForm.aiAssessment"
            type="textarea"
            :rows="3"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="feedbackVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="feedbackSubmitting" @click="submitFeedback">
          提交反馈
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.fu-card {
  padding: var(--space-5);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.field-id {
  width: 200px;
}

.filter-summary {
  color: var(--color-text-muted);
  font-size: 13px;
}

@media (max-width: 720px) {
  .field-id {
    width: 100%;
    flex-basis: 100%;
  }
}
</style>
