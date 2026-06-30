# 药房管理：分页改造 + drug_info 表结构适配 设计文档

> 状态：待评审（先文档，后实现）
> 日期：2026-06-29
> 模块：pharmacy-service（后端）+ pharmacy 前端模块
> 关联：physician-service（参照样板，本次不改）

---

## 0. 一句话目标

**让药房「药品字典」页面在几十万条数据下能正常打开、翻页、搜索，并把 pharmacy-service 的 `DrugInfo` 实体从已失效的旧字段名对齐到线上真实表 `drug_info` 的 `drug_*` 列。**

---

## 1. 现状诊断（已核实）

### 1.1 「页面跑不出来」是两个问题叠加

| 问题 | 证据 | 后果 |
|---|---|---|
| **A. 字段名错位** | 后端 `SELECT name, generic_name, ..., category FROM drug_info`，但最新线上表（`port_3307_postgresql_full_dump.sql`）**没有 category 列**，且真实数据在 `drug_name` / `drug_type` 里，`name` 列为空 | PSQLException 直接 500，或查出全空 |
| **B. 无分页** | `DrugInfoMapper.selectAll()` / `selectByConditions()` 是裸 `SELECT ... FROM drug_info`，前端 `DrugDictionaryPage.vue` 一次性拉全量再 `slice()`（假分页） | 几十万行 → PG 内存暴涨 / Java 堆 OOM / JSON 传输卡死 / 浏览器 parse 卡死 |

两者必须**同时**修。只修字段不加分页，数据量上来仍会卡死；只加分页不对齐字段，接口仍报错。

### 1.2 线上真实表结构（来源：`docker/init-db/port_3307_postgresql_full_dump.sql` + `xikang_hospital_snapshot_20260624.sql`，两份一致）

`drug_info` 是「官方药品目录列」+「业务扩展列」的**并集**：

```
官方目录列（有真实数据，几十万条）：          业务扩展列（旧 pharmacy 模块加的，多为空/未用）：
  drug_code        varchar NOT NULL UNIQUE     name, generic_name, brand_name,
  drug_name        varchar NOT NULL            specification, dosage_form, unit,
  drug_format      varchar                     approval_number, price,
  drug_unit        varchar(16)                 stock_quantity, low_stock_threshold,
  manufacturer     varchar                     storage_conditions, instructions,
  drug_dosage      varchar(64)                 contraindications, adverse_reactions,
  drug_type        varchar(64)                 status, create_time, update_time
  drug_price       numeric(8,2) NOT NULL
  mnemonic_code    varchar        ← 助记码，有索引 idx_drug_mnemonic
  creation_date    date
```

**关键差异**：`snapshot_20260624` 里还有 `category` 列，但更晚的 `port_3307_full_dump` **没有** `category`。即线上最近被人去掉/未导入 category。这就是用户说的「别人改了 drug_info」。

> ⚠️ 注意 `drug_info` 同时存在 `drug_price` 和 `price`、`drug_name` 和 `name` 两套。**真实数据在前者**，后者基本是 NULL。读写必须统一走 `drug_*`。

`drug_stock` 表干净，与现有 `DrugStock` 实体完全对得上（仅多一个 `update_time`，无害）。**此表只加分页，不动实体。**

### 1.3 已有的可复用资产（重要，省大量工作）

**physician-service 早就把 drug_info 适配好了，而且已实现真分页**——本次 pharmacy 改造直接照搬它的模式即可：

- 前端类型：`physician.ts:152` `interface Drug`（字段就是 `drugCode/drugName/drugFormat/drugUnit/drugDosage/drugType/drugPrice/mnemonicCode`）
- 前端分页类型：`physician.ts:168` `DrugPageResult = { list, total, page, pageSize }`
- 前端分页调用：`physician.ts:525` `drugsPage(keyword, page, pageSize)`
- 后端 Controller：`PhysicianController.java:146` 双模式（传了 page/pageSize 走分页，没传走全量，老调用不破坏）
- 后端 Service：`PhysicianService.java:258` `getDrugsPage()`（边界保护 + offset 计算 + 返回 Map）
- 后端 SQL 片段：`PhysicianMapper.xml:355-404` `DrugSearchWhere`（ILIKE 搜索 drug_code/drug_name/mnemonic_code + status 兼容）、`DrugSearchColumns`（drug_* 列映射）、`selectDrugsPage` / `countDrugs`

> 项目里**没有统一的 PageResult 类**（已确认全仓搜不到）。physician 的做法是 Service 返回 `Map<String,Object>`。本次沿用，不新建公共类，保持一致性。

---

## 2. 设计原则

1. **代码迁就数据库，绝不改表。** 真实数据在 `drug_*` 列，删列=删数据；用户对服务器无 root（见 memory: 服务器环境约束）。pharmacy 实体重映射列名即可。
2. **照搬 physician 模式。** 字段命名、分页结构、Controller 双模式、SQL 模板，全部对齐 physician-service，降低认知成本和未来维护成本。
3. **波及面最小化。** physician 模块（药品搜索/处方）**不动**——它已对齐真实表且已分页。只改 pharmacy 后端 + pharmacy 前端。
4. **分页用 LIMIT/OFFSET。** 几十万条、翻页不会深到 10 万页之后，OFFSET 完全够用，且前端可跳页。不上 keyset（复杂度高、不能跳页，收益不匹配）。

---

## 3. 改造方案

### 3.1 后端：DrugInfo 实体重映射（适配真实表）

**文件**：`pharmacy-service/.../entity/DrugInfo.java`

实体字段全部改为对齐 `drug_*` 真实列（与 physician-service 的 `Drug` 字段保持一致）：

| 现有字段（删） | 新字段（对齐真实表） | 对应 DB 列 |
|---|---|---|
| `name` | `drugName` | drug_name |
| `genericName` | —（删除，表里虽有空列但不用） | — |
| `brandName` | —（删除） | — |
| `specification` | `drugFormat` | drug_format |
| `dosageForm` | `drugDosage` | drug_dosage |
| `category` | `drugType` | drug_type（category 概念映射到此） |
| `unit` | `drugUnit` | drug_unit |
| `approvalNumber` | `drugCode` | drug_code |
| `price` | `drugPrice` | drug_price |
| —（新增） | `mnemonicCode` | mnemonic_code |
| —（新增） | `creationDate` | creation_date |
| `manufacturer` | `manufacturer`（保留） | manufacturer |
| `stockQuantity` | `stockQuantity`（保留，汇总库存） | stock_quantity |
| `lowStockThreshold` | `lowStockThreshold`（保留） | low_stock_threshold |
| `storageConditions / instructions / contraindications / adverseReactions / status / createTime / updateTime` | 全部保留（表里有这些列） | 同名 |
| `id` | `id`（保留） | id |

**新增药品写入策略**：`insert` 只写 `drug_*` 列（drug_name/drug_format/drug_unit/drug_dosage/drug_type/drug_price/drug_code + 业务列 stock_quantity 等），**不再写 name/specification 等旧列**，让它们自然保持 NULL。

### 3.2 后端：DrugInfoMapper.xml 重写

**文件**：`pharmacy-service/.../resources/mapper/DrugInfoMapper.xml`

- `resultMap` / `Base_Column_List` 全部换成 `drug_*` 列
- 抄 physician 的两个 sql 片段（直接复用命名，便于对照）：
  - `DrugSearchWhere`：`status IS NULL OR status=1` + keyword 对 `drug_code / drug_name / mnemonic_code` 三列 `ILIKE`
  - `DrugSearchColumns`：`drug_*` 列映射（与 physician 一致）
- `selectByConditions` 改为对 `drug_type`（原 category）+ `drug_dosage`（原 dosageForm）+ keyword 组合过滤
- `selectCategories` → 重命名为 `selectDrugTypes`：`SELECT DISTINCT drug_type FROM drug_info WHERE status=1 AND drug_type IS NOT NULL`
- 关键词搜索走 `drug_name ILIKE OR mnemonic_code ILIKE`（均有索引，几十万条搜得动）

### 3.3 后端：分页查询（照搬 physician 模式）

**文件**：`DrugInfoMapper.java` + `DrugInfoMapper.xml` + `PharmacyService.java` + `PharmacyController.java`

新增方法（与 physician 同名同构，便于维护）：

```java
// DrugInfoMapper.java
long countDrugs(@Param("keyword") String keyword,
                @Param("drugDosage") String drugDosage,
                @Param("drugType") String drugType);

List<DrugInfo> selectDrugsPage(@Param("keyword") String keyword,
                               @Param("drugDosage") String drugDosage,
                               @Param("drugType") String drugType,
                               @Param("offset") int offset,
                               @Param("limit") int limit);
```

```java
// PharmacyService.java（照搬 PhysicianService.getDrugsPage 边界保护逻辑）
public Map<String,Object> getDrugsPage(String keyword, String dosageForm,
                                       String category, Integer page, Integer pageSize) {
    int safePage  = page==null||page<1  ? 1 : page;
    int safeSize  = pageSize==null||pageSize<1 ? 20 : Math.min(pageSize, 100);
    int offset    = (safePage-1) * safeSize;
    // category 入参 → 映射到 drugType；dosageForm → drugDosage
    long total = drugInfoMapper.countDrugs(keyword, dosageForm, category);
    List<DrugInfo> list = drugInfoMapper.selectDrugsPage(keyword, dosageForm, category, offset, safeSize);
    Map<String,Object> r = new LinkedHashMap<>();
    r.put("list", list); r.put("total", total);
    r.put("page", safePage); r.put("pageSize", safeSize);
    return r;
}
```

**Controller 双模式**（关键，保证不传分页参数的老调用不炸，照搬 `PhysicianController:146`）：

```java
// PharmacyController.getDrugs() 改为：
@GetMapping("/drugs")
public Result<?> getDrugs(@RequestParam(required=false) String keyword,
                          @RequestParam(required=false) String dosageForm,
                          @RequestParam(required=false) String category,
                          @RequestParam(required=false) Integer page,
                          @RequestParam(required=false) Integer pageSize) {
    if (page != null || pageSize != null) {
        return Result.success(pharmacyService.getDrugsPage(keyword, dosageForm, category, page, pageSize));
    }
    // 兼容老调用（无分页）—— 但为防 OOM，加硬上限 LIMIT 200
    return Result.success(pharmacyService.getDrugs(keyword, dosageForm));
}
```

> ⚠️ 老的 `getDrugs()`（无分页）保留，但 Mapper 里加 `LIMIT 200` 兜底，避免别处误调拉全量。

### 3.4 后端：drug_stock 近效期分页

**文件**：`DrugStockMapper.xml` + Controller `/inventory/expiring`

几十万批次，近效期也可能几千条。`selectExpiring` 加 `countExpiring` + `selectExpiringPage(offset,limit)`，Controller 加 `page/pageSize` 双模式，返回同结构 `{list,total,page,pageSize}`。

### 3.5 后端：低库存分页（可选但建议）

`/drugs/low-stock` 同样可能命中几千条。建议同上加分页。若想缩小本次改动范围，可先只做主列表 + 近效期，低库存留到二期。

---

## 4. 前端改造

### 4.1 类型重定义

**文件**：`shared/types/pharmacy.ts`

`DrugOption` 字段对齐 physician 的 `Drug`（保持两模块一致）：

```ts
export interface DrugOption {
  id: number
  drugCode?: string          // 原 approvalNumber
  drugName: string           // 原 name（必填，对齐 NOT NULL）
  drugFormat?: string        // 原 specification
  drugUnit?: string          // 原 unit
  drugDosage?: string        // 原 dosageForm
  drugType?: string          // 原 category
  drugPrice: number          // 原 price
  mnemonicCode?: string      // 新增
  manufacturer?: string
  stockQuantity?: number
  lowStockThreshold?: number
  storageConditions?: string
  instructions?: string
  contraindications?: string
  adverseReactions?: string
  status?: number
  creationDate?: string
  createTime?: string
  updateTime?: string
}

export interface DrugPageResult {
  list: DrugOption[]
  total: number
  page: number
  pageSize: number
}

export interface DrugQuery {
  keyword?: string
  drugDosage?: string    // 原 dosageForm
  drugType?: string      // 原 category
  page?: number
  pageSize?: number
}
```

### 4.2 API 改造

**文件**：`shared/api/modules/pharmacy.ts`

```ts
drugs(params?: DrugQuery) {
  return http<DrugPageResult>({ url: '/pharmacy/drugs', method: 'GET', params })
},
// 返回类型从 DrugOption[] → DrugPageResult
```

### 4.3 页面：假分页 → 真分页

**文件**：`modules/pharmacy/pages/DrugDictionaryPage.vue`

- 删除内存切片：去掉 `paged = drugs.value.slice(...)`
- `drugs` 改为只存当前页 `records`，新增 `total` ref
- `load()` 把 `page/pageSize` 带上，从返回体取 `list` 和 `total`
- 翻页（`@current-change`）、改 pageSize（`@size-change`）都触发 `load()`
- 表格列字段改名：`prop="drugName"` / `drugFormat` / `drugDosage` / `drugType` / `drugPrice` / `drugUnit`
- 分页器 `:total="total"`（绑后端 total，不再是 `drugs.length`）
- 筛选条件变化（keyword/dosageForm/category）时 `page` 重置为 1 再查

### 4.4 表单/详情弹窗字段对齐

**文件**：
- `components/DrugFormDialog.vue`（新增/编辑）
- `components/DrugDetailDialog.vue`（详情）

两文件里所有 `drug.name / drug.specification / drug.dosageForm / drug.category / drug.price / drug.approvalNumber` 等绑定，全部改为 `drug.drugName / drug.drugFormat / drug.drugDosage / drug.drugType / drug.drugPrice / drug.drugCode`。表单的 `makeEmpty()` 默认值同步改字段名。

### 4.5 常量校验

**文件**：`shared/constants/pharmacy.ts`

`DOSAGE_FORMS`（'片剂/胶囊/...'）是前端写死的枚举，但真实表 `drug_dosage` 的取值是别人导入的目录决定的。**需要抽样核对**：上线前先 `SELECT DISTINCT drug_dosage FROM drug_info WHERE drug_dosage IS NOT NULL` 看真实取值，若与前端枚举不一致，筛选下拉会筛不出数据。文档先标记为「待核对项」。

---

## 5. 波及面清单（影响范围）

| 层 | 文件 | 改动类型 |
|---|---|---|
| 后端实体 | `DrugInfo.java` | 重映射字段 |
| 后端 Mapper | `DrugInfoMapper.java` / `.xml` | 重写列映射 + 新增分页方法 |
| 后端 Mapper | `DrugStockMapper.xml` | 新增近效期分页 |
| 后端 Service | `PharmacyService.java` | 新增 `getDrugsPage` + 近效期分页方法 |
| 后端 Controller | `PharmacyController.java` | `/drugs` `/inventory/expiring` 改双模式 |
| 前端类型 | `shared/types/pharmacy.ts` | `DrugOption`/`DrugQuery` 重定义 + `DrugPageResult` |
| 前端 API | `shared/api/modules/pharmacy.ts` | `drugs()` 返回类型改 |
| 前端页面 | `DrugDictionaryPage.vue` | 假分页→真分页 + 列字段改名 |
| 前端组件 | `DrugFormDialog.vue` / `DrugDetailDialog.vue` | 字段绑定改名 |
| 前端常量 | `shared/constants/pharmacy.ts` | `DOSAGE_FORMS` 待核对真实取值 |

**不受影响**（已核实，避免误改）：
- ✅ `physician-service` 全部药品相关代码 —— 已对齐 `drug_*`、已分页
- ✅ 前端 `DrugSearchPicker.vue` / `PhysicianDrugDetailDialog.vue` —— 用的是 physicianApi 的 `Drug` 类型，独立
- ✅ `drug_stock` 实体 `DrugStock.java` —— 表干净，只加分页查询不动实体
- ⚠️ 处方详情 / 发药 / 交易记录等接口里若拼了 drug 名称展示，需检查是否依赖旧 `name` 字段 —— 实现阶段 grep `drug_name`/`d.drug_name` 确认（physician 的 `ClinicalRecordMapper.xml:153` / `PhysicianMapper.xml:444` 已用 `d.drug_name`，pharmacy 侧的处方/发药 Mapper 需同样核对）

---

## 6. 风险与对策

| 风险 | 对策 |
|---|---|
| `drug_dosage` 真实取值和前端 `DOSAGE_FORMS` 不一致，剂型筛选筛空 | 上线前 `SELECT DISTINCT drug_dosage` 核对，必要时把前端下拉改成「后端返回的真实取值」或改用自由文本输入 |
| 别处（发药/处方/统计）Mapper 仍 `SELECT name` 残留 → 500 | 实现阶段全仓 grep `\bname\b` 在 pharmacy Mapper XML 里的引用，逐个改 `drug_name` |
| 老调用不传 page 走全量，仍可能 OOM | 兼容分支 Mapper 加 `LIMIT 200` 硬上限 |
| `drug_info` 里 `stock_quantity` 与 `drug_stock` 汇总不同步 | 本次不处理（已有 migrate_023 回写逻辑），仅保证读 `stock_quantity` 不报错 |
| OFFSET 深翻页慢 | 几十万条 + 不会翻到几万页，可接受；若未来真有问题再升 keyset |

---

## 7. 验证清单（实现后）

- [ ] 后端启动无报错，`/api/pharmacy/drugs?page=1&pageSize=20` 返回 `{list,total,page,pageSize}`，list 元素字段为 drug_*
- [ ] 不传 page 的老接口 `/api/pharmacy/drugs` 仍可用（LIMIT 200）
- [ ] 前端药品字典页：首屏只请求 1 页、翻页发新请求、筛选后 page 重置
- [ ] 几十万条数据下页面 2 秒内出首屏，不再卡死/500
- [ ] 新增药品写入后，`drug_name/drug_format/...` 有值，旧 `name` 列为 NULL（符合预期）
- [ ] `/inventory/expiring?page=1&pageSize=20` 分页正常
- [ ] physician 模块药品搜索不受影响（回归测试）

---

## 8. 待用户拍板的开放项

1. **低库存 `/drugs/low-stock` 是否本次一起分页？**（建议：是，但可二期）
2. **`DOSAGE_FORMS` 前端枚举**：保持写死待核对，还是改成由后端 `SELECT DISTINCT drug_dosage` 动态返回？（建议：先核对再定）
3. **实现顺序**：先后端（实体+Mapper+分页）跑通接口，还是前后端一起改？（建议：先后端，用 curl/Postman 验证接口，再改前端）

---

## 9. 不做（Out of Scope）

- 不改 `drug_info` / `drug_stock` 表结构（无 root + 有真实数据）
- 不新建公共 `PageResult` 类（沿用 physician 的 Map 模式）
- 不引入 MyBatis PageHelper（项目目前没用，引入风险大于收益）
- 不动 physician-service（已正确）
- 不做 keyset 游标分页（LIMIT/OFFSET 够用）
