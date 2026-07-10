<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElIcon, ElMessage } from 'element-plus'
import { ArrowDown, ArrowUp, Lock } from '@element-plus/icons-vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { queueApi, type CallingResult, type PhysicianQueueItem } from '@/shared/api/modules/physician'

const props = defineProps<{
  selectedRegisterId?: number | null
  currentCalling?: CallingResult | null
  callingBusy?: boolean
}>()

const emit = defineEmits<{
  select: [registerId: number]
  refreshed: []
  'call-next': []
  'recall-current': [registerId: number]
}>()

const items = ref<PhysicianQueueItem[]>([])
const loading = ref(false)
const busy = ref(false)

const canRecall = computed(() =>
  Boolean(
    props.currentCalling?.hasCalling
    && props.currentCalling?.registerId
    && !props.callingBusy,
  ),
)

function callStatusLabel(item: PhysicianQueueItem): string {
  switch (item.callStatus) {
    case 1: return '呼叫中'
    case 3: return item.callRound && item.callRound >= 2 ? '过号(终)' : '过号'
    case 0: return '未叫'
    default: return '—'
  }
}

function callStatusTone(item: PhysicianQueueItem): 'primary' | 'success' | 'warning' | 'danger' | 'neutral' {
  switch (item.callStatus) {
    case 1: return 'warning'
    case 3: return 'danger'
    case 0: return 'neutral'
    default: return 'neutral'
  }
}

function canMoveUp(index: number): boolean {
  if (index <= 0) return false
  const prev = items.value[index - 1]
  return Boolean(items.value[index].canReorder && prev?.canReorder)
}

function canMoveDown(index: number): boolean {
  if (index >= items.value.length - 1) return false
  const next = items.value[index + 1]
  return Boolean(items.value[index].canReorder && next?.canReorder)
}

async function refresh() {
  loading.value = true
  try {
    items.value = await queueApi.waiting() || []
    emit('refreshed')
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function persistOrder() {
  busy.value = true
  try {
    await queueApi.reorder(items.value.map((i) => i.registerId))
    await refresh()
    ElMessage.success('队列顺序已更新')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '调整顺序失败'
    ElMessage.error(msg)
    await refresh()
  } finally {
    busy.value = false
  }
}

function move(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= items.value.length) return
  if (!items.value[index].canReorder || !items.value[target].canReorder) return
  const list = [...items.value]
  const tmp = list[index]
  list[index] = list[target]
  list[target] = tmp
  items.value = list
  void persistOrder()
}

function onRecall() {
  const id = props.currentCalling?.registerId
  if (id == null) return
  emit('recall-current', id)
}

defineExpose({ refresh })

void refresh()
</script>

<template>
  <GlassCard class="waiting-queue-panel">
    <div class="panel-heading">
      <h2>候诊队列</h2>
    </div>

    <div class="waiting-queue__calling-pad">
      <button
        type="button"
        class="call-btn call-btn--primary"
        :disabled="callingBusy"
        @click="emit('call-next')"
      >
        <span class="call-btn__icon" aria-hidden="true">▶</span>
        叫下一个
      </button>
      <button
        type="button"
        class="call-btn call-btn--ghost"
        :disabled="!canRecall"
        @click="onRecall"
      >
        重呼当前
      </button>
    </div>

    <div v-if="loading" class="waiting-queue__hint">加载中…</div>
    <div v-else-if="!items.length" class="waiting-queue__hint">今日暂无候诊患者</div>

    <ul v-else class="waiting-queue__list">
      <li
        v-for="(item, index) in items"
        :key="item.registerId"
        class="waiting-queue__item"
        :class="{
          'is-active': item.registerId === selectedRegisterId,
          'is-calling': item.callStatus === 1,
        }"
      >
        <button type="button" class="waiting-queue__main" @click="emit('select', item.registerId)">
          <span class="waiting-queue__num" aria-hidden="true">{{ item.queueNumber ?? '—' }}</span>
          <span class="waiting-queue__body">
            <strong class="waiting-queue__name">{{ item.realName || '—' }}</strong>
            <span class="waiting-queue__meta">
              {{ item.caseNumber || '暂无病历号' }}
            </span>
          </span>
          <StatusTag :tone="callStatusTone(item)">
            {{ callStatusLabel(item) }}
          </StatusTag>
        </button>

        <div v-if="item.canReorder" class="waiting-queue__actions">
          <button
            type="button"
            class="move-btn"
            :disabled="busy || !canMoveUp(index)"
            title="上移"
            aria-label="上移"
            @click.stop="move(index, -1)"
          >
            <ElIcon :size="14"><ArrowUp /></ElIcon>
          </button>
          <button
            type="button"
            class="move-btn"
            :disabled="busy || !canMoveDown(index)"
            title="下移"
            aria-label="下移"
            @click.stop="move(index, 1)"
          >
            <ElIcon :size="14"><ArrowDown /></ElIcon>
          </button>
        </div>
        <span
          v-else-if="item.callStatus === 1"
          class="waiting-queue__lock"
          title="正在叫号，暂不可调整顺序"
        >
          <ElIcon :size="12"><Lock /></ElIcon>
          锁定
        </span>
      </li>
    </ul>
  </GlassCard>
</template>

<style scoped>
.waiting-queue-panel {
  padding: var(--space-5);
}

.panel-heading h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.panel-heading p {
  margin-block-start: 6px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.waiting-queue__calling-pad {
  margin-block-start: var(--space-4);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2);
}

.call-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease, border-color 0.15s ease;
}

.call-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.call-btn:not(:disabled):active {
  transform: translateY(1px);
}

.call-btn__icon {
  font-size: 10px;
}

.call-btn--primary {
  background: var(--gradient-primary);
  color: #fff;
  box-shadow: 0 4px 12px rgba(31, 140, 255, 0.28);
}

.call-btn--primary:not(:disabled):hover {
  box-shadow: 0 6px 16px rgba(31, 140, 255, 0.38);
  transform: translateY(-1px);
}

.call-btn--ghost {
  background: var(--color-control);
  color: var(--color-text);
  border-color: var(--color-border);
}

.call-btn--ghost:not(:disabled):hover {
  background: var(--color-control-hover);
  border-color: var(--color-border-strong);
}

.waiting-queue__hint {
  margin-block-start: var(--space-4);
  padding: var(--space-4);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  text-align: center;
  font-size: 13px;
  color: var(--color-text-soft);
  background: rgba(255, 255, 255, 0.4);
}

.waiting-queue__list {
  list-style: none;
  margin: var(--space-4) 0 0;
  padding: 0;
  display: grid;
  gap: var(--space-2);
  max-height: 280px;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-inline-end: 2px;
}

.waiting-queue__item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.55);
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.waiting-queue__item:hover {
  border-color: rgba(31, 140, 255, 0.28);
  background: rgba(255, 255, 255, 0.92);
}

.waiting-queue__item.is-active {
  border-color: rgba(31, 140, 255, 0.45);
  background: linear-gradient(90deg, rgba(31, 140, 255, 0.1), rgba(255, 255, 255, 0.95));
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.waiting-queue__item.is-calling {
  border-color: rgba(245, 159, 0, 0.45);
  background: linear-gradient(90deg, rgba(245, 159, 0, 0.1), rgba(255, 255, 255, 0.95));
}

.waiting-queue__item.is-calling.is-active {
  box-shadow: inset 3px 0 0 var(--color-warning);
}

.waiting-queue__main {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border: none;
  background: transparent;
  text-align: start;
  cursor: pointer;
}

.waiting-queue__num {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  color: var(--color-primary-strong);
  font-size: 15px;
  font-weight: 800;
  background: var(--color-primary-soft);
}

.waiting-queue__item.is-calling .waiting-queue__num {
  color: var(--color-warning-strong);
  background: var(--color-warning-soft);
}

.waiting-queue__body {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.waiting-queue__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
}

.waiting-queue__meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-muted);
  font-size: 12px;
}

.waiting-queue__actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-inline-end: var(--space-3);
}

.move-btn {
  display: grid;
  place-items: center;
  width: 28px;
  height: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.move-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.move-btn:not(:disabled):hover {
  border-color: rgba(31, 140, 255, 0.35);
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}

.waiting-queue__lock {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  margin-inline-end: var(--space-3);
  padding: 4px 8px;
  border-radius: 999px;
  color: var(--color-warning-strong);
  font-size: 11px;
  font-weight: 600;
  background: var(--color-warning-soft);
}

@media (prefers-reduced-motion: reduce) {
  .call-btn {
    transition: none;
  }

  .call-btn:not(:disabled):active,
  .call-btn--primary:not(:disabled):hover {
    transform: none;
  }
}
</style>
