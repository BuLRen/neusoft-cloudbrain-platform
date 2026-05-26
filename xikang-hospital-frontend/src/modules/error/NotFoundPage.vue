
<template>
  <main class="error-page u-glass">
    <h1>404</h1>
    <p>页面不存在或路由尚未配置。</p>
    <el-button type="primary" @click="$router.push('/dashboard')">返回仪表盘</el-button>
  </main>
</template>

<style scoped>
.error-page {
  display: grid;
  place-items: center;
  gap: var(--space-4);
  min-height: 100vh;
  padding: var(--space-8);
  text-align: center;
}

.error-page h1 {
  font-size: 96px;
  letter-spacing: -0.08em;
}

.error-page p {
  color: var(--color-text-muted);
}
</style>
