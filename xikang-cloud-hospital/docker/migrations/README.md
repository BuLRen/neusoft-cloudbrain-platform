# 数据库迁移说明（联调用）

> 快照日期：2026-06-18  
> 来源：本地 Docker 容器 `xikang-postgres`（PostgreSQL 16.x，库名 `xikang_hospital`）  
> 共 **26 张表**

## 给同事的快速指引

### 场景 A：全新建库（推荐）

若同事本地还没有 `xikang_hospital` 库，或可以清空重建：

```bash
# 1. 启动 Docker 数据库（首次启动会自动执行 init-db/init.sql）
cd xikang-cloud-hospital/docker
docker compose up -d postgres

# 2. 连接验证
docker exec -it xikang-postgres psql -U postgres -d xikang_hospital -c "\dt"
```

权威初始化脚本：`docker/init-db/init.sql`（含演示种子数据）。

### 场景 B：已有旧库，只做增量迁移

若同事已有旧版表结构，按顺序手动执行以下脚本（**不要重复执行 init.sql**）：

| 顺序 | 脚本 | 作用 |
|------|------|------|
| 1 | `init-db/migrate-result-form.sql` | 新增 `result_form_category` / `result_form_field` 表单引擎表；`check_result` 改 TEXT |
| 2 | `init-db/migrate-inspection-result-text.sql` | `inspection_result` 改 TEXT；新增 `general_lab` 表单模板 |
| 3 | `init-db/migrate-archive-state.sql` | 检查/检验/处置状态增加「已归档」 |
| 4 | `migrations/001_visit_state_5_6.sql` | `register.visit_state` 扩展为 1–6 |
| 5 | `migrations/incremental_to_local_20250618.sql` | 补齐本地快照中其余差异（见脚本内注释） |

执行示例：

```bash
docker exec -i xikang-postgres psql -U postgres -d xikang_hospital < docker/init-db/migrate-result-form.sql
docker exec -i xikang-postgres psql -U postgres -d xikang_hospital < docker/init-db/migrate-inspection-result-text.sql
docker exec -i xikang-postgres psql -U postgres -d xikang_hospital < docker/init-db/migrate-archive-state.sql
docker exec -i xikang-postgres psql -U postgres -d xikang_hospital < docker/migrations/001_visit_state_5_6.sql
docker exec -i xikang-postgres psql -U postgres -d xikang_hospital < docker/migrations/incremental_to_local_20250618.sql
```

### 场景 C：对照完整 DDL

完整建表语句（从本地库 `pg_dump --schema-only` 导出）：

- `migrations/schema_snapshot_20250618.sql`

> 注意：该文件为 pg_dump 原始输出，含 `public.` 前缀和 SEQUENCE 定义，适合对照或在新库上执行；若已有表会报「already exists」错误，请用场景 B 增量脚本。

## 连接信息

| 项目 | 值 |
|------|-----|
| 主机 | `localhost` |
| 端口 | `3307` |
| 数据库 | `xikang_hospital` |
| 用户 | `postgres` |
| 密码 | `postgres123` |

JDBC：`jdbc:postgresql://localhost:3307/xikang_hospital`

## 表清单（26 张）

**基础字典（7）**：department, regist_level, scheduling, settle_category, disease, drug_info, medical_technology

**表单引擎（2）**：result_form_category, result_form_field

**核心业务（7）**：employee, register, medical_record, check_request, inspection_request, disposal_request, prescription

**关联（1）**：medical_record_disease

**AI 记录（9）**：ai_triage_record, ai_consultation_record, ai_medical_record_log, ai_exam_suggestion, ai_exam_analysis, ai_diagnosis_suggestion, ai_prescription_review, ai_follow_up_plan, ai_follow_up_record
