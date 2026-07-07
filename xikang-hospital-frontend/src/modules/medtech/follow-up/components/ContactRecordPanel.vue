<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElTimeline,
  ElTimelineItem,
  ElMessage,
} from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import type { FollowUpContactRecord } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  registerId?: number
}>()

const records = ref<FollowUpContactRecord[]>([])
const loading = ref(false)
const saving = ref(false)

const form = reactive({
  channel: 'phone' as FollowUpContactRecord['channel'],
  durationMinutes: undefined as number | undefined,
  summary: '',
  nextAction: '',
})

const channelOptions = [
  { value: 'phone', label: '电话' },
  { value: 'wechat', label: '微信' },
  { value: 'visit', label: '上门/到院' },
  { value: 'other', label: '其他' },
]

async function loadRecords() {
  if (!props.registerId) {
    records.value = []
    return
  }
  loading.value = true
  try {
    records.value = await medtechFollowUpApi.listContactRecords(props.registerId, 20)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!props.registerId) return
  if (!form.summary.trim()) {
    ElMessage.warning('请填写联系摘要')
    return
  }
  saving.value = true
  try {
    await medtechFollowUpApi.createContactRecord({
      registerId: props.registerId,
      channel: form.channel,
      durationMinutes: form.durationMinutes,
      summary: form.summary.trim(),
      nextAction: form.nextAction.trim() || undefined,
    })
    form.summary = ''
    form.nextAction = ''
    form.durationMinutes = undefined
    ElMessage.success('联系记录已保存')
    await loadRecords()
  } finally {
    saving.value = false
  }
}

watch(
  () => props.registerId,
  () => {
    void loadRecords()
  },
)

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <GlassCard class="contact-record-panel" v-loading="loading">
    <h4 class="contact-record-panel__title">联系记录</h4>
    <p class="contact-record-panel__hint">记录与患者的每日联系，将同步至随访历史。</p>

    <ElForm v-if="registerId" label-width="72px" class="contact-record-panel__form">
      <ElFormItem label="方式">
        <ElSelect v-model="form.channel" style="width: 100%">
          <ElOption v-for="opt in channelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="时长">
        <ElInputNumber v-model="form.durationMinutes" :min="1" :max="240" placeholder="分钟" />
      </ElFormItem>
      <ElFormItem label="摘要" required>
        <ElInput v-model="form.summary" type="textarea" :rows="2" placeholder="本次沟通要点" />
      </ElFormItem>
      <ElFormItem label="跟进">
        <ElInput v-model="form.nextAction" placeholder="下次跟进事项（可选）" />
      </ElFormItem>
      <ElButton type="primary" :loading="saving" @click="submit">保存联系记录</ElButton>
    </ElForm>

    <ElTimeline v-if="records.length" class="contact-record-panel__timeline">
      <ElTimelineItem
        v-for="item in records"
        :key="item.id"
        :timestamp="`${item.contactDate}${item.employeeName ? ' · ' + item.employeeName : ''}`"
      >
        <strong>{{ channelOptions.find((c) => c.value === item.channel)?.label ?? item.channel }}</strong>
        <p>{{ item.summary }}</p>
        <p v-if="item.nextAction" class="contact-record-panel__next">跟进：{{ item.nextAction }}</p>
      </ElTimelineItem>
    </ElTimeline>
    <p v-else-if="registerId" class="contact-record-panel__empty">暂无联系记录</p>
  </GlassCard>
</template>

<style scoped>
.contact-record-panel {
  padding: var(--space-4);
}

.contact-record-panel__title {
  margin: 0 0 var(--space-1);
  font-size: 15px;
}

.contact-record-panel__hint {
  margin: 0 0 var(--space-3);
  color: var(--color-text-muted);
  font-size: 12px;
}

.contact-record-panel__form {
  margin-block-end: var(--space-4);
}

.contact-record-panel__timeline {
  margin-block-start: var(--space-3);
}

.contact-record-panel__timeline p {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.contact-record-panel__next {
  font-size: 12px !important;
}

.contact-record-panel__empty {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 13px;
}
</style>
