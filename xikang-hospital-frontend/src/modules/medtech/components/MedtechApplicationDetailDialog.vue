<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElAlert, ElDescriptions, ElDescriptionsItem, ElDialog } from 'element-plus'
import {
  medtechApi,
  type CheckReport,
  type DisposalReport,
  type InspectionReport,
  type MedtechApplication,
  type MedtechTechType,
} from '@/shared/api/modules/medtech'
import ResultPayloadViewer from '@/shared/components/ResultPayloadViewer.vue'

const props = defineProps<{
  visible: boolean
  application: MedtechApplication | null
  emphasizeResult?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const TECH_TYPE_LABEL: Record<MedtechTechType, string> = {
  check: '检查',
  inspection: '检验',
  disposal: '处置',
}

const loading = ref(false)
const errorMessage = ref('')
const report = ref<CheckReport | InspectionReport | DisposalReport | null>(null)

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

const title = computed(() => {
  const name = props.application?.techName || '医技申请'
  return props.emphasizeResult ? `${name} · 结果详情` : `${name} · 申请详情`
})

const statusText = computed(() => report.value?.statusText || props.application?.statusText || '-')

const resultRaw = computed(() => {
  const data = report.value
  if (!data) return undefined
  if (data.techType === 'check') return data.checkResult
  if (data.techType === 'inspection') return data.inspectionResult ?? data.result
  return data.disposalResult ?? data.result
})

const remarkText = computed(() => {
  const data = report.value
  if (!data) return undefined
  if (data.techType === 'check') return data.checkRemark
  if (data.techType === 'inspection') return data.inspectionRemark
  return data.disposalRemark
})

const isArchived = computed(() => statusText.value === '已归档')

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

async function loadReport() {
  const app = props.application
  if (!app?.id) return
  loading.value = true
  errorMessage.value = ''
  report.value = null
  try {
    if (app.techType === 'check') {
      report.value = await medtechApi.checkReport(app.id)
    } else if (app.techType === 'inspection') {
      report.value = await medtechApi.inspectionReport(app.id)
    } else {
      report.value = await medtechApi.disposalReport(app.id)
    }
  } catch {
    errorMessage.value = '详情加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.visible, props.application?.techType, props.application?.id] as const,
  ([visible]) => {
    if (visible) void loadReport()
  },
)
</script>

<template>
  <ElDialog v-model="dialogVisible" :title="title" width="640px" align-center destroy-on-close>
    <ElAlert
      v-if="errorMessage"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
      class="detail-alert"
    />
    <div v-else v-loading="loading">
      <ElDescriptions v-if="application" :column="2" border size="small">
        <ElDescriptionsItem label="类型">
          {{ TECH_TYPE_LABEL[application.techType] }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="状态">{{ statusText }}</ElDescriptionsItem>
        <ElDescriptionsItem label="病历号">{{ report?.caseNumber || application.caseNumber || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="患者">{{ report?.patientName || application.patientName || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="项目名称">{{ report?.techName || application.techName || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="项目编码">{{ report?.techCode || application.techCode || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="部位">{{ report?.position || application.position || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="开立时间">{{ formatTime(report?.creationTime || application.creationTime) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="目的要求" :span="2">{{ report?.info || application.info || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem v-if="isArchived || remarkText" label="备注" :span="2">
          {{ remarkText || '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem v-if="!isArchived && (emphasizeResult || resultRaw)" label="结果" :span="2">
          <ResultPayloadViewer v-if="resultRaw" :raw="resultRaw" />
          <span v-else>-</span>
        </ElDescriptionsItem>
      </ElDescriptions>
    </div>
  </ElDialog>
</template>

<style scoped>
.detail-alert {
  margin-block-end: var(--space-3);
}
</style>
