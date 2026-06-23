<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElInput, ElTable, ElTableColumn, ElTooltip } from 'element-plus'
import { physicianApi, type MedicalTechnology } from '@/shared/api/modules/physician'

const TECH_TYPE_LABEL: Record<MedicalTechnology['techType'], string> = {
  check: '检查',
  inspection: '检验',
  disposal: '处置',
}

const SEARCH_DEBOUNCE_MS = 300

const props = withDefaults(
  defineProps<{
    modelValue?: number
    placeholder?: string
    inputId?: string
  }>(),
  {
    placeholder: '输入名称或编码搜索并选择医技项目',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  select: [item: MedicalTechnology]
}>()

const query = ref('')
const results = ref<MedicalTechnology[]>([])
const loading = ref(false)
const panelVisible = ref(false)
const activeIndex = ref(-1)
const selectedItem = ref<MedicalTechnology | null>(null)

const tableRef = ref<InstanceType<typeof ElTable> | null>(null)
const inputRef = ref<InstanceType<typeof ElInput> | null>(null)

const panelActivated = ref(false)
const isComposing = ref(false)

let searchTimer: ReturnType<typeof setTimeout> | undefined
let blurTimer: ReturnType<typeof setTimeout> | undefined

const hasResults = computed(() => results.value.length > 0)
const panelEmptyText = computed(() => {
  if (loading.value) return '搜索中…'
  return query.value.trim() ? '未找到匹配项目' : '暂无项目数据'
})

function formatLabel(item: MedicalTechnology) {
  return `${item.techName} / ${TECH_TYPE_LABEL[item.techType]} / ${item.techPrice}元`
}

async function fetchResults(keyword = '') {
  const normalized = keyword.trim()
  loading.value = true
  try {
    results.value = await physicianApi.medicalTechnologies(undefined, normalized || undefined)
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
    void fetchResults(keyword)
  }, SEARCH_DEBOUNCE_MS)
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
  if (!results.value.length) {
    void fetchResults(query.value)
  }
}

function onInput(value: string) {
  selectedItem.value = null
  emit('update:modelValue', undefined)
  if (!panelActivated.value || isComposing.value) return
  scheduleSearch(value)
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

function onKeydown(evt: Event | KeyboardEvent) {
  if (!(evt instanceof KeyboardEvent)) return
  const event = evt
  if (!panelVisible.value && (event.key === 'ArrowDown' || event.key === 'ArrowUp')) {
    if (!panelActivated.value) return
    openPanel()
    if (!results.value.length) {
      void fetchResults(query.value)
    }
    return
  }

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
    case 'Enter':
      if (event.isComposing || isComposing.value) return
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
  void fetchResults('')
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
          @blur="onBlur"
          @input="onInput"
          @compositionstart="onCompositionStart"
          @compositionend="onCompositionEnd"
          @clear="reset"
          @keydown="onKeydown"
        />
      </template>

      <template #content>
        <div class="med-tech-picker__panel" @mousedown="onPanelMouseDown">
          <div class="med-tech-picker__panel-head">
            <span>{{ loading ? '搜索中…' : `共 ${results.length} 项` }}</span>
            <span class="med-tech-picker__panel-hint">↑↓ 导航 · Enter 选择 · Esc 关闭</span>
          </div>

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
              <template #default="{ row }">{{ TECH_TYPE_LABEL[row.techType as MedicalTechnology['techType']] }}</template>
            </ElTableColumn>
            <ElTableColumn label="价格" width="88" align="right">
              <template #default="{ row }">{{ row.techPrice }} 元</template>
            </ElTableColumn>
            <ElTableColumn prop="deptName" label="科室" min-width="100" show-overflow-tooltip />
          </ElTable>
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

.med-tech-picker__table {
  width: 100%;
}

.med-tech-picker__table :deep(.el-table__body tr) {
  cursor: pointer;
}

.med-tech-picker__table :deep(.med-tech-picker__row--active > td.el-table__cell) {
  background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface-strong)) !important;
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
  width: min(640px, calc(100vw - 48px));
  max-height: 320px;
  overflow: auto;
  background: var(--color-surface-strong);
}
</style>
