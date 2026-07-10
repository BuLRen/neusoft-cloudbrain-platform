<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElButton, ElInput } from 'element-plus'

const props = defineProps<{
  disabled?: boolean
  sending?: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
  sendCard: [messageType: 'drug_card' | 'diagnosis_card', payload: Record<string, unknown>]
  openPicker: [mode: 'drug' | 'diagnosis']
  openRevisitReminder: []
}>()

const draft = ref('')

function submit() {
  const text = draft.value.trim()
  if (!text || props.disabled) return
  emit('send', text)
  draft.value = ''
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter') return

  if (event.ctrlKey || event.metaKey) {
    event.preventDefault()
    const target = event.target as HTMLTextAreaElement | null
    if (!target || target.selectionStart == null) return
    const start = target.selectionStart
    const end = target.selectionEnd ?? start
    draft.value = `${draft.value.slice(0, start)}\n${draft.value.slice(end)}`
    void nextTick(() => {
      target.selectionStart = target.selectionEnd = start + 1
    })
    return
  }

  if (event.shiftKey || event.altKey) return

  event.preventDefault()
  submit()
}
</script>

<template>
  <div class="comm-composer">
    <ElInput
      v-model="draft"
      type="textarea"
      :rows="3"
      :disabled="disabled"
      placeholder="输入消息发送给患者..."
      @keydown="onKeydown"
    />
    <div class="comm-composer__actions">
      <div class="comm-composer__extras">
        <ElButton
          size="small"
          type="warning"
          plain
          :disabled="disabled || sending"
          @click="emit('openRevisitReminder')"
        >
          复诊提醒
        </ElButton>
        <ElButton size="small" :disabled="disabled || sending" @click="emit('openPicker', 'drug')">荐药卡片</ElButton>
        <ElButton size="small" :disabled="disabled || sending" @click="emit('openPicker', 'diagnosis')">病况卡片</ElButton>
      </div>
      <span class="comm-composer__hint">Enter 发送 · Ctrl + Enter 换行</span>
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

.comm-composer__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.comm-composer__extras {
  display: flex;
  flex-wrap: wrap;
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
