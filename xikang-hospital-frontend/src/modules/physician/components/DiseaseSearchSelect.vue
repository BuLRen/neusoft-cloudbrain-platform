<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElOption, ElSelect } from 'element-plus'
import { physicianApi, type Disease } from '@/shared/api/modules/physician'

const props = withDefaults(
  defineProps<{
    modelValue: number[]
    multiple?: boolean
    placeholder?: string
    initialDiseases?: Disease[]
  }>(),
  {
    multiple: true,
    placeholder: '输入病名或 ICD 搜索疾病库',
    initialDiseases: () => [],
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
  select: [diseases: Disease[]]
}>()

const loading = ref(false)
const options = ref<Disease[]>([])
let searchTimer: ReturnType<typeof setTimeout> | undefined

function mergeOptions(items: Disease[]) {
  const map = new Map<number, Disease>()
  for (const item of props.initialDiseases) {
    map.set(item.id, item)
  }
  for (const item of options.value) {
    map.set(item.id, item)
  }
  for (const item of items) {
    map.set(item.id, item)
  }
  options.value = Array.from(map.values())
}

async function searchDiseases(keyword: string) {
  const trimmed = keyword.trim()
  if (!trimmed) {
    options.value = [...props.initialDiseases]
    return
  }
  loading.value = true
  try {
    const results = await physicianApi.diseases(trimmed)
    mergeOptions(results)
  } finally {
    loading.value = false
  }
}

function onRemoteSearch(keyword: string) {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    void searchDiseases(keyword)
  }, 300)
}

function onChange(value: number | number[]) {
  const ids = Array.isArray(value) ? value : value ? [value] : []
  emit('update:modelValue', ids)
  const selected = options.value.filter((item) => ids.includes(item.id))
  emit('select', selected)
}

watch(
  () => props.initialDiseases,
  (items) => {
    mergeOptions(items ?? [])
  },
  { immediate: true, deep: true },
)
</script>

<template>
  <ElSelect
    :model-value="modelValue"
    :multiple="multiple"
    filterable
    remote
    reserve-keyword
    :remote-method="onRemoteSearch"
    :loading="loading"
    :placeholder="placeholder"
    no-data-text="请输入关键词搜索疾病库"
    style="width: 100%"
    @update:model-value="onChange"
  >
    <ElOption
      v-for="item in options"
      :key="item.id"
      :label="`${item.diseaseName}${item.diseaseIcd ? ` · ${item.diseaseIcd}` : ''}${item.diseaseCategory ? ` · ${item.diseaseCategory}` : ''}`"
      :value="item.id"
    />
  </ElSelect>
</template>
