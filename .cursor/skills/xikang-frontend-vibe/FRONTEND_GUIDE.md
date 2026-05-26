# 希康云医院前端 Vibe Coding 指南

本指南给后续参与前端开发的 AI 和小组成员使用。它不依赖某个特定模型，Cursor、Claude、GPT、DeepSeek、Qwen 等主流模型都可以直接读取并遵守。

## 项目范围

前端工程位于：

```txt
xikang-hospital-frontend/
```

技术栈：

- Vue 3 + TypeScript + Vite
- Vue Router
- Pinia
- Axios
- Element Plus
- ECharts

设计文档主要参考：

- `task_requirements/设计文档/01_系统架构设计文档.md`
- `task_requirements/设计文档/02_API接口文档.md`
- `task_requirements/设计文档/DEV_GUIDE_A.md`
- `task_requirements/设计文档/DEV_GUIDE_B.md`

## 目录职责

```txt
src/app/        全局框架：路由、权限、状态、布局、全局样式
src/shared/     共享能力：API、通用组件、类型、工具
src/modules/    业务页面：后续功能开发的主要区域
```

开发具体功能时，优先只修改：

```txt
src/modules/<module>/**
```

例如：

```txt
src/modules/physician/
src/modules/registration/
src/modules/pharmacy/
```

## 全局文件修改限制

除非用户明确要求，或者当前功能确实无法在模块内完成，否则不要修改这些文件：

```txt
src/app/router/**
src/app/stores/**
src/app/layouts/**
src/app/styles/**
src/shared/api/request.ts
src/shared/components/**
src/shared/types/**
```

如果必须修改全局文件：

1. 先说明为什么模块内无法完成。
2. 只做最小改动。
3. 不重构现有框架。
4. 修改后运行 `npm run type-check`，必要时运行 `npm run build`。

## 页面开发规则

每个业务页面尽量对应一个 Vue 单文件组件。

推荐结构：

```txt
src/modules/<module>/<PageName>.vue
```

页面组件应负责：

- 页面查询条件
- 页面表格或表单
- 页面弹窗
- 页面内状态
- 调用对应业务 API

页面组件不应负责：

- 修改全局路由结构
- 修改全局主题 token
- 创建新的 Axios 实例
- 写入患者敏感信息到 URL、localStorage、console 或错误提示

## 样式规则

优先使用已有通用组件：

- `PageHeader`
- `GlassCard`
- `StatusTag`
- `EmptyState`
- `ErrorState`
- `LoadingState`
- `RoutePlaceholder`

优先使用已有全局 CSS token：

```css
var(--color-primary)
var(--color-text)
var(--color-text-muted)
var(--color-border)
var(--color-surface)
var(--radius-lg)
var(--radius-xl)
var(--space-4)
var(--shadow-sm)
```

页面内可以写 scoped CSS，但应保持短小。不要为单个页面重新定义整套颜色、圆角、阴影或按钮风格。

## API 规则

统一使用：

```txt
src/shared/api/request.ts
src/shared/api/modules/*.ts
```

不要在页面里直接创建新的 Axios 实例。

不要提前虚构复杂字段。字段来源优先级：

1. `task_requirements/设计文档/02_API接口文档.md`
2. 后端实际接口返回
3. 与小组成员确认后的临时类型

统一响应结构：

```ts
interface ApiResult<T> {
  code: number
  message: string
  data: T
}
```

分页结构：

```ts
interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  totalPages: number
}
```

## 路由和菜单规则

已有路由在：

```txt
src/app/router/routes.ts
```

新增页面时：

- 优先在已有模块路由下替换占位组件。
- 保留已有 `path`、`name`、`meta.title`、`meta.roles` 语义。
- 不重写整个路由文件。
- 不删除其他成员的路由。

路由 `meta.roles` 应使用现有角色：

```txt
admin
physician
registration
medtech
pharmacy
patient
```

## 状态管理规则

Pinia 全局 store 用于全局状态，例如登录、角色、主题、菜单权限。

页面内临时数据优先用组件局部状态：

```ts
const loading = ref(false)
const list = ref([])
```

不要为了单个页面的表单数据新增全局 store。

## 安全和隐私规则

这是医疗相关项目，必须避免泄露患者敏感信息。

禁止：

- 把患者姓名、身份证、手机号、病历、诊断、处方放进 URL。
- 把患者敏感信息写进 localStorage。
- 在 console 中打印患者敏感信息。
- 在错误提示中直接展示后端返回的敏感详情。

可以：

- 页面内临时展示必要业务信息。
- 使用 `registerId`、`patientId` 等非直接敏感标识进行内部查询。
- 在错误提示中展示通用描述，例如“查询失败，请稍后重试”。

## 开发前检查

开始写代码前，先确认：

- 这个功能属于哪个模块。
- 是否已有对应占位路由。
- 需要参考哪个 API 文档章节。
- 是否可以只改 `src/modules/**`。
- 是否真的需要修改全局文件。

## 开发后验证

至少运行：

```bash
cd xikang-hospital-frontend
npm run type-check
```

如果修改了以下内容，还要运行：

```bash
npm run build
```

需要构建验证的情况：

- 路由
- 权限
- Layout
- 全局样式
- API 基础层
- 共享组件

## AI 执行要求

当 AI 根据本指南开发功能时，应遵守：

1. 先读相关文件，不要凭空猜结构。
2. 优先小范围修改。
3. 不要重写框架。
4. 不要顺手美化无关页面。
5. 不要改动分工文档，除非用户明确要求。
6. 不要提交 Git commit，除非用户明确要求。
7. 说明改了哪些文件、如何验证。
