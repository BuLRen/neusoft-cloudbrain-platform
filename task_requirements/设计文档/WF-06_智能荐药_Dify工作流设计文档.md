# WF-06: 智能荐药 Dify 工作流设计文档

> 版本：v1.2  
> 日期：2026-06-29  
> 适配平台：Dify 1.11.2  
> 状态：设计完成，Dify 控制台需按 **v1.2** 同步节点 5.6 / 8A / 9A  
> 核心目标：根据门诊确诊病名、病历上下文与 **NMPA 本位码导入的** `drug_info` 药品库，为医生推荐 1-5 种**有库存**、可采纳药品。  
> **v1.1 变更**：药品主数据改为「国家药品编码本位码信息（国产 + 进口）」导入远程库；补充字段映射、导入范围与工作流适配说明。  
> **v1.2 变更**：荐药须考虑库存——`/drugs/ai-search` 已排除零库存；Dify 节点 5.6 / 8A / 9A 需同步；`physician-service` 落库与前端采纳已加二次校验（代码侧已完成，见第 7 节）。

---

## 1. 工作流定位

本工作流用于门诊医生工作站「开立处方」页面（诊疗第 ⑥ 步），负责 AI 智能荐药。

工作流只负责：

- 整理本次就诊的临床上下文（含 W4 确诊建议摘要）。
- 提取药品检索线索。
- 通过 HTTP 节点调用后端药品库检索接口。
- 从候选药品中选择推荐方案（含用法用量与数量）。
- 输出可写入 `ai_drug_suggestion` 表的结构化结果。

工作流不负责：

- 直接连接数据库或写入 `prescription`。
- 替医生做最终开方决策。
- 将完整 `drug_info` 目录作为 LLM 上下文。

---

## 2. 关键设计原则

### 2.1 药品库处理方式

与 WF-05（W4 确诊）一致，采用 **「权威主数据入库 + 工作流内检索召回」**，不把完整药品目录传入 LLM：

```text
国家药品编码本位码（国产 Excel + 进口 Excel）
  ↓ ETL 导入远程 PostgreSQL drug_info
Dify 提取检索关键词
  ↓
HTTP 节点调用 /internal/drugs/ai-search
  ↓
只返回 20-40 条候选药品给 LLM
  ↓
LLM 只能从候选药品中选择（drugId 必须来自候选集）
```

### 2.2 编排前置条件

在 Dify 控制台编排 WF-06 **之前**，远程数据库应已完成：

1. **第 3 节** 所述 NMPA 本位码 ETL 导入（国产 + 进口合并进 `drug_info`）。
2. 门诊常见病相关品种的 `instructions` / `contraindications` 已做第二层补全（见 **3.5 节**），否则 TC-002 过敏过滤与适应症检索效果不足。
3. `POST /api/physician/internal/drugs/ai-search` 在 Dify 所在网络可达（云侧 **ai-catalog-service :8098**，Docker 内常用 `http://172.17.0.1:8098/...`）。
4. `INTERNAL_AI_TOKEN` 与后端 `.env` 一致。

### 2.3 Start 节点输入策略

Dify 1.11.2 开始节点 **全部使用 string 类型输入**。

### 2.4 库存约束（v1.2 新增）

荐药必须尊重药房库存，采用 **三层防护**（后端硬过滤 → 工作流确定性校验 → 采纳兜底）：

```text
drug_info.stock_quantity（由 drug_stock 批次汇总）
  ↓
ai-catalog /drugs/ai-search：SQL 排除 stock_quantity = 0，响应带 stockQuantity / unit
  ↓
Dify 9A 校验：recommendQuantity = min(LLM建议, stockQuantity, 5)
  ↓
physician-service persistW5：落库前再次查库过滤/截断
  ↓
前端 adoptW5Suggestion：采纳进处方篮前查实时库存
```

| 库存状态 | 行为 |
|----------|------|
| `stock_quantity = 0` | **不推荐**（不进 HTTP 候选集） |
| `0 < stock ≤ low_stock_threshold` | 可推荐，数量 ≤ 库存，`cautionNotes` 标注「库存紧张」 |
| `stock > threshold` | 正常推荐，`recommendQuantity ≤ min(stock, 5)` |

**Dify 侧职责**：LLM 只在有货候选中选药；**数量截断由节点 9A Code 确定性完成**，不要仅靠 Prompt。

---

## 3. 后端药品库要求（NMPA 药品编码本位码）

> 对标 WF-05 第 4 节「后端 ICD/疾病库要求」。工作流本身不直连数据库，但检索质量完全依赖本节数据是否按规范入库。

### 3.1 数据来源

| 文件 | 说明 |
|------|------|
| **国家药品编码本位码信息（国产药品）** | NMPA 官方 Excel；含批准文号、产品名称、剂型、规格、持有人、生产单位、14 位药品本编码 |
| **国家药品编码本位码信息（进口药品）** | NMPA 官方 Excel；含注册证号、产品名称、剂型、规格、持有人（中/英）、药品本编码 |

两份文件 **合并导入同一张 `drug_info` 表**；以 `drug_code`（药品本编码）为全局唯一键，国产与进口编码体系不冲突。

### 3.2 导入目标与范围建议

| 数据集 | 建议策略 | 理由 |
|--------|----------|------|
| 国产 | 先按 **国家医保药品目录（2024）** 通用名过滤，再导入对应规格行（约数千～一万条 SKU） | 全量国产十几万条噪音大、演示与门诊场景不符 |
| 进口 | **建议全量导入**（通常数千～两万条） | 体量可控，覆盖进口中成药/OTC，更贴近真实目录 |
| 运营字段 | `price`、`stock_quantity`、`mnemonic_code` 由 ETL 或迁移脚本生成演示值 | 本位码表不含价格库存；生产环境日后由 HIS 同步 |

导入脚本建议幂等：`INSERT ... ON CONFLICT (drug_code) DO UPDATE`，便于重复执行与增量补全临床字段。

### 3.3 Excel → `drug_info` 字段映射

**国产药品**

| Excel 列 | `drug_info` 字段 | 说明 |
|----------|------------------|------|
| 药品本编码 | `drug_code` | **主键业务键**，14 位数字字符串，如 `86903931000326` |
| 产品名称 | `name`、`drug_name` | 两列填相同值（兼容老字段） |
| 批准文号 | `approval_number` | 如 `国药准字H37020591` |
| 剂型 | `dosage_form`、`drug_dosage` | |
| 规格 | `specification`、`drug_format` | |
| 生产单位 | `manufacturer` | 优先生产单位；空则用上市许可持有人 |
| 上市许可持有人 | （可选） | 与生产单位不同时可拼入 `manufacturer` |

**进口药品**

| Excel 列 | `drug_info` 字段 | 说明 |
|----------|------------------|------|
| 药品本编码 | `drug_code` | 同国产 |
| 产品名称 | `name`、`drug_name` | |
| 注册证号 | `approval_number` | 如 `国药准字ZJ20191000` |
| 剂型 | `dosage_form`、`drug_dosage` | |
| 规格 | `specification`、`drug_format` | |
| 上市许可持有人中文 | `manufacturer` | 进口表常无单独「生产单位」 |

**ETL 派生字段（Excel 中无，脚本生成）**

| 字段 | 规则 |
|------|------|
| `generic_name` | 从产品名称去剂型后缀（胶囊、片、颗粒、注射液…）；进口商品名可暂与产品名相同，后续用知识库细化 |
| `category` / `drug_type` | 按批准文号/注册证号前缀：`H`→西药，`Z`→中成药，`S`→生物制品；可叠加医保 OTC 标记 |
| `unit` | 按剂型默认：片/胶囊→盒，注射液→支，饮片→袋 等 |
| `price` / `drug_price` | 演示默认 `0.00` 或固定档位价 |
| `stock_quantity` | 演示默认 `100`；`low_stock_threshold` 默认 `20` |
| `mnemonic_code` | 产品名称拼音首字母 |
| `status` | `1`（启用） |

### 3.4 检索与工作流相关字段

`/internal/drugs/ai-search` 与 `DrugAiSearchService` 实际使用：

| 字段 | 检索 / 打分用途 |
|------|-----------------|
| `drug_name` / `name` | `drugKeywords`、适应症关键词兜底匹配 |
| `generic_name` | `genericKeywords` |
| `category` / `drug_type`、`dosage_form` | `categoryKeywords` |
| `instructions` | `indicationKeywords` 主匹配字段 |
| `contraindications` | `negativeKeywords` 过敏排除 |
| `drug_code` | 返回给前端的 `drugCode`（**不再是** `AMX001` 类演示编码） |

### 3.5 临床字段第二层补全（必做）

本位码表 **不含** 用法、禁忌、不良反应。仅导入第一层时：

- 按药名检索（如「阿莫西林」）通常可用；
- 按适应症（「上呼吸道感染」）召回弱；
- **TC-002 青霉素过敏**：依赖 `contraindications` 含「青霉素」或药名排除，空字段时后端难以过滤。

**推荐**：对医保过滤后的品种，按 `approval_number` 关联说明书摘要，写入：

- `instructions`：适应症 + 用法用量摘要（1～3 句）
- `contraindications`：禁忌摘要（含过敏相关成分）
- `adverse_reactions`：常见不良反应摘要（可选，供前端说明书）

至少保证 **WF-06 测试用例病种包**（上感、高血压、糖尿病、胃炎 + 青霉素类抗菌药）相关品种已补全。

### 3.6 成功导入后的自检 SQL

```sql
-- 应有国产 + 进口记录，drug_code 为 14 位本位码
SELECT COUNT(*) AS total,
       COUNT(*) FILTER (WHERE approval_number LIKE '%J%') AS imported_like,
       COUNT(*) FILTER (WHERE instructions IS NOT NULL AND instructions <> '') AS with_instructions
FROM drug_info WHERE status = 1;

-- 上感相关常用药是否在库且可检索
SELECT id, drug_code, drug_name, generic_name, category
FROM drug_info
WHERE status = 1 AND (drug_name ILIKE '%阿莫西林%' OR drug_name ILIKE '%头孢%')
LIMIT 10;
```

---

## 4. 工作流输入 / 输出总览

> 适配 **Dify 1.11.2**。Dify 节点逐步配置见 **第 5 节**。

### 4.1 Start 节点输入变量（全部 `String` 类型）

| 变量名 | 必填 | 说明 | 示例 |
|--------|------|------|------|
| `register_id` | 是 | 挂号 ID | `1001` |
| `patient_info_text` | 否 | 性别/年龄/科室 | `女，32岁，呼吸内科` |
| `confirmed_diagnosis_text` | **是** | 医生已保存的确诊病名 | `急性上呼吸道感染` |
| `w4_suggestions_text` | 否 | W4 推荐，一行一条：`ICD\|病名\|概率\|治疗方向` | `J06.900\|急性上呼吸道感染\|72%\|对症+抗感染` |
| `allergy_history` | 否 | 过敏史 | `青霉素过敏` |
| `past_history` | 否 | 既往史/慢病 | `高血压病史3年` |
| `chief_complaint` | 否 | 主诉 | `咽痛发热2天` |
| `w3_analysis_text` | 否 | W3 临床印象摘要 | `炎症指标轻度升高` |
| `abnormal_indicators_text` | 否 | 异常检验指标 | `WBC 11.2↑` |
| `preliminary_diagnosis_text` | 否 | 初步诊断 | `上感待排` |
| `doctor_notes` | 否 | 医生补充 | `咽充血，扁桃体I度肿大` |

后端由 [`W5DifyInputBuilder`](xikang-cloud-hospital/physician-service/src/main/java/com/xikang/physician/ai/W5DifyInputBuilder.java) 组装（Phase A 即实现）。

### 4.2 End 节点统一结构化输出（10A / 10B 字段一致）

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `status` | String | `success` / `fallback` |
| `registerId` | String | 挂号 ID |
| `suggestions` | Array[Object] | 可写库荐药列表（成功分支） |
| `fallbackSuggestions` | Array[Object] | 无药品库匹配时的文本建议（兜底分支） |
| `clinicalSummaryForDoctor` | String | 给医生的综合用药摘要 |
| `allergyWarnings` | Array[String] | 过敏/禁忌提醒 |
| `searchAdvice` | String | 建议补充搜索的关键词（兜底分支） |

#### `suggestions[]` 单条 Object 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `drugId` | Number | **必须**来自 HTTP 候选集 |
| `drugName` | String | 药品名称 |
| `drugCode` | String | 药品编码（**NMPA 14 位本位码**，来自候选集） |
| `recommendUsage` | String | 推荐用法用量 |
| `recommendQuantity` | Number | 推荐数量（盒/瓶） |
| `confidence` | Number | 0–100 |
| `recommendationBasis` | String | 推荐理由 |
| `cautionNotes` | String | 注意事项 |
| `sortOrder` | Number | 1 起 |

#### 成功分支示例

```json
{
  "status": "success",
  "registerId": "1001",
  "suggestions": [
    {
      "drugId": 12045,
      "drugName": "阿莫西林胶囊",
      "drugCode": "86903931000326",
      "recommendUsage": "口服，一次0.5g，一日3次，饭后服用",
      "recommendQuantity": 2,
      "confidence": 85.5,
      "recommendationBasis": "与确诊「急性上呼吸道感染」一线口服用药相符",
      "cautionNotes": "服药期间忌酒；注意胃肠道反应",
      "sortOrder": 1
    }
  ],
  "fallbackSuggestions": [],
  "clinicalSummaryForDoctor": "基于确诊与无青霉素过敏史，推荐口服抗生素方案，共1种主药。",
  "allergyWarnings": [],
  "searchAdvice": ""
}
```

#### 兜底分支示例

```json
{
  "status": "fallback",
  "registerId": "1001",
  "suggestions": [],
  "fallbackSuggestions": [
    {
      "drugName": "阿莫西林克拉维酸钾",
      "recommendUsage": "口服，遵医嘱",
      "recommendationBasis": "上呼吸道感染常用抗生素方向",
      "note": "本地药品库未匹配到对应条目，请医生手动搜索选药"
    }
  ],
  "clinicalSummaryForDoctor": "药品库未召回候选，仅提供用药方向参考。",
  "allergyWarnings": ["患者过敏史：青霉素过敏，已排除相关药物"],
  "searchAdvice": "建议搜索：头孢类、大环内酯类抗生素"
}
```

### 4.3 内部药品检索 API 契约（HTTP 节点 5 调用）

```http
POST http://172.17.0.1:8098/api/physician/internal/drugs/ai-search
Content-Type: application/json
Authorization: Bearer {{INTERNAL_AI_TOKEN}}
```

请求体：

```json
{
  "drugKeywords": ["阿莫西林", "头孢"],
  "genericKeywords": ["amoxicillin"],
  "categoryKeywords": ["抗生素", "西药"],
  "indicationKeywords": ["上呼吸道感染"],
  "negativeKeywords": ["青霉素"],
  "limit": 40
}
```

响应体（`data.candidates` 供下游代码节点读取；**v1.2 起含库存字段，且零库存药品不会出现在列表中**）：

```json
{
  "code": 200,
  "data": {
    "candidates": [
      {
        "drugId": 12045,
        "drugCode": "86903931000326",
        "drugName": "阿莫西林胶囊",
        "specification": "0.25g",
        "category": "西药",
        "price": 0.00,
        "genericName": "阿莫西林",
        "instructions": "用于敏感菌所致感染。口服，成人一次0.5g，一日3次。",
        "contraindications": "青霉素过敏者禁用",
        "stockQuantity": 100,
        "unit": "盒",
        "lowStockThreshold": 20,
        "matchScore": 50
      }
    ]
  }
}
```

> **实现说明（代码已完成）**：`ai-catalog-service` 的 `DrugCatalogMapper.xml` 在 `activeDrugFilter` 中增加 `stock_quantity > 0`；`DrugAiSearchService` 在候选中返回 `stockQuantity` / `unit` / `lowStockThreshold`。

---

## 5. 节点详细设计（Dify 1.11.2）

> 在 Dify 控制台按本节逐节点配置。药品库须已按 **第 3 节** 导入远程数据库。

### 5.0 工作流总览（10 节点 + 双 End）

```text
1. 开始节点（Start）
   ↓
2. 整理输入（Code）
   ↓
3. 提取药品检索线索（LLM · 结构化输出）
   ↓
4. 构造药品检索请求（Code）
   ↓
5. 查询药品候选（HTTP）
   ↓
6. 格式化候选药品（Code）
   ↓
7. 判断是否有候选药品（IF）
   ├─ 有候选 → 8A. 核心用药推荐（LLM） → 9A. 校验最终输出（Code） → 10A. 结束（成功）
   └─ 无候选 → 8B. 无候选兜底建议（LLM） → 9B. 格式化兜底输出（Code） → 10B. 结束（兜底）
```

### 5.0.1 LLM 节点统一约定（Dify 1.11.2）

共 3 个 LLM 节点，**全部开启「结构化输出」**，禁止 `text` 模式 + 手动 JSON 解析：

| 节点 | 名称 | 结构化输出 Schema 根字段 |
|------|------|---------------------------|
| 3 | 提取药品检索线索 | `drugKeywords`, `genericKeywords`, `categoryKeywords`, `indicationKeywords`, `negativeKeywords`, `reason` |
| 8A | 核心用药推荐 | `drugSuggestions`, `clinicalSummaryForDoctor`, `allergyWarnings` |
| 8B | 无候选兜底建议 | `fallbackSuggestions`, `clinicalSummaryForDoctor`, `searchAdvice` |

**模型建议**：与 W4 相同（如 `deepseek-chat` 或 Dify 已配置的医学向模型），Temperature `0.2`。

---

### 5.1：开始

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `开始` |
| **节点类型** | Start |
| **Dify 版本** | 1.11.2 Workflow App |

**输入变量**（全部类型选 `短文本 / text`，即 String）：

见 **4.1 节** 11 个变量；`confirmed_diagnosis_text` 在 Dify 侧标记为必填。

**输出变量**：Start 节点自动透传全部输入变量给下游。

**注意**：不使用 Object / Array 类型输入，避免 1.11.2 开始节点解析不稳定（与 WF-05 一致）。

---

### 5.2：整理输入

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `整理输入` |
| **节点类型** | Code |
| **语言** | Python 3 |

**输入变量**：

| 变量名 | 来源 |
|--------|------|
| `register_id` | `开始.register_id` |
| `patient_info_text` | `开始.patient_info_text` |
| `confirmed_diagnosis_text` | `开始.confirmed_diagnosis_text` |
| `w4_suggestions_text` | `开始.w4_suggestions_text` |
| `allergy_history` | `开始.allergy_history` |
| `past_history` | `开始.past_history` |
| `chief_complaint` | `开始.chief_complaint` |
| `w3_analysis_text` | `开始.w3_analysis_text` |
| `abnormal_indicators_text` | `开始.abnormal_indicators_text` |
| `preliminary_diagnosis_text` | `开始.preliminary_diagnosis_text` |
| `doctor_notes` | `开始.doctor_notes` |

**输出变量**：

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `register_id` | String | 清洗后挂号 ID |
| `clinical_context` | String | 汇总临床上下文（供 LLM 阅读） |
| `has_confirmed_diagnosis` | String | `"true"` / `"false"` |
| `allergy_keywords` | String | 从过敏史提取的禁用关键词，逗号分隔 |

**节点代码（可直接粘贴）**：

```python
def clean(v):
    return "" if v is None else str(v).strip()

def extract_allergy_keywords(text):
    text = clean(text)
    if not text or text in ("无", "否认", "未发现"):
        return ""
    # 常见过敏源关键词
    seeds = ["青霉素", "头孢", "磺胺", "阿司匹林", "布洛芬", "海鲜", "花粉", "芒果"]
    found = [s for s in seeds if s in text]
    return ",".join(found[:8])

def main(
    register_id, patient_info_text, confirmed_diagnosis_text,
    w4_suggestions_text, allergy_history, past_history, chief_complaint,
    w3_analysis_text, abnormal_indicators_text,
    preliminary_diagnosis_text, doctor_notes
):
    diagnosis = clean(confirmed_diagnosis_text)
    has_dx = "true" if diagnosis else "false"

    clinical_context = f"""
【患者信息】{clean(patient_info_text) or "未提供"}
【确诊病名】{diagnosis or "未提供"}
【W4诊断建议】{clean(w4_suggestions_text) or "无"}
【主诉】{clean(chief_complaint) or "未提供"}
【既往史】{clean(past_history) or "未提供"}
【过敏史】{clean(allergy_history) or "未提供"}
【初步诊断】{clean(preliminary_diagnosis_text) or "未提供"}
【W3分析】{clean(w3_analysis_text) or "无"}
【异常指标】{clean(abnormal_indicators_text) or "无"}
【医生备注】{clean(doctor_notes) or "无"}
""".strip()

    return {
        "register_id": clean(register_id),
        "clinical_context": clinical_context,
        "has_confirmed_diagnosis": has_dx,
        "allergy_keywords": extract_allergy_keywords(allergy_history),
    }
```

---

### 5.3：提取药品检索线索

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `提取药品检索线索` |
| **节点类型** | LLM |
| **输出模式** | 结构化输出（JSON Schema） |

**输入变量**：

| 变量名 | 来源 |
|--------|------|
| `clinical_context` | `整理输入.clinical_context` |
| `confirmed_diagnosis_text` | `开始.confirmed_diagnosis_text` |
| `allergy_keywords` | `整理输入.allergy_keywords` |

**结构化输出 Schema**：

```json
{
  "type": "object",
  "properties": {
    "drugKeywords": { "type": "array", "items": { "type": "string" } },
    "genericKeywords": { "type": "array", "items": { "type": "string" } },
    "categoryKeywords": { "type": "array", "items": { "type": "string" } },
    "indicationKeywords": { "type": "array", "items": { "type": "string" } },
    "negativeKeywords": { "type": "array", "items": { "type": "string" } },
    "reason": { "type": "string" }
  },
  "required": ["drugKeywords", "categoryKeywords", "indicationKeywords", "negativeKeywords", "reason"]
}
```

**System Prompt**：

```text
你是医院 AI 用药检索助手。根据确诊病名和临床上下文，提取用于查询 **NMPA 本位码药品库（drug_info）** 的检索线索。

规则：
1. drugKeywords：具体药品通用名/商品名关键词，2-6 个，中文为主（如阿莫西林、头孢克肟、布洛芬）。
2. genericKeywords：英文/generic 名，可为空数组。
3. categoryKeywords：药理分类或剂型分类，如「抗生素」「解热镇痛」「西药」「中成药」；进口中成药可用「中成药」。
4. indicationKeywords：适应症关键词，来自确诊病名及相关症状，2-4 个。
5. negativeKeywords：必须包含过敏史相关禁用药物类别/成分；无过敏则返回空数组。
6. 不要编造药品 ID 或 14 位本位码；只输出检索线索。
7. 输出必须符合 JSON Schema。
```

**User Prompt**：

```text
【确诊病名】
{{confirmed_diagnosis_text}}

【临床上下文】
{{clinical_context}}

【已提取过敏禁用关键词】
{{allergy_keywords}}

请提取药品检索线索。
```

**输出变量**（Dify LLM 结构化输出自动映射）：`drugKeywords`, `genericKeywords`, `categoryKeywords`, `indicationKeywords`, `negativeKeywords`, `reason`

---

### 5.4：构造药品检索请求

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `构造药品检索请求` |
| **节点类型** | Code |
| **语言** | Python 3 |

**输入变量**：

| 变量名 | 来源 |
|--------|------|
| `drugKeywords` | `提取药品检索线索.drugKeywords` |
| `genericKeywords` | `提取药品检索线索.genericKeywords` |
| `categoryKeywords` | `提取药品检索线索.categoryKeywords` |
| `indicationKeywords` | `提取药品检索线索.indicationKeywords` |
| `negativeKeywords` | `提取药品检索线索.negativeKeywords` |

**输出变量**：

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `search_request_body` | String | HTTP 节点用的 JSON 字符串 |

**节点代码**：

```python
import json

def norm_list(val, max_n=8):
    if not val:
        return []
    if isinstance(val, str):
        val = [v.strip() for v in val.replace("，", ",").split(",") if v.strip()]
    return [str(x).strip() for x in val if str(x).strip()][:max_n]

def main(drugKeywords, genericKeywords, categoryKeywords, indicationKeywords, negativeKeywords):
    body = {
        "drugKeywords": norm_list(drugKeywords),
        "genericKeywords": norm_list(genericKeywords),
        "categoryKeywords": norm_list(categoryKeywords),
        "indicationKeywords": norm_list(indicationKeywords),
        "negativeKeywords": norm_list(negativeKeywords),
        "limit": 40
    }
    return {"search_request_body": json.dumps(body, ensure_ascii=False)}
```

---

### 5.5：查询药品候选

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `查询药品候选` |
| **节点类型** | HTTP Request |

**输入变量**：

| 变量名 | 来源 |
|--------|------|
| `search_request_body` | `构造药品检索请求.search_request_body` |

**HTTP 配置（Dify 1.11.2）**：

| 项 | 值 |
|----|-----|
| Method | POST |
| URL | `http://172.17.0.1:8098/api/physician/internal/drugs/ai-search` |
| Headers | `Content-Type: application/json` |
| Headers | `Authorization: Bearer <INTERNAL_AI_TOKEN>`（在 Dify 环境变量或密钥中配置） |
| Body | `{{search_request_body}}`（raw JSON） |
| Timeout | 30s |

**失败处理（必配）**：

| 情况 | 处理 |
|------|------|
| `status_code` 非 2xx，或 body 解析失败 | 节点 6 将 `candidate_count` 置 0，走 **8B 兜底分支**，不要让工作流整体失败 |
| 超时 | 同上；可适当提高到 45s（本位码库较大时） |

**输出变量**：`body`（HTTP 响应体）、`status_code`

---

### 5.6：格式化候选药品

> **【v1.2 需在 Dify 控制台修改】** 在候选文本中展示库存，供 LLM 参考；`candidates_json` 须保留完整字段供 9A 截断数量。

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `格式化候选药品` |
| **节点类型** | Code |
| **语言** | Python 3 |

**输入变量**：

| 变量名 | 来源 |
|--------|------|
| `http_body` | `查询药品候选.body` |
| `register_id` | `整理输入.register_id` |
| `clinical_context` | `整理输入.clinical_context` |
| `confirmed_diagnosis_text` | `开始.confirmed_diagnosis_text` |
| `allergy_history` | `开始.allergy_history` |

**输出变量**：

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `candidate_count` | Number | 候选药品数量 |
| `candidates_text` | String | 供 LLM 阅读的候选列表文本（**含库存**） |
| `candidates_json` | String | 候选 JSON 字符串（供校验节点使用，**含 stockQuantity**） |
| `register_id` | String | 透传 |
| `clinical_context` | String | 透传 |
| `confirmed_diagnosis_text` | String | 透传 |
| `allergy_history` | String | 透传 |

**节点代码（v1.2 替换整段）**：

```python
import json

def main(http_body, register_id, clinical_context, confirmed_diagnosis_text, allergy_history):
    try:
        payload = json.loads(http_body) if isinstance(http_body, str) else http_body
    except Exception:
        payload = {}

    data = payload.get("data") or payload
    candidates = data.get("candidates") or data.get("data") or []
    if isinstance(candidates, dict):
        candidates = candidates.get("candidates", [])

    lines = []
    for i, c in enumerate(candidates[:40], 1):
        contra = str(c.get('contraindications') or '')[:80]
        instr = str(c.get('instructions') or '（库内暂无说明书摘要）')[:100]
        stock = c.get('stockQuantity', 0) or 0
        unit = c.get('unit') or '盒'
        lines.append(
            f"{i}. drugId={c.get('drugId')}; drugCode={c.get('drugCode','')}; "
            f"名称={c.get('drugName')}; 通用名={c.get('genericName','')}; "
            f"规格={c.get('specification','')}; 分类={c.get('category','')}; "
            f"可用库存={stock}{unit}; "
            f"适应症/用法={instr}; 禁忌={contra or '（未录入）'}"
        )

    return {
        "candidate_count": len(candidates),
        "candidates_text": "\n".join(lines) if lines else "（无候选药品）",
        "candidates_json": json.dumps(candidates, ensure_ascii=False),
        "register_id": str(register_id),
        "clinical_context": clinical_context,
        "confirmed_diagnosis_text": confirmed_diagnosis_text,
        "allergy_history": allergy_history or "",
    }
```

---

### 5.7：判断是否有候选药品

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `判断是否有候选药品` |
| **节点类型** | IF / 条件分支 |

**条件**：

| 分支 | 条件 | 走向 |
|------|------|------|
| IF | `格式化候选药品.candidate_count` **>** `0` | → 节点 8A |
| ELSE | 其他 | → 节点 8B |

---

### 5.8A：核心用药推荐

> **【v1.2 需在 Dify 控制台修改】** System Prompt 增加库存相关规则（第 4、9 条）。

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `核心用药推荐` |
| **节点类型** | LLM |
| **输出模式** | 结构化输出 |

**输入变量**：

| 变量名 | 来源 |
|--------|------|
| `clinical_context` | `格式化候选药品.clinical_context` |
| `confirmed_diagnosis_text` | `格式化候选药品.confirmed_diagnosis_text` |
| `candidates_text` | `格式化候选药品.candidates_text` |
| `allergy_history` | `格式化候选药品.allergy_history` |

**结构化输出 Schema**：

```json
{
  "type": "object",
  "properties": {
    "drugSuggestions": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "drugId": { "type": "number" },
          "drugName": { "type": "string" },
          "drugCode": { "type": "string" },
          "recommendUsage": { "type": "string" },
          "recommendQuantity": { "type": "number" },
          "confidence": { "type": "number" },
          "recommendationBasis": { "type": "string" },
          "cautionNotes": { "type": "string" },
          "sortOrder": { "type": "number" }
        },
        "required": ["drugId", "drugName", "recommendUsage", "recommendQuantity", "confidence", "recommendationBasis", "sortOrder"]
      }
    },
    "clinicalSummaryForDoctor": { "type": "string" },
    "allergyWarnings": { "type": "array", "items": { "type": "string" } }
  },
  "required": ["drugSuggestions", "clinicalSummaryForDoctor", "allergyWarnings"]
}
```

**System Prompt（v1.2 替换）**：

```text
你是医院 AI 用药推荐助手。你只能从【候选药品列表】中选择 1-5 种药品推荐给医生。

硬性规则：
1. drugId、drugCode 必须来自候选列表，禁止编造。
2. 有过敏史时，排除相关药物并在 allergyWarnings 中说明；若候选禁忌字段为「未录入」，须根据药名/通用名常识排除（如青霉素过敏排除阿莫西林、氨苄西林等）。
3. recommendUsage 格式：给药途径 + 单次剂量 + 频次 + 特殊说明（如饭后）；候选无说明书摘要时，按该药常规临床用法填写，并在 cautionNotes 注明「用法参考通用知识，请以院内说明书为准」。
4. recommendQuantity 为整数，表示盒/瓶数量；不得超过候选列表中该药的「可用库存」；合理上限 1-5。
5. confidence 为 0-100 的数值。
6. 优先推荐与确诊病名匹配的一线用药；可推荐主药 + 对症药，总数不超过 5。
7. 同一通用名多规格/多厂家时，优先选列表中排序靠前且规格常见的条目。
8. 你是辅助参考，不替代医生决策。
9. 候选列表已排除零库存药品；若库存较低，在 cautionNotes 中提醒「库存紧张」。
```

**User Prompt**：

```text
【确诊病名】{{confirmed_diagnosis_text}}
【过敏史】{{allergy_history}}

【临床上下文】
{{clinical_context}}

【候选药品列表（只能从这里选）】
{{candidates_text}}

请推荐 1-5 种药品。
```

---

### 5.9A：校验最终输出

> **【v1.2 需在 Dify 控制台修改 — 最重要】** 用 Code 节点**确定性**截断 `recommendQuantity`，过滤无库存项；不要仅依赖 LLM 遵守库存规则。

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `校验最终输出` |
| **节点类型** | Code |
| **语言** | Python 3 |

**输入变量**：

| 变量名 | 来源 |
|--------|------|
| `register_id` | `格式化候选药品.register_id` |
| `drugSuggestions` | `核心用药推荐.drugSuggestions` |
| `clinicalSummaryForDoctor` | `核心用药推荐.clinicalSummaryForDoctor` |
| `allergyWarnings` | `核心用药推荐.allergyWarnings` |
| `candidates_json` | `格式化候选药品.candidates_json` |

**输出变量**（映射到 End 10A）：

| 变量名 | 类型 |
|--------|------|
| `status` | String |
| `registerId` | String |
| `suggestions` | Array[Object] |
| `fallbackSuggestions` | Array[Object] |
| `clinicalSummaryForDoctor` | String |
| `allergyWarnings` | Array[String] |
| `searchAdvice` | String |

**节点代码（v1.2 替换整段）**：

```python
import json

def main(register_id, drugSuggestions, clinicalSummaryForDoctor, allergyWarnings, candidates_json):
    try:
        candidates = json.loads(candidates_json)
    except Exception:
        candidates = []

    valid_ids = {c.get("drugId") for c in candidates}
    code_by_id = {c.get("drugId"): c.get("drugCode") for c in candidates}
    stock_by_id = {c.get("drugId"): int(c.get("stockQuantity") or 0) for c in candidates}
    threshold_by_id = {c.get("drugId"): int(c.get("lowStockThreshold") or 20) for c in candidates}
    unit_by_id = {c.get("drugId"): c.get("unit") or "盒" for c in candidates}

    cleaned = []
    for i, s in enumerate(drugSuggestions or [], 1):
        did = s.get("drugId")
        if did not in valid_ids:
            continue
        stock = stock_by_id.get(did, 0)
        if stock <= 0:
            continue

        row = dict(s)
        row["sortOrder"] = row.get("sortOrder") or i
        if not row.get("drugCode"):
            row["drugCode"] = code_by_id.get(did) or ""

        qty = row.get("recommendQuantity") or 1
        try:
            qty = int(qty)
        except Exception:
            qty = 1
        if qty < 1:
            qty = 1
        row["recommendQuantity"] = min(qty, stock, 5)

        threshold = threshold_by_id.get(did, 20)
        if stock <= threshold:
            unit = unit_by_id.get(did, "盒")
            note = f"库存紧张（当前可用 {stock}{unit}）"
            existing = str(row.get("cautionNotes") or "").strip()
            row["cautionNotes"] = note if not existing else f"{existing}；{note}"

        cleaned.append(row)

    status = "success" if cleaned else "fallback"
    return {
        "status": status,
        "registerId": str(register_id),
        "suggestions": cleaned,
        "fallbackSuggestions": [],
        "clinicalSummaryForDoctor": clinicalSummaryForDoctor or "",
        "allergyWarnings": allergyWarnings or [],
        "searchAdvice": "" if cleaned else "候选均无可用库存或校验未通过，请手动搜索选药",
    }
```

---

### 5.8B：无候选兜底建议

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `无候选兜底建议` |
| **节点类型** | LLM |
| **输出模式** | 结构化输出 |

**输入变量**：`clinical_context`、`confirmed_diagnosis_text`、`allergy_history`（均来自节点 6 透传）

**结构化输出 Schema**：

```json
{
  "type": "object",
  "properties": {
    "fallbackSuggestions": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "drugName": { "type": "string" },
          "recommendUsage": { "type": "string" },
          "recommendationBasis": { "type": "string" },
          "note": { "type": "string" }
        },
        "required": ["drugName", "recommendationBasis", "note"]
      }
    },
    "clinicalSummaryForDoctor": { "type": "string" },
    "searchAdvice": { "type": "string" }
  },
  "required": ["fallbackSuggestions", "clinicalSummaryForDoctor", "searchAdvice"]
}
```

**System Prompt**：

```text
本地药品库未召回候选。请根据确诊和临床上下文，给出 1-3 条非结构化的用药方向建议。
注意：
1. 不要输出 drugId（因为库中无匹配）。
2. 必须提醒医生手动搜索选药。
3. 尊重过敏史，在建议中排除禁忌药物。
```

---

### 5.9B：格式化兜底输出

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `格式化兜底输出` |
| **节点类型** | Code |

**输入**：`register_id`、`fallbackSuggestions`、`clinicalSummaryForDoctor`、`searchAdvice`、`allergy_history`

**输出**（映射到 End 10B）：

```python
def main(register_id, fallbackSuggestions, clinicalSummaryForDoctor, searchAdvice, allergy_history):
    warnings = []
    ah = (allergy_history or "").strip()
    if ah and ah not in ("无", "否认"):
        warnings.append(f"患者过敏史：{ah}")
    return {
        "status": "fallback",
        "registerId": str(register_id),
        "suggestions": [],
        "fallbackSuggestions": fallbackSuggestions or [],
        "clinicalSummaryForDoctor": clinicalSummaryForDoctor or "",
        "allergyWarnings": warnings,
        "searchAdvice": searchAdvice or "",
    }
```

---

### 5.10A / 10B：结束

| 配置项 | 值 |
|--------|-----|
| **节点名称** | `结束-成功` / `结束-兜底` |
| **节点类型** | End |
| **输出模式** | 结构化多字段输出（**不使用** `output_json` 单字符串） |

**输出变量映射**：

| End 变量 | 10A 来源 | 10B 来源 |
|----------|----------|----------|
| `status` | `校验最终输出.status` | `格式化兜底输出.status` |
| `registerId` | `校验最终输出.registerId` | `格式化兜底输出.registerId` |
| `suggestions` | `校验最终输出.suggestions` | `[]` |
| `fallbackSuggestions` | `[]` | `格式化兜底输出.fallbackSuggestions` |
| `clinicalSummaryForDoctor` | 同左 | 同左 |
| `allergyWarnings` | 同左 | 同左 |
| `searchAdvice` | `""` | `格式化兜底输出.searchAdvice` |

---

## 6. 测试用例

> 执行前确认 **第 3 节** 药品库已导入远程库，且常见病种说明书已补全。

| 编号 | 场景 | 输入要点 | 预期 |
|------|------|----------|------|
| TC-001 | 常见病有库 | 确诊「急性上呼吸道感染」，无过敏 | `status=success`，1-5 条带 `drugId`；`drugCode` 为 14 位本位码 |
| TC-002 | 过敏过滤 | 过敏史「青霉素过敏」 | 推荐不含阿莫西林/氨苄西林等；`allergyWarnings` 非空 |
| TC-003 | 库无匹配 | 极冷门确诊（或故意错误关键词） | `status=fallback`，`fallbackSuggestions` 有文本建议 |
| TC-004 | 无确诊 | `confirmed_diagnosis_text` 为空 | 后端 400，前端按钮禁用 |
| TC-005 | 进口品种 | 确诊与胃肠不适相关，库含和胃整肠丸等 | 候选或推荐可出现进口中成药（`approval_number` 含 J/ZJ） |
| TC-006 | HTTP 失败 | 临时关闭 internal API 或错误 Token | 走 8B 兜底，工作流不整体报错 |
| TC-007 | 库存约束 | 将某常用药 `stock_quantity` 设为 0 后跑 TC-001 | HTTP 候选不含该药；若 LLM 仍输出则 9A 丢弃；采纳时前端提示无库存 |

**病种包自检**（导入后建议人工 spot-check）：

| 确诊示例 | 库内应能召回（示例通用名） |
|----------|---------------------------|
| 急性上呼吸道感染 | 阿莫西林、头孢克肟、布洛芬、复方氨酚烷胺 |
| 高血压 | 硝苯地平、氨氯地平、缬沙坦、氯沙坦 |
| 2 型糖尿病 | 二甲双胍 |
| 反流性食管炎 | 奥美拉唑 |

---

## 7. 关联代码与数据

| 组件 | 路径 / 说明 |
|------|-------------|
| InputBuilder | `physician-service/.../ai/W5DifyInputBuilder.java` |
| OutputMapper | `physician-service/.../ai/DifyW5OutputMapper.java` |
| 药品检索 | `ai-catalog-service` + `POST /internal/drugs/ai-search`（**v1.2**：过滤零库存，返回 `stockQuantity`） |
| Pipeline | `PhysicianAiPipelineService.runW5`（**v1.2**：`sanitizeW5Suggestions` 落库前二次校验） |
| Fallback | `FallbackWorkflowEngine.runW5`（**v1.2**：跳过零库存） |
| 前端 | `W5PrescriptionPanel.vue` + `PhysicianPrescriptionPage.vue`（**v1.2**：采纳时查库存） |
| 药品表结构 | `docker/init-db/migrate_to_partner.sql` → `drug_info` |
| 库存初始化 | `docker/init-db/migrate_023_init_drug_stock_from_catalog.sql` |
| 接入 handoff | `WF-06_智能荐药_W5接入实施规格.md` |
| **药品 ETL** | 待新增：`migrate_023_nmpa_drug_import.sql` 或独立 Python ETL（国产+进口 Excel → 远程 `drug_info`） |

### 7.1 Dify 控制台 v1.2 同步清单

编排完成后若已发布旧版，请按序更新并**重新发布**工作流：

- [ ] 重新部署 `ai-catalog-service`（8098），确认 smoke-test 响应含 `stockQuantity`
- [ ] **节点 5.6** `格式化候选药品`：替换为 v1.2 Python 代码（候选文本含「可用库存=」）
- [ ] **节点 8A** `核心用药推荐`：替换 System Prompt（第 4、9 条库存规则）
- [ ] **节点 9A** `校验最终输出`：替换为 v1.2 Python 代码（`min(qty, stock, 5)` + 低库存提示）
- [ ] 用 TC-001 / TC-007 回归
