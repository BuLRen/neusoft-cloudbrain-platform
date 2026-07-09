<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDialog, ElInput, ElTag } from 'element-plus'

const QUICK_TEMPLATES = [
  '建议您近期到院复诊，请点击下方按钮自行预约。',
  '根据近期血糖监测，建议尽快复诊，请自行挂号。',
] as const

const DEFAULT_REMINDER = QUICK_TEMPLATES[0]

const props = defineProps<{
  visible: boolean
  sending?: boolean
  patientName?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  send: [reminderText: string]
}>()

const reminderText = ref(DEFAULT_REMINDER)

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

watch(
  () => props.visible,
  (open) => {
    if (open) {
      reminderText.value = DEFAULT_REMINDER
    }
  },
)

function applyTemplate(text: string) {
  reminderText.value = text
}

function handleSend() {
  const text = reminderText.value.trim()
  if (!text || props.sending) return
  emit('send', text)
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    title="发送复诊提醒"
    width="520px"
    :lock-scroll="false"
    destroy-on-close
  >
    <p class="revisit-dialog__intro">
      将向患者发送<strong>复诊挂号卡片</strong>，患者可点击卡片自行预约。
      <template v-if="patientName">当前患者：{{ patientName }}</template>
    </p>

    <div class="revisit-dialog__templates">
      <span class="revisit-dialog__label">快捷话术</span>
      <div class="revisit-dialog__chips">
        <ElTag
          v-for="item in QUICK_TEMPLATES"
          :key="item"
          class="revisit-dialog__chip"
          effect="plain"
          type="warning"
          @click="applyTemplate(item)"
        >
          {{ item }}
        </ElTag>
      </div>
    </div>

    <label class="revisit-dialog__label" for="revisit-reminder-text">提醒内容</label>
    <ElInput
      id="revisit-reminder-text"
      v-model="reminderText"
      type="textarea"
      :rows="4"
      :disabled="sending"
      placeholder="填写复诊提醒说明，将展示在挂号卡片中"
    />

    <template #footer>
      <ElButton @click="dialogVisible = false">取消</ElButton>
      <ElButton
        type="primary"
        :disabled="!reminderText.trim()"
        :loading="sending"
        @click="handleSend"
      >
        发送挂号卡片
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.revisit-dialog__intro {
  margin: 0 0 var(--space-4);
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-muted);
}

.revisit-dialog__templates {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.revisit-dialog__label {
  display: block;
  margin-bottom: var(--space-2);
  font-size: 13px;
  font-weight: 650;
}

.revisit-dialog__chips {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.revisit-dialog__chip {
  cursor: pointer;
  white-space: normal;
  height: auto;
  padding: var(--space-2) var(--space-3);
  line-height: 1.5;
}
</style>
