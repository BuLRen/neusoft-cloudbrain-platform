<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElInput } from 'element-plus'

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
</script>

<template>
  <div class="comm-composer">
    <ElInput
      v-model="draft"
      type="textarea"
      :rows="3"
      :disabled="disabled"
      placeholder="输入消息发送给患者..."
      @keydown.ctrl.enter="submit"
    />
    <div class="comm-composer__actions">
      <span class="comm-composer__hint">Ctrl + Enter 发送</span>
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
