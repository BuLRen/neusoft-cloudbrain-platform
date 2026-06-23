<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { aiApi } from '@/shared/api/modules/ai'
import { medtechApi } from '@/shared/api/modules/medtech'
import type { CheckApplication, CheckReport, DisposalApplication, InspectionApplication } from '@/shared/types/medtech'
import type { ExamAnalyzeResult } from '@/shared/types/ai'

const activeTab = ref('check')
const statusFilter = ref<number | undefined>()

const checkApplications = ref<CheckApplication[]>([])
const inspectionApplications = ref<InspectionApplication[]>([])
const disposalApplications = ref<DisposalApplication[]>([])

const selectedCheckId = ref<number | undefined>()
const selectedInspectionId = ref<number | undefined>()
const selectedDisposalId = ref<number | undefined>()

const selectedCheck = ref<CheckReport | null>(null)
const selectedInspection = ref<InspectionApplication | null>(null)
const selectedDisposal = ref<DisposalApplication | null>(null)

const checkAiResult = ref<ExamAnalyzeResult | null>(null)
const inspectionAiResult = ref<ExamAnalyzeResult | null>(null)

const checkForm = reactive({
  result: '',
  findings: '',
  conclusion: '',
  impression: '',
  aiAnalysis: '',
})

const inspectionForm = reactive({
  result: '',
  aiAnalysis: '',
})

const disposalForm = reactive({
  result: '',
  remarks: '',
})

const loading = ref(false)

function statusTone(status?: number) {
  if (status === 2 || status === 3) return 'success'
  if (status === 1) return 'warning'
  return 'primary'
}

function stringifyAi(result: ExamAnalyzeResult | null) {
  if (!result) {
    return ''
  }
  return JSON.stringify(
    {
      riskLevel: result.riskLevel,
      analysisReport: result.analysisReport,
      abnormalIndicators: result.abnormalIndicators,
      suggestions: result.suggestions,
    },
    null,
    2,
  )
}

async function loadAll() {
  loading.value = true
  try {
    const [checks, inspections, disposals] = await Promise.all([
      medtechApi.checkApplications({ status: statusFilter.value }),
      medtechApi.inspectionApplications({ status: statusFilter.value }),
      medtechApi.disposalApplications({ status: statusFilter.value }),
    ])
    checkApplications.value = checks
    inspectionApplications.value = inspections
    disposalApplications.value = disposals

    if (!selectedCheckId.value && checks.length > 0) {
      selectedCheckId.value = checks[0].id
      await loadCheckDetail(checks[0].id)
    }
    if (!selectedInspectionId.value && inspections.length > 0) {
      selectInspection(inspections[0])
    }
    if (!selectedDisposalId.value && disposals.length > 0) {
      selectDisposal(disposals[0])
    }
  } finally {
    loading.value = false
  }
}

async function loadCheckDetail(id?: number) {
  if (!id) {
    selectedCheck.value = null
    return
  }
  selectedCheck.value = await medtechApi.checkReport(id)
  checkForm.result = selectedCheck.value.result || ''
  checkForm.findings = selectedCheck.value.findings || ''
  checkForm.conclusion = selectedCheck.value.conclusion || ''
  checkForm.impression = selectedCheck.value.impression || ''
  checkForm.aiAnalysis = selectedCheck.value.aiAnalysis || ''
}

function selectCheck(row: CheckApplication) {
  selectedCheckId.value = row.id
  void loadCheckDetail(row.id)
}

function selectInspection(row: InspectionApplication) {
  selectedInspectionId.value = row.id
  selectedInspection.value = row
  inspectionForm.result = ''
  inspectionForm.aiAnalysis = ''
}

function selectDisposal(row: DisposalApplication) {
  selectedDisposalId.value = row.id
  selectedDisposal.value = row
  disposalForm.result = ''
  disposalForm.remarks = ''
}

async function startCheck() {
  if (!selectedCheckId.value) return
  await medtechApi.startCheck(selectedCheckId.value)
  ElMessage.success('检查已开始执行')
  await Promise.all([loadAll(), loadCheckDetail(selectedCheckId.value)])
}

async function analyzeCheck() {
  if (!selectedCheck.value) {
    ElMessage.warning('请先选择检查申请')
    return
  }
  checkAiResult.value = await aiApi.examAnalyze({
    patientId: selectedCheck.value.patientId,
    registerId: selectedCheck.value.registerId,
    requestId: selectedCheck.value.id,
    examType: 'check',
    result: {
      result: checkForm.result,
      findings: checkForm.findings,
      conclusion: checkForm.conclusion,
      impression: checkForm.impression,
    },
  })
  checkForm.aiAnalysis = stringifyAi(checkAiResult.value)
  ElMessage.success('AI 检查分析完成')
}

async function submitCheckResult() {
  if (!selectedCheckId.value || !checkForm.result.trim()) {
    ElMessage.warning('请先选择检查申请并填写结果')
    return
  }
  try {
    await analyzeCheck()
  } catch {
    checkForm.aiAnalysis = ''
    ElMessage.warning('AI 检查分析失败，本次仍继续提交检查结果')
  }
  await medtechApi.submitCheckResult(selectedCheckId.value, { ...checkForm })
  ElMessage.success('检查结果已提交')
  await Promise.all([loadAll(), loadCheckDetail(selectedCheckId.value)])
}

async function startInspection() {
  if (!selectedInspectionId.value) return
  await medtechApi.startInspection(selectedInspectionId.value)
  ElMessage.success('检验已开始执行')
  await loadAll()
}

async function analyzeInspection() {
  if (!selectedInspection.value) {
    ElMessage.warning('请先选择检验申请')
    return
  }
  inspectionAiResult.value = await aiApi.examAnalyze({
    patientId: selectedInspection.value.patientId,
    registerId: selectedInspection.value.registerId,
    requestId: selectedInspection.value.id,
    examType: 'inspection',
    result: inspectionForm.result,
  })
  inspectionForm.aiAnalysis = stringifyAi(inspectionAiResult.value)
  ElMessage.success('AI 检验分析完成')
}

async function submitInspectionResult() {
  if (!selectedInspectionId.value || !inspectionForm.result.trim()) {
    ElMessage.warning('请先选择检验申请并填写结果')
    return
  }
  try {
    await analyzeInspection()
  } catch {
    inspectionForm.aiAnalysis = ''
    ElMessage.warning('AI 检验分析失败，本次仍继续提交检验结果')
  }
  await medtechApi.submitInspectionResult(selectedInspectionId.value, {
    result: inspectionForm.result,
    aiAnalysis: inspectionForm.aiAnalysis || undefined,
  })
  ElMessage.success('检验结果已提交')
  await loadAll()
}

async function startDisposal() {
  if (!selectedDisposalId.value) return
  await medtechApi.startDisposal(selectedDisposalId.value)
  ElMessage.success('处置已开始执行')
  await loadAll()
}

async function submitDisposalResult() {
  if (!selectedDisposalId.value || !disposalForm.result.trim()) {
    ElMessage.warning('请先选择处置申请并填写结果')
    return
  }
  await medtechApi.submitDisposalResult(selectedDisposalId.value, { ...disposalForm })
  ElMessage.success('处置结果已提交')
  await loadAll()
}

onMounted(async () => {
  await loadAll()
})
</script>

<template>
  <div class="medical-tech-workspace u-page-grid">
    <PageHeader
      title="医技执行工作台"
      description="在一个工作台中完成检查、检验、处置申请查看、开始执行与结果录入。AI 分析失败不会阻塞结果提交。"
      eyebrow="Role B / Medtech"
    >
      <template #actions>
        <ElButton @click="loadAll">刷新申请列表</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="toolbar-card">
      <div class="toolbar-row">
        <div>
          <h3>执行状态筛选</h3>
          <p>0/空值通常表示待执行，1 表示执行中，2/3 代表已完成或已出报告，具体以后端返回为准。</p>
        </div>
        <div class="toolbar-actions">
          <ElInputNumber v-model="statusFilter" :min="0" :controls="false" placeholder="按状态筛选" class="filter-field" />
          <ElButton type="primary" @click="loadAll">应用筛选</ElButton>
        </div>
      </div>
    </GlassCard>

    <GlassCard class="flow-card">
      <ElTabs v-model="activeTab">
        <ElTabPane label="检查" name="check">
          <div class="split-grid">
            <section>
              <div class="section-title">
                <h3>检查申请</h3>
                <StatusTag :tone="loading ? 'warning' : 'primary'">{{ checkApplications.length }} 条</StatusTag>
              </div>
              <ElTable :data="checkApplications" @row-click="selectCheck">
                <ElTableColumn prop="patientName" label="患者" min-width="120" />
                <ElTableColumn prop="medicalTechnologyName" label="项目" min-width="180" />
                <ElTableColumn label="状态" min-width="120">
                  <template #default="{ row }">
                    <StatusTag :tone="statusTone(row.status)">{{ row.statusName || '-' }}</StatusTag>
                  </template>
                </ElTableColumn>
              </ElTable>
            </section>

            <section>
              <h3>检查结果录入</h3>
              <ElEmpty v-if="!selectedCheck" description="请选择一条检查申请" />
              <template v-else>
                <ElDescriptions :column="1" border>
                  <ElDescriptionsItem label="患者">{{ selectedCheck.patientName || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="项目">{{ selectedCheck.medicalTechnologyName || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="临床诊断">{{ selectedCheck.clinicalDiagnosis || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="检查部位">{{ selectedCheck.bodyPart || '-' }}</ElDescriptionsItem>
                </ElDescriptions>
                <ElForm label-position="top" class="mt">
                  <ElFormItem label="检查结果">
                    <ElInput v-model="checkForm.result" type="textarea" :rows="2" />
                  </ElFormItem>
                  <ElFormItem label="检查所见">
                    <ElInput v-model="checkForm.findings" type="textarea" :rows="2" />
                  </ElFormItem>
                  <ElFormItem label="结论">
                    <ElInput v-model="checkForm.conclusion" type="textarea" :rows="2" />
                  </ElFormItem>
                  <ElFormItem label="印象">
                    <ElInput v-model="checkForm.impression" type="textarea" :rows="2" />
                  </ElFormItem>
                </ElForm>
                <div class="actions">
                  <ElButton @click="startCheck">开始执行</ElButton>
                  <ElButton @click="analyzeCheck">先跑 AI 分析</ElButton>
                  <ElButton type="primary" @click="submitCheckResult">提交结果</ElButton>
                </div>
                <ElAlert
                  v-if="checkForm.aiAnalysis"
                  class="mt"
                  type="success"
                  show-icon
                  :closable="false"
                  title="AI 检查分析已生成"
                />
                <ElCard class="json-card" v-if="checkForm.aiAnalysis">
                  <pre>{{ checkForm.aiAnalysis }}</pre>
                </ElCard>
              </template>
            </section>
          </div>
        </ElTabPane>

        <ElTabPane label="检验" name="inspection">
          <div class="split-grid">
            <section>
              <div class="section-title">
                <h3>检验申请</h3>
                <StatusTag tone="primary">{{ inspectionApplications.length }} 条</StatusTag>
              </div>
              <ElTable :data="inspectionApplications" @row-click="selectInspection">
                <ElTableColumn prop="patientName" label="患者" min-width="120" />
                <ElTableColumn prop="medicalTechnologyName" label="项目" min-width="180" />
                <ElTableColumn prop="specimenType" label="标本类型" min-width="120" />
                <ElTableColumn label="状态" min-width="120">
                  <template #default="{ row }">
                    <StatusTag :tone="statusTone(row.status)">{{ row.statusName || '-' }}</StatusTag>
                  </template>
                </ElTableColumn>
              </ElTable>
            </section>

            <section>
              <h3>检验结果录入</h3>
              <ElEmpty v-if="!selectedInspection" description="请选择一条检验申请" />
              <template v-else>
                <ElDescriptions :column="1" border>
                  <ElDescriptionsItem label="患者">{{ selectedInspection.patientName || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="项目">{{ selectedInspection.medicalTechnologyName || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="标本类型">{{ selectedInspection.specimenType || '-' }}</ElDescriptionsItem>
                </ElDescriptions>
                <ElForm label-position="top" class="mt">
                  <ElFormItem label="检验结果">
                    <ElInput v-model="inspectionForm.result" type="textarea" :rows="6" placeholder="可填写结构化摘要或原始结果文本" />
                  </ElFormItem>
                </ElForm>
                <div class="actions">
                  <ElButton @click="startInspection">开始执行</ElButton>
                  <ElButton @click="analyzeInspection">先跑 AI 分析</ElButton>
                  <ElButton type="primary" @click="submitInspectionResult">提交结果</ElButton>
                </div>
                <ElCard class="json-card" v-if="inspectionForm.aiAnalysis">
                  <pre>{{ inspectionForm.aiAnalysis }}</pre>
                </ElCard>
              </template>
            </section>
          </div>
        </ElTabPane>

        <ElTabPane label="处置" name="disposal">
          <div class="split-grid">
            <section>
              <div class="section-title">
                <h3>处置申请</h3>
                <StatusTag tone="primary">{{ disposalApplications.length }} 条</StatusTag>
              </div>
              <ElTable :data="disposalApplications" @row-click="selectDisposal">
                <ElTableColumn prop="patientName" label="患者" min-width="120" />
                <ElTableColumn prop="medicalTechnologyName" label="项目" min-width="180" />
                <ElTableColumn prop="quantity" label="数量" min-width="80" />
                <ElTableColumn label="状态" min-width="120">
                  <template #default="{ row }">
                    <StatusTag :tone="statusTone(row.status)">{{ row.statusName || '-' }}</StatusTag>
                  </template>
                </ElTableColumn>
              </ElTable>
            </section>

            <section>
              <h3>处置结果录入</h3>
              <ElEmpty v-if="!selectedDisposal" description="请选择一条处置申请" />
              <template v-else>
                <ElDescriptions :column="1" border>
                  <ElDescriptionsItem label="患者">{{ selectedDisposal.patientName || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="项目">{{ selectedDisposal.medicalTechnologyName || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="执行说明">{{ selectedDisposal.description || '-' }}</ElDescriptionsItem>
                </ElDescriptions>
                <ElForm label-position="top" class="mt">
                  <ElFormItem label="处置结果">
                    <ElInput v-model="disposalForm.result" type="textarea" :rows="3" />
                  </ElFormItem>
                  <ElFormItem label="备注">
                    <ElInput v-model="disposalForm.remarks" type="textarea" :rows="3" />
                  </ElFormItem>
                </ElForm>
                <div class="actions">
                  <ElButton @click="startDisposal">开始执行</ElButton>
                  <ElButton type="primary" @click="submitDisposalResult">提交结果</ElButton>
                </div>
              </template>
            </section>
          </div>
        </ElTabPane>
      </ElTabs>
    </GlassCard>
  </div>
</template>

<style scoped>
.toolbar-card,
.flow-card {
  padding: var(--space-5);
}

.toolbar-row,
.split-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-4);
}

.split-grid {
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.9fr);
}

.toolbar-row p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.7;
}

.toolbar-actions {
  display: flex;
  gap: var(--space-3);
  align-items: start;
}

.filter-field {
  width: 160px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.mt {
  margin-block-start: var(--space-4);
}

.json-card {
  margin-block-start: var(--space-4);
}

.json-card pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.flow-card :deep(.el-tabs__content) {
  padding-block-start: var(--space-4);
}

@media (max-width: 1080px) {
  .toolbar-row,
  .split-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    flex-wrap: wrap;
  }
}
</style>
