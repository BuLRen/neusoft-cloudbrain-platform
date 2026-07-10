<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElButton, ElInput, ElTable, ElTableColumn, ElTooltip } from 'element-plus'
import { physicianApi, type MedicalTechnology } from '@/shared/api/modules/physician'

const TECH_TYPE_LABEL: Record<MedicalTechnology['techType'], string> = {
  check: '检查',
  inspection: '检验',
  disposal: '处置',
}

const SEARCH_DEBOUNCE_MS = 300
const PAGE_SIZE = 7

const props = withDefaults(
  defineProps<{
    modelValue?: number
    placeholder?: string
    inputId?: string
  }>(),
  {
    placeholder: '点击选择，或输入名称/编码筛选',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  select: [item: MedicalTechnology]
}>()

const query = ref('')
const allResults = ref<MedicalTechnology[]>([])
const loading = ref(false)
const panelVisible = ref(false)
const activeIndex = ref(-1)
const selectedItem = ref<MedicalTechnology | null>(null)
const currentPage = ref(1)

const tableRef = ref<InstanceType<typeof ElTable> | null>(null)
const inputRef = ref<InstanceType<typeof ElInput> | null>(null)

const panelActivated = ref(false)
const isComposing = ref(false)

let searchTimer: ReturnType<typeof setTimeout> | undefined
let blurTimer: ReturnType<typeof setTimeout> | undefined

const hasQuery = computed(() => query.value.trim().length > 0)
const total = computed(() => allResults.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const canPrevPage = computed(() => currentPage.value > 1)
const canNextPage = computed(() => currentPage.value < totalPages.value)
const results = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return allResults.value.slice(start, start + PAGE_SIZE)
})
const hasResults = computed(() => results.value.length > 0)
const panelSummary = computed(() => {
  if (loading.value) return '搜索中…'
  if (!total.value) return hasQuery.value ? '未找到匹配项目' : '暂无项目数据'
  return `共 ${total.value} 项 · 第 ${currentPage.value}/${totalPages.value} 页`
})
const panelEmptyText = computed(() => {
  if (loading.value) return ' '
  return hasQuery.value ? '未找到匹配项目' : '暂无项目数据'
})

function formatLabel(item: MedicalTechnology) {
  const deptSuffix = item.deptName ? ` / ${item.deptName}` : ''
  return `${item.techName} / ${TECH_TYPE_LABEL[item.techType]} / ${item.techPrice}元${deptSuffix}`
}

/** 已选中展示文案不算筛选词，点击时应拉全量列表 */
function resolveSearchKeyword(raw = query.value): string {
  const normalized = raw.trim()
  if (!normalized) return ''
  if (selectedItem.value && normalized === formatLabel(selectedItem.value)) return ''
  return normalized
}

function getInputElement(): HTMLInputElement | null {
  const raw = inputRef.value as { input?: HTMLInputElement; $el?: HTMLElement } | null
  return raw?.input ?? raw?.$el?.querySelector('input') ?? null
}

function shouldHandleShortcut(): boolean {
  if (!panelVisible.value || !panelActivated.value) return false
  return getInputElement() === document.activeElement
}

async function fetchResults(keyword = '') {
  const normalized = resolveSearchKeyword(keyword)
  loading.value = true
  if (panelActivated.value) {
    openPanel()
  }
  try {
    allResults.value = await physicianApi.medicalTechnologies(undefined, normalized || undefined)
    currentPage.value = 1
    if (panelActivated.value) {
      openPanel()
      activeIndex.value = results.value.length ? 0 : -1
      await scrollActiveRowIntoView()
    }
  } finally {
    loading.value = false
  }
}

function scheduleSearch(keyword: string) {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    void fetchResults(keyword)
  }, SEARCH_DEBOUNCE_MS)
}

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  activeIndex.value = results.value.length ? 0 : -1
  void scrollActiveRowIntoView()
}

function openPanel() {
  panelVisible.value = true
}

function closePanel() {
  panelVisible.value = false
  activeIndex.value = -1
}

function onInputClick() {
  if (blurTimer) clearTimeout(blurTimer)
  panelActivated.value = true
  openPanel()
  if (loading.value) return
  // 无结果，或处于「空词/已选展示」浏览态时拉取列表
  if (!allResults.value.length || !resolveSearchKeyword(query.value)) {
    void fetchResults(query.value)
  }
}

function onInput(value: string) {
  selectedItem.value = null
  emit('update:modelValue', undefined)
  if (!panelActivated.value || isComposing.value) return
  openPanel()
  scheduleSearch(value)
}

function onFocus() {
  if (blurTimer) clearTimeout(blurTimer)
  panelActivated.value = true
}

function onCompositionStart() {
  isComposing.value = true
  if (blurTimer) clearTimeout(blurTimer)
}

function onCompositionEnd(event: CompositionEvent) {
  isComposing.value = false
  if (!panelActivated.value) return
  const value = (event.target as HTMLInputElement | null)?.value ?? query.value
  openPanel()
  scheduleSearch(value)
}

function onBlur() {
  if (isComposing.value) return
  blurTimer = setTimeout(() => {
    if (isComposing.value) return
    panelActivated.value = false
    closePanel()
    if (selectedItem.value) {
      query.value = formatLabel(selectedItem.value)
    }
  }, 200)
}

function onPanelMouseDown(event: MouseEvent) {
  event.preventDefault()
}

function selectItem(item: MedicalTechnology) {
  if (blurTimer) clearTimeout(blurTimer)
  selectedItem.value = item
  query.value = formatLabel(item)
  emit('update:modelValue', item.id)
  emit('select', item)
  panelActivated.value = false
  closePanel()
  inputRef.value?.blur()
}

function selectActiveItem() {
  if (activeIndex.value >= 0 && activeIndex.value < results.value.length) {
    selectItem(results.value[activeIndex.value])
    return true
  }
  if (results.value.length === 1) {
    selectItem(results.value[0])
    return true
  }
  return false
}

function onInputKeydown(event: KeyboardEvent) {
  if (panelVisible.value) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    if (!panelActivated.value) return
    event.preventDefault()
    panelActivated.value = true
    void fetchResults(query.value)
    return
  }
  if (event.key === 'Enter' && !event.isComposing && !isComposing.value) {
    event.preventDefault()
    panelActivated.value = true
    currentPage.value = 1
    void fetchResults(query.value)
  }
}

function onShortcutKeydown(event: KeyboardEvent) {
  if (!shouldHandleShortcut()) return
  if (event.isComposing || isComposing.value) return

  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      if (!hasResults.value) return
      activeIndex.value = activeIndex.value < results.value.length - 1 ? activeIndex.value + 1 : 0
      void scrollActiveRowIntoView()
      break
    case 'ArrowUp':
      event.preventDefault()
      if (!hasResults.value) return
      activeIndex.value = activeIndex.value > 0 ? activeIndex.value - 1 : results.value.length - 1
      void scrollActiveRowIntoView()
      break
    case 'ArrowLeft':
      if (!panelVisible.value || !canPrevPage.value) return
      event.preventDefault()
      goToPage(currentPage.value - 1)
      break
    case 'ArrowRight':
      if (!panelVisible.value || !canNextPage.value) return
      event.preventDefault()
      goToPage(currentPage.value + 1)
      break
    case 'Enter':
      if (panelVisible.value && hasResults.value) {
        event.preventDefault()
        selectActiveItem()
      }
      break
    case 'Escape':
      event.preventDefault()
      closePanel()
      break
    default:
      break
  }
}

function rowClassName({ rowIndex }: { rowIndex: number }) {
  return rowIndex === activeIndex.value ? 'med-tech-picker__row--active' : ''
}

async function scrollActiveRowIntoView() {
  await nextTick()
  const tableEl = tableRef.value?.$el as HTMLElement | undefined
  const activeRow = tableEl?.querySelector('.med-tech-picker__row--active') as HTMLElement | null
  activeRow?.scrollIntoView({ block: 'nearest' })
}

function reset() {
  selectedItem.value = null
  query.value = ''
  allResults.value = []
  currentPage.value = 1
  panelActivated.value = false
  closePanel()
  emit('update:modelValue', undefined)
}

defineExpose({ reset })

watch(
  () => props.modelValue,
  (id) => {
    if (!id) {
      if (selectedItem.value) {
        selectedItem.value = null
      }
      return
    }
    if (selectedItem.value?.id === id) return
    const found = allResults.value.find((item) => item.id === id)
    if (found) {
      selectedItem.value = found
      query.value = formatLabel(found)
    }
  },
)

onMounted(() => {
  document.addEventListener('keydown', onShortcutKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onShortcutKeydown)
  if (searchTimer) clearTimeout(searchTimer)
  if (blurTimer) clearTimeout(blurTimer)
})
</script>

<template>
  <div class="med-tech-picker">
    <ElTooltip
      v-model:visible="panelVisible"
      placement="bottom-start"
      effect="light"
      trigger="contextmenu"
      :show-arrow="false"
      :offset="6"
      :teleported="true"
      :hide-after="0"
      popper-class="med-tech-picker__popper"
    >
      <template #default>
        <ElInput
          :id="inputId"
          ref="inputRef"
          v-model="query"
          class="med-tech-picker__input"
          clearable
          :placeholder="placeholder"
          @click="onInputClick"
          @focus="onFocus"
          @blur="onBlur"
          @input="onInput"
          @compositionstart="onCompositionStart"
          @compositionend="onCompositionEnd"
          @clear="reset"
          @keydown="onInputKeydown"
        />
      </template>

      <template #content>
        <div class="med-tech-picker__panel" @mousedown="onPanelMouseDown">
          <div class="med-tech-picker__panel-head">
            <span>{{ panelSummary }}</span>
            <span class="med-tech-picker__panel-hint">↑↓ 行 · ←→ 翻页 · Enter 选择</span>
          </div>

          <div
            v-loading="loading"
            class="med-tech-picker__table-wrap"
            :class="{ 'med-tech-picker__table-wrap--initial-loading': loading && !results.length }"
            element-loading-text="加载中…"
            element-loading-background="rgba(255, 255, 255, 0.72)"
          >
            <ElTable
              ref="tableRef"
              :data="results"
              size="small"
              :row-class-name="rowClassName"
              :empty-text="panelEmptyText"
              class="med-tech-picker__table"
              @row-click="selectItem"
            >
              <ElTableColumn prop="techCode" label="编码" width="96" show-overflow-tooltip />
              <ElTableColumn prop="techName" label="名称" min-width="140" show-overflow-tooltip />
              <ElTableColumn label="类型" width="72">
                <template #default="{ row }">
                  {{ TECH_TYPE_LABEL[row.techType as MedicalTechnology['techType']] }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="价格" width="88" align="right">
                <template #default="{ row }">{{ row.techPrice }} 元</template>
              </ElTableColumn>
              <ElTableColumn prop="deptName" label="执行科室" min-width="100" show-overflow-tooltip>
                <template #default="{ row }">{{ row.deptName || '未指定' }}</template>
              </ElTableColumn>
            </ElTable>
          </div>

          <footer v-if="total > 0 || loading" class="med-tech-picker__pager">
            <ElButton size="small" :disabled="!canPrevPage || loading" @click="goToPage(currentPage - 1)">
              上一页
            </ElButton>
            <span class="med-tech-picker__pager-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
            <ElButton size="small" :disabled="!canNextPage || loading" @click="goToPage(currentPage + 1)">
              下一页
            </ElButton>
          </footer>
        </div>
      </template>
    </ElTooltip>
  </div>
</template>

<style scoped>
.med-tech-picker {
  width: 100%;
}

.med-tech-picker__input :deep(.el-input__wrapper) {
  padding-inline: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-control);
  box-shadow: none;
  border: 1px solid var(--color-border-strong);
  min-height: 40px;
}

.med-tech-picker__input :deep(.el-input__wrapper:hover),
.med-tech-picker__input :deep(.el-input__wrapper.is-focus) {
  border-color: color-mix(in srgb, var(--color-primary) 35%, var(--color-border-strong));
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.med-tech-picker__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-block-end: 1px solid var(--color-border);
  font-size: var(--font-size-sm, 0.875rem);
  color: var(--color-text-muted);
}

.med-tech-picker__panel-hint {
  color: var(--color-text-soft);
  white-space: nowrap;
}

.med-tech-picker__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-block-start: 1px solid var(--color-border);
}

.med-tech-picker__pager-info {
  min-width: 96px;
  color: var(--color-text-muted);
  font-size: 12px;
  text-align: center;
}

.med-tech-picker__table-wrap--initial-loading {
  min-height: 240px;
}

.med-tech-picker__table {
  width: 100%;
}

.med-tech-picker__table :deep(.el-table__empty-block) {
  min-height: 0;
}

.med-tech-picker__table :deep(.el-table__body tr) {
  cursor: pointer;
}

.med-tech-picker__table :deep(.med-tech-picker__row--active > td.el-table__cell) {
  background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface-strong)) !important;
}

@media (max-width: 720px) {
  .med-tech-picker__panel-hint {
    display: none;
  }
}
</style>

<style>
.med-tech-picker__popper.el-popper {
  padding: 0 !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.med-tech-picker__popper .med-tech-picker__panel {
  width: min(760px, calc(100vw - 48px));
  background: var(--color-surface-strong);
}
</style>
