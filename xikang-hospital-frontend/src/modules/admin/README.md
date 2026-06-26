# 管理端（admin）

## 医技项目（`/admin/medtech-items`）

维护 `medical_technology` 表及检查结果表单配置，与医生「开立检查检验」下拉数据一致。

- **项目目录** Tab：检查类型字段 `tech_type`（检查 / 检验 / 处置）
- **结果表单** Tab：分类通用模板与项目扩展字段
- API：`GET /api/medtech/medical-technologies?page=&size=`（分页，返回 `records/total/page/size/totalPages`）、`/api/medtech/departments`

旧路径 `/admin/check-equipment`、`/admin/result-form` 会自动重定向至本页对应 Tab。

已有库若曾创建 `check_category`，可执行 `init.sql` 末尾迁移注释删除该表及 `check_category_id` 列。
