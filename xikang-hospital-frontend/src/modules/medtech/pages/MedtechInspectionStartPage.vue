<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElDescriptions, ElDescriptionsItem, ElDialog, ElEmpty, ElMessage, ElSwitch } from 'element-plus'
import { medtechApi, type InspectionReport } from '@/shared/api/modules/medtech'
import { resultFormApi } from '@/shared/api/modules/resultForm'
import DynamicResultForm from '@/shared/components/DynamicResultForm.vue'
import SimulatedCheckResultContent from '@/shared/components/SimulatedCheckResultContent.vue'
import type { ResultFormSchema } from '@/shared/types/resultForm'
import {
  resolveSimulationDisplayOutput,
  type SimulatedCheckStructuredOutput,
} from '@/shared/types/simulatedCheckResult'
import MedtechStepLayout from '../layouts/MedtechStepLayout.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const specimenLoading = ref(false)
const simulating = ref(false)
const errorMessage = ref('')
const simulateError = ref('')
const report = ref<InspectionReport | null>(null)
const schema = ref<ResultFormSchema | null>(null)
const formValues = ref<Record<string, unknown>>({})
const formRef = ref<InstanceType<typeof DynamicResultForm>>()
const started = ref(false)
const specimenRecorded = ref(false)
const isNormal = ref(false)
const structuredOutput = ref<SimulatedCheckStructuredOutput | null>(null)
const dialogVisible = ref(false)

const id = computed(() => Number(route.query.id || 0))
const canRecordSpecimen = computed(() => started.value && !specimenLoading.value && !specimenRecorded.value)
const canSimulate = computed(() => started.value && !loading.value && !simulating.value)
const canSubmit = computed(() => started.value && !!schema.value && !loading.value && !simulating.value)

function hasDisplayableStructuredOutput(data: SimulatedCheckStructuredOutput | null): boolean {
  if (!data) return false
  if ((data.resultItems?.length ?? 0) > 0) return true
  return !!data.conclusion?.trim() || !!data.checkName?.trim()
}

const canViewResult = computed(() => hasDisplayableStructuredOutput(structuredOutput.value))
const dialogTitle = computed(() =>
  structuredOutput.value?.checkName ? `${structuredOutput.value.checkName} 模拟结果` : '模拟检验结果',
)

async function loadPage() {
  if (!id.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    report.value = await medtechApi.inspectionReport(id.value)
    schema.value = await resultFormApi.resolveInspectionForm({ inspectionRequestId: id.value })
    formValues.value = { ...(schema.value.existingValues ?? {}) }
    specimenRecorded.value = !!report.value.inspectionTime

    if (report.value.inspectionState === '待检验') {
      await medtechApi.startInspection(id.value)
      report.value = { ...report.value, inspectionState: '检验中', statusText: '检验中' }
    }
    started.value = report.value.inspectionState === '检验中'
  } catch {
    report.value = null
    schema.value = null
    errorMessage.value = '检验申请加载失败，请返回列表重试'
  } finally {
    loading.value = false
  }
}

async function recordSpecimen() {
  if (!id.value || !canRecordSpecimen.value) return
  specimenLoading.value = true
  try {
    await medtechApi.recordInspectionSpecimen(id.value)
    specimenRecorded.value = true
    ElMessage.success('采样已记录')
  } catch {
    ElMessage.error('采样记录失败，请稍后重试')
  } finally {
    specimenLoading.value = false
  }
}

async function runSimulation() {
  if (!id.value || !canSimulate.value) return
  simulating.value = true
  simulateError.value = ''
  structuredOutput.value = null
  try {
    const result = await medtechApi.simulateInspection(id.value, { isNormal: isNormal.value })

    structuredOutput.value = resolveSimulationDisplayOutput(result, {
      defaultCheckName: report.value?.techName,
    })
    if (result.simulatedValues) {
      formValues.value = { ...formValues.value, ...result.simulatedValues }
    }
    if (result.source === 'workflow') {
      ElMessage.success('模拟检验完成（Dify 工作流），请确认后提交')
      openResultDialog()
    } else {
      const hint = result.difyError ? `Dify 调用失败：${result.difyError}` : '未检测到 Dify 配置'
      ElMessage.warning(`模拟检验完成（内置模拟）。${hint}`)
      openResultDialog()
    }
  } catch {
    simulateError.value = '模拟检验失败，请稍后重试或手动录入'
  } finally {
    simulating.value = false
  }
}

function openResultDialog() {
  if (!hasDisplayableStructuredOutput(structuredOutput.value)) return
  dialogVisible.value = true
}

async function submit() {
  if (!id.value || !schema.value) return
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    await medtechApi.submitInspectionResult(id.value, { values: formValues.value })
    ElMessage.success('检验结果已提交')
    router.push('/medtech/check-queue')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadPage()
})
</script>

<template>
  <MedtechStepLayout
    :step="2"
    :total-steps="2"
    title="检验执行"
    description="确认申请信息，记录采样，运行模拟检验并录入结果。"
    prev-path="/medtech/check-queue"
  >
    <ElEmpty v-if="!id" description="请从医技申请列表选择一条检验记录" />

    <template v-else>
      <ElAlert
        v-if="errorMessage"
        type="error"
        :title="errorMessage"
        show-icon
        :closable="false"
        class="section-alert"
      />

      <section v-if="report" v-loading="loading" class="detail-section">
        <h3 class="section-title">申请信息</h3>
        <ElDescriptions :column="2" border size="small">
          <ElDescriptionsItem label="病历号">{{ report.caseNumber || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="患者">{{ report.patientName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="检验项目">{{ report.techName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">{{ report.statusText || report.inspectionState || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="检验部位">{{ report.position || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="项目编码">{{ report.techCode || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="目的要求" :span="2">{{ report.info || '-' }}</ElDescriptionsItem>
        </ElDescriptions>
      </section>

      <section v-if="started" class="action-section">
        <h3 class="section-title">检验执行</h3>
        <p class="action-hint">可先记录采样，再运行模拟检验生成初步结果，也可直接录入后提交。</p>
        <ElButton
          class="specimen-btn"
          :loading="specimenLoading"
          :disabled="!canRecordSpecimen"
          @click="recordSpecimen"
        >
          {{ specimenRecorded ? '已记录采样' : '记录采样' }}
        </ElButton>
      </section>

      <section v-if="started && schema" class="action-section">
        <h3 class="section-title">模拟工作流</h3>
        <p class="action-hint">运行模拟检验工作流生成初步结果，可在下方修改后提交。</p>
        <div class="simulate-options">
          <span class="simulate-options__label">模拟为正常结果</span>
          <ElSwitch v-model="isNormal" :disabled="simulating" />
        </div>
        <div class="action-buttons">
          <ElButton type="primary" :loading="simulating" :disabled="!canSimulate" @click="runSimulation">
            运行模拟检验
          </ElButton>
          <ElButton v-if="canViewResult" @click="openResultDialog">查看结果</ElButton>
        </div>
        <ElAlert
          v-if="simulateError"
          type="warning"
          :title="simulateError"
          show-icon
          :closable="false"
          class="section-alert"
        />
      </section>

      <section v-if="schema" class="form-section">
        <h3 class="section-title">结果录入</h3>
        <p v-if="schema.extensionFieldCount" class="form-meta">
          表单分类：{{ schema.categoryName || schema.categoryCode }} ·
          {{ schema.baseFieldCount }} 个基础字段、{{ schema.extensionFieldCount }} 个扩展字段
        </p>
        <DynamicResultForm
          ref="formRef"
          v-model="formValues"
          :fields="schema.fields"
          :base-field-count="schema.baseFieldCount"
        />
      </section>

      <div class="actions">
        <ElButton @click="router.push('/medtech/check-queue')">返回列表</ElButton>
        <ElButton type="primary" :loading="loading" :disabled="!canSubmit" @click="submit">
          提交检验结果
        </ElButton>
      </div>
    </template>
  </MedtechStepLayout>

  <ElDialog v-model="dialogVisible" :title="dialogTitle" width="760px" align-center destroy-on-close>
    <SimulatedCheckResultContent :data="structuredOutput" />
  </ElDialog>
</template>

<style scoped>
.section-title {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-md);
  font-weight: 600;
}

.detail-section,
.action-section,
.form-section {
  margin-block-end: var(--space-5);
}

.section-alert {
  margin-block-end: var(--space-4);
}

.action-hint,
.form-meta {
  margin: 0 0 var(--space-3);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.specimen-btn {
  margin-block-end: var(--space-3);
}

.simulate-options {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-block-end: var(--space-3);
}

.simulate-options__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.action-buttons {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}
</style>
