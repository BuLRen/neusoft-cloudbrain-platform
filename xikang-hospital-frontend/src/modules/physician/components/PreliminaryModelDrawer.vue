<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDrawer, ElIcon } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import {
  DEFAULT_PRELIMINARY_AI_MODEL,
  PRELIMINARY_AI_MODEL_GROUPS,
  findPreliminaryAiModel,
} from '../constants/preliminary-ai-models'

const visible = defineModel<boolean>('visible', { default: false })
const selectedModel = defineModel<string>('model', { default: DEFAULT_PRELIMINARY_AI_MODEL })

const draftModel = ref(selectedModel.value)

watch(visible, (open) => {
  if (open) {
    draftModel.value = selectedModel.value || DEFAULT_PRELIMINARY_AI_MODEL
  }
})

const draftModelLabel = computed(() => findPreliminaryAiModel(draftModel.value)?.label ?? draftModel.value)

function selectModel(modelId: string) {
  draftModel.value = modelId
}

function confirmSelection() {
  selectedModel.value = draftModel.value
  visible.value = false
}
</script>

<template>
  <ElDrawer
    v-model="visible"
    title="选择诊断模型"
    direction="rtl"
    size="360px"
    class="model-drawer"
  >
    <p class="model-drawer__hint">所选模型将作为 <code>model</code> 传入 AI 工作流。</p>

    <div v-for="group in PRELIMINARY_AI_MODEL_GROUPS" :key="group.provider" class="model-drawer__group">
      <h3 class="model-drawer__group-title">{{ group.providerLabel }}</h3>
      <ul class="model-drawer__list" role="listbox" :aria-label="group.providerLabel">
        <li
          v-for="item in group.models"
          :key="item.id"
          class="model-drawer__item"
          :class="{ 'model-drawer__item--active': draftModel === item.id }"
          role="option"
          :aria-selected="draftModel === item.id"
          tabindex="0"
          @click="selectModel(item.id)"
          @keydown.enter="selectModel(item.id)"
        >
          <span class="model-drawer__item-main">
            <span class="model-drawer__item-name">{{ item.label }}</span>
            <span v-if="item.description" class="model-drawer__item-desc">{{ item.description }}</span>
          </span>
          <ElIcon v-if="draftModel === item.id" class="model-drawer__check"><Check /></ElIcon>
        </li>
      </ul>
    </div>

    <template #footer>
      <div class="model-drawer__footer">
        <span class="model-drawer__footer-label">已选：{{ draftModelLabel }}</span>
        <ElButton type="primary" @click="confirmSelection">确定</ElButton>
      </div>
    </template>
  </ElDrawer>
</template>

<style scoped>
.model-drawer__hint {
  margin: 0 0 var(--space-4);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  line-height: 1.6;
}

.model-drawer__hint code {
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: var(--color-primary-soft);
  font-size: 0.9em;
}

.model-drawer__group {
  margin-block-end: var(--space-5);
}

.model-drawer__group-title {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text-muted);
}

.model-drawer__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.model-drawer__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.model-drawer__item:hover {
  background: var(--color-menu-hover);
}

.model-drawer__item--active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.model-drawer__item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.model-drawer__item-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-text);
  word-break: break-all;
}

.model-drawer__item-desc {
  font-size: 12px;
  color: var(--color-text-soft);
}

.model-drawer__check {
  flex-shrink: 0;
  color: var(--color-primary);
  font-size: 18px;
}

.model-drawer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  width: 100%;
}

.model-drawer__footer-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
