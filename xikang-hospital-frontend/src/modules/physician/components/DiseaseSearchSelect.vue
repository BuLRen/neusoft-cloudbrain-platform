<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElButton, ElInput, ElTable, ElTableColumn, ElTag, ElTooltip } from 'element-plus'
import { physicianApi, type Disease } from '@/shared/api/modules/physician'

const SEARCH_DEBOUNCE_MS = 300
const PAGE_SIZE = 7

const props = withDefaults(
  defineProps<{
    modelValue: number[]
    multiple?: boolean
    placeholder?: string
    initialDiseases?: Disease[]
    inputId?: string
  }>(),
  {
    multiple: true,
    placeholder: '点击选择，或输入病名/ICD 筛选',
    initialDiseases: () => [],
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
  select: [diseases: Disease[]]
}>()

const query = ref('')
const allResults = ref<Disease[]>([])
const loading = ref(false)
const panelVisible = ref(false)
const activeIndex = ref(-1)
const selectedItems = ref<Disease[]>([])
const currentPage = ref(1)

const tableRef = ref<InstanceType<typeof ElTable> | null>(null)
const inputRef = ref<InstanceType<typeof ElInput> | null>(null)

const panelActivated = ref(false)
const isComposing = ref(false)

let searchTimer: ReturnType<typeof setTimeout> | undefined
let blurTimer: ReturnType<typeof setTimeout> | undefined

const diseaseCache = new Map<number, Disease>()

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
  if (!total.value) return hasQuery.value ? '未找到匹配疾病' : '暂无疾病数据'
  return `共 ${total.value} 项 · 第 ${currentPage.value}/${totalPages.value} 页`
})
const panelEmptyText = computed(() => {
  if (loading.value) return ' '
  return hasQuery.value ? '未找到匹配疾病' : '暂无疾病数据'
})

function formatLabel(item: Disease) {
  const icd = item.diseaseIcd ? ` · ${item.diseaseIcd}` : ''
  const category = item.diseaseCategory ? ` · ${item.diseaseCategory}` : ''
  return `${item.diseaseName}${icd}${category}`
}

function cacheDiseases(items: Disease[]) {
  for (const item of items) {
    diseaseCache.set(item.id, item)
  }
}

function resolveSelectedFromIds(ids: number[]): Disease[] {
  return ids
    .map((id) => diseaseCache.get(id) || selectedItems.value.find((item) => item.id === id))
    .filter((item): item is Disease => Boolean(item))
}

function emitSelection(items: Disease[]) {
  selectedItems.value = items
  cacheDiseases(items)
  const ids = items.map((item) => item.id)
  emit('update:modelValue', ids)
  emit('select', items)
}

/** 已选中展示文案不算筛选词，点击时应拉列表 */
function resolveSearchKeyword(raw = query.value): string {
  const normalized = raw.trim()
  if (!normalized) return ''
  if (!props.multiple && selectedItems.value[0] && normalized === formatLabel(selectedItems.value[0])) {
    return ''
  }
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
    const data = await physicianApi.diseases(normalized || undefined)
    allResults.value = data
    cacheDiseases(data)
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
  if (!allResults.value.length || !resolveSearchKeyword(query.value)) {
    void fetchResults(query.value)
  }
}

function onInput(value: string) {
  if (!props.multiple) {
    selectedItems.value = []
    emit('update:modelValue', [])
    emit('select', [])
  }
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
    if (!props.multiple && selectedItems.value[0]) {
      query.value = formatLabel(selectedItems.value[0])
    } else if (props.multiple) {
      query.value = ''
    }
  }, 200)
}

function onPanelMouseDown(event: MouseEvent) {
  event.preventDefault()
}

function isSelected(item: Disease) {
  return selectedItems.value.some((selected) => selected.id === item.id)
}

function selectItem(item: Disease) {
  if (blurTimer) clearTimeout(blurTimer)
  cacheDiseases([item])

  if (props.multiple) {
    if (isSelected(item)) return
    const next = [...selectedItems.value, item]
    emitSelection(next)
    query.value = ''
    activeIndex.value = results.value.length ? 0 : -1
    void refocusInput()
    return
  }

  emitSelection([item])
  query.value = formatLabel(item)
  panelActivated.value = false
  closePanel()
  inputRef.value?.blur()
}

function removeSelected(id: number) {
  const next = selectedItems.value.filter((item) => item.id !== id)
  emitSelection(next)
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

async function refocusInput() {
  await nextTick()
  getInputElement()?.focus()
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
  return rowIndex === activeIndex.value ? 'disease-search-picker__row--active' : ''
}

async function scrollActiveRowIntoView() {
  await nextTick()
  const tableEl = tableRef.value?.$el as HTMLElement | undefined
  const activeRow = tableEl?.querySelector('.disease-search-picker__row--active') as HTMLElement | null
  activeRow?.scrollIntoView({ block: 'nearest' })
}

function onClear() {
  query.value = ''
  if (!props.multiple) {
    emitSelection([])
  }
  if (panelActivated.value) {
    openPanel()
    void fetchResults('')
  }
}

function reset() {
  selectedItems.value = []
  query.value = ''
  allResults.value = []
  currentPage.value = 1
  panelActivated.value = false
  closePanel()
  emit('update:modelValue', [])
  emit('select', [])
}

defineExpose({ reset })

watch(
  () => props.initialDiseases,
  (items) => {
    cacheDiseases(items ?? [])
    if (props.modelValue.length) {
      selectedItems.value = resolveSelectedFromIds(props.modelValue)
      if (!props.multiple && selectedItems.value[0] && !panelActivated.value) {
        query.value = formatLabel(selectedItems.value[0])
      }
    }
  },
  { immediate: true, deep: true },
)

watch(
  () => props.modelValue,
  (ids) => {
    const currentIds = selectedItems.value.map((item) => item.id).join(',')
    const nextIds = ids.join(',')
    if (currentIds === nextIds) return
    selectedItems.value = resolveSelectedFromIds(ids)
    if (!props.multiple) {
      query.value = selectedItems.value[0] ? formatLabel(selectedItems.value[0]) : ''
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
  <div class="disease-search-picker">
    <div v-if="multiple && selectedItems.length" class="disease-search-picker__tags">
      <ElTag
        v-for="item in selectedItems"
        :key="item.id"
        closable
        effect="plain"
        type="info"
        @close="removeSelected(item.id)"
      >
        {{ item.diseaseName }}
      </ElTag>
    </div>

    <ElTooltip
      v-model:visible="panelVisible"
      placement="bottom-start"
      effect="light"
      trigger="contextmenu"
      :show-arrow="false"
      :offset="6"
      :teleported="true"
      :hide-after="0"
      popper-class="disease-search-picker__popper"
    >
      <template #default>
        <ElInput
          :id="inputId"
          ref="inputRef"
          v-model="query"
          class="disease-search-picker__input"
          clearable
          :placeholder="placeholder"
          @click="onInputClick"
          @focus="onFocus"
          @blur="onBlur"
          @input="onInput"
          @compositionstart="onCompositionStart"
          @compositionend="onCompositionEnd"
          @clear="onClear"
          @keydown="onInputKeydown"
        />
      </template>

      <template #content>
        <div class="disease-search-picker__panel" @mousedown="onPanelMouseDown">
          <div class="disease-search-picker__panel-head">
            <span>{{ panelSummary }}</span>
            <span class="disease-search-picker__panel-hint">↑↓ 行 · ←→ 翻页 · Enter 选择</span>
          </div>

          <div
            v-loading="loading"
            class="disease-search-picker__table-wrap"
            :class="{ 'disease-search-picker__table-wrap--initial-loading': loading && !results.length }"
            element-loading-text="加载中…"
            element-loading-background="rgba(255, 255, 255, 0.72)"
          >
            <ElTable
              ref="tableRef"
              :data="results"
              size="small"
              :row-class-name="rowClassName"
              :empty-text="panelEmptyText"
              class="disease-search-picker__table"
              @row-click="selectItem"
            >
              <ElTableColumn prop="diseaseName" label="疾病名称" min-width="160" show-overflow-tooltip />
              <ElTableColumn prop="diseaseIcd" label="ICD" width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.diseaseIcd || '-' }}</template>
              </ElTableColumn>
              <ElTableColumn prop="diseaseCategory" label="分类" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.diseaseCategory || '-' }}</template>
              </ElTableColumn>
              <ElTableColumn v-if="multiple" label="状态" width="72" align="center">
                <template #default="{ row }">
                  <ElTag v-if="isSelected(row)" size="small" type="success" effect="light">已选</ElTag>
                  <span v-else class="disease-search-picker__dash">-</span>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>

          <footer v-if="total > 0 || loading" class="disease-search-picker__pager">
            <ElButton size="small" :disabled="!canPrevPage || loading" @click="goToPage(currentPage - 1)">
              上一页
            </ElButton>
            <span class="disease-search-picker__pager-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
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
.disease-search-picker {
  width: 100%;
}

.disease-search-picker__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-end: var(--space-2);
}

.disease-search-picker__input :deep(.el-input__wrapper) {
  padding-inline: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-control);
  box-shadow: none;
  border: 1px solid var(--color-border-strong);
  min-height: 40px;
}

.disease-search-picker__input :deep(.el-input__wrapper:hover),
.disease-search-picker__input :deep(.el-input__wrapper.is-focus) {
  border-color: color-mix(in srgb, var(--color-primary) 35%, var(--color-border-strong));
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.disease-search-picker__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-block-end: 1px solid var(--color-border);
  font-size: var(--font-size-sm, 0.875rem);
  color: var(--color-text-muted);
}

.disease-search-picker__panel-hint {
  color: var(--color-text-soft);
  white-space: nowrap;
}

.disease-search-picker__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-block-start: 1px solid var(--color-border);
}

.disease-search-picker__pager-info {
  min-width: 96px;
  color: var(--color-text-muted);
  font-size: 12px;
  text-align: center;
}

.disease-search-picker__table-wrap--initial-loading {
  min-height: 240px;
}

.disease-search-picker__table {
  width: 100%;
}

.disease-search-picker__table :deep(.el-table__empty-block) {
  min-height: 0;
}

.disease-search-picker__table :deep(.el-table__body tr) {
  cursor: pointer;
}

.disease-search-picker__table :deep(.disease-search-picker__row--active > td.el-table__cell) {
  background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface-strong)) !important;
}

.disease-search-picker__dash {
  color: var(--color-text-soft);
}

@media (max-width: 720px) {
  .disease-search-picker__panel-hint {
    display: none;
  }
}
</style>

<style>
.disease-search-picker__popper.el-popper {
  padding: 0 !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.disease-search-picker__popper .disease-search-picker__panel {
  width: min(760px, calc(100vw - 48px));
  background: var(--color-surface-strong);
}
</style>
