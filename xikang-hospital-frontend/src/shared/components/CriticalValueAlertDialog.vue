<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ElButton, ElDialog, ElInput, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { criticalValueApi, type CriticalItemHit, type CriticalValueAlert } from '@/shared/api/modules/criticalValue'

const props = defineProps<{
  alert: CriticalValueAlert | null
}>()

const emit = defineEmits<{
  resolved: [alertId: number]
}>()

const visible = computed(() => Boolean(props.alert))
const step = ref<'ack' | 'handle'>('ack')
const acknowledging = ref(false)
const handling = ref(false)
const handleNote = ref('')
const nowTs = ref(Date.now())
let ticker: ReturnType<typeof setInterval> | null = null

const items = computed<CriticalItemHit[]>(() => {
  const raw = props.alert?.criticalItems
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return []
    }
  }
  return []
})

const isEscalated = computed(() => props.alert?.status === 'ESCALATED')

const countdownText = computed(() => {
  const deadline = props.alert?.ackDeadline
  if (!deadline) return ''
  const remainMs = new Date(deadline).getTime() - nowTs.value
  if (remainMs <= 0) return '已超时'
  const totalSec = Math.floor(remainMs / 1000)
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  return `${min}:${String(sec).padStart(2, '0')}`
})

watch(
  () => props.alert?.id,
  () => {
    step.value = 'ack'
    handleNote.value = ''
  },
)

watch(visible, (open) => {
  if (open) {
    nowTs.value = Date.now()
    ticker = setInterval(() => {
      nowTs.value = Date.now()
    }, 1000)
    return
  }
  if (ticker) {
    clearInterval(ticker)
    ticker = null
  }
})

onUnmounted(() => {
  if (ticker) clearInterval(ticker)
})

async function confirmAck() {
  if (!props.alert) return
  acknowledging.value = true
  try {
    await criticalValueApi.ack(props.alert.id)
    step.value = 'handle'
  } finally {
    acknowledging.value = false
  }
}

async function submitHandle() {
  if (!props.alert || !handleNote.value.trim()) return
  handling.value = true
  try {
    await criticalValueApi.handle(props.alert.id, handleNote.value.trim())
    emit('resolved', props.alert.id)
  } finally {
    handling.value = false
  }
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="isEscalated ? '危急值升级告警' : '危急值强制签收'"
    width="720px"
    class="cv-alert-dialog"
    :class="{ 'cv-alert-dialog--escalated': isEscalated }"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    append-to-body
    align-center
  >
    <div v-if="alert" class="cv-alert-dialog__banner">
      <p class="cv-alert-dialog__patient">
        {{ alert.patientName || '患者' }}
        <span v-if="alert.caseNumber">（{{ alert.caseNumber }}）</span>
      </p>
      <p class="cv-alert-dialog__meta">
        {{ alert.techName || '医技项目' }} · 上报人 {{ alert.reporterName || '-' }}
        <ElTag v-if="isEscalated" type="danger" effect="dark" size="small">已升级</ElTag>
        <ElTag v-else type="danger" size="small">待签收</ElTag>
      </p>
      <p v-if="alert.ackDeadline" class="cv-alert-dialog__countdown">
        签收倒计时：<strong>{{ countdownText }}</strong>
      </p>
    </div>

    <ElTable :data="items" size="small" border stripe>
      <ElTableColumn prop="itemName" label="危急项" min-width="120" />
      <ElTableColumn label="结果" min-width="100">
        <template #default="{ row }">
          {{ row.value }}{{ row.unit ? ` ${row.unit}` : '' }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="referenceRange" label="参考范围" min-width="120" />
      <ElTableColumn prop="reason" label="说明" min-width="160" show-overflow-tooltip />
    </ElTable>

    <div v-if="step === 'handle'" class="cv-alert-dialog__handle">
      <label for="cv-handle-note">处置意见（必填）</label>
      <ElInput
        id="cv-handle-note"
        v-model="handleNote"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="请填写临床处置措施，如：立即联系患者、复查、调整治疗方案等"
      />
    </div>

    <template #footer>
      <template v-if="step === 'ack'">
        <ElButton type="danger" :loading="acknowledging" @click="confirmAck">我已知晓，签收</ElButton>
      </template>
      <template v-else>
        <ElButton type="primary" :loading="handling" :disabled="!handleNote.trim()" @click="submitHandle">
          提交处置
        </ElButton>
      </template>
    </template>
  </ElDialog>
</template>

<style scoped>
.cv-alert-dialog--escalated :deep(.el-dialog) {
  border: 2px solid var(--el-color-danger);
  box-shadow: 0 0 24px rgba(245, 108, 108, 0.45);
}

.cv-alert-dialog__banner {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  background: linear-gradient(135deg, #fff1f0, #ffe7e7);
  border: 1px solid #ffccc7;
}

.cv-alert-dialog__patient {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #cf1322;
}

.cv-alert-dialog__meta {
  margin: 6px 0 0;
  color: var(--el-text-color-regular);
}

.cv-alert-dialog__countdown {
  margin: 8px 0 0;
  color: #a8071a;
}

.cv-alert-dialog__handle {
  margin-top: 14px;
  display: grid;
  gap: 8px;
}
</style>
