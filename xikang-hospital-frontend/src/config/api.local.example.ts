/**
 * API 地址配置（模板）
 *
 * 首次使用请复制为同目录下的 api.local.ts 后按需修改：
 *   cp src/config/api.local.example.ts src/config/api.local.ts
 *
 * api.local.ts 已加入 .gitignore，不会提交到 git。
 */

export const apiLocalConfig = {
  /**
   * 浏览器侧 API 前缀。
   * 生产环境通常保持 `/api`，由 Nginx 将 `/api` 反代到 gateway-service。
   */
  apiBasePath: '/api',

  /**
   * 完整 API 根地址（可选）。
   * 例: `http://43.139.102.203:8080/api`
   * 留空则使用 apiBasePath 相对路径（推荐配合 Nginx / Vite 代理）。
   */
  apiBaseUrl: '',

  /**
   * 仅开发环境：Vite dev server 将 `/api`、`/ws` 代理到此地址。
   */
  devProxyTarget: 'http://localhost:8080',
} as const

export type ApiLocalConfig = typeof apiLocalConfig
