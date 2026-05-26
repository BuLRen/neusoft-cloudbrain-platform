
<template>
  <div class="error-state">
    <strong><slot name="title">出现异常</slot></strong>
    <p><slot>请稍后重试，或联系项目成员确认接口和权限配置。</slot></p>
  </div>
</template>

<style scoped>
.error-state {
  padding: var(--space-5);
  border: 1px solid rgba(239, 77, 90, 0.22);
  border-radius: var(--radius-lg);
  color: var(--color-danger);
  background: rgba(239, 77, 90, 0.08);
}

.error-state p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
}
</style>
