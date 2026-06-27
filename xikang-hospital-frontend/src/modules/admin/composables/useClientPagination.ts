import { computed, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'

export function useClientPagination<T>(
  source: MaybeRefOrGetter<T[]>,
  defaultSize = 20,
  options?: { resetOnSourceChange?: boolean },
) {
  const page = ref(1)
  const size = ref(defaultSize)

  const total = computed(() => toValue(source).length)

  const totalPages = computed(() => {
    if (total.value === 0) return 0
    return Math.ceil(total.value / size.value)
  })

  const pagedRecords = computed(() => {
    const items = toValue(source)
    const start = (page.value - 1) * size.value
    return items.slice(start, start + size.value)
  })

  function resetPage() {
    page.value = 1
  }

  function onPageChange(nextPage: number) {
    page.value = nextPage
  }

  function onPageSizeChange(nextSize: number) {
    size.value = nextSize
    page.value = 1
  }

  function clampPageIfEmpty() {
    if (total.value === 0) {
      page.value = 1
      return
    }
    const maxPage = totalPages.value
    if (page.value > maxPage) {
      page.value = maxPage
    }
  }

  if (options?.resetOnSourceChange !== false) {
    watch(source, resetPage)
  }

  return {
    page,
    size,
    total,
    totalPages,
    pagedRecords,
    resetPage,
    onPageChange,
    onPageSizeChange,
    clampPageIfEmpty,
  }
}
