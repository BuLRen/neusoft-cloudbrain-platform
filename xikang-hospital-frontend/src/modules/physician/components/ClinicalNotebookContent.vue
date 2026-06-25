<script setup lang="ts">
import { ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElIcon,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { Document, MagicStick, View } from '@element-plus/icons-vue'
import type { ClinicalExamItem, ClinicalNotebook } from '@/shared/api/modules/clinicalRecord'
import LabReportPrintSheet from '@/shared/components/LabReportPrintSheet.vue'
import ResultPayloadViewer from '@/shared/components/ResultPayloadViewer.vue'
import SimulatedCheckResultContent from '@/shared/components/SimulatedCheckResultContent.vue'
import { useLabReportExport } from '@/shared/composables/useLabReportExport'
import { buildLabReportContextFromClinicalNotebook } from '@/shared/types/labReportPdf'
import {
  canExportExamItemPdf,
  canViewFullExamResult,
  displayText,
  examCategoryLabel,
  formatVisitDate,
  hasExamAiAnalysis,
  hasStructuredExamResult,
  resolveExamStateTone,
  resolveExamStructuredOutput,
} from '@/shared/utils/clinicalNotebook'

const props = withDefaults(defineProps<{
  notebook: ClinicalNotebook | null
  loading?: boolean
  archived?: boolean
  emptyText?: string
  mode?: 'physician' | 'patient'
}>(), {
  loading: false,
  archived: false,
  emptyText: '暂无记录，各环节保存后将自动出现在此处。',
  mode: 'physician',
})

const printSheetRef = ref<InstanceType<typeof LabReportPrintSheet> | null>(null)
const { exportContext, exporting, exportPdf } = useLabReportExport()

const resultDialogVisible = ref(false)
const resultDialogTitle = ref('结果详情')
const resultDialogItem = ref<ClinicalExamItem | null>(null)

const aiDialogVisible = ref(false)
const aiDialogTitle = ref('AI 分析')
const aiDialogContent = ref('')

function openResultDialog(item: ClinicalExamItem) {
  if (!canViewFullExamResult(item)) return
  resultDialogTitle.value = item.techName ? `${item.techName} · 结果详情` : '结果详情'
  resultDialogItem.value = item
  resultDialogVisible.value = true
}

function openAiDialog(item: ClinicalExamItem) {
  if (!hasExamAiAnalysis(item)) return
  aiDialogTitle.value = item.techName ? `${item.techName} · AI 分析` : 'AI 分析'
  aiDialogContent.value = item.aiAnalysis?.trim() || ''
  aiDialogVisible.value = true
}

async function handleExportResultPdf() {
  const item = resultDialogItem.value
  if (!item || !canExportExamItemPdf(item)) return
  const structuredOutput = resolveExamStructuredOutput(item)
  if (!structuredOutput) {
    ElMessage.warning('暂无结构化检验明细，无法导出 PDF')
    return
  }
  await exportPdf(
    buildLabReportContextFromClinicalNotebook(item, structuredOutput, props.notebook?.header),
    printSheetRef,
  )
}
</script>

<template>
  <div class="clinical-notebook">
    <div v-if="loading" class="clinical-notebook__empty">加载中...</div>
    <div v-else-if="!notebook" class="clinical-notebook__empty">{{ emptyText }}</div>
    <article v-else class="clinical-notebook__paper">
      <header class="clinical-notebook__page-header">
        <h2 class="clinical-notebook__title">门诊病历本</h2>
        <dl class="clinical-notebook__meta-grid">
          <div>
            <dt>病历号</dt>
            <dd>{{ displayText(notebook.header.caseNumber) }}</dd>
          </div>
          <div>
            <dt>患者</dt>
            <dd>
              {{ displayText(notebook.header.realName) }}
              <template v-if="notebook.header.gender || notebook.header.age != null">
                （{{ [notebook.header.gender, notebook.header.age != null ? `${notebook.header.age}岁` : ''].filter(Boolean).join(' · ') }}）
              </template>
            </dd>
          </div>
          <div>
            <dt>科室</dt>
            <dd>{{ displayText(notebook.header.departmentName) }}</dd>
          </div>
          <div>
            <dt>医生</dt>
            <dd>{{ displayText(notebook.header.physicianName) }}</dd>
          </div>
          <div class="clinical-notebook__meta-span">
            <dt>就诊时间</dt>
            <dd>{{ formatVisitDate(notebook.header.visitDate) }}</dd>
          </div>
        </dl>
      </header>

      <section class="clinical-notebook__section">
        <h3 class="clinical-notebook__section-title">一、病历摘要</h3>
        <dl class="clinical-notebook__field-list">
          <div>
            <dt>主诉</dt>
            <dd>{{ displayText(notebook.medicalSummary.readme) }}</dd>
          </div>
          <div>
            <dt>现病史</dt>
            <dd>{{ displayText(notebook.medicalSummary.present) }}</dd>
          </div>
          <div>
            <dt>既往史</dt>
            <dd>{{ displayText(notebook.medicalSummary.history) }}</dd>
          </div>
          <div>
            <dt>过敏史</dt>
            <dd>{{ displayText(notebook.medicalSummary.allergy) }}</dd>
          </div>
        </dl>
      </section>

      <section class="clinical-notebook__section">
        <h3 class="clinical-notebook__section-title">二、初步诊断</h3>
        <p class="clinical-notebook__text-block">
          {{ displayText(notebook.preliminaryDiagnosis, '暂无初步诊断') }}
        </p>
      </section>

      <section class="clinical-notebook__section">
        <h3 class="clinical-notebook__section-title">三、检查检验项目</h3>
        <p v-if="!notebook.examItems.length" class="clinical-notebook__placeholder">暂无检查检验项目</p>
        <div v-else class="clinical-notebook__table-wrap">
          <ElTable :data="notebook.examItems" size="small" border class="clinical-notebook__table">
            <ElTableColumn type="index" label="#" width="44" align="center" />
            <ElTableColumn prop="techName" label="项目" min-width="88" show-overflow-tooltip />
            <ElTableColumn label="类型" width="52" align="center">
              <template #default="{ row }">
                <span class="clinical-notebook__type-tag">{{ examCategoryLabel(row.category) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" width="84" align="center">
              <template #default="{ row }">
                <span class="state-badge" :data-tone="resolveExamStateTone(row.state)">
                  <span class="state-badge__dot" aria-hidden="true" />
                  {{ row.state || '—' }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="结果" width="76" align="center">
              <template #default="{ row }">
                <ElButton
                  v-if="canViewFullExamResult(row)"
                  class="clinical-notebook__cell-btn"
                  text
                  type="primary"
                  size="small"
                  @click="openResultDialog(row)"
                >
                  <ElIcon><View /></ElIcon>
                  查看
                </ElButton>
                <span v-else class="clinical-notebook__muted">—</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="AI" width="76" align="center">
              <template #default="{ row }">
                <ElButton
                  v-if="hasExamAiAnalysis(row)"
                  class="clinical-notebook__cell-btn clinical-notebook__cell-btn--ai"
                  text
                  type="primary"
                  size="small"
                  @click="openAiDialog(row)"
                >
                  <ElIcon><MagicStick /></ElIcon>
                  查看
                </ElButton>
                <span v-else class="clinical-notebook__muted">—</span>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </section>

      <section class="clinical-notebook__section">
        <h3 class="clinical-notebook__section-title">四、综合分析</h3>
        <p v-if="notebook.w3Analysis?.overallAnalysis" class="clinical-notebook__text-block">
          {{ notebook.w3Analysis.overallAnalysis }}
        </p>
        <p v-else class="clinical-notebook__placeholder">
          {{ notebook.w3Analysis?.completed ? '暂无综合分析内容' : '待医生解读' }}
        </p>
      </section>

      <section class="clinical-notebook__section">
        <h3 class="clinical-notebook__section-title">五、门诊确诊</h3>
        <dl class="clinical-notebook__field-list">
          <div>
            <dt>诊断结论</dt>
            <dd>{{ displayText(notebook.diagnosis.diagnosis, '暂无确诊') }}</dd>
          </div>
          <div v-if="notebook.diagnosis.diseases?.length">
            <dt>疾病</dt>
            <dd>
              <ElTag
                v-for="disease in notebook.diagnosis.diseases"
                :key="disease.diseaseCode || disease.diseaseName"
                size="small"
                class="clinical-notebook__tag"
              >
                {{ disease.diseaseName }}
              </ElTag>
            </dd>
          </div>
          <div>
            <dt>治疗</dt>
            <dd>{{ displayText(notebook.diagnosis.cure) }}</dd>
          </div>
          <div>
            <dt>注意事项</dt>
            <dd>{{ displayText(notebook.diagnosis.careful) }}</dd>
          </div>
        </dl>
      </section>

      <section class="clinical-notebook__section clinical-notebook__section--last">
        <h3 class="clinical-notebook__section-title">六、处方</h3>
        <p v-if="!notebook.prescription.items.length" class="clinical-notebook__placeholder">暂无处方</p>
        <div v-else class="clinical-notebook__table-wrap">
          <ElTable :data="notebook.prescription.items" size="small" border class="clinical-notebook__table">
            <ElTableColumn type="index" label="序号" width="56" align="center" />
            <ElTableColumn prop="drugName" label="药品" min-width="120" />
            <ElTableColumn prop="drugUsage" label="用法" min-width="100" />
            <ElTableColumn label="数量" width="80" align="center">
              <template #default="{ row }">
                {{ row.drugNumber ?? '—' }}
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </section>
    </article>

    <ElDialog
      v-model="resultDialogVisible"
      :title="resultDialogTitle"
      :width="resultDialogItem && hasStructuredExamResult(resultDialogItem) ? 'min(760px, 94vw)' : 'min(560px, 92vw)'"
      align-center
      append-to-body
      destroy-on-close
      class="clinical-notebook__result-dialog"
    >
      <div class="clinical-notebook__dialog-scroll">
        <SimulatedCheckResultContent
          v-if="resultDialogItem && hasStructuredExamResult(resultDialogItem)"
          :data="resolveExamStructuredOutput(resultDialogItem)"
        />
        <ResultPayloadViewer v-else-if="resultDialogItem" :raw="resultDialogItem.resultRaw" />
      </div>
      <template #footer>
        <ElButton @click="resultDialogVisible = false">关闭</ElButton>
        <ElButton
          v-if="resultDialogItem && canExportExamItemPdf(resultDialogItem)"
          :loading="exporting"
          type="primary"
          @click="handleExportResultPdf"
        >
          <ElIcon><Document /></ElIcon>
          导出 PDF
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="aiDialogVisible"
      :title="aiDialogTitle"
      width="min(560px, 92vw)"
      align-center
      append-to-body
      destroy-on-close
      class="clinical-notebook__ai-dialog"
    >
      <div class="clinical-notebook__dialog-scroll">
        <p class="clinical-notebook__ai-text">{{ aiDialogContent }}</p>
      </div>
      <template #footer>
        <ElButton @click="aiDialogVisible = false">关闭</ElButton>
      </template>
    </ElDialog>

    <div class="lab-report-print-host" aria-hidden="true">
      <LabReportPrintSheet ref="printSheetRef" :context="exportContext" />
    </div>
  </div>
</template>

<style scoped>
.clinical-notebook__empty {
  padding: var(--space-6) var(--space-4);
  text-align: center;
  color: var(--color-text-muted);
  font-size: 14px;
}

.clinical-notebook__paper {
  background: #faf8f3;
  border: 1px solid #e8e2d6;
  border-radius: var(--radius-md);
  box-shadow: 0 2px 12px rgba(60, 48, 32, 0.06);
  padding: var(--space-5) var(--space-4);
}

.clinical-notebook__page-header {
  padding-block-end: var(--space-4);
  border-block-end: 2px solid #d4c9b8;
  margin-block-end: var(--space-4);
}

.clinical-notebook__title {
  margin: 0 0 var(--space-3);
  font-size: 18px;
  font-weight: 700;
  text-align: center;
  letter-spacing: 0.12em;
  color: #3d3428;
}

.clinical-notebook__meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2) var(--space-4);
  margin: 0;
  font-size: 13px;
}

.clinical-notebook__meta-grid dt {
  color: #8a7f6f;
  font-weight: 500;
}

.clinical-notebook__meta-grid dd {
  margin: 2px 0 0;
  color: #3d3428;
}

.clinical-notebook__meta-span {
  grid-column: 1 / -1;
}

.clinical-notebook__section {
  padding-block: var(--space-3);
  border-block-end: 1px dashed #e0d6c8;
}

.clinical-notebook__section--last {
  border-block-end: none;
  padding-block-end: 0;
}

.clinical-notebook__section-title {
  margin: 0 0 var(--space-3);
  font-size: 14px;
  font-weight: 700;
  color: #4a4034;
  padding-block-end: var(--space-2);
  border-block-end: 1px solid #e8e0d4;
}

.clinical-notebook__field-list {
  margin: 0;
  display: grid;
  gap: var(--space-3);
  font-size: 13px;
}

.clinical-notebook__field-list dt {
  color: #8a7f6f;
  font-weight: 500;
  margin-block-end: 2px;
}

.clinical-notebook__field-list dd {
  margin: 0;
  color: #3d3428;
  line-height: 1.6;
  white-space: pre-wrap;
}

.clinical-notebook__text-block {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #3d3428;
  white-space: pre-wrap;
}

.clinical-notebook__placeholder {
  margin: 0;
  font-size: 13px;
  color: #a09888;
}

.clinical-notebook__muted {
  color: #a09888;
  font-size: 12px;
}

.clinical-notebook__type-tag {
  font-size: 12px;
  color: #6b6256;
}

.clinical-notebook__cell-btn {
  padding: 2px 4px;
  height: auto;
  font-size: 12px;
}

.clinical-notebook__cell-btn--ai {
  color: #7c5cbf;
}

.clinical-notebook__cell-btn--ai:hover {
  color: #6a4dad;
}

.clinical-notebook__ai-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: #3d3428;
  white-space: pre-wrap;
}

.clinical-notebook__dialog-scroll {
  max-height: min(68vh, 560px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-inline-end: 2px;
}

.clinical-notebook__table-wrap {
  overflow-x: auto;
}

.clinical-notebook__table {
  width: 100%;
  background: #fff;
}

.clinical-notebook__table :deep(.el-table__cell) {
  padding-block: 6px;
}

.clinical-notebook__table :deep(.el-table__header .el-table__cell) {
  font-size: 12px;
  color: #8a7f6f;
  background: #f7f4ee;
}

.lab-report-print-host {
  position: fixed;
  left: -10000px;
  top: 0;
  pointer-events: none;
  visibility: hidden;
}

.clinical-notebook__tag + .clinical-notebook__tag {
  margin-inline-start: var(--space-2);
}

.state-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text);
}

.state-badge__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-text-muted);
}

.state-badge[data-tone='primary'] .state-badge__dot {
  background: var(--color-primary);
}

.state-badge[data-tone='success'] .state-badge__dot {
  background: #52c41a;
}

.state-badge[data-tone='warning'] .state-badge__dot {
  background: #faad14;
}

.state-badge[data-tone='neutral'] .state-badge__dot {
  background: var(--color-text-muted);
}
</style>

<style>
.clinical-notebook__result-dialog.el-dialog,
.clinical-notebook__ai-dialog.el-dialog {
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  margin: auto;
}

.clinical-notebook__result-dialog .el-dialog__body,
.clinical-notebook__ai-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
