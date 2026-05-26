
<template>
  <section class="glass-card">
    <slot />
  </section>
</template>

<style scoped>
.glass-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  backdrop-filter: var(--blur-glass);
}
</style>
