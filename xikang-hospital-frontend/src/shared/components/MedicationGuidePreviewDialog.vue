<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDescriptions, ElDescriptionsItem, ElDialog, ElEmpty, ElMessage } from 'element-plus'
import MedicationGuidePrintSheet from '@/shared/components/MedicationGuidePrintSheet.vue'
import { useMedicationGuideExport } from '@/shared/composables/useMedicationGuideExport'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import type { MedicationGuideRecord } from '@/shared/types/pharmacy'

const props = defineProps<{
  visible: boolean
  /** 直接传入记录；若为空则用 registerId 自动加载 */
  record?: MedicationGuideRecord | null
  registerId?: number
  /** 控制是否显示"重新生成"按钮（生成失败时用） */
  showRetry?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  /** 指导单状态发生变化（生成/重试成功、失败）时通知父组件刷新 */
  changed: [record: MedicationGuideRecord]
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const loading = ref(false)
const generating = ref(false)
const errorMessage = ref('')
const innerRecord = ref<MedicationGuideRecord | null>(null)

const record = computed<MedicationGuideRecord | null>(() => {
  if (props.record !== undefined) return props.record
  return innerRecord.value
})

const { exportPdf, exporting } = useMedicationGuideExport()
const printSheetRef = ref<InstanceType<typeof MedicationGuidePrintSheet> | null>(null)

const isSuccess = computed(() => record.value?.status === 'success')
const isFailed = computed(() => record.value?.status === 'failed')

async function loadByRegisterId() {
  if (props.registerId == null) return
  loading.value = true
  errorMessage.value = ''
  try {
    innerRecord.value = await pharmacyApi.medicationGuideStatus(props.registerId)
  } catch {
    innerRecord.value = null
    errorMessage.value = '指导单查询失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function regenerate() {
  if (props.registerId == null) return
  generating.value = true
  errorMessage.value = ''
  try {
    const fresh = await pharmacyApi.generateMedicationGuide(props.registerId)
    innerRecord.value = fresh
    emit('changed', fresh)
    if (fresh.status === 'success') {
      ElMessage.success('用药指导单已生成')
    } else if (fresh.status === 'failed') {
      errorMessage.value = fresh.errorMessage || 'AI 生成失败，请重试或联系管理员'
    }
  } catch {
    errorMessage.value = '生成失败，请稍后重试'
  } finally {
    generating.value = false
  }
}

async function download() {
  if (!record.value || record.value.status !== 'success') {
    ElMessage.warning('指导单尚未就绪')
    return
  }
  await exportPdf(record.value, printSheetRef)
}

function formatTime(t?: string) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

watch(
  () => [props.visible, props.registerId] as const,
  ([visible, rid]) => {
    if (!visible) return
    // 没传 record 才主动加载
    if (props.record === undefined && rid != null) {
      void loadByRegisterId()
    }
  },
  { immediate: true },
)
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    title="用药指导单"
    width="900px"
    top="5vh"
    align-center
    append-to-body
    destroy-on-close
    class="mg-preview-dialog"
  >
    <!-- 顶部患者摘要条 -->
    <div class="mg-preview-summary">
      <ElDescriptions :column="3" border size="small">
        <ElDescriptionsItem label="患者">{{ record?.patientName || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="挂号号">{{ record?.registerId ?? '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="生成时间">{{ formatTime(record?.createTime) }}</ElDescriptionsItem>
      </ElDescriptions>
    </div>

    <!-- 主体滚动区 -->
    <div v-loading="loading || generating" class="mg-preview-body">
      <ElAlert
        v-if="errorMessage"
        type="error"
        :title="errorMessage"
        show-icon
        :closable="false"
        class="mg-preview-alert"
      />

      <template v-else-if="record && isSuccess">
        <!-- A4 缩放容器：MedicationGuidePrintSheet 本体 210mm，这里缩放展示 -->
        <div class="mg-preview-scale">
          <MedicationGuidePrintSheet ref="printSheetRef" :record="record" />
        </div>
      </template>

      <ElEmpty
        v-else-if="!record"
        description="暂无用药指导单"
      />

      <ElEmpty
        v-else-if="isFailed"
        description="指导单生成失败"
      >
        <ElButton type="warning" plain @click="regenerate">重新生成</ElButton>
      </ElEmpty>
    </div>

    <template #footer>
      <div class="mg-preview-footer">
        <ElButton @click="dialogVisible = false">关闭</ElButton>
        <ElButton
          v-if="showRetry && (isFailed || !record)"
          type="warning"
          :loading="generating"
          @click="regenerate"
        >{{ generating ? '生成中…' : '生成指导单' }}</ElButton>
        <ElButton
          type="primary"
          :loading="exporting"
          :disabled="!isSuccess"
          @click="download"
        >下载 PDF</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.mg-preview-summary {
  margin-block-end: var(--space-3);
}

.mg-preview-body {
  max-height: 65vh;
  overflow-y: auto;
  padding-inline: var(--space-1);
}

.mg-preview-alert {
  margin-block-end: var(--space-3);
}

/* A4 210mm ≈ 794px @96dpi；弹窗内容区约 850px，缩放 0.85 后正好留出页边距 */
.mg-preview-scale {
  display: flex;
  justify-content: center;
  padding: var(--space-2) 0;
}

.mg-preview-scale :deep(.mg-sheet) {
  /* 保留 A4 比例但适配弹窗 */
  width: 100%;
  max-width: 760px;
  min-height: auto;
  padding: 14mm 12mm;
  box-shadow: var(--shadow-md, 0 4px 16px rgba(0, 0, 0, 0.08));
  border-radius: 6px;
}

.mg-preview-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}
</style>
