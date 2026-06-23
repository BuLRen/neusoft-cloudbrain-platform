<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElButton, ElTag } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import {
  CRITICAL_EXPIRY_DAYS,
  NEAR_EXPIRY_DAYS,
} from '@/shared/constants/pharmacy'
import type { ExpiringStockItem } from '@/shared/types/pharmacy'

const props = defineProps<{
  /** 是否在挂载时自动加载；默认 true。传入 false 时由父组件控制 load() */
  autoLoad?: boolean
}>()

const emit = defineEmits<{
  (e: 'loaded', count: number): void
}>()

const expiringStocks = ref<ExpiringStockItem[]>([])
const loading = ref(false)

const criticalCount = computed(() =>
  expiringStocks.value.filter((s) => (s.daysRemaining ?? 999) <= CRITICAL_EXPIRY_DAYS).length,
)
const nearCount = computed(() =>
  expiringStocks.value.filter((s) => {
    const d = s.daysRemaining ?? 999
    return d > CRITICAL_EXPIRY_DAYS && d <= NEAR_EXPIRY_DAYS
  }).length,
)
const total = computed(() => expiringStocks.value.length)
const hasCritical = computed(() => criticalCount.value > 0)

async function load() {
  loading.value = true
  try {
    expiringStocks.value = await pharmacyApi.expiringStock(NEAR_EXPIRY_DAYS)
    emit('loaded', expiringStocks.value.length)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (props.autoLoad !== false) void load()
})

defineExpose({ load, refresh: load })
</script>

<template>
  <GlassCard
    v-if="total > 0"
    class="expiry-banner"
    :class="{ 'is-critical': hasCritical }"
  >
    <div class="expiry-banner__inner">
      <div class="expiry-banner__icon" aria-hidden>⚠</div>
      <div class="expiry-banner__text">
        <strong>近效期预警</strong>
        <span class="expiry-banner__detail">
          <ElTag v-if="criticalCount > 0" type="danger" size="small">
            {{ criticalCount }} 批次 ≤ {{ CRITICAL_EXPIRY_DAYS }} 天到期
          </ElTag>
          <ElTag v-if="nearCount > 0" type="warning" size="small">
            {{ nearCount }} 批次 ≤ {{ NEAR_EXPIRY_DAYS }} 天到期
          </ElTag>
          <span class="expiry-banner__hint">发药时请优先出库近期批次，避免过期损耗。</span>
        </span>
      </div>
      <div class="expiry-banner__action">
        <ElButton link size="small" :loading="loading" @click="load">刷新</ElButton>
      </div>
    </div>
  </GlassCard>
</template>

<style scoped>
.expiry-banner {
  padding: var(--space-3) var(--space-4);
  border-left: 4px solid var(--color-warning, #e6a23c);
  background: var(--color-surface);
}

.expiry-banner.is-critical {
  border-left-color: var(--color-danger, #f56c6c);
}

.expiry-banner__inner {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.expiry-banner__icon {
  font-size: 20px;
  color: var(--color-warning, #e6a23c);
}

.is-critical .expiry-banner__icon {
  color: var(--color-danger, #f56c6c);
}

.expiry-banner__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 auto;
}

.expiry-banner__detail {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.expiry-banner__hint {
  color: var(--color-text-muted);
  font-size: 13px;
}

.expiry-banner__action {
  margin-left: auto;
}
</style>
