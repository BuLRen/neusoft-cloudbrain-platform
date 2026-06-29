<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { ElButton, ElDialog, ElIcon, ElInput, ElTable, ElTableColumn, ElTag, ElTooltip } from 'element-plus'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Calendar,
  Coin,
  CollectionTag,
  Document,
  FirstAidKit,
  Grid,
  Key,
  OfficeBuilding,
  Postcard,
  Wallet,
} from '@element-plus/icons-vue'
import { physicianApi, type Drug } from '@/shared/api/modules/physician'

const SEARCH_DEBOUNCE_MS = 300
const PAGE_SIZE = 7

const props = withDefaults(
  defineProps<{
    modelValue?: number
    placeholder?: string
    inputId?: string
  }>(),
  {
    placeholder: '输入药品名称、编码或助记码搜索并选择',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  select: [item: Drug]
}>()

interface DetailField {
  label: string
  value: string
  icon: Component
}

const query = ref('')
const results = ref<Drug[]>([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(false)
const panelVisible = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const activeIndex = ref(-1)
const selectedItem = ref<Drug | null>(null)
const detailDrug = ref<Drug | null>(null)

const tableRef = ref<InstanceType<typeof ElTable> | null>(null)
const inputRef = ref<InstanceType<typeof ElInput> | null>(null)

const panelActivated = ref(false)
const isComposing = ref(false)

const detailCache = new Map<number, Drug>()

let searchTimer: ReturnType<typeof setTimeout> | undefined
let blurTimer: ReturnType<typeof setTimeout> | undefined

const hasQuery = computed(() => query.value.trim().length > 0)
const hasResults = computed(() => results.value.length > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const canPrevPage = computed(() => currentPage.value > 1)
const canNextPage = computed(() => currentPage.value < totalPages.value)
const activeDrug = computed(() => {
  if (activeIndex.value < 0 || activeIndex.value >= results.value.length) return null
  return results.value[activeIndex.value]
})
const panelSummary = computed(() => {
  if (loading.value) return '搜索中…'
  if (!total.value) return '未找到匹配药品'
  return `共 ${total.value} 种 · 第 ${currentPage.value}/${totalPages.value} 页`
})
const panelEmptyText = computed(() => {
  if (loading.value) return ' '
  return '未找到匹配药品'
})

const displayDrug = computed(() => detailDrug.value ?? activeDrug.value)

const detailTags = computed(() => {
  const drug = displayDrug.value
  if (!drug) return []
  return [drug.drugFormat, drug.drugDosage, drug.drugType].filter((item): item is string => Boolean(item?.trim()))
})

const detailQuickItems = computed(() => {
  const drug = displayDrug.value
  if (!drug) return []
  return [
    { label: '规格', value: drug.drugFormat || '-', icon: Grid },
    { label: '剂型', value: drug.drugDosage || '-', icon: FirstAidKit },
    { label: '药品类型', value: drug.drugType || '-', icon: CollectionTag },
  ]
})

const detailLeftFields = computed<DetailField[]>(() => {
  const drug = displayDrug.value
  if (!drug) return []
  return [
    { label: '药品编码', value: drug.drugCode, icon: Postcard },
    { label: '规格', value: drug.drugFormat || '-', icon: Grid },
    { label: '药品类型', value: drug.drugType || '-', icon: CollectionTag },
    { label: '生产企业', value: drug.manufacturer || '-', icon: OfficeBuilding },
    { label: '助记码', value: drug.mnemonicCode || '-', icon: Key },
    { label: '可用库存', value: stockText(drug), icon: Wallet },
  ]
})

const detailRightFields = computed<DetailField[]>(() => {
  const drug = displayDrug.value
  if (!drug) return []
  return [
    { label: '药品名称', value: drug.drugName, icon: Document },
    { label: '剂型', value: drug.drugDosage || '-', icon: FirstAidKit },
    { label: '单位', value: drug.drugUnit || '-', icon: Wallet },
    { label: '单价', value: `${drug.drugPrice} 元`, icon: Coin },
    { label: '录入日期', value: drug.creationDate || '-', icon: Calendar },
  ]
})

function formatLabel(item: Drug) {
  const format = item.drugFormat ? ` / ${item.drugFormat}` : ''
  return `${item.drugName}${format} / ${item.drugPrice}元`
}

function stockText(item: Drug): string {
  const qty = item.stockQuantity ?? 0
  const unit = item.drugUnit || '盒'
  if (qty <= 0) return '缺货'
  return `${qty} ${unit}`
}

function isOutOfStock(item: Drug): boolean {
  return (item.stockQuantity ?? 0) <= 0
}

function getInputElement(): HTMLInputElement | null {
  const raw = inputRef.value as { input?: HTMLInputElement; $el?: HTMLElement } | null
  return raw?.input ?? raw?.$el?.querySelector('input') ?? null
}

function shouldHandleShortcut(): boolean {
  if (detailVisible.value) return true
  if (!panelVisible.value || !panelActivated.value) return false
  return getInputElement() === document.activeElement
}

function closeDetail() {
  detailVisible.value = false
  detailDrug.value = null
}

async function loadDetailDrug(drug: Drug) {
  const cached = detailCache.get(drug.id)
  if (cached) {
    detailDrug.value = cached
    return
  }
  detailLoading.value = true
  try {
    const full = await physicianApi.drug(drug.id)
    detailCache.set(drug.id, full)
    detailDrug.value = full
  } catch {
    detailDrug.value = drug
  } finally {
    detailLoading.value = false
  }
}

async function toggleDetail() {
  if (!activeDrug.value) return
  if (detailVisible.value) {
    closeDetail()
    await refocusInput()
    return
  }
  detailVisible.value = true
  await loadDetailDrug(activeDrug.value)
  await refocusInput()
}

async function refocusInput() {
  await nextTick()
  getInputElement()?.focus()
}

async function fetchResults(keyword = '', page = currentPage.value) {
  const normalized = keyword.trim()
  if (!normalized) {
    results.value = []
    total.value = 0
    currentPage.value = 1
    closePanel()
    return
  }
  loading.value = true
  if (panelActivated.value) {
    openPanel()
  }
  try {
    const data = await physicianApi.drugsPage(normalized, page, PAGE_SIZE)
    results.value = data.list
    total.value = data.total
    currentPage.value = data.page
    if (panelActivated.value) {
      openPanel()
      activeIndex.value = results.value.length ? 0 : -1
      if (detailVisible.value) {
        if (activeDrug.value) {
          await loadDetailDrug(activeDrug.value)
        } else {
          closeDetail()
        }
      }
      await scrollActiveRowIntoView()
    }
  } finally {
    loading.value = false
  }
}

function scheduleSearch(keyword: string) {
  if (searchTimer) clearTimeout(searchTimer)
  const normalized = keyword.trim()
  if (!normalized) {
    results.value = []
    total.value = 0
    currentPage.value = 1
    closePanel()
    return
  }
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    void fetchResults(keyword, 1)
  }, SEARCH_DEBOUNCE_MS)
}

async function goToPage(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value || !hasQuery.value) return
  currentPage.value = page
  activeIndex.value = 0
  await fetchResults(query.value, page)
}

function openPanel() {
  if (!hasQuery.value) {
    panelVisible.value = false
    return
  }
  panelVisible.value = true
}

function closePanel() {
  panelVisible.value = false
  activeIndex.value = -1
  closeDetail()
}

function onInputClick() {
  if (blurTimer) clearTimeout(blurTimer)
  panelActivated.value = true
  if (!hasQuery.value) return
  openPanel()
  if (!results.value.length && !loading.value) {
    void fetchResults(query.value, currentPage.value)
  }
}

function onInput(value: string) {
  selectedItem.value = null
  emit('update:modelValue', undefined)
  if (!panelActivated.value || isComposing.value) return
  if (value.trim()) {
    openPanel()
  } else {
    closePanel()
  }
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
  if (!value.trim()) {
    closePanel()
    return
  }
  scheduleSearch(value)
}

function onBlur() {
  if (isComposing.value || detailVisible.value) return
  blurTimer = setTimeout(() => {
    if (isComposing.value || detailVisible.value) return
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

function selectItem(item: Drug) {
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
  if (panelVisible.value || detailVisible.value) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    if (!panelActivated.value || !hasQuery.value) return
    event.preventDefault()
    panelActivated.value = true
    void fetchResults(query.value, 1)
    return
  }
  if (event.key === 'Enter' && !event.isComposing && !isComposing.value && hasQuery.value) {
    event.preventDefault()
    panelActivated.value = true
    currentPage.value = 1
    void fetchResults(query.value, 1)
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
      void onActiveRowChanged()
      break
    case 'ArrowUp':
      event.preventDefault()
      if (!hasResults.value) return
      activeIndex.value = activeIndex.value > 0 ? activeIndex.value - 1 : results.value.length - 1
      void onActiveRowChanged()
      break
    case 'ArrowLeft':
      if (!panelVisible.value || !canPrevPage.value) return
      event.preventDefault()
      void goToPage(currentPage.value - 1)
      break
    case 'ArrowRight':
      if (!panelVisible.value || !canNextPage.value) return
      event.preventDefault()
      void goToPage(currentPage.value + 1)
      break
    case 'F2':
      if (!activeDrug.value) return
      event.preventDefault()
      void toggleDetail()
      break
    case 'Enter':
      if (detailVisible.value) return
      if (panelVisible.value && hasResults.value) {
        event.preventDefault()
        selectActiveItem()
      }
      break
    case 'Escape':
      event.preventDefault()
      if (detailVisible.value) {
        closeDetail()
        void refocusInput()
      } else {
        closePanel()
      }
      break
    default:
      break
  }
}

async function onActiveRowChanged() {
  await scrollActiveRowIntoView()
  if (detailVisible.value && activeDrug.value) {
    await loadDetailDrug(activeDrug.value)
  }
}

function rowClassName({ rowIndex }: { rowIndex: number }) {
  return rowIndex === activeIndex.value ? 'drug-search-picker__row--active' : ''
}

async function scrollActiveRowIntoView() {
  await nextTick()
  const tableEl = tableRef.value?.$el as HTMLElement | undefined
  const activeRow = tableEl?.querySelector('.drug-search-picker__row--active') as HTMLElement | null
  activeRow?.scrollIntoView({ block: 'nearest' })
}

function reset() {
  selectedItem.value = null
  query.value = ''
  results.value = []
  total.value = 0
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
    const found = results.value.find((item) => item.id === id)
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
  <div class="drug-search-picker">
    <ElTooltip
      v-model:visible="panelVisible"
      placement="bottom-start"
      effect="light"
      trigger="contextmenu"
      :show-arrow="false"
      :offset="6"
      :teleported="true"
      :hide-after="0"
      popper-class="drug-search-picker__popper"
    >
      <template #default>
        <ElInput
          :id="inputId"
          ref="inputRef"
          v-model="query"
          class="drug-search-picker__input"
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
        <div class="drug-search-picker__panel" @mousedown="onPanelMouseDown">
          <div class="drug-search-picker__panel-head">
            <span>{{ panelSummary }}</span>
            <span class="drug-search-picker__panel-hint">↑↓ 行 · ←→ 翻页 · F2 详情 · Enter 选择</span>
          </div>

          <div
            v-loading="loading"
            class="drug-search-picker__table-wrap"
            :class="{ 'drug-search-picker__table-wrap--initial-loading': loading && !results.length }"
            element-loading-text="加载中…"
            element-loading-background="rgba(255, 255, 255, 0.72)"
          >
            <ElTable
              ref="tableRef"
              :data="results"
              size="small"
              :row-class-name="rowClassName"
              :empty-text="panelEmptyText"
              class="drug-search-picker__table"
              @row-click="selectItem"
            >
            <ElTableColumn prop="drugCode" label="编码" width="96" show-overflow-tooltip />
            <ElTableColumn prop="drugName" label="药品名称" min-width="120" show-overflow-tooltip />
            <ElTableColumn prop="drugFormat" label="规格" min-width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.drugFormat || '-' }}</template>
            </ElTableColumn>
            <ElTableColumn prop="manufacturer" label="生产企业" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.manufacturer || '-' }}</template>
            </ElTableColumn>
            <ElTableColumn label="单价" width="88" align="right">
              <template #default="{ row }">{{ row.drugPrice }} 元</template>
            </ElTableColumn>
            <ElTableColumn label="库存" width="96" align="center">
              <template #default="{ row }">
                <ElTag v-if="isOutOfStock(row)" type="danger" size="small" effect="light">缺货</ElTag>
                <span v-else>{{ stockText(row) }}</span>
              </template>
            </ElTableColumn>
            </ElTable>
          </div>

          <footer v-if="total > 0 || loading" class="drug-search-picker__pager">
            <ElButton size="small" :disabled="!canPrevPage || loading" @click="goToPage(currentPage - 1)">
              上一页
            </ElButton>
            <span class="drug-search-picker__pager-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
            <ElButton size="small" :disabled="!canNextPage || loading" @click="goToPage(currentPage + 1)">
              下一页
            </ElButton>
          </footer>
        </div>
      </template>
    </ElTooltip>

    <ElDialog
      v-model="detailVisible"
      class="drug-search-picker__detail-dialog"
      width="min(860px, 94vw)"
      align-center
      append-to-body
      destroy-on-close
      :show-header="false"
      :trap-focus="false"
      @opened="refocusInput"
      @closed="closeDetail"
    >
      <div v-loading="detailLoading" class="drug-detail">
        <header v-if="displayDrug" class="drug-detail__hero">
          <div class="drug-detail__hero-main">
            <h2 class="drug-detail__title">{{ displayDrug.drugName }}</h2>
            <div v-if="detailTags.length" class="drug-detail__tags">
              <ElTag
                v-for="tag in detailTags"
                :key="tag"
                class="drug-detail__tag"
                size="small"
                effect="plain"
                round
              >
                {{ tag }}
              </ElTag>
            </div>
            <div class="drug-detail__shortcuts" aria-label="快捷键说明">
              <span class="drug-detail__shortcut-item">
                <kbd>F2</kbd> 开关详情
              </span>
              <span class="drug-detail__shortcut-dot" aria-hidden="true" />
              <span class="drug-detail__shortcut-item">
                <kbd>Esc</kbd> 关闭
              </span>
              <span class="drug-detail__shortcut-dot" aria-hidden="true" />
              <span class="drug-detail__shortcut-item">
                <ElIcon><ArrowUp /></ElIcon>
                <ElIcon><ArrowDown /></ElIcon>
                切换药品
              </span>
              <span class="drug-detail__shortcut-dot" aria-hidden="true" />
              <span class="drug-detail__shortcut-item">
                <ElIcon><ArrowLeft /></ElIcon>
                <ElIcon><ArrowRight /></ElIcon>
                翻页
              </span>
            </div>
          </div>

          <aside class="drug-detail__summary-card">
            <ul class="drug-detail__quick-list">
              <li v-for="item in detailQuickItems" :key="item.label" class="drug-detail__quick-item">
                <ElIcon class="drug-detail__quick-icon" aria-hidden="true">
                  <component :is="item.icon" />
                </ElIcon>
                <span class="drug-detail__quick-label">{{ item.label }}</span>
                <span class="drug-detail__quick-value">{{ item.value }}</span>
              </li>
            </ul>
          </aside>
        </header>

        <div v-if="displayDrug" class="drug-detail__columns">
          <section class="drug-detail__panel">
            <div
              v-for="field in detailLeftFields"
              :key="field.label"
              class="drug-detail__row"
            >
              <ElIcon class="drug-detail__row-icon" aria-hidden="true">
                <component :is="field.icon" />
              </ElIcon>
              <span class="drug-detail__row-label">{{ field.label }}</span>
              <span class="drug-detail__row-value">{{ field.value }}</span>
            </div>
          </section>

          <section class="drug-detail__panel">
            <div
              v-for="field in detailRightFields"
              :key="field.label"
              class="drug-detail__row"
            >
              <ElIcon class="drug-detail__row-icon" aria-hidden="true">
                <component :is="field.icon" />
              </ElIcon>
              <span class="drug-detail__row-label">{{ field.label }}</span>
              <span class="drug-detail__row-value">{{ field.value }}</span>
            </div>
          </section>
        </div>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped>
.drug-search-picker {
  width: 100%;
}

.drug-search-picker__input :deep(.el-input__wrapper) {
  padding-inline: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-control);
  box-shadow: none;
  border: 1px solid var(--color-border-strong);
  min-height: 40px;
}

.drug-search-picker__input :deep(.el-input__wrapper:hover),
.drug-search-picker__input :deep(.el-input__wrapper.is-focus) {
  border-color: color-mix(in srgb, var(--color-primary) 35%, var(--color-border-strong));
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.drug-search-picker__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-block-end: 1px solid var(--color-border);
  font-size: var(--font-size-sm, 0.875rem);
  color: var(--color-text-muted);
}

.drug-search-picker__panel-hint {
  color: var(--color-text-soft);
  white-space: nowrap;
}

.drug-search-picker__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-block-start: 1px solid var(--color-border);
}

.drug-search-picker__pager-info {
  min-width: 96px;
  color: var(--color-text-muted);
  font-size: 12px;
  text-align: center;
}

.drug-search-picker__table-wrap--initial-loading {
  min-height: 240px;
}

.drug-search-picker__table {
  width: 100%;
}

.drug-search-picker__table :deep(.el-table__empty-block) {
  min-height: 0;
}

.drug-search-picker__table :deep(.el-table__body tr) {
  cursor: pointer;
}

.drug-search-picker__table :deep(.drug-search-picker__row--active > td.el-table__cell) {
  background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface-strong)) !important;
}

.drug-detail {
  min-height: 120px;
}

.drug-detail__hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
  gap: var(--space-5);
  margin-block-end: var(--space-5);
}

.drug-detail__title {
  margin: 0;
  font-size: clamp(22px, 2.4vw, 30px);
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: -0.03em;
  color: var(--color-primary-strong);
}

.drug-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-start: var(--space-3);
}

.drug-detail__tag {
  --el-tag-bg-color: rgba(31, 140, 255, 0.1);
  --el-tag-border-color: rgba(31, 140, 255, 0.2);
  --el-tag-text-color: var(--color-primary-strong);
}

.drug-detail__shortcuts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  margin-block-start: var(--space-4);
  padding: var(--space-2) var(--space-4);
  border-radius: 999px;
  color: var(--color-text-muted);
  font-size: 12px;
  background: linear-gradient(90deg, rgba(31, 140, 255, 0.08), rgba(47, 216, 196, 0.06));
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.1);
}

.drug-detail__shortcut-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.drug-detail__shortcut-item kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  min-height: 22px;
  padding: 0 6px;
  border: 1px solid rgba(31, 140, 255, 0.18);
  border-radius: 6px;
  color: var(--color-primary-strong);
  font-size: 11px;
  font-family: inherit;
  background: rgba(255, 255, 255, 0.88);
}

.drug-detail__shortcut-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-text-soft);
}

.drug-detail__summary-card {
  display: flex;
  align-items: center;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}

.drug-detail__quick-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
  min-width: 0;
}

.drug-detail__quick-item {
  display: grid;
  grid-template-columns: 18px minmax(56px, auto) minmax(0, 1fr);
  gap: var(--space-2);
  align-items: center;
  font-size: 12px;
}

.drug-detail__quick-icon {
  color: var(--color-primary);
}

.drug-detail__quick-label {
  color: var(--color-text-soft);
}

.drug-detail__quick-value {
  color: var(--color-text);
  font-weight: 600;
}

.drug-detail__columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.drug-detail__panel {
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}

.drug-detail__row {
  display: grid;
  grid-template-columns: 20px 88px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
  padding: var(--space-3) 0;
  border-block-end: 1px solid rgba(70, 111, 160, 0.1);
}

.drug-detail__row:last-child {
  border-block-end: none;
}

.drug-detail__row-icon {
  margin-block-start: 2px;
  color: var(--color-primary);
  font-size: 16px;
}

.drug-detail__row-label {
  color: var(--color-text-soft);
  font-size: 13px;
  line-height: 1.6;
}

.drug-detail__row-value {
  color: var(--color-text);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
  word-break: break-word;
}

@media (max-width: 720px) {
  .drug-search-picker__panel-hint {
    display: none;
  }
}

@media (max-width: 760px) {
  .drug-detail__hero {
    grid-template-columns: 1fr;
  }

  .drug-detail__columns {
    grid-template-columns: 1fr;
  }

  .drug-detail__shortcuts {
    border-radius: var(--radius-md);
  }
}
</style>

<style>
.drug-search-picker__popper.el-popper {
  padding: 0 !important;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.drug-search-picker__popper .drug-search-picker__panel {
  width: min(760px, calc(100vw - 48px));
  background: var(--color-surface-strong);
}

.drug-search-picker__detail-dialog.el-dialog {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: linear-gradient(180deg, #f7fbff 0%, #ffffff 42%);
}

.drug-search-picker__detail-dialog .el-dialog__header {
  display: none;
}

.drug-search-picker__detail-dialog .el-dialog__body {
  padding: var(--space-5);
}
</style>
