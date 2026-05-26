# 希康云医院前端框架

本目录是东软云医院项目前端工程，当前阶段只搭建框架、菜单、权限、全局样式和统一占位页面，不实现具体业务页面内容。

## 技术栈

- Vue 3 + TypeScript + Vite
- Vue Router
- Pinia
- Axios
- Element Plus
- ECharts

## 开发命令

```bash
npm install
npm run dev
npm run type-check
npm run build
```

## 目录约定

- `src/app`：路由、权限、状态、布局、全局样式。
- `src/shared`：API 基础层、通用组件、类型、工具。
- `src/modules`：后续业务页面目录，保持“一个页面一个组件”。

后续开发具体页面时，在对应 `src/modules/*` 目录中新增页面组件，并在 `src/app/router/routes.ts` 中替换当前占位组件。
