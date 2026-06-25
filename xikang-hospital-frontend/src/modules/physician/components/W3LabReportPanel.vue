<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import { ElCollapse, ElCollapseItem, ElIcon, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { Document, FirstAidKit, InfoFilled, List, MagicStick, Odometer, Picture } from '@element-plus/icons-vue'
import type { W3ExamSummary, W3Output } from '@/shared/api/modules/physician'
import { statusLabel, statusTone } from '@/shared/types/simulatedCheckResult'
import {
  countRiskLevels,
  hasIndicatorTable,
  partitionIndicatorRows,
  sortExamSummaries,
} from '@/shared/types/w3Result'

const props = defineProps<{
  data?: W3Output | null
}>()

const sortedSummaries = computed(() => sortExamSummaries(props.data?.examSummaries))
const riskCounts = computed(() => countRiskLevels(props.data?.examSummaries))
const overallExpanded = ref(['overall'])

function resolveRiskLabel(level?: string): string {
  if (level === 'high' || level === 'danger') return '高风险'
  if (level === 'attention' || level === 'warning') return '需关注'
  return '正常'
}

function resolveRiskTone(level?: string): 'success' | 'warning' | 'danger' {
  if (level === 'high' || level === 'danger') return 'danger'
  if (level === 'attention' || level === 'warning') return 'warning'
  return 'success'
}

function formatValue(row: { value: string | number; unit?: string }) {
  const value = row.value ?? ''
  return row.unit ? `${value} ${row.unit}` : String(value)
}

function examSections(summary: W3ExamSummary) {
  return partitionIndicatorRows(summary.indicatorRows)
}

function examIcon(name: string): Component {
  if (/CT|MRI|X|超声|影像|片|胸|肺/.test(name)) return Picture
  if (/血|化验|检验|蛋白|常规|尿|CRP|反应/.test(name)) return Odometer
  return FirstAidKit
}

function examIconTone(level?: string): 'attention' | 'danger' | 'normal' {
  const normalized = String(level || 'normal').toLowerCase()
  if (normalized === 'high' || normalized === 'danger') return 'danger'
  if (normalized === 'attention' || normalized === 'warning') return 'attention'
  return 'normal'
}
</script>

<template>
  <div v-if="data" class="w3-report">
    <div v-if="data.clinicalImpression" class="w3-report__impression">
      <span class="w3-report__impression-icon" aria-hidden="true">
        <ElIcon :size="22"><Document /></ElIcon>
      </span>
      <div class="w3-report__impression-body">
        <span class="w3-report__impression-label">临床印象</span>
        <p class="w3-report__impression-text">{{ data.clinicalImpression }}</p>
        <div v-if="sortedSummaries.length" class="w3-report__impression-stats">
          <span v-if="riskCounts.attention > 0" class="w3-report__stat w3-report__stat--attention">
            <span class="w3-report__stat-dot" aria-hidden="true" />
            {{ riskCounts.attention }} 项需关注
          </span>
          <span v-if="riskCounts.normal > 0" class="w3-report__stat w3-report__stat--normal">
            <span class="w3-report__stat-dot" aria-hidden="true" />
            {{ riskCounts.normal }} 项正常
          </span>
        </div>
      </div>
    </div>

    <section
      v-for="exam in sortedSummaries"
      :key="exam.techName"
      class="w3-report__exam"
      :data-risk="exam.riskLevel || 'normal'"
    >
      <header class="w3-report__exam-head">
        <span class="w3-report__exam-icon" :data-tone="examIconTone(exam.riskLevel)" aria-hidden="true">
          <ElIcon :size="20"><component :is="examIcon(exam.techName)" /></ElIcon>
        </span>
        <div class="w3-report__exam-head-body">
          <div class="w3-report__exam-title-wrap">
            <strong class="w3-report__exam-title">{{ exam.techName }}</strong>
            <ElTag :type="resolveRiskTone(exam.riskLevel)" size="small" effect="light" round>
              {{ resolveRiskLabel(exam.riskLevel) }}
            </ElTag>
          </div>
          <p v-if="exam.clinicalImpression" class="w3-report__exam-impression">{{ exam.clinicalImpression }}</p>
        </div>
      </header>

      <template v-if="hasIndicatorTable(exam)">
        <div v-if="examSections(exam).abnormal.length" class="w3-report__table-block">
          <h4 class="w3-report__table-title">异常指标 ({{ examSections(exam).abnormal.length }})</h4>
          <ElTable :data="examSections(exam).abnormal" border size="small" class="w3-report__table">
            <ElTableColumn prop="itemName" label="项目" min-width="110" />
            <ElTableColumn label="结果" min-width="110">
              <template #default="{ row }">{{ formatValue(row) }}</template>
            </ElTableColumn>
            <ElTableColumn prop="referenceRange" label="参考范围" min-width="100" />
            <ElTableColumn label="状态" width="88">
              <template #default="{ row }">
                <ElTag :type="statusTone(row.status)" size="small" effect="light" round>
                  {{ statusLabel(row.status) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="aiNote" label="AI 解读" min-width="160" show-overflow-tooltip />
          </ElTable>
        </div>

        <ElCollapse v-if="examSections(exam).normal.length" class="w3-report__collapse w3-report__collapse--expandable">
          <ElCollapseItem name="normal">
            <template #title>
              <div class="w3-report__collapse-trigger">
                <span class="w3-report__collapse-trigger-main">
                  <ElIcon class="w3-report__collapse-trigger-icon" aria-hidden="true"><List /></ElIcon>
                  <span class="w3-report__collapse-trigger-label">
                    正常指标 ({{ examSections(exam).normal.length }})
                  </span>
                </span>
                <span class="w3-report__collapse-trigger-hint">点击展开查看</span>
              </div>
            </template>
            <ElTable :data="examSections(exam).normal" border size="small" class="w3-report__table">
              <ElTableColumn prop="itemName" label="项目" min-width="110" />
              <ElTableColumn label="结果" min-width="110">
                <template #default="{ row }">{{ formatValue(row) }}</template>
              </ElTableColumn>
              <ElTableColumn prop="referenceRange" label="参考范围" min-width="100" />
              <ElTableColumn label="状态" width="88">
                <template #default="{ row }">
                  <ElTag :type="statusTone(row.status)" size="small" effect="light" round>
                    {{ statusLabel(row.status) }}
                  </ElTag>
                </template>
              </ElTableColumn>
            </ElTable>
          </ElCollapseItem>
        </ElCollapse>
      </template>

      <ul v-else-if="exam.keyFindings?.length" class="w3-report__findings">
        <li v-for="(finding, idx) in exam.keyFindings" :key="`${exam.techName}-finding-${idx}`">{{ finding }}</li>
      </ul>

      <div v-if="exam.interpretation" class="w3-report__info-box">
        <ElIcon class="w3-report__info-box-icon" aria-hidden="true"><InfoFilled /></ElIcon>
        <p class="w3-report__info-box-text">{{ exam.interpretation }}</p>
      </div>
    </section>

    <ElCollapse
      v-if="data.overallAnalysis"
      v-model="overallExpanded"
      class="w3-report__collapse w3-report__collapse--overall"
    >
      <ElCollapseItem name="overall">
        <template #title>
          <div class="w3-report__overall-head">
            <span class="w3-report__overall-icon" aria-hidden="true">
              <ElIcon :size="18"><MagicStick /></ElIcon>
            </span>
            <span class="w3-report__overall-title">综合解读</span>
          </div>
        </template>
        <p class="w3-report__overall">{{ data.overallAnalysis }}</p>
      </ElCollapseItem>
    </ElCollapse>

    <p v-if="data.explicitNonDiagnosis !== false" class="w3-report__notice">
      <ElIcon class="w3-report__notice-icon" aria-hidden="true"><MagicStick /></ElIcon>
      以上为 AI 辅助解读，不构成最终诊断。
    </p>
  </div>
</template>

<style scoped>
.w3-report {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* ── 临床印象 ── */
.w3-report__impression {
  display: flex;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 1px solid rgba(124, 92, 255, 0.16);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(circle at 100% 0%, rgba(124, 92, 255, 0.1), transparent 42%),
    linear-gradient(135deg, rgba(124, 92, 255, 0.07) 0%, rgba(247, 251, 255, 0.95) 58%);
  box-shadow: var(--shadow-sm);
}

.w3-report__impression-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  color: var(--color-ai);
  background: linear-gradient(135deg, rgba(124, 92, 255, 0.18), rgba(31, 140, 255, 0.12));
}

.w3-report__impression-body {
  flex: 1;
  min-width: 0;
}

.w3-report__impression-label {
  display: block;
  margin-block-end: 6px;
  color: var(--color-ai);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.w3-report__impression-text {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.7;
}

.w3-report__impression-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-start: var(--space-3);
}

.w3-report__stat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.w3-report__stat-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.w3-report__stat--attention {
  color: var(--color-warning);
  background: rgba(245, 159, 0, 0.12);
}

.w3-report__stat--normal {
  color: var(--color-success);
  background: rgba(32, 180, 134, 0.12);
}

/* ── 检查卡片 ── */
.w3-report__exam {
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
  box-shadow: var(--shadow-sm);
}

.w3-report__exam[data-risk='attention'],
.w3-report__exam[data-risk='warning'] {
  border-color: rgba(245, 159, 0, 0.24);
}

.w3-report__exam[data-risk='high'],
.w3-report__exam[data-risk='danger'] {
  border-color: rgba(245, 63, 63, 0.24);
}

.w3-report__exam-head {
  display: flex;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.w3-report__exam-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  color: var(--color-success);
  background: rgba(32, 180, 134, 0.14);
}

.w3-report__exam-icon[data-tone='attention'] {
  color: var(--color-warning);
  background: rgba(245, 159, 0, 0.14);
}

.w3-report__exam-icon[data-tone='danger'] {
  color: var(--color-danger);
  background: rgba(239, 77, 90, 0.14);
}

.w3-report__exam-head-body {
  flex: 1;
  min-width: 0;
}

.w3-report__exam-title-wrap {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
}

.w3-report__exam-title {
  font-size: 16px;
  font-weight: 700;
}

.w3-report__exam-impression {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.w3-report__table-block + .w3-report__collapse,
.w3-report__table-block + .w3-report__info-box,
.w3-report__collapse + .w3-report__info-box {
  margin-block-start: var(--space-3);
}

.w3-report__table-title {
  margin: 0 0 var(--space-2);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.w3-report__table {
  width: 100%;
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: var(--color-table-header);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.w3-report__table :deep(.el-table__header th) {
  color: var(--color-text-muted);
  font-weight: 700;
}

/* ── 折叠区 ── */
.w3-report__collapse {
  border: none;
  background: transparent;
}

.w3-report__collapse :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 40px;
  padding-block: 4px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 600;
}

.w3-report__collapse :deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

.w3-report__collapse--expandable :deep(.el-collapse-item) {
  overflow: hidden;
  border: 1px solid rgba(31, 140, 255, 0.14);
  border-radius: var(--radius-md);
  background: rgba(31, 140, 255, 0.05);
}

.w3-report__collapse--expandable :deep(.el-collapse-item__header) {
  min-height: 46px;
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard);
}

.w3-report__collapse--expandable :deep(.el-collapse-item__header:hover),
.w3-report__collapse--expandable :deep(.el-collapse-item__header:focus-visible) {
  background: rgba(31, 140, 255, 0.1);
  outline: none;
}

.w3-report__collapse--expandable :deep(.el-collapse-item__arrow) {
  margin-inline-end: var(--space-2);
  color: var(--color-primary-strong);
  font-size: 14px;
  font-weight: 700;
}

.w3-report__collapse--expandable :deep(.el-collapse-item__content) {
  padding: 0 var(--space-3) var(--space-3);
}

.w3-report__collapse-trigger {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-width: 0;
  padding-inline-end: var(--space-1);
}

.w3-report__collapse-trigger-main {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.w3-report__collapse-trigger-icon {
  flex-shrink: 0;
  color: var(--color-primary-strong);
  font-size: 15px;
}

.w3-report__collapse-trigger-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.w3-report__collapse-trigger-hint {
  flex-shrink: 0;
  color: var(--color-primary-strong);
  font-size: 12px;
  font-weight: 500;
}

/* ── 综合解读 ── */
.w3-report__collapse--overall {
  overflow: hidden;
  border: 1px solid rgba(124, 92, 255, 0.16);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(circle at 0% 0%, rgba(124, 92, 255, 0.08), transparent 40%),
    rgba(247, 251, 255, 0.95);
  box-shadow: var(--shadow-sm);
}

.w3-report__collapse--overall :deep(.el-collapse-item__header) {
  min-height: 56px;
  padding: var(--space-4) var(--space-5);
  color: var(--color-text);
  font-size: 15px;
  font-weight: 600;
}

.w3-report__collapse--overall :deep(.el-collapse-item__arrow) {
  margin-inline-end: var(--space-5);
  color: var(--color-text-muted);
  font-size: 14px;
}

.w3-report__collapse--overall :deep(.el-collapse-item__content) {
  padding: 0 var(--space-5) var(--space-5);
}

.w3-report__overall-head {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
}

.w3-report__overall-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--color-ai);
  background: rgba(124, 92, 255, 0.14);
}

.w3-report__overall-title {
  font-size: 16px;
  font-weight: 700;
}

/* ── 信息提示框 ── */
.w3-report__info-box {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid rgba(31, 140, 255, 0.14);
  border-radius: var(--radius-md);
  background: rgba(31, 140, 255, 0.06);
}

.w3-report__info-box-icon {
  flex-shrink: 0;
  margin-block-start: 2px;
  color: var(--color-primary-strong);
  font-size: 16px;
}

.w3-report__info-box-text {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.75;
}

.w3-report__findings {
  margin: 0;
  padding-inline-start: 1.2rem;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.w3-report__overall {
  margin: 0;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  color: var(--color-text-muted);
  font-size: 14px;
  line-height: 1.85;
  background: rgba(255, 255, 255, 0.72);
}

.w3-report__notice {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  color: var(--color-ai);
  font-size: 13px;
  line-height: 1.7;
  background: rgba(124, 92, 255, 0.06);
}

.w3-report__notice-icon {
  flex-shrink: 0;
  margin-block-start: 2px;
  font-size: 14px;
}
</style>
