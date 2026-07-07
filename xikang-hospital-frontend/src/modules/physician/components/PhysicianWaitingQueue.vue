<template>
  <GlassCard class="waiting-queue-panel">
    <div class="panel-heading">
      <h2>候诊队列</h2>
      <p>调整看诊顺序（正在叫号的患者不可移动）</p>
    </div>

    <div v-if="loading" class="waiting-queue__loading">加载中…</div>
    <div v-else-if="!items.length" class="waiting-queue__empty">今日暂无候诊患者</div>

    <ul v-else class="waiting-queue__list">
      <li
        v-for="(item, index) in items"
        :key="item.registerId"
        class="waiting-queue__item"
        :class="{
          'is-active': item.registerId === selectedRegisterId,
          'is-calling': item.callStatus === 1,
          'is-locked': !item.canReorder,
        }"
      >
        <button type="button" class="waiting-queue__main" @click="emit('select', item.registerId)">
          <span class="waiting-queue__num">{{ item.queueNumber ?? '—' }}</span>
          <div class="waiting-queue__info">
            <span class="waiting-queue__name">{{ item.realName || '—' }}</span>
            <span class="waiting-queue__status">{{ callStatusLabel(item) }}</span>
          </div>
        </button>

        <div v-if="item.canReorder" class="waiting-queue__actions">
          <button
            type="button"
            class="move-btn"
            :disabled="busy || !canMoveUp(index)"
            title="上移"
            @click.stop="move(index, -1)"
          >
            ↑
          </button>
          <button
            type="button"
            class="move-btn"
            :disabled="busy || !canMoveDown(index)"
            title="下移"
            @click.stop="move(index, 1)"
          >
            ↓
          </button>
        </div>
        <span v-else-if="item.callStatus === 1" class="waiting-queue__lock" title="正在叫号">锁定</span>
      </li>
    </ul>
  </GlassCard>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import { queueApi, type PhysicianQueueItem } from '@/shared/api/modules/physician'

const props = defineProps<{
  selectedRegisterId?: number | null
}>()

const emit = defineEmits<{
  select: [registerId: number]
  refreshed: []
}>()

const items = ref<PhysicianQueueItem[]>([])
const loading = ref(false)
const busy = ref(false)

function callStatusLabel(item: PhysicianQueueItem): string {
  switch (item.callStatus) {
    case 1: return '呼叫中'
    case 3: return item.callRound && item.callRound >= 2 ? '过号(终)' : '过号'
    case 0: return '未叫'
    default: return '—'
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
    await queueApi.reorder(items.value.map(i => i.registerId))
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

defineExpose({ refresh })

void refresh()
</script>

<style scoped>
.waiting-queue-panel {
  margin-bottom: 14px;
}

.panel-heading h2 {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 700;
}

.panel-heading p {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.waiting-queue__loading,
.waiting-queue__empty {
  padding: 16px 0;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.waiting-queue__list {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}

.waiting-queue__item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.6);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.waiting-queue__item.is-active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.2);
}

.waiting-queue__item.is-calling {
  border-color: var(--el-color-warning);
  background: rgba(255, 193, 7, 0.08);
}

.waiting-queue__main {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.waiting-queue__num {
  min-width: 28px;
  font-size: 20px;
  font-weight: 800;
  color: var(--el-color-primary);
}

.waiting-queue__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.waiting-queue__name {
  font-size: 14px;
  font-weight: 600;
}

.waiting-queue__status {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.waiting-queue__actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-right: 8px;
}

.move-btn {
  width: 28px;
  height: 24px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
}

.move-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.move-btn:not(:disabled):hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.waiting-queue__lock {
  padding-right: 12px;
  font-size: 11px;
  color: var(--el-color-warning);
}
</style>
