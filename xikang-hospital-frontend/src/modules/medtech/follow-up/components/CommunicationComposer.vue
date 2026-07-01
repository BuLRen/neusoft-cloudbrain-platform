<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElInput, ElTag } from 'element-plus'

const REVISIT_QUICK_TEMPLATES = [
  '【复诊提醒】建议您近期到院复诊，请通过患者端「我的挂号」自行预约。',
  '【复诊提醒】根据近期血糖监测，建议尽快复诊，请自行挂号。',
] as const

const props = defineProps<{
  disabled?: boolean
  sending?: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
}>()

const draft = ref('')

function submit() {
  const text = draft.value.trim()
  if (!text || props.disabled) return
  emit('send', text)
  draft.value = ''
}

function sendQuickTemplate(text: string) {
  if (props.disabled || props.sending) return
  emit('send', text)
}
</script>

<template>
  <div class="comm-composer">
    <div class="comm-composer__quick">
      <span class="comm-composer__quick-label">复诊提醒话术</span>
      <div class="comm-composer__chips">
        <ElTag
          v-for="item in REVISIT_QUICK_TEMPLATES"
          :key="item"
          class="comm-composer__chip"
          :class="{ 'is-disabled': disabled || sending }"
          effect="plain"
          type="warning"
          @click="sendQuickTemplate(item)"
        >
          {{ item }}
        </ElTag>
      </div>
    </div>
    <ElInput
      v-model="draft"
      type="textarea"
      :rows="3"
      :disabled="disabled"
      placeholder="输入消息发送给患者（复诊请使用上方话术，勿代患者挂号）..."
      @keydown.ctrl.enter="submit"
    />
    <div class="comm-composer__actions">
      <span class="comm-composer__hint">Ctrl + Enter 发送 · 随访系统不参与挂号</span>
      <ElButton type="primary" :disabled="disabled || !draft.trim()" :loading="sending" @click="submit">
        发送
      </ElButton>
    </div>
  </div>
</template>

<style scoped>
.comm-composer {
  display: grid;
  gap: var(--space-2);
  flex-shrink: 0;
}

.comm-composer__quick {
  display: grid;
  gap: var(--space-2);
}

.comm-composer__quick-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.comm-composer__chips {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.comm-composer__chip {
  cursor: pointer;
  white-space: normal;
  height: auto;
  padding: var(--space-2) var(--space-3);
  line-height: 1.5;
}

.comm-composer__chip.is-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.comm-composer__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.comm-composer__hint {
  color: var(--color-text-soft);
  font-size: 12px;
}

.comm-composer :deep(.el-textarea__inner) {
  resize: none;
  min-height: 72px;
  max-height: 120px;
}
</style>
