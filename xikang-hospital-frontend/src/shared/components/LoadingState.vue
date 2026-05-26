
<template>
  <div class="loading-state" aria-live="polite">
    <span />
    <span />
    <span />
    <p><slot>正在加载...</slot></p>
  </div>
</template>

<style scoped>
.loading-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
}

.loading-state span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--color-primary);
  animation: pulse 900ms infinite ease-in-out;
}

.loading-state span:nth-child(2) {
  animation-delay: 120ms;
}

.loading-state span:nth-child(3) {
  animation-delay: 240ms;
}

.loading-state p {
  margin-inline-start: var(--space-2);
}

@keyframes pulse {
  0%, 100% {
    transform: translateY(0);
    opacity: 0.38;
  }

  50% {
    transform: translateY(-4px);
    opacity: 1;
  }
}
</style>
