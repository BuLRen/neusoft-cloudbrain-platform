# 管理端（admin）

## 检查项目（`/admin/check-equipment`）

维护 `medical_technology` 表，与医生「开立检查检验」下拉数据一致。

- **检查类型**：字段 `tech_type`（检查 / 检验 / 处置）
- API：`GET /api/medtech/medical-technologies?page=&size=`（分页，返回 `records/total/page/size/totalPages`）、`/api/medtech/departments`

已有库若曾创建 `check_category`，可执行 `init.sql` 末尾迁移注释删除该表及 `check_category_id` 列。
