# 管理端（admin）

左侧菜单「管理端」用于维护医院基础数据。每个子菜单对应一个独立维护页。

## 已实现

| 子路由 | 菜单名 | 说明 | API |
|--------|--------|------|-----|
| `/admin/check-equipment` | 检查设备 | `medical_technology` 中 `tech_type=check` 的检查项目 CRUD | `medtech-service` `/api/medtech/medical-technologies`、`/api/medtech/departments` |

## 扩展新子页

1. 在 `src/app/router/routes.ts` 的 `admin.children` 中增加子路由与 `meta.title`。
2. 在 `src/modules/admin/pages/` 新增页面组件。
3. 在 `src/shared/api/modules/admin.ts` 增加对应 API 方法（或新建域模块再导出）。
4. 使用 `admin` 角色访问；侧栏由路由 `meta.roles` 自动过滤。

## 权限

管理端路由统一 `roles: ['admin']`。
