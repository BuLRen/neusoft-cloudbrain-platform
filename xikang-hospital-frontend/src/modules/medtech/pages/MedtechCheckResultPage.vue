<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  const id = route.query.id
  router.replace({ path: '/medtech/check-start', query: id ? { id: String(id) } : {} })
})
</script>

<template>
  <p style="padding: var(--space-4); color: var(--color-text-muted)">正在跳转…</p>
</template>
