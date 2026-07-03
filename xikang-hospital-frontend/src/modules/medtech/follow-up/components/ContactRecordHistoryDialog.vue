<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElEmpty, ElTimeline, ElTimelineItem } from 'element-plus'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import type { FollowUpContactRecord } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  registerId?: number
}>()

const visible = defineModel<boolean>({ default: false })

const records = ref<FollowUpContactRecord[]>([])
const loading = ref(false)

const channelLabels: Record<string, string> = {
  phone: '电话',
  wechat: '微信',
  visit: '上门/到院',
  other: '其他',
}

async function load() {
  if (!props.registerId) return
  loading.value = true
  try {
    records.value = await medtechFollowUpApi.listContactRecords(props.registerId, 50)
  } finally {
    loading.value = false
  }
}

watch(visible, (open) => {
  if (open) void load()
})
</script>

<template>
  <ElDialog v-model="visible" title="历史联系记录" width="560px">
    <div v-loading="loading">
      <ElTimeline v-if="records.length">
        <ElTimelineItem
          v-for="item in records"
          :key="item.id"
          :timestamp="`${item.contactDate}${item.employeeName ? ' · ' + item.employeeName : ''}`"
        >
          <strong>{{ channelLabels[item.channel] ?? item.channel }}</strong>
          <span v-if="item.durationMinutes"> · {{ item.durationMinutes }} 分钟</span>
          <p>{{ item.summary }}</p>
          <p v-if="item.nextAction" class="contact-history__next">跟进：{{ item.nextAction }}</p>
        </ElTimelineItem>
      </ElTimeline>
      <ElEmpty v-else description="暂无联系记录" />
    </div>
  </ElDialog>
</template>

<style scoped>
.contact-history__next {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
}
</style>
