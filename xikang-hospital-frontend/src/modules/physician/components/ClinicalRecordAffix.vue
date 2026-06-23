<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElAffix, ElBadge, ElIcon, ElMessage } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import ClinicalRecordDrawer from './ClinicalRecordDrawer.vue'

const props = withDefaults(defineProps<{
  registerId: number | null
  mode: 'physician' | 'patient'
  disabled?: boolean
  disabledHint?: string
  subtitle?: string
}>(), {
  disabled: false,
  disabledHint: '请先选择就诊记录',
})

const drawerVisible = ref(false)
const drawerRef = ref<InstanceType<typeof ClinicalRecordDrawer> | null>(null)

const badgeText = computed(() => {
  if (props.disabled || !props.registerId) return ''
  return props.mode === 'physician' ? '' : ''
})

function openDrawer() {
  if (props.disabled || !props.registerId) {
    ElMessage.info(props.disabledHint)
    return
  }
  drawerVisible.value = true
}

defineExpose({
  open: openDrawer,
  reload: () => drawerRef.value?.reload(),
})
</script>

<template>
  <Teleport to="body">
    <div class="clinical-affix-anchor">
      <ElAffix position="bottom" :offset="24">
        <button
          type="button"
          class="clinical-affix__trigger"
          aria-label="打开病历本"
          :class="{ 'clinical-affix__trigger--disabled': disabled || !registerId }"
          @click="openDrawer"
        >
          <ElBadge :hidden="!badgeText" :value="badgeText">
            <span class="clinical-affix__icon-wrap">
              <ElIcon :size="22"><Document /></ElIcon>
            </span>
          </ElBadge>
          <span class="clinical-affix__label">病历本</span>
        </button>
      </ElAffix>
    </div>
  </Teleport>

  <ClinicalRecordDrawer
    ref="drawerRef"
    v-model:visible="drawerVisible"
    :register-id="registerId"
    :mode="mode"
    :subtitle="subtitle"
  />
</template>

<style scoped>
.clinical-affix-anchor {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1200;
}

.clinical-affix__trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 48px;
  padding: 0 var(--space-4);
  border: none;
  border-radius: 999px;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-strong, #1677ff));
  box-shadow: 0 8px 24px rgba(31, 140, 255, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.clinical-affix__trigger:hover:not(.clinical-affix__trigger--disabled) {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(31, 140, 255, 0.42);
}

.clinical-affix__trigger--disabled {
  opacity: 0.72;
  cursor: not-allowed;
}

.clinical-affix__icon-wrap {
  display: grid;
  place-items: center;
}

@media (max-width: 640px) {
  .clinical-affix-anchor {
    right: 16px;
    bottom: 16px;
  }

  .clinical-affix__label {
    display: none;
  }

  .clinical-affix__trigger {
    width: 52px;
    height: 52px;
    padding: 0;
    justify-content: center;
    border-radius: 50%;
  }
}
</style>
