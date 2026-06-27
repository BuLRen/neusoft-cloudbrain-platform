# WF-05: 门诊确诊疾病 Dify 工作流设计文档

> 版本：v1.0  
> 日期：2026-06-26  
> 适配平台：Dify 1.11.2  
> 状态：设计完成，待在 Dify 中编排  
> 核心目标：根据门诊病历、初步诊断、检查检验结果和本地 ICD/疾病库，为医生推荐 3-5 个最可能的确诊疾病。

---

## 1. 工作流定位

本工作流用于门诊医生工作站的「门诊确诊」页面，负责完成 AI 辅助确诊推荐。

工作流只负责：

- 整理本次就诊的临床上下文。
- 提取疾病检索线索。
- 通过 HTTP 节点调用后端 ICD/疾病库检索接口。
- 从候选诊断代码中选择最可能的疾病。
- 输出可写入 `ai_diagnosis_suggestion` 表的结构化结果。

工作流不负责：

- 直接连接数据库。
- 直接写入 `medical_record` 或 `ai_diagnosis_suggestion`。
- 替医生做最终确诊。
- 使用完整 ICD 库作为 LLM 上下文。

---

## 2. 关键设计原则

### 2.1 ICD/疾病库处理方式

不要把完整 ICD 库传入 Dify 工作流。

推荐方式：

```text
权威 ICD/疾病库 PDF/Excel
  ↓
清洗导入后端数据库
  ↓
Dify 提取检索关键词和 ICD 前缀
  ↓
HTTP 节点调用后端疾病检索 API
  ↓
只返回 30-80 条候选诊断给 LLM
  ↓
LLM 只能从候选诊断中选择
```

这样可以避免 token 过大、候选过多、编码幻觉和后续写库失败。

### 2.2 Start 节点输入策略

Dify 1.11.2 的开始节点如果直接接收复杂 JSON 对象，可能出现卡死或解析不稳定。因此本工作流约定：

**开始节点全部使用 string 类型输入。**

复杂数据由后端提前拼接成文本，工作流第一步使用代码节点统一清洗和整理。

---

## 3. 工作流总览

```text
1. 开始节点
   ↓
2. 整理输入（代码节点）
   ↓
3. 提取疾病检索线索（LLM 节点）
   ↓
4. 构造疾病检索请求（代码节点）
   ↓
5. 查询 ICD 候选诊断（HTTP 节点）
   ↓
6. 格式化候选诊断（代码节点）
   ↓
7. 判断是否有候选诊断（条件分支）
   ├─ 有候选 → 8A. 核心确诊推荐（LLM） → 9A. 校验最终输出（代码） → 10A. 结束
   └─ 无候选 → 8B. 无候选兜底建议（LLM） → 9B. 格式化兜底输出（代码） → 10B. 结束
```

### 3.1 LLM 节点统一约定

本工作流共有 3 个 LLM 节点，**全部使用结构化输出**，不使用 `text` + 手动 JSON 解析：

| 节点 | 名称 | 结构化输出字段 |
|---|---|---|
| 节点 3 | 提取疾病检索线索 | `diseaseKeywords`、`symptomKeywords`、`icdPrefixes`、`categoryKeywords`、`negativeKeywords`、`reason` |
| 节点 8A | 核心确诊推荐 | `diagnosisSuggestions`、`clinicalSummaryForDoctor`、`differentialDiagnosis`、`warningSigns` |
| 节点 8B | 无候选兜底建议 | `fallbackSuggestions`、`clinicalSummaryForDoctor`、`searchAdvice` |

两个 End 节点（10A、10B）同样使用结构化多字段输出，字段名与类型完全一致，详见第 6.1 节。

---

## 4. 后端 ICD/疾病库要求

### 4.1 推荐数据库字段

从国家 ICD PDF 表格导入时，建议至少保留以下字段：

| 字段 | 说明 | 示例 |
|---|---|---|
| `id` | 数据库主键 | `1001` |
| `chapter_no` | 章 | `1` |
| `chapter_code_range` | 章代码范围 | `A00-B99` |
| `chapter_name` | 章名称 | `某些传染病和寄生虫病` |
| `section_code_range` | 节代码范围 | `A00-A09` |
| `section_name` | 节名称 | `肠道传染病` |
| `category_code` | 类目代码 | `A00` |
| `category_name` | 类目名称 | `霍乱` |
| `subcategory_code` | 亚目代码 | `A00.0` |
| `subcategory_name` | 亚目名称 | `霍乱，由于O1群霍乱弧菌，霍乱生物型所致` |
| `diagnosis_code` | 诊断代码 | `A00.000x001` |
| `diagnosis_name` | 诊断名称 | `古典生物型霍乱` |
| `icd_prefix` | ICD 前三位 | `A00` |
| `search_text` | 检索拼接字段 | `霍乱 古典生物型霍乱 A00 A00.0 A00.000x001 肠道传染病` |
| `source_version` | 来源版本 | `ICD-10医保2.0版` |
| `enabled` | 是否启用 | `true` |

如果暂时兼容现有 `disease` 表，可以映射为：

| 现有字段 | 建议映射 |
|---|---|
| `disease_code` | `diagnosis_code` |
| `disease_name` | `diagnosis_name` |
| `diseaseicd` | `diagnosis_code` |
| `disease_category` | `chapter_name / section_name / category_name` 的组合 |

### 4.2 后端检索接口

工作流需要调用一个后端内部接口：

```http
POST /api/physician/diseases/ai-search
Content-Type: application/json
```

请求体：

```json
{
  "diseaseKeywords": ["肺炎", "支气管炎"],
  "symptomKeywords": ["咳嗽", "发热"],
  "icdPrefixes": ["J18", "J20"],
  "categoryKeywords": ["呼吸系统疾病", "感染"],
  "negativeKeywords": ["肿瘤"],
  "limit": 80
}
```

响应体：

```json
{
  "data": [
    {
      "id": 1001,
      "diagnosisCode": "J18.900",
      "diagnosisName": "肺炎",
      "categoryCode": "J18",
      "categoryName": "肺炎",
      "subcategoryCode": "J18.9",
      "subcategoryName": "未特指的肺炎",
      "chapterName": "呼吸系统疾病",
      "sectionName": "呼吸系统其他疾病",
      "matchScore": 96
    }
  ]
}
```

后端检索建议优先级：

1. 初步诊断已有关联疾病或 ICD 前缀时，优先召回同前缀疾病。
2. 用 `diseaseKeywords` 匹配 `diagnosis_name`、`category_name`、`subcategory_name`。
3. 用 `categoryKeywords` 匹配 `chapter_name`、`section_name`、`category_name`。
4. 用 `search_text` 做综合模糊匹配。
5. 用 `negativeKeywords` 降权或排除明显不相关结果。
6. 返回结果按 `matchScore` 降序，最多 80 条。

---

## 5. 工作流输入

### 5.1 Start 节点输入变量

所有变量类型均设置为 `String`。

| 变量名 | 必填 | 说明 | 示例 |
|---|---|---|---|
| `register_id` | 是 | 就诊/挂号 ID | `123` |
| `patient_info_text` | 否 | 患者基本信息，建议包含年龄、性别、科室 | `男，45岁，呼吸内科` |
| `chief_complaint` | 否 | 主诉 | `咳嗽、发热3天` |
| `present_illness` | 否 | 现病史 | `3天前受凉后出现咳嗽...` |
| `past_history` | 否 | 既往史/慢病史 | `高血压病史5年` |
| `allergy_history` | 否 | 过敏史 | `青霉素过敏` |
| `preliminary_diagnosis_text` | 否 | 初步诊断文本 | `考虑肺炎可能` |
| `preliminary_diseases_text` | 否 | 初步诊断已关联疾病，后端拼成文本 | `J18.900|肺炎|呼吸系统疾病` |
| `check_results_text` | 否 | 检查结果文本 | `胸部CT提示右下肺炎症` |
| `inspection_results_text` | 否 | 检验结果文本 | `白细胞升高，CRP升高` |
| `w3_analysis_text` | 否 | W3 检查检验 AI 解读 | `炎症指标升高，结合影像支持感染` |
| `abnormal_indicators_text` | 否 | 异常指标文本 | `WBC 12.5↑; CRP 38↑` |
| `ai_previsit_summary` | 否 | AI 预问诊摘要 | `患者自述发热最高38.6℃...` |
| `doctor_notes` | 否 | 医生补充说明 | `听诊右下肺湿啰音` |

### 5.2 `preliminary_diseases_text` 推荐格式

后端建议用一行一个疾病：

```text
J18.900|肺炎|呼吸系统疾病
J20.900|急性支气管炎|呼吸系统疾病
```

---

## 6. 工作流输出

### 6.1 End 节点统一结构化输出

两个结束节点（10A 成功分支、10B 兜底分支）使用 **相同的输出字段和类型**，便于后端统一解析，不再使用 `output_json` 字符串。

| 变量名 | 类型 | 说明 |
|---|---|---|
| `status` | String | `success` / `empty` / `fallback` |
| `registerId` | String | 就诊 ID |
| `suggestions` | Array[Object] | 可写库的确诊推荐列表（成功分支有值） |
| `fallbackSuggestions` | Array[Object] | 未匹配疾病库的兜底建议（兜底分支有值） |
| `clinicalSummaryForDoctor` | String | 给医生的综合判断摘要 |
| `differentialDiagnosis` | Array[Object] | 鉴别诊断列表（成功分支） |
| `warningSigns` | Array[String] | 危险信号列表（成功分支） |
| `searchAdvice` | String | 建议进一步搜索的关键词（兜底分支） |

### 6.2 Dify End 节点配置步骤

1. 打开结束节点 → **输出变量**。
2. 按上表添加 8 个输出变量，并设置对应类型。
3. 成功分支（10A）将各变量映射到节点 9A 的同名字段。
4. 兜底分支（10B）将各变量映射到节点 9B 的同名字段。
5. 不要配置 `output_json` 字符串输出。

### 6.3 成功分支输出示例

```json
{
  "status": "success",
  "registerId": "123",
  "suggestions": [
    {
      "registerId": "123",
      "diseaseId": 1001,
      "diagnosisCode": "J18.900",
      "diagnosisName": "肺炎",
      "recommendIcd": "J18.900",
      "probability": 0.72,
      "riskLevel": "medium",
      "diagnosisBasis": "患者咳嗽、发热3天，胸部CT提示右下肺炎症，白细胞和CRP升高，支持肺炎诊断。",
      "treatmentDirection": "建议抗感染治疗，结合病原学结果调整用药。",
      "sortOrder": 1,
      "isAdopted": false
    }
  ],
  "fallbackSuggestions": [],
  "clinicalSummaryForDoctor": "患者以咳嗽发热就诊，检查检验支持呼吸道感染，优先考虑肺炎。",
  "differentialDiagnosis": [
    {
      "diagnosisName": "急性支气管炎",
      "reason": "症状相似，但影像学肺部炎症支持肺炎可能更高。"
    }
  ],
  "warningSigns": [
    "持续高热",
    "呼吸困难",
    "血氧下降"
  ],
  "searchAdvice": ""
}
```

### 6.4 无候选兜底分支输出示例

```json
{
  "status": "fallback",
  "registerId": "123",
  "suggestions": [],
  "fallbackSuggestions": [
    {
      "diagnosisName": "肺炎",
      "estimatedIcdPrefix": "J18",
      "probability": 0.6,
      "riskLevel": "medium",
      "diagnosisBasis": "症状和检查结果支持呼吸系统感染。",
      "note": "未匹配到本地疾病库，需要医生手动确认。"
    }
  ],
  "clinicalSummaryForDoctor": "疾病库未召回候选疾病，仅提供非结构化诊断方向。",
  "differentialDiagnosis": [],
  "warningSigns": [],
  "searchAdvice": "建议使用关键词：肺炎、支气管炎、呼吸道感染。"
}
```

---

## 7. 节点详细设计

## 节点 1：开始节点

| 配置项 | 值 |
|---|---|
| 节点名称 | `开始` |
| 节点类型 | Start |
| 输入变量 | 见第 5.1 节 |
| 输出变量 | Start 节点所有输入变量 |

注意：所有输入变量均使用 `String` 类型，不使用 Object 或 Array。

---

## 节点 2：整理输入

| 配置项 | 值 |
|---|---|
| 节点名称 | `整理输入` |
| 节点类型 | Code |
| 语言 | Python 3 |

### 输入变量

| 变量名 | 来源 |
|---|---|
| `register_id` | Start.register_id |
| `patient_info_text` | Start.patient_info_text |
| `chief_complaint` | Start.chief_complaint |
| `present_illness` | Start.present_illness |
| `past_history` | Start.past_history |
| `allergy_history` | Start.allergy_history |
| `preliminary_diagnosis_text` | Start.preliminary_diagnosis_text |
| `preliminary_diseases_text` | Start.preliminary_diseases_text |
| `check_results_text` | Start.check_results_text |
| `inspection_results_text` | Start.inspection_results_text |
| `w3_analysis_text` | Start.w3_analysis_text |
| `abnormal_indicators_text` | Start.abnormal_indicators_text |
| `ai_previsit_summary` | Start.ai_previsit_summary |
| `doctor_notes` | Start.doctor_notes |

### 输出变量

| 变量名 | 类型 | 说明 |
|---|---|---|
| `register_id` | String | 清洗后的就诊 ID |
| `clinical_context` | String | 汇总后的临床上下文 |
| `prior_icd_prefixes` | String | 从初步诊断中提取的 ICD 前缀，逗号分隔 |

### 可直接粘贴代码

```python
import re

def clean(value):
    if value is None:
        return ""
    return str(value).strip()

def extract_icd_prefixes(text):
    text = clean(text).upper()
    # 匹配常见 ICD 格式，例如 J18.900、E11.900、I10.x00、A00.000x001
    codes = re.findall(r'\b[A-Z][0-9]{2}(?:\.[A-Z0-9]+)?\b', text)
    prefixes = []
    for code in codes:
        prefix = code[:3]
        if prefix not in prefixes:
            prefixes.append(prefix)
    return prefixes[:10]

def main(
    register_id,
    patient_info_text,
    chief_complaint,
    present_illness,
    past_history,
    allergy_history,
    preliminary_diagnosis_text,
    preliminary_diseases_text,
    check_results_text,
    inspection_results_text,
    w3_analysis_text,
    abnormal_indicators_text,
    ai_previsit_summary,
    doctor_notes
):
    preliminary_text = clean(preliminary_diagnosis_text)
    preliminary_diseases = clean(preliminary_diseases_text)
    icd_prefixes = extract_icd_prefixes(preliminary_text + "\n" + preliminary_diseases)

    clinical_context = f"""
【患者基本信息】
{clean(patient_info_text) or "未提供"}

【主诉】
{clean(chief_complaint) or "未提供"}

【现病史】
{clean(present_illness) or "未提供"}

【既往史/慢病史】
{clean(past_history) or "未提供"}

【过敏史】
{clean(allergy_history) or "未提供"}

【AI预问诊摘要】
{clean(ai_previsit_summary) or "无"}

【初步诊断文本】
{preliminary_text or "无"}

【初步诊断关联疾病】
{preliminary_diseases or "无"}

【检查结果】
{clean(check_results_text) or "无"}

【检验结果】
{clean(inspection_results_text) or "无"}

【W3检查检验解读】
{clean(w3_analysis_text) or "无"}

【异常指标】
{clean(abnormal_indicators_text) or "无"}

【医生补充说明】
{clean(doctor_notes) or "无"}
""".strip()

    return {
        "register_id": clean(register_id),
        "clinical_context": clinical_context,
        "prior_icd_prefixes": ",".join(icd_prefixes)
    }
```

---

## 节点 3：提取疾病检索线索

| 配置项 | 值 |
|---|---|
| 节点名称 | `提取疾病检索线索` |
| 节点类型 | LLM |
| 建议温度 | `0.2` |
| 输出方式 | **结构化输出（Structured Output）** |

### 为什么使用结构化输出

Dify 1.11.2 的 LLM 节点支持直接定义 JSON Schema 输出，比让模型输出 `text` 再手动 `json.loads` 更稳定：

- 避免模型输出 Markdown 代码块或多余解释文字。
- 下游节点可直接引用 `diseaseKeywords`、`icdPrefixes` 等字段，无需再解析字符串。
- 减少节点 4 的容错代码，降低工作流失败率。

### Dify 配置步骤

1. 打开 LLM 节点 → **输出** → 选择 **结构化输出**。
2. 将下方 JSON Schema 粘贴到 Schema 编辑器中。
3. 确认各输出字段类型与 Schema 一致。
4. 保存后，Dify 会自动将结构化字段暴露为下游可引用变量。

### 结构化输出 Schema

```json
{
  "type": "object",
  "properties": {
    "diseaseKeywords": {
      "type": "array",
      "items": { "type": "string" },
      "description": "疾病关键词，3-8个，使用中文疾病名或疾病族名"
    },
    "symptomKeywords": {
      "type": "array",
      "items": { "type": "string" },
      "description": "症状关键词或异常指标关键词"
    },
    "icdPrefixes": {
      "type": "array",
      "items": { "type": "string" },
      "description": "ICD前三位前缀，例如 J18、E11、I10，不超过10个"
    },
    "categoryKeywords": {
      "type": "array",
      "items": { "type": "string" },
      "description": "疾病系统或分类关键词"
    },
    "negativeKeywords": {
      "type": "array",
      "items": { "type": "string" },
      "description": "可明显排除的疾病关键词"
    },
    "reason": {
      "type": "string",
      "description": "一句话说明检索方向"
    }
  },
  "required": [
    "diseaseKeywords",
    "symptomKeywords",
    "icdPrefixes",
    "categoryKeywords",
    "negativeKeywords",
    "reason"
  ]
}
```

### 输入变量

| 变量名 | 来源 |
|---|---|
| `clinical_context` | 节点 2.clinical_context |
| `prior_icd_prefixes` | 节点 2.prior_icd_prefixes |

### 输出变量

启用结构化输出后，LLM 节点直接输出以下字段（不再使用 `text`）：

| 变量名 | 类型 | 说明 |
|---|---|---|
| `diseaseKeywords` | Array[String] | 疾病关键词 |
| `symptomKeywords` | Array[String] | 症状关键词 |
| `icdPrefixes` | Array[String] | ICD 前三位前缀 |
| `categoryKeywords` | Array[String] | 疾病分类关键词 |
| `negativeKeywords` | Array[String] | 排除关键词 |
| `reason` | String | 检索方向说明 |

下游节点 4 直接引用这些字段，无需再做 JSON 解析。

### System Prompt

```text
你是一名临床医学诊断助手，熟悉 ICD-10 疾病分类。

你的任务不是直接做最终诊断，而是从患者临床资料中提取用于本地疾病库检索的关键词、ICD 前缀和疾病类别。

要求：
1. 不要编造完整 ICD 编码，只能输出 ICD 前三位前缀，例如 J18、E11、I10。
2. 关键词应尽量使用中文医学术语，便于匹配本地疾病库。
3. 如果临床信息不足，数组字段可以返回空数组，但必须保证字段存在。
4. reason 用一句话概括检索方向。
```

### User Prompt

```text
请根据以下临床资料，提取疾病库检索线索。

【临床资料】
{{#整理输入.clinical_context#}}

【已知初步诊断 ICD 前缀】
{{#整理输入.prior_icd_prefixes#}}

字段填写要求：
1. diseaseKeywords 使用中文疾病名或疾病族名，例如 肺炎、支气管炎、糖尿病、冠心病，数量 3-8 个。
2. symptomKeywords 使用患者关键症状或异常指标，例如 咳嗽、发热、胸痛、白细胞升高。
3. icdPrefixes 优先保留已知初步诊断 ICD 前缀，也可以补充你根据资料推断出的前缀，数量不超过 10 个。
4. categoryKeywords 使用疾病系统或分类关键词，例如 呼吸系统疾病、循环系统疾病、感染、内分泌疾病。
5. negativeKeywords 填写可明显排除的疾病类别或关键词。
6. reason 用一句话说明本次检索方向。
```

---

## 节点 4：构造疾病检索请求

| 配置项 | 值 |
|---|---|
| 节点名称 | `构造疾病检索请求` |
| 节点类型 | Code |
| 语言 | Python 3 |

### 输入变量

| 变量名 | 来源 |
|---|---|
| `disease_keywords` | 节点 3.diseaseKeywords |
| `symptom_keywords` | 节点 3.symptomKeywords |
| `icd_prefixes_llm` | 节点 3.icdPrefixes |
| `category_keywords` | 节点 3.categoryKeywords |
| `negative_keywords` | 节点 3.negativeKeywords |
| `prior_icd_prefixes` | 节点 2.prior_icd_prefixes |

> 节点 3 已启用结构化输出，此处直接接收数组字段，不再解析 `text` JSON 字符串。

### 输出变量

| 变量名 | 类型 | 说明 |
|---|---|---|
| `disease_search_body` | String | HTTP 节点请求体 JSON 字符串 |
| `keywords_text` | String | 疾病关键词，逗号分隔 |
| `icd_prefixes_text` | String | ICD 前缀，逗号分隔 |

### 可直接粘贴代码

```python
import json
import re

def as_list(value):
    # Dify 结构化输出通常为 list；兼容空值和字符串兜底
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return []
        try:
            parsed = json.loads(text)
            if isinstance(parsed, list):
                return [str(item).strip() for item in parsed if str(item).strip()]
        except Exception:
            pass
        return [item.strip() for item in re.split(r"[,，;；\n]", text) if item.strip()]
    return []

def normalize_icd_prefix(prefix):
    prefix = str(prefix or "").strip().upper()
    match = re.match(r"^[A-Z][0-9]{2}", prefix)
    if not match:
        return ""
    return match.group(0)

def main(
    disease_keywords,
    symptom_keywords,
    icd_prefixes_llm,
    category_keywords,
    negative_keywords,
    prior_icd_prefixes
):
    disease_keywords = as_list(disease_keywords)[:8]
    symptom_keywords = as_list(symptom_keywords)[:8]
    category_keywords = as_list(category_keywords)[:6]
    negative_keywords = as_list(negative_keywords)[:8]

    prior_prefix_list = as_list(prior_icd_prefixes)
    llm_prefix_list = as_list(icd_prefixes_llm)

    icd_prefixes = []
    for prefix in prior_prefix_list + llm_prefix_list:
        normalized = normalize_icd_prefix(prefix)
        if normalized and normalized not in icd_prefixes:
            icd_prefixes.append(normalized)
    icd_prefixes = icd_prefixes[:10]

    request_body = {
        "diseaseKeywords": disease_keywords,
        "symptomKeywords": symptom_keywords,
        "icdPrefixes": icd_prefixes,
        "categoryKeywords": category_keywords,
        "negativeKeywords": negative_keywords,
        "limit": 80
    }

    return {
        "disease_search_body": json.dumps(request_body, ensure_ascii=False),
        "keywords_text": ",".join(disease_keywords),
        "icd_prefixes_text": ",".join(icd_prefixes)
    }
```

---

## 节点 5：查询 ICD 候选诊断

| 配置项 | 值 |
|---|---|
| 节点名称 | `查询ICD候选诊断` |
| 节点类型 | HTTP Request |
| 方法 | POST |
| URL | `http://后端服务地址/api/physician/diseases/ai-search` |
| Body 类型 | raw JSON |

### 输入变量

| 变量名 | 来源 |
|---|---|
| `disease_search_body` | 节点 4.disease_search_body |

### Headers

```text
Content-Type: application/json
```

如后端需要内部鉴权，可增加：

```text
Authorization: Bearer ${INTERNAL_AI_TOKEN}
```

### Body

在 Dify HTTP 节点中使用 raw body，内容直接填写：

```text
{{#构造疾病检索请求.disease_search_body#}}
```

不要再额外包一层 JSON 字符串。

### 输出变量

Dify HTTP 节点通常会输出响应体文本。后续节点将响应体作为：

| 变量名 | 类型 | 说明 |
|---|---|---|
| `disease_search_response` | String | 后端返回的候选诊断 JSON |

---

## 节点 6：格式化候选诊断

| 配置项 | 值 |
|---|---|
| 节点名称 | `格式化候选诊断` |
| 节点类型 | Code |
| 语言 | Python 3 |

### 输入变量

| 变量名 | 来源 |
|---|---|
| `body` | 节点 5（HTTP 请求）→ **body**（Dify 1.11 HTTP 节点默认输出变量名） |

> 注意：输入变量名必须与 `main()` 参数名一致。若 Dify 里映射的是 HTTP 节点的 `body`，则此处填 `body`，不要写成 `disease_search_response`。

### 输出变量

| 变量名 | 类型 | 说明 |
|---|---|---|
| `candidate_diagnoses_text` | String | 给 LLM 阅读的候选诊断清单 |
| `candidate_count` | Number | 候选数量 |
| `candidate_id_code_map_json` | String | 候选 ID 和诊断代码映射 |

### 可直接粘贴代码

```python
import json

def safe_json_loads(text):
    if not text:
        return {}
    try:
        return json.loads(str(text))
    except Exception:
        return {}

def pick_response_data(response):
    if isinstance(response.get("data"), list):
        return response.get("data")
    if isinstance(response.get("data"), dict) and isinstance(response["data"].get("data"), list):
        return response["data"]["data"]
    if isinstance(response.get("result"), list):
        return response.get("result")
    return []

def main(body):
    response = safe_json_loads(body)
    diagnoses = pick_response_data(response)

    if not isinstance(diagnoses, list):
        diagnoses = []

    diagnoses = diagnoses[:80]

    id_code_map = {}
    lines = []

    for index, item in enumerate(diagnoses, 1):
        disease_id = item.get("id")
        diagnosis_code = str(item.get("diagnosisCode") or item.get("diseaseCode") or item.get("diseaseicd") or "")
        diagnosis_name = str(item.get("diagnosisName") or item.get("diseaseName") or "")
        category_code = str(item.get("categoryCode") or "")
        category_name = str(item.get("categoryName") or "")
        subcategory_code = str(item.get("subcategoryCode") or "")
        subcategory_name = str(item.get("subcategoryName") or "")
        chapter_name = str(item.get("chapterName") or item.get("diseaseCategory") or "")
        section_name = str(item.get("sectionName") or "")
        match_score = str(item.get("matchScore") or "")

        if disease_id is None or not diagnosis_code or not diagnosis_name:
            continue

        id_text = str(disease_id)
        id_code_map[id_text] = {
            "diagnosisCode": diagnosis_code,
            "diagnosisName": diagnosis_name
        }

        lines.append(
            f"{index}. diseaseId={disease_id}; "
            f"diagnosisCode={diagnosis_code}; "
            f"diagnosisName={diagnosis_name}; "
            f"categoryCode={category_code}; "
            f"categoryName={category_name}; "
            f"subcategoryCode={subcategory_code}; "
            f"subcategoryName={subcategory_name}; "
            f"sectionName={section_name}; "
            f"chapterName={chapter_name}; "
            f"matchScore={match_score}"
        )

    return {
        "candidate_diagnoses_text": "\n".join(lines) if lines else "无候选诊断",
        "candidate_count": len(lines),
        "candidate_id_code_map_json": json.dumps(id_code_map, ensure_ascii=False)
    }
```

---

## 节点 7：判断是否有候选诊断

| 配置项 | 值 |
|---|---|
| 节点名称 | `判断是否有候选诊断` |
| 节点类型 | IF/ELSE 条件分支 |

### 条件

```text
candidate_count > 0
```

### 分支

| 分支 | 条件 | 下一节点 |
|---|---|---|
| true | `candidate_count > 0` | 节点 8A：核心确诊推荐 |
| false | `candidate_count <= 0` | 节点 8B：无候选兜底建议 |

---

## 节点 8A：核心确诊推荐

| 配置项 | 值 |
|---|---|
| 节点名称 | `核心确诊推荐` |
| 节点类型 | LLM |
| 建议温度 | `0.2` |
| 输出方式 | **结构化输出（Structured Output）** |

### 为什么使用结构化输出

与节点 3 相同，核心确诊推荐节点输出包含嵌套数组，使用结构化输出可以：

- 避免模型输出 Markdown 代码块导致解析失败。
- 让 `diagnosisSuggestions`、`differentialDiagnosis` 等字段直接可被节点 9A 引用。
- 减少节点 9A 的 JSON 清洗逻辑，保留候选校验即可。

### Dify 配置步骤

1. 打开 LLM 节点 → **输出** → 选择 **结构化输出**。
2. 将下方 JSON Schema 粘贴到 Schema 编辑器中。
3. 确认 `riskLevel` 枚举值与提示词一致。
4. 保存后，下游节点直接引用结构化字段。

### 结构化输出 Schema

```json
{
  "type": "object",
  "properties": {
    "diagnosisSuggestions": {
      "type": "array",
      "description": "确诊推荐列表，按可能性从高到低排序，3-5个",
      "items": {
        "type": "object",
        "properties": {
          "diseaseId": {
            "type": "integer",
            "description": "候选列表中的 diseaseId，不得编造"
          },
          "diagnosisCode": {
            "type": "string",
            "description": "候选列表中的诊断代码，不得修改"
          },
          "diagnosisName": {
            "type": "string",
            "description": "候选列表中的诊断名称，不得修改"
          },
          "probability": {
            "type": "number",
            "description": "可能性，0-1 小数"
          },
          "riskLevel": {
            "type": "string",
            "enum": ["low", "medium", "high", "critical"],
            "description": "风险等级"
          },
          "diagnosisBasis": {
            "type": "string",
            "description": "诊断依据，需引用患者具体临床信息"
          },
          "treatmentDirection": {
            "type": "string",
            "description": "建议治疗方向，50字以内"
          }
        },
        "required": [
          "diseaseId",
          "diagnosisCode",
          "diagnosisName",
          "probability",
          "riskLevel",
          "diagnosisBasis",
          "treatmentDirection"
        ]
      }
    },
    "clinicalSummaryForDoctor": {
      "type": "string",
      "description": "100字以内总结本次确诊判断"
    },
    "differentialDiagnosis": {
      "type": "array",
      "description": "需要鉴别的疾病列表",
      "items": {
        "type": "object",
        "properties": {
          "diagnosisName": {
            "type": "string",
            "description": "需要鉴别的疾病名称"
          },
          "reason": {
            "type": "string",
            "description": "为什么需要鉴别"
          }
        },
        "required": ["diagnosisName", "reason"]
      }
    },
    "warningSigns": {
      "type": "array",
      "description": "需要医生重点关注的危险信号",
      "items": {
        "type": "string"
      }
    }
  },
  "required": [
    "diagnosisSuggestions",
    "clinicalSummaryForDoctor",
    "differentialDiagnosis",
    "warningSigns"
  ]
}
```

### 输入变量

| 变量名 | 来源 |
|---|---|
| `clinical_context` | 节点 2.clinical_context |
| `candidate_diagnoses_text` | 节点 6.candidate_diagnoses_text |

### 输出变量

启用结构化输出后，LLM 节点直接输出以下字段（不再使用 `text`）：

| 变量名 | 类型 | 说明 |
|---|---|---|
| `diagnosisSuggestions` | Array[Object] | 确诊推荐列表 |
| `clinicalSummaryForDoctor` | String | 给医生的综合判断摘要 |
| `differentialDiagnosis` | Array[Object] | 鉴别诊断列表 |
| `warningSigns` | Array[String] | 危险信号列表 |

`diagnosisSuggestions` 每项包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `diseaseId` | Integer | 候选列表中的疾病 ID |
| `diagnosisCode` | String | 候选列表中的诊断代码 |
| `diagnosisName` | String | 候选列表中的诊断名称 |
| `probability` | Number | 可能性 0-1 |
| `riskLevel` | String | low / medium / high / critical |
| `diagnosisBasis` | String | 诊断依据 |
| `treatmentDirection` | String | 治疗方向 |

下游节点 9A 直接引用这些字段，无需再解析 `text` JSON 字符串。

### System Prompt

```text
你是一名经验丰富的门诊主治医师，负责根据病史、初步诊断、检查检验结果，从候选疾病库中选择最可能的确诊疾病。

你必须遵守：
1. 只能从候选诊断列表中选择疾病。
2. 不得编造 diseaseId、diagnosisCode、diagnosisName。
3. diseaseId、diagnosisCode、diagnosisName 必须与候选列表完全一致。
4. 你的建议仅供医生参考，不能替代医生最终诊断。
5. 如果检查检验信息不足，需要在 diagnosisBasis 或 clinicalSummaryForDoctor 中明确指出“不足以完全确诊”。
```

### User Prompt

```text
请根据以下临床资料，从候选诊断列表中选择 3-5 个最可能的确诊疾病。

【临床资料】
{{#整理输入.clinical_context#}}

【候选诊断列表】
{{#格式化候选诊断.candidate_diagnoses_text#}}

填写要求：
1. diagnosisSuggestions 按可能性从高到低排序。
2. diagnosisSuggestions 数量为 3-5 个；如果证据不足，可以少于 3 个，但必须说明依据不足。
3. probability 使用 0-1 小数。
4. riskLevel 只能是 low、medium、high、critical。
5. diagnosisBasis 不能泛泛而谈，必须引用患者具体信息。
6. treatmentDirection 控制在 50 字以内。
7. 不要选择候选列表之外的疾病。
8. 不要修改候选列表中的 diseaseId、diagnosisCode、diagnosisName。
9. differentialDiagnosis 填写需要鉴别的疾病及原因。
10. warningSigns 填写需要医生重点关注的危险信号，可为空数组。
```

---

## 节点 8B：无候选兜底建议

| 配置项 | 值 |
|---|---|
| 节点名称 | `无候选兜底建议` |
| 节点类型 | LLM |
| 建议温度 | `0.2` |
| 输出方式 | **结构化输出（Structured Output）** |

### 为什么使用结构化输出

与节点 3、8A 相同，兜底建议节点也包含嵌套数组。使用结构化输出可以：

- 避免模型输出 Markdown 代码块导致解析失败。
- 让 `fallbackSuggestions` 等字段直接可被节点 9B 引用。
- 减少节点 9B 的 JSON 清洗逻辑。

### Dify 配置步骤

1. 打开 LLM 节点 → **输出** → 选择 **结构化输出**。
2. 将下方 JSON Schema 粘贴到 Schema 编辑器中。
3. 确认 `riskLevel` 枚举值与提示词一致。
4. 保存后，下游节点直接引用结构化字段。

### 结构化输出 Schema

```json
{
  "type": "object",
  "properties": {
    "fallbackSuggestions": {
      "type": "array",
      "description": "兜底诊断建议列表，1-5个，未匹配本地疾病库",
      "items": {
        "type": "object",
        "properties": {
          "diagnosisName": {
            "type": "string",
            "description": "可能疾病名称"
          },
          "estimatedIcdPrefix": {
            "type": "string",
            "description": "可能ICD前三位前缀，例如 J18、E11、I10；不确定则为空字符串"
          },
          "probability": {
            "type": "number",
            "description": "可能性，0-1 小数"
          },
          "riskLevel": {
            "type": "string",
            "enum": ["low", "medium", "high", "critical"],
            "description": "风险等级"
          },
          "diagnosisBasis": {
            "type": "string",
            "description": "诊断依据"
          },
          "note": {
            "type": "string",
            "description": "必须提醒未匹配本地疾病库，需要医生手动确认"
          }
        },
        "required": [
          "diagnosisName",
          "estimatedIcdPrefix",
          "probability",
          "riskLevel",
          "diagnosisBasis",
          "note"
        ]
      }
    },
    "clinicalSummaryForDoctor": {
      "type": "string",
      "description": "100字以内总结"
    },
    "searchAdvice": {
      "type": "string",
      "description": "建议后端或医生进一步搜索的关键词"
    }
  },
  "required": [
    "fallbackSuggestions",
    "clinicalSummaryForDoctor",
    "searchAdvice"
  ]
}
```

### 输入变量

| 变量名 | 来源 |
|---|---|
| `clinical_context` | 节点 2.clinical_context |
| `keywords_text` | 节点 4.keywords_text |
| `icd_prefixes_text` | 节点 4.icd_prefixes_text |

### 输出变量

启用结构化输出后，LLM 节点直接输出以下字段（不再使用 `text`）：

| 变量名 | 类型 | 说明 |
|---|---|---|
| `fallbackSuggestions` | Array[Object] | 兜底诊断建议列表 |
| `clinicalSummaryForDoctor` | String | 给医生的综合判断摘要 |
| `searchAdvice` | String | 建议进一步搜索的关键词 |

`fallbackSuggestions` 每项包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `diagnosisName` | String | 可能疾病名称 |
| `estimatedIcdPrefix` | String | ICD 前三位前缀，不确定可为空 |
| `probability` | Number | 可能性 0-1 |
| `riskLevel` | String | low / medium / high / critical |
| `diagnosisBasis` | String | 诊断依据 |
| `note` | String | 未匹配本地疾病库的提示 |

下游节点 9B 直接引用这些字段，无需再解析 `text` JSON 字符串。

### System Prompt

```text
你是一名临床诊断助手。当前本地疾病库检索没有返回候选诊断。

你可以根据临床资料给出可能诊断方向，但必须明确说明这些疾病未匹配到本地疾病库，不能直接作为结构化确诊疾病写库。

要求：
1. fallbackSuggestions 中的 note 必须提醒医生需要手动确认。
2. estimatedIcdPrefix 只能是 ICD 前三位，例如 J18、E11、I10；不确定则返回空字符串。
3. 如果临床信息不足，fallbackSuggestions 可以为空数组，但必须保证字段存在。
```

### User Prompt

```text
【临床资料】
{{#整理输入.clinical_context#}}

【已使用的疾病关键词】
{{#构造疾病检索请求.keywords_text#}}

【已使用的 ICD 前缀】
{{#构造疾病检索请求.icd_prefixes_text#}}

填写要求：
1. fallbackSuggestions 数量控制在 1-5 个。
2. diagnosisName 使用中文疾病名。
3. estimatedIcdPrefix 只能是 ICD 前三位，例如 J18、E11、I10；不确定则为空字符串。
4. probability 使用 0-1 小数。
5. riskLevel 只能是 low、medium、high、critical。
6. diagnosisBasis 需结合患者具体临床信息。
7. note 必须说明“未匹配到本地疾病库，需要医生手动确认”。
8. clinicalSummaryForDoctor 控制在 100 字以内。
9. searchAdvice 给出建议后端或医生进一步搜索的关键词。
```

---

## 节点 9A：校验最终输出

| 配置项 | 值 |
|---|---|
| 节点名称 | `校验最终输出` |
| 节点类型 | Code |
| 语言 | Python 3 |

### 输入变量

| 变量名 | 来源 |
|---|---|
| `diagnosisSuggestions` | 节点 8A → `diagnosisSuggestions` |
| `clinicalSummaryForDoctor` | 节点 8A → `clinicalSummaryForDoctor` |
| `differentialDiagnosis` | 节点 8A → `differentialDiagnosis` |
| `warningSigns` | 节点 8A → `warningSigns` |
| `register_id` | 节点 2 → `register_id` |
| `candidate_id_code_map_json` | 节点 6 → `candidate_id_code_map_json` |

> 节点 8A 结构化输出为 camelCase，输入变量名必须与 `main()` 参数名完全一致（不要用 snake_case 别名）。

### 输出变量

在 Dify 代码节点中，为以下变量设置对应类型（**不要使用 String 类型的 `output_json`**）：

| 变量名 | Dify 类型 | 说明 |
|---|---|---|
| `status` | String | `success` 或 `empty` |
| `registerId` | String | 就诊 ID |
| `suggestions` | Array[Object] | 校验后的确诊推荐列表 |
| `fallbackSuggestions` | Array[Object] | 成功分支固定返回空数组 `[]` |
| `clinicalSummaryForDoctor` | String | 综合判断摘要 |
| `differentialDiagnosis` | Array[Object] | 鉴别诊断列表 |
| `warningSigns` | Array[String] | 危险信号列表 |
| `searchAdvice` | String | 成功分支固定返回空字符串 `""` |

### 可直接粘贴代码

```python
import json

def as_object_list(value):
    if value is None:
        return []
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            if isinstance(parsed, list):
                return [item for item in parsed if isinstance(item, dict)]
        except Exception:
            pass
    return []

def as_string_list(value):
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            if isinstance(parsed, list):
                return [str(item).strip() for item in parsed if str(item).strip()]
        except Exception:
            pass
        return [value.strip()] if value.strip() else []
    return []

def safe_json_loads(text):
    if not text:
        return {}
    try:
        return json.loads(str(text))
    except Exception:
        return {}

def normalize_risk_level(value):
    value = str(value or "").strip().lower()
    if value in ["low", "medium", "high", "critical"]:
        return value
    return "medium"

def normalize_probability(value):
    try:
        probability = float(value)
    except Exception:
        return 0.0
    if probability < 0:
        return 0.0
    if probability > 1:
        return 1.0
    return probability

def main(
    diagnosisSuggestions,
    clinicalSummaryForDoctor,
    differentialDiagnosis,
    warningSigns,
    register_id,
    candidate_id_code_map_json
):
    suggestions = as_object_list(diagnosisSuggestions)
    candidate_map = safe_json_loads(candidate_id_code_map_json)

    final_suggestions = []

    for item in suggestions:
        disease_id = item.get("diseaseId")
        if disease_id is None:
            continue

        disease_id_text = str(disease_id)
        candidate = candidate_map.get(disease_id_text)
        if not candidate:
            continue

        expected_code = str(candidate.get("diagnosisCode") or "")
        expected_name = str(candidate.get("diagnosisName") or "")
        input_code = str(item.get("diagnosisCode") or "")
        input_name = str(item.get("diagnosisName") or "")

        # 严格校验：LLM 必须选择候选列表里的 ID、编码、名称。
        if input_code != expected_code or input_name != expected_name:
            continue

        final_suggestions.append({
            "registerId": str(register_id),
            "diseaseId": int(disease_id_text),
            "diagnosisCode": expected_code,
            "diagnosisName": expected_name,
            "recommendIcd": expected_code,
            "probability": normalize_probability(item.get("probability")),
            "riskLevel": normalize_risk_level(item.get("riskLevel")),
            "diagnosisBasis": str(item.get("diagnosisBasis") or ""),
            "treatmentDirection": str(item.get("treatmentDirection") or ""),
            "sortOrder": len(final_suggestions) + 1,
            "isAdopted": False
        })

    return {
        "status": "success" if final_suggestions else "empty",
        "registerId": str(register_id),
        "suggestions": final_suggestions,
        "fallbackSuggestions": [],
        "clinicalSummaryForDoctor": str(clinicalSummaryForDoctor or ""),
        "differentialDiagnosis": as_object_list(differentialDiagnosis),
        "warningSigns": as_string_list(warningSigns),
        "searchAdvice": ""
    }
```

---

## 节点 9B：格式化兜底输出

| 配置项 | 值 |
|---|---|
| 节点名称 | `格式化兜底输出` |
| 节点类型 | Code |
| 语言 | Python 3 |

### 输入变量

| 变量名 | 来源 |
|---|---|
| `fallbackSuggestions` | 节点 8B → `fallbackSuggestions` |
| `clinicalSummaryForDoctor` | 节点 8B → `clinicalSummaryForDoctor` |
| `searchAdvice` | 节点 8B → `searchAdvice` |
| `register_id` | 节点 2 → `register_id` |

> 节点 8B 结构化输出为 camelCase，输入变量名必须与 `main()` 参数名完全一致。

### 输出变量

在 Dify 代码节点中，为以下变量设置对应类型（**不要使用 String 类型的 `output_json`**）：

| 变量名 | Dify 类型 | 说明 |
|---|---|---|
| `status` | String | 固定为 `fallback` |
| `registerId` | String | 就诊 ID |
| `suggestions` | Array[Object] | 兜底分支固定返回空数组 `[]` |
| `fallbackSuggestions` | Array[Object] | 兜底诊断建议列表 |
| `clinicalSummaryForDoctor` | String | 综合判断摘要 |
| `differentialDiagnosis` | Array[Object] | 兜底分支固定返回空数组 `[]` |
| `warningSigns` | Array[String] | 兜底分支固定返回空数组 `[]` |
| `searchAdvice` | String | 建议进一步搜索的关键词 |

### 可直接粘贴代码

```python
import json
import re

def as_object_list(value):
    if value is None:
        return []
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            if isinstance(parsed, list):
                return [item for item in parsed if isinstance(item, dict)]
        except Exception:
            pass
    return []

def normalize_risk_level(value):
    value = str(value or "").strip().lower()
    if value in ["low", "medium", "high", "critical"]:
        return value
    return "medium"

def normalize_probability(value):
    try:
        probability = float(value)
    except Exception:
        return 0.0
    if probability < 0:
        return 0.0
    if probability > 1:
        return 1.0
    return probability

def normalize_icd_prefix(value):
    value = str(value or "").strip().upper()
    match = re.match(r"^[A-Z][0-9]{2}", value)
    return match.group(0) if match else ""

def main(
    fallbackSuggestions,
    clinicalSummaryForDoctor,
    searchAdvice,
    register_id
):
    raw_suggestions = as_object_list(fallbackSuggestions)

    fallback_items = []
    for item in raw_suggestions[:5]:
        fallback_items.append({
            "diagnosisName": str(item.get("diagnosisName") or ""),
            "estimatedIcdPrefix": normalize_icd_prefix(item.get("estimatedIcdPrefix")),
            "probability": normalize_probability(item.get("probability")),
            "riskLevel": normalize_risk_level(item.get("riskLevel")),
            "diagnosisBasis": str(item.get("diagnosisBasis") or ""),
            "note": str(item.get("note") or "未匹配到本地疾病库，需要医生手动确认。")
        })

    return {
        "status": "fallback",
        "registerId": str(register_id),
        "suggestions": [],
        "fallbackSuggestions": fallback_items,
        "clinicalSummaryForDoctor": str(clinicalSummaryForDoctor or ""),
        "differentialDiagnosis": [],
        "warningSigns": [],
        "searchAdvice": str(searchAdvice or "")
    }
```

---

## 节点 10A：结束节点（成功分支）

| 配置项 | 值 |
|---|---|
| 节点名称 | `结束-成功` |
| 节点类型 | End |
| 输出方式 | **结构化输出（多字段）** |

### 输出变量映射

| End 输出变量 | 类型 | 来源 |
|---|---|---|
| `status` | String | 节点 9A.status |
| `registerId` | String | 节点 9A.registerId |
| `suggestions` | Array[Object] | 节点 9A.suggestions |
| `fallbackSuggestions` | Array[Object] | 节点 9A.fallbackSuggestions |
| `clinicalSummaryForDoctor` | String | 节点 9A.clinicalSummaryForDoctor |
| `differentialDiagnosis` | Array[Object] | 节点 9A.differentialDiagnosis |
| `warningSigns` | Array[String] | 节点 9A.warningSigns |
| `searchAdvice` | String | 节点 9A.searchAdvice |

---

## 节点 10B：结束节点（兜底分支）

| 配置项 | 值 |
|---|---|
| 节点名称 | `结束-兜底` |
| 节点类型 | End |
| 输出方式 | **结构化输出（多字段）** |

### 输出变量映射

| End 输出变量 | 类型 | 来源 |
|---|---|---|
| `status` | String | 节点 9B.status |
| `registerId` | String | 节点 9B.registerId |
| `suggestions` | Array[Object] | 节点 9B.suggestions |
| `fallbackSuggestions` | Array[Object] | 节点 9B.fallbackSuggestions |
| `clinicalSummaryForDoctor` | String | 节点 9B.clinicalSummaryForDoctor |
| `differentialDiagnosis` | Array[Object] | 节点 9B.differentialDiagnosis |
| `warningSigns` | Array[String] | 节点 9B.warningSigns |
| `searchAdvice` | String | 节点 9B.searchAdvice |

> 两个结束节点使用**完全相同的 8 个输出字段**，后端无需区分分支即可统一解析。

---

## 8. 后端落库映射

后端从 Dify 工作流运行结果中直接读取结构化输出字段，**无需再解析 `output_json` 字符串**。

```java
// 伪代码示例
String status = outputs.get("status");
String registerId = outputs.get("registerId");
List<Map<String, Object>> suggestions = outputs.get("suggestions");
List<Map<String, Object>> fallbackSuggestions = outputs.get("fallbackSuggestions");
```

当 `status` 为 `success` 或 `empty` 时，遍历 `suggestions` 写入 `ai_diagnosis_suggestion`：

| 输出字段 | 数据库字段 |
|---|---|
| `registerId` | `register_id` |
| `diseaseId` | `disease_id` |
| `diagnosisName` | `disease_name` |
| `recommendIcd` | `recommend_icd` |
| `probability` | `probability` |
| `riskLevel` | `risk_level` |
| `treatmentDirection` | `treatment_direction` |
| `diagnosisBasis` | `diagnosis_basis` |
| `sortOrder` | `sort_order` |
| `isAdopted` | `is_adopted` |

注意：

- `status = fallback` 时不要写入 `ai_diagnosis_suggestion.disease_id`，因为没有本地疾病库 ID。
- 医生最终选择某个推荐疾病后，再更新 `is_adopted = true`。
- 医生也可以手动输入或搜索疾病，最终确诊仍以医生提交为准。

---

## 9. 测试用输入样例

```json
{
  "register_id": "123",
  "patient_info_text": "男，45岁，呼吸内科门诊",
  "chief_complaint": "咳嗽、发热3天",
  "present_illness": "患者3天前受凉后出现咳嗽，咳黄痰，最高体温38.6℃，伴轻度胸闷，无明显咯血。",
  "past_history": "高血压病史5年，规律服药。",
  "allergy_history": "否认药物过敏史。",
  "preliminary_diagnosis_text": "考虑肺炎可能，需结合胸部影像和炎症指标进一步明确。",
  "preliminary_diseases_text": "J18.900|肺炎|呼吸系统疾病\nJ20.900|急性支气管炎|呼吸系统疾病",
  "check_results_text": "胸部CT提示右下肺片状炎症影，考虑感染性病变。",
  "inspection_results_text": "WBC 12.5×10^9/L，NEUT% 82%，CRP 38mg/L。",
  "w3_analysis_text": "白细胞、中性粒细胞比例及CRP升高，结合胸部CT炎症影，支持细菌性呼吸道感染。",
  "abnormal_indicators_text": "WBC升高；NEUT%升高；CRP升高。",
  "ai_previsit_summary": "患者发热咳嗽，黄痰，无基础肺病史。",
  "doctor_notes": "右下肺可闻及湿啰音。"
}
```

---

## 10. 编排注意事项

1. Start 节点所有变量都设为 String。
2. 所有 LLM 节点（节点 3、8A、8B）必须使用 **结构化输出**，不要回退为 `text` + 手动 JSON 解析。
3. 两个 End 节点（10A、10B）必须使用 **相同的 8 个结构化输出字段**，不要使用 `output_json` 字符串。
4. 节点 9A、9B 的代码节点输出变量类型必须与 End 节点配置一致（Array 字段返回 Python list，不要 `json.dumps`）。
5. LLM 节点不要开启自由发挥，温度建议 `0.2`。
6. HTTP 节点 Body 直接使用节点 4 输出的 JSON 字符串，不要再次转义。
7. 核心确诊推荐节点必须强约束“只能从候选列表选择”。
8. 节点 9A 必须保留，不能省略；它用于过滤 LLM 编造的 diseaseId 或 diagnosisCode。
9. 候选诊断数量建议控制在 30-80 条，过少会漏召回，过多会降低 LLM 选择精度。
10. 如果 HTTP 检索失败或返回空数组，走兜底分支，不要让工作流直接失败。
11. 最终确诊仍由医生提交，AI 推荐只写入建议表。
