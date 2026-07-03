# 模拟检查医生 · Dify 工作流设计文档

> 版本：v2.0  
> 日期：2026-07-03  
> 适配平台：Dify 1.11.2  
> App 名称：模拟检查医生  
> 状态：设计完成，需在 Dify 控制台按 v2.0 **新建工作流**（替换旧版「8 个 LLM + contains 分支」编排）  
> 核心目标：根据检查名称、正常/异常开关、可能疾病，**稳定**生成结构化模拟检查结果；新增检验项目时**只改数据库配置**，不改 Dify 画布。

---

## 1. 工作流定位

### 1.1 用途

本工作流对接医技工作站「运行模拟检查 / 模拟检验」功能，模拟 **除胸部 CT、头颅 CT、肿瘤切割以外** 的检查检验结果。

调用方：`medtech-service` → Dify Workflow → 医技前端展示 `structuredOutput`。

### 1.2 工作流负责

- 解析并统一 `isNormal` / `normal_status` 控制信号。
- HTTP 拉取 `simulation_config` 中该检查项的 Prompt 片段与医学规则。
- Code 组装 Prompt，交给 **2 个固定 LLM**（正常 / 异常）生成 JSON。
- Code **强制修正** `isNormal` 与各指标 `status`，输出 `structured_output`。

### 1.3 工作流不负责

- 直连 PostgreSQL（必须走 HTTP 内部 API）。
- 胸部 CT、头颅 CT（后端 `isCtCategory` 已拦截）、肿瘤切割（独立流程）。
- 写入数据库、签发正式报告。
- 为每个检查项复制独立 LLM 节点。

---

## 2. 编排思路

### 2.1 为何改成「配置驱动 + 双 LLM」

| 旧编排问题 | v2.0 做法 |
|-----------|-----------|
| 8 个 LLM，加项目就要复制节点 | 固定 2 个 LLM；规则放 `simulation_config` 表 |
| `normal_status` 与 `isNormal` 字段不一致 | Code 节点双字段兼容 |
| Prompt 里「要异常」又写「可以正常」 | 正常/异常拆成两个 LLM + 两套 User Prompt |
| LLM 自行填写 `isNormal` | 后置 Code 按输入强制写入 |
| `contains` 路由不准 | HTTP API 按 `techCode` / `checkName` 匹配配置 |

### 2.2 数据流

```text
medtech-service 传入 checkName / techCode / isNormal / possibleDiseases ...
        ↓
[1 Start] → [2 输入标准化 Code]
        ↓
[3 HTTP 拉取 simulation_config]
        ↓
[4 组装 Prompt Code] ──不支持──→ [10 结束-不支持]
        ↓ 支持
[5 IF isNormal?]
   ├─ true  → [6A LLM 正常]
   └─ false → [6B LLM 异常]
        ↓
[7 校验修正 Code]
        ↓
[9 结束-成功]  → structured_output
```

### 2.3 正常 / 异常控制（三层）

```text
① Code 输入标准化：normal_status 优先；正常模式清空 possibleDiseases
② IF 分支：走不同 LLM，User Prompt 不含冲突语句
③ Code 校验修正：isNormal=true → 全部 status=normal；异常 → 校验 ≥2 项 abnormal
```

### 2.4 编排前置条件

1. `simulation_config` 表已迁移，至少录入血常规、C 反应蛋白等常用项（见第 3 节）。
2. `GET /api/medtech/internal/simulation-config` 从 Dify 容器网络可达。
3. 后端 `CheckSimulationService.buildWorkflowInputs()` 已传 `isNormal`、`techCode`（见 4.1）。
4. Dify Start 节点变量 **全部用 String**（见 **§4.0、§4.1**）。

---

## 3. 后端配置库（simulation_config）

工作流通过 HTTP 读配置，不直连库。新增检查项 = **插一条配置记录**，不改 Dify。

### 3.1 表结构

```sql
CREATE TABLE simulation_config (
    id                  SERIAL PRIMARY KEY,
    config_key          VARCHAR(64)  NOT NULL UNIQUE,
    tech_code           VARCHAR(64)  DEFAULT NULL,
    check_name          VARCHAR(128) NOT NULL,
    match_keywords      TEXT         DEFAULT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    simulation_mode     VARCHAR(32)  NOT NULL DEFAULT 'lab_items',
    version             INTEGER      NOT NULL DEFAULT 1,
    prompt_sections     JSONB        NOT NULL,
    disease_mappings    JSONB        NOT NULL DEFAULT '[]',
    output_schema       JSONB        NOT NULL,
    defaults            JSONB        NOT NULL DEFAULT '{}',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 3.2 prompt_sections 字段

| 键 | 写入 LLM 的位置 | 内容 |
|----|----------------|------|
| `role` | System | 角色：医学检查结果模拟助手 |
| `scope` | System | 只模拟本检查项，禁止 CT/B 超/其他检验 |
| `itemCatalog` | System | 必含指标清单 |
| `referenceRanges` | System | 成人常见参考范围 |
| `normalRules` | User（正常） | 全部 normal 的规则 |
| `abnormalRules` | User（异常） | 至少 2 项异常、医学约束 |
| `outputFormat` | System | JSON 字段说明（**不要要求 LLM 输出 isNormal**） |

### 3.3 disease_mappings 字段

```json
[
  { "keywords": ["细菌", "肺炎"], "hint": "WBC↑ NEUT%↑", "priority": 1 },
  { "keywords": ["病毒", "流感"], "hint": "LYMPH%↑ WBC 正常或↓", "priority": 2 }
]
```

异常配置的 `abnormalRules` / `disease_mappings` 中 **禁止** 写「可以正常」「可正常或轻度升高」等与异常模式冲突的句子。

### 3.4 匹配优先级

`tech_code` 精确 → `config_key` → `check_name` → `match_keywords` 任一命中 → 否则 404。

### 3.5 初始 8 类（从旧工作流迁移）

| config_key | check_name | tech_code |
|------------|------------|-----------|
| XCG | 血常规 | XCG |
| NCG | 尿常规 | NCG |
| LFT | 肝功能 | LFT |
| RFT | 肾功能 | RFT |
| TFT | 甲状腺功能 | TFT |
| Coag | 凝血功能 | Coag |
| Stool | 粪便常规 | Stool |
| CRP | C反应蛋白 | CRP |

---

## 4. 工作流输入 / 输出总览

> 适配 **Dify 1.11.2**。Dify 逐节点配置见 **第 5 节**。

### 4.0 Dify 1.11.2 变量类型约定

本工作流在 Dify 控制台中仅使用以下类型（与 1.11.2 Workflow 变量面板一致）：

| Dify 类型 | 控制台选法 | 本工作流用途 |
|-----------|------------|--------------|
| **String** | 短文本 / 段落 | Start 全部入参；Code 中间字符串；LLM `text`；HTTP `body` |
| **Number** | 数字 | HTTP `status_code` |
| **Boolean** | 布尔 | 仅出现在 **Object 内部字段**（如 `structured_output.isNormal`），**不作为 Start / IF 条件类型** |
| **Object** | 对象 | End 的 `structured_output`；Code ⑦⑧ 输出 |
| **Array[String]** | 字符串数组 | `structured_output.simulatedForDiseases`（Object 内字段） |
| **Array[Object]** | 对象数组 | `structured_output.resultItems`（Object 内字段） |

**重要（1.11.2 实践约束）：**

1. **Start 节点**：7 个输入变量 **全部选 String**（短文本或段落），不要用 Boolean / Object / Array，避免 blocking API 入参解析不稳定。
2. **IF 条件**：比较 **String**（如 `skipLlm` 等于 `"false"`），不要用 Boolean 节点输出做条件。
3. **Code 节点**：在「输出变量」面板为每个返回值 **手动声明类型**（见第 5 节各节点表格）；Python `return` 的字典 key 须与声明名一致。
4. **End 节点**：`structured_output` 选 **Object**；后端与旧工作流一样读 `outputs.structured_output`。

### 4.1 Start 输入变量（全部 String）

| 变量名 | 必填 | 控制台选法 | Dify 类型 | 说明 | 示例 |
|--------|------|------------|-----------|------|------|
| `checkName` | 是 | 短文本 | String | 检查/检验名称 | `血常规` |
| `techCode` | 否 | 短文本 | String | 医技编码，优先匹配配置 | `XCG` |
| `isNormal` | 是 | 短文本 | String | 字面量 `"true"` / `"false"` | `true` |
| `normal_status` | 否 | 短文本 | String | 后端兼容；非空时覆盖 `isNormal` | `false` |
| `possibleDiseases` | 否 | 段落 | String | JSON 数组**字符串**（不是 Array 类型） | 见下 |
| `patientContext` | 否 | 段落 | String | 病历摘要 | `主诉：发热2天` |
| `checkPurpose` | 否 | 短文本 | String | 检查目的 | `感染鉴别` |

`possibleDiseases` 示例：

```json
[{"name":"急性上呼吸道感染","diseaseSymptoms":"发热、咳嗽"}]
```

后端组装（`CheckSimulationService.buildWorkflowInputs`）须同时传入：

```java
inputs.put("checkName", examName);
inputs.put("techCode", technology.getTechCode());
inputs.put("isNormal", normalStatus ? "true" : "false");
inputs.put("normal_status", normalStatus ? "true" : "false");
inputs.put("possibleDiseases", contextBuilder.serializePossibleDiseases(registerId));
inputs.put("patientContext", contextBuilder.buildPatientContext(registerId));
inputs.put("checkPurpose", purpose == null ? "" : purpose);
```

### 4.2 End 输出变量

| 变量名 | Dify 类型 | 控制台选法 | 说明 |
|--------|-----------|------------|------|
| `structured_output` | **Object** | 对象 | 工作流唯一对外输出；映射来源见 §5.10 |

### 4.3 structured_output 字段契约（Object 内部结构，与现网不变）

| 字段 | JSON 类型 | Dify 语义类型 | 必填 | 说明 |
|------|-----------|---------------|------|------|
| `checkName` | string | String | 是 | 检查名称 |
| `isNormal` | boolean | Boolean | 是 | **由 Code ⑦ 写入**，非 LLM 决定 |
| `simulatedForDiseases` | string[] | Array[String] | 是 | 正常模式 `[]` |
| `resultItems` | object[] | Array[Object] | 是 | 指标明细 |
| `conclusion` | string | String | 是 | 一句话总结 |
| `notice` | string | String | 是 | 固定免责声明 |
| `inflammationLevel` | string | String | 否 | CRP 等：`normal` / `mild` / `moderate` / `marked` / `severe` |
| `_validationWarning` | string | String | 否 | 异常项不足时供后端 retry |

**resultItems[] 每条（Array[Object] 元素）：**

| 字段 | JSON 类型 | Dify 语义类型 | 说明 |
|------|-----------|---------------|------|
| `itemName` | string | String | 中文名 |
| `itemCode` | string | String | 英文缩写 |
| `value` | number 或 string | Number 或 String | 定量 number；定性 string |
| `unit` | string | String | 无单位填 `""` |
| `referenceRange` | string | String | 参考范围 |
| `status` | string | String | 见下表 |
| `meaning` | string | String | 该指标含义 |

**status 枚举（按检查类型）：**

| 检查类型 | 允许值 |
|----------|--------|
| 血常规 / 肝功能 / 肾功能 / 甲状腺 / 凝血 | `normal` `high` `low` |
| 尿常规 / 粪便常规 | `normal` `high` `low` `positive` `abnormal` |
| C 反应蛋白 | `normal` `high` |

### 4.4 输出示例

**正常模式：**

```json
{
  "checkName": "血常规",
  "isNormal": true,
  "simulatedForDiseases": [],
  "resultItems": [
    {
      "itemCode": "WBC",
      "itemName": "白细胞计数",
      "value": 6.8,
      "unit": "×10^9/L",
      "referenceRange": "3.5-9.5",
      "status": "normal",
      "meaning": "白细胞计数在正常范围"
    }
  ],
  "conclusion": "血常规模拟结果未见明显异常",
  "notice": "本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。"
}
```

**异常模式：**

```json
{
  "checkName": "血常规",
  "isNormal": false,
  "simulatedForDiseases": ["急性上呼吸道感染"],
  "resultItems": [
    {
      "itemCode": "WBC",
      "itemName": "白细胞计数",
      "value": 6.2,
      "unit": "×10^9/L",
      "referenceRange": "3.5-9.5",
      "status": "normal",
      "meaning": "白细胞总数正常"
    },
    {
      "itemCode": "LYMPH%",
      "itemName": "淋巴细胞百分比",
      "value": 48,
      "unit": "%",
      "referenceRange": "20-50",
      "status": "high",
      "meaning": "淋巴细胞比例升高，符合病毒感染倾向"
    }
  ],
  "conclusion": "血常规模拟结果提示淋巴细胞比例升高，符合病毒感染倾向。",
  "notice": "本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。"
}
```

**不支持该检查类型：**

```json
{
  "checkName": "未知项目",
  "isNormal": true,
  "simulatedForDiseases": [],
  "resultItems": [],
  "conclusion": "暂不支持该检查类型的模拟：未知项目",
  "notice": "请联系管理员在 simulation_config 中补充配置"
}
```

### 4.5 HTTP 配置 API（节点 3 调用）

```http
GET /api/medtech/internal/simulation-config?techCode=XCG&checkName=血常规&mode=false
Authorization: Bearer {{INTERNAL_AI_TOKEN}}
```

成功响应 `data` 结构见第 3 节；404 / 非 2xx → 节点 4 置 `skipLlm=true`，不调 LLM。

---

## 5. 节点详细设计（Dify 控制台逐步配置）

### 5.0 节点一览

| 序号 | 节点名称 | Dify 节点类型 | 作用 |
|------|----------|---------------|------|
| 1 | 开始 | Start | 接收后端 inputs（全 String） |
| 2 | 输入标准化 | Code | 解析 isNormal、清空正常模式 diseases |
| 3 | 拉取模拟配置 | HTTP Request | GET simulation-config |
| 4 | 组装 Prompt | Code | 拼 Prompt；输出 skipLlm 等 String |
| 5 | 是否支持模拟 | IF / 条件分支 | String equals 比较 skipLlm |
| 6 | 正常还是异常 | IF / 条件分支 | String equals 比较 isNormalBool |
| 6A | 正常结果生成 | LLM | 输出 String `text` |
| 6B | 异常结果生成 | LLM | 输出 String `text` |
| 7 | 校验并修正 | Code | 输出 Object `structured_output` |
| 8 | 格式化错误输出 | Code | JSON 字符串 → Object |
| 9 | 结束-成功 | End | 输出 Object `structured_output` |
| 10 | 结束-不支持 | End | 输出 Object `structured_output` |

```text
1 → 2 → 3 → 4 → 5
                  ├─ skipLlm=true  → 8 → 10
                  └─ skipLlm=false → 6 → 6A 或 6B → 7 → 9
```

### 5.0.1 全工作流变量类型速查

| 节点 | 变量名 | Dify 类型 | 方向 |
|------|--------|-----------|------|
| 开始 | `checkName` … `checkPurpose`（7 个） | String | 入 → 出（透传） |
| 输入标准化 | 全部输出（8 个，见 §5.2） | String | 出 |
| 拉取模拟配置 | `body` | String | 出 |
| 拉取模拟配置 | `status_code` | Number | 出 |
| 组装 Prompt | 全部输出（10 个，见 §5.4） | String | 出 |
| 正常结果生成 | `text` | String | 出 |
| 异常结果生成 | `text` | String | 出 |
| 校验并修正 | `structured_output` | **Object** | 出 |
| 格式化错误输出 | `structured_output` | **Object** | 出 |
| 结束-成功 / 不支持 | `structured_output` | **Object** | 工作流最终输出 |

---

### 5.0.2 LLM 节点统一约定

共 **2 个** LLM 节点；推荐关闭 structured output，读 String 类型 `text`。

| 节点 | 名称 | Prompt 输入（均为 String） | 输出（推荐） |
|------|------|---------------------------|--------------|
| 6A | 正常结果生成 | `systemPrompt` + `userPromptNormal` | `text` → String |
| 6B | 异常结果生成 | `systemPrompt` + `userPromptAbnormal` | `text` → String |

**模型**：`deepseek-v4-pro`；Temperature **0.1**；top_p `0.9`。

---

### 5.1 节点 1：开始

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `开始` |
| 节点类型 | Start |
| Dify 版本 | 1.11.2 Workflow App |

**输入变量：** 见 **§4.1**；`checkName`、`isNormal` 标记必填；`isNormal` 默认值 `"true"`。

**输出变量（Start 自动透传，类型与输入相同）：**

| 变量名 | Dify 类型 |
|--------|-----------|
| `checkName` | String |
| `techCode` | String |
| `isNormal` | String |
| `normal_status` | String |
| `possibleDiseases` | String |
| `patientContext` | String |
| `checkPurpose` | String |

**注意：** 删除旧工作流 Start 里错误的「尿蛋白检测」选项；与 `尿常规` / `NCG` 对齐。不要使用 Boolean / Object / Array 作为 Start 变量类型。

---

### 5.2 节点 2：输入标准化

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `输入标准化` |
| 节点类型 | Code |
| 语言 | Python 3 |

**输入变量：**

| 变量名 | Dify 类型 | 来源 |
|--------|-----------|------|
| `checkName` | String | `开始.checkName` |
| `techCode` | String | `开始.techCode` |
| `isNormal` | String | `开始.isNormal` |
| `normal_status` | String | `开始.normal_status` |
| `possibleDiseases` | String | `开始.possibleDiseases` |
| `patientContext` | String | `开始.patientContext` |
| `checkPurpose` | String | `开始.checkPurpose` |

**输出变量（须在 Code 节点「输出变量」面板逐一添加并声明类型）：**

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `checkName` | String | trim 后检查名 |
| `techCode` | String | trim 后编码 |
| `isNormalBool` | String | `"true"` / `"false"`，供 IF §5.6 使用 |
| `isNormal` | String | 同左，供 HTTP query `mode` |
| `possibleDiseases` | String | JSON 字符串；正常模式固定 `[]` |
| `possibleDiseasesForPrompt` | String | 正常：`无（正常模拟模式）`；异常：格式化 JSON |
| `patientContext` | String | 患者背景 |
| `checkPurpose` | String | 检查目的 |

**节点代码：**

```python
import json

def clean(v):
    return "" if v is None else str(v).strip()

def parse_bool(*values):
    for v in values:
        t = clean(v).lower()
        if not t or t in ("none", "null"):
            continue
        if t in ("true", "1", "yes"):
            return True
        if t in ("false", "0", "no"):
            return False
    return True

def main(checkName, techCode, isNormal, normal_status, possibleDiseases,
         patientContext, checkPurpose):
    normal = parse_bool(normal_status, isNormal)
    diseases = []
    if not normal:
        raw = clean(possibleDiseases)
        if raw:
            try:
                parsed = json.loads(raw)
                if isinstance(parsed, list):
                    diseases = parsed
            except Exception:
                pass
    flag = "true" if normal else "false"
    return {
        "checkName": clean(checkName),
        "techCode": clean(techCode),
        "isNormalBool": flag,
        "isNormal": flag,
        "possibleDiseases": "[]" if normal else json.dumps(diseases, ensure_ascii=False),
        "possibleDiseasesForPrompt": "无（正常模拟模式）" if normal else json.dumps(diseases, ensure_ascii=False, indent=2),
        "patientContext": clean(patientContext),
        "checkPurpose": clean(checkPurpose),
    }
```

---

### 5.3 节点 3：拉取模拟配置

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `拉取模拟配置` |
| 节点类型 | HTTP Request |

**用于 URL Query 的输入（引用上游 String，非 HTTP 节点独立入参）：**

| 引用变量 | Dify 类型 | 来源 |
|----------|-----------|------|
| `techCode` | String | `输入标准化.techCode` |
| `checkName` | String | `输入标准化.checkName` |
| `mode` | String | `输入标准化.isNormal` |

**HTTP 配置：**

| 项 | 值 |
|----|-----|
| Method | GET |
| URL | `http://<medtech-host>:<port>/api/medtech/internal/simulation-config` |
| Query | `techCode={{输入标准化.techCode}}&checkName={{输入标准化.checkName}}&mode={{输入标准化.isNormal}}` |
| Header | `Authorization: Bearer <INTERNAL_AI_TOKEN>` |
| Timeout | 10s |

**失败策略：** 404 / 5xx / 超时 **不要** 让整个工作流失败；由节点 4 读 `status_code` 走不支持分支。

**输出变量（HTTP 节点自动产生）：**

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `body` | String | 响应体原文（JSON 字符串） |
| `status_code` | Number | HTTP 状态码，如 `200`、`404` |

**下游引用：** 节点 4 输入 `http_body` ← `拉取模拟配置.body`，`status_code` ← `拉取模拟配置.status_code`。

---

### 5.4 节点 4：组装 Prompt

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `组装 Prompt` |
| 节点类型 | Code |
| 语言 | Python 3 |

**输入变量：**

| 变量名 | Dify 类型 | 来源 |
|--------|-----------|------|
| `http_body` | String | `拉取模拟配置.body` |
| `status_code` | Number | `拉取模拟配置.status_code` |
| `checkName` | String | `输入标准化.checkName` |
| `isNormalBool` | String | `输入标准化.isNormalBool` |
| `possibleDiseases` | String | `输入标准化.possibleDiseases` |
| `possibleDiseasesForPrompt` | String | `输入标准化.possibleDiseasesForPrompt` |
| `patientContext` | String | `输入标准化.patientContext` |
| `checkPurpose` | String | `输入标准化.checkPurpose` |

**输出变量：**

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `skipLlm` | String | `"true"` / `"false"`，供 IF §5.5 |
| `errorOutputJson` | String | 不支持时的 structured_output **JSON 字符串** |
| `systemPrompt` | String | LLM System |
| `userPromptNormal` | String | 正常模式 User → 节点 6A |
| `userPromptAbnormal` | String | 异常模式 User → 节点 6B |
| `resolvedCheckName` | String | 配置标准检查名 |
| `defaultNotice` | String | 固定 notice |
| `defaultNormalConclusion` | String | 正常结论模板 |
| `outputSchemaJson` | String | outputSchema 的 JSON 字符串 |
| `isNormalBool` | String | 透传，供 IF §5.6 |

**组装规则：**

**systemPrompt** = `role` + `scope` + 指标清单 + 参考范围 + outputFormat（均来自 API `promptSections`）

**userPromptNormal** 固定骨架 + `normalRules`：

```text
【硬性控制 — 优先级最高】
模拟模式：NORMAL
检查名称：{resolvedCheckName}
1. 所有 resultItems.status 必须为 "normal"
2. 忽略 possibleDiseases
3. conclusion：{defaultNormalConclusion}
4. simulatedForDiseases：[]
5. 只输出 JSON，无 Markdown
6. 不要输出 isNormal 字段

患者背景（不得据此生成异常）：{patientContext}
检查目的：{checkPurpose}
{normalRules}
```

**userPromptAbnormal** 固定骨架 + 筛选后的 disease_mappings + `abnormalRules`：

```text
【硬性控制 — 优先级最高】
模拟模式：ABNORMAL
检查名称：{resolvedCheckName}
1. 至少 2 项、至多 5 项 status 非 normal，其余 normal
2. 不得全部 normal
3. 只写「模拟结果倾向」，不给确诊结论
4. 只输出 JSON，无 Markdown
5. 不要输出 isNormal 字段

可能疾病：{possibleDiseasesForPrompt}
患者背景：{patientContext}
检查目的：{checkPurpose}
{abnormalRules}
【疾病-指标映射】{top3 mappings}
```

**节点代码：**

```python
import json

def clean(v):
    return "" if v is None else str(v).strip()

def pick_mappings(mappings, diseases_json):
    try:
        diseases = json.loads(diseases_json or "[]")
    except Exception:
        diseases = []
    if not isinstance(mappings, list):
        return []
    if not diseases:
        return mappings[:3]
    text = " ".join(
        f"{d.get('name','')} {d.get('diseaseSymptoms','')}"
        for d in diseases if isinstance(d, dict)
    ).lower()
    scored = []
    for m in mappings:
        score = sum(1 for kw in (m.get("keywords") or []) if str(kw).lower() in text)
        if score > 0:
            row = dict(m)
            row["_score"] = score
            scored.append(row)
    scored.sort(key=lambda x: (-x["_score"], x.get("priority", 99)))
    return (scored or mappings)[:3]

def error_payload(check_name):
    name = check_name or "未知项目"
    return {
        "checkName": name,
        "isNormal": True,
        "simulatedForDiseases": [],
        "resultItems": [],
        "conclusion": f"暂不支持该检查类型的模拟：{name}",
        "notice": "请联系管理员在 simulation_config 中补充配置",
    }

def main(http_body, status_code, checkName, isNormalBool,
         possibleDiseases, possibleDiseasesForPrompt,
         patientContext, checkPurpose):
    if str(status_code or "") != "200":
        err = error_payload(checkName)
        return _skip(err, checkName, isNormalBool)

    try:
        payload = json.loads(http_body) if isinstance(http_body, str) else (http_body or {})
    except Exception:
        payload = {}
    data = payload.get("data") or {}
    if not data.get("enabled", True):
        return _skip(error_payload(checkName), checkName, isNormalBool)

    sec = data.get("promptSections") or {}
    defaults = data.get("defaults") or {}
    resolved = data.get("checkName") or checkName
    notice = defaults.get("notice") or "本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。"
    normal_conc = defaults.get("normalConclusion") or f"{resolved}模拟结果未见明显异常"

    system = "\n\n".join([
        clean(sec.get("role")),
        clean(sec.get("scope")),
        "【检查项目清单】\n" + clean(sec.get("itemCatalog")),
        "【参考范围】\n" + clean(sec.get("referenceRanges")),
        "【输出格式】\n" + clean(sec.get("outputFormat")),
    ]).strip()

    user_normal = f"""【硬性控制 — 优先级最高】
模拟模式：NORMAL
检查名称：{resolved}
1. 所有 resultItems.status 必须为 "normal"
2. 忽略 possibleDiseases
3. conclusion：{normal_conc}
4. simulatedForDiseases：[]
5. 只输出 JSON，无 Markdown
6. 不要输出 isNormal 字段

患者背景（不得据此生成异常）：{patientContext or "未提供"}
检查目的：{checkPurpose or "未提供"}

{clean(sec.get("normalRules"))}""".strip()

    maps = pick_mappings(data.get("diseaseMappings") or [], possibleDiseases)
    map_lines = "\n".join(f"- {m.get('keywords')} → {m.get('hint')}" for m in maps) or "- 通用异常模式"

    user_abnormal = f"""【硬性控制 — 优先级最高】
模拟模式：ABNORMAL
检查名称：{resolved}
1. 至少 2 项、至多 5 项 status 非 normal，其余 normal
2. 不得全部 normal
3. 只写「模拟结果倾向」
4. 只输出 JSON，无 Markdown
5. 不要输出 isNormal 字段

可能疾病：
{possibleDiseasesForPrompt}
患者背景：{patientContext or "未提供"}
检查目的：{checkPurpose or "未提供"}

【异常模拟规则】
{clean(sec.get("abnormalRules"))}

【疾病-指标映射】
{map_lines}""".strip()

    return {
        "skipLlm": "false",
        "errorOutputJson": "",
        "systemPrompt": system,
        "userPromptNormal": user_normal,
        "userPromptAbnormal": user_abnormal,
        "resolvedCheckName": resolved,
        "defaultNotice": notice,
        "defaultNormalConclusion": normal_conc,
        "outputSchemaJson": json.dumps(data.get("outputSchema") or {}, ensure_ascii=False),
        "isNormalBool": isNormalBool,
    }

def _skip(err, check_name, is_normal_bool):
    return {
        "skipLlm": "true",
        "errorOutputJson": json.dumps(err, ensure_ascii=False),
        "systemPrompt": "",
        "userPromptNormal": "",
        "userPromptAbnormal": "",
        "resolvedCheckName": check_name or "",
        "defaultNotice": err["notice"],
        "defaultNormalConclusion": "",
        "outputSchemaJson": "{}",
        "isNormalBool": is_normal_bool,
    }
```

---

### 5.5 节点 5：是否支持模拟

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `是否支持模拟` |
| 节点类型 | IF / 条件分支 |

**条件配置（Dify 1.11.2）：**

| 项 | 值 |
|----|-----|
| 变量 | `组装 Prompt.skipLlm` |
| 变量类型 | String |
| 比较符 | **is / 等于（equals）** |
| 比较值 | `false`（字面量字符串，不要选 Boolean 类型） |

| 分支 | 条件 | 下一节点 |
|------|------|----------|
| IF | `skipLlm` **等于** `"false"` | 节点 6 |
| ELSE | 其他 | 节点 8 → 10 |

**输出：** IF 节点无新增变量，仅路由。

---

### 5.6 节点 6：正常还是异常

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `正常还是异常` |
| 节点类型 | IF / 条件分支 |

**条件配置（Dify 1.11.2）：**

| 项 | 值 |
|----|-----|
| 变量 | `组装 Prompt.isNormalBool` |
| 变量类型 | String |
| 比较符 | **is / 等于（equals）** |
| 比较值 | `true`（字面量字符串） |

| 分支 | 条件 | 下一节点 |
|------|------|----------|
| IF | `isNormalBool` **等于** `"true"` | 节点 6A |
| ELSE | 其他 | 节点 6B |

**输出：** IF 节点无新增变量，仅路由。

---

### 5.7 节点 6A / 6B：LLM 生成

两个节点配置相同，仅 User Prompt 来源不同。

| 配置项 | 6A 正常 | 6B 异常 |
|--------|---------|---------|
| 节点名称 | `正常结果生成` | `异常结果生成` |
| 节点类型 | LLM | LLM |
| Model | deepseek-v4-pro（或现网模型） | 同左 |
| Temperature | **0.1** | **0.1** |
| top_p | 0.9 | 0.9 |

**6A 输入（Prompt 变量引用）：**

| 角色 | 引用变量 | Dify 类型 |
|------|----------|-----------|
| System | `组装 Prompt.systemPrompt` | String |
| User | `组装 Prompt.userPromptNormal` | String |

**6B 输入（Prompt 变量引用）：**

| 角色 | 引用变量 | Dify 类型 |
|------|----------|-----------|
| System | `组装 Prompt.systemPrompt` | String |
| User | `组装 Prompt.userPromptAbnormal` | String |

**LLM 输出模式（二选一）：**

| 方案 | 输出变量 | Dify 类型 | 说明 |
|------|----------|-----------|------|
| **推荐** | `text` | **String** | 关闭 structured output；节点 7 解析 JSON |
| 可选 | `structured_output` | **Object** | 开启 structured output，schema 见 §5.7.1；**不含 isNormal** |

**6A / 6B 输出变量：**

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `text` | String | 推荐方案；仅执行到的分支有值 |
| `structured_output` | Object | 可选方案；LLM 原始 Object（仍须经节点 7 写入 isNormal） |

#### 5.7.1 可选：LLM 统一 Structured Output Schema

与旧工作流字段一致，但不包含 `isNormal`：

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["checkName", "simulatedForDiseases", "resultItems", "conclusion", "notice"],
  "properties": {
    "checkName": { "type": "string" },
    "simulatedForDiseases": { "type": "array", "items": { "type": "string" } },
    "resultItems": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["itemName", "itemCode", "value", "unit", "referenceRange", "status", "meaning"],
        "properties": {
          "itemName": { "type": "string" },
          "itemCode": { "type": "string" },
          "value": {},
          "unit": { "type": "string" },
          "referenceRange": { "type": "string" },
          "status": { "type": "string" },
          "meaning": { "type": "string" }
        }
      }
    },
    "conclusion": { "type": "string" },
    "notice": { "type": "string" },
    "inflammationLevel": {
      "type": "string",
      "enum": ["normal", "mild", "moderate", "marked", "severe"]
    }
  }
}
```

---

### 5.8 节点 7：校验并修正

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `校验并修正` |
| 节点类型 | Code |
| 语言 | Python 3 |

**输入变量：**

| 变量名 | Dify 类型 | 来源 |
|--------|-----------|------|
| `llmTextNormal` | String | `正常结果生成.text`（6A 分支；未执行则为空） |
| `llmTextAbnormal` | String | `异常结果生成.text`（6B 分支；未执行则为空） |
| `isNormalBool` | String | `组装 Prompt.isNormalBool` |
| `resolvedCheckName` | String | `组装 Prompt.resolvedCheckName` |
| `defaultNotice` | String | `组装 Prompt.defaultNotice` |
| `defaultNormalConclusion` | String | `组装 Prompt.defaultNormalConclusion` |

> 6A、6B 都连到本节点。两路 LLM 只有一路有输出，代码取 `llmTextNormal or llmTextAbnormal`。若 LLM 用 structured output 方案，可在代码中改为读取 `structured_output` Object 再合并（仍须声明输入类型为 Object）。

**输出变量：**

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `structured_output` | **Object** | 最终业务 JSON；映射到 End §5.10 节点 9 |

**节点代码：**

```python
import json

ABNORMAL = {"high", "low", "positive", "abnormal"}

def parse_json(text):
    if text is None:
        return {}
    if isinstance(text, dict):
        return text
    s = str(text).strip()
    if not s:
        return {}
    try:
        return json.loads(s)
    except Exception:
        i, j = s.find("{"), s.rfind("}")
        if i >= 0 and j > i:
            try:
                return json.loads(s[i:j + 1])
            except Exception:
                pass
    return {}

def main(llmTextNormal, llmTextAbnormal, isNormalBool,
         resolvedCheckName, defaultNotice, defaultNormalConclusion):
    raw = llmTextNormal or llmTextAbnormal
    out = parse_json(raw)
    normal = str(isNormalBool).lower() == "true"

    out["checkName"] = out.get("checkName") or resolvedCheckName
    out["notice"] = out.get("notice") or defaultNotice
    out["isNormal"] = normal
    out["resultItems"] = out.get("resultItems") if isinstance(out.get("resultItems"), list) else []
    out["simulatedForDiseases"] = out.get("simulatedForDiseases") if isinstance(out.get("simulatedForDiseases"), list) else []

    if normal:
        for item in out["resultItems"]:
            if isinstance(item, dict):
                item["status"] = "normal"
        out["simulatedForDiseases"] = []
        out["conclusion"] = out.get("conclusion") or defaultNormalConclusion
    else:
        n = sum(1 for it in out["resultItems"]
                if isinstance(it, dict) and str(it.get("status", "")).lower() in ABNORMAL)
        if n < 2:
            out["_validationWarning"] = f"异常指标仅 {n} 项，建议重试"

    return {"structured_output": out}
```

---

### 5.9 节点 8：格式化错误输出（不支持分支）

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `格式化错误输出` |
| 节点类型 | Code |
| 语言 | Python 3 |

**输入变量：**

| 变量名 | Dify 类型 | 来源 |
|--------|-----------|------|
| `errorOutputJson` | String | `组装 Prompt.errorOutputJson` |

**输出变量：**

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `structured_output` | **Object** | 解析 JSON 字符串后的业务对象；映射到 End 节点 10 |

**节点代码：**

```python
import json

def main(errorOutputJson):
    try:
        obj = json.loads(errorOutputJson or "{}")
    except Exception:
        obj = {
            "checkName": "未知项目",
            "isNormal": True,
            "simulatedForDiseases": [],
            "resultItems": [],
            "conclusion": "配置解析失败",
            "notice": "请联系管理员",
        }
    return {"structured_output": obj}
```

---

### 5.10 节点 9 / 10：结束

| 配置项 | 节点 9 | 节点 10 |
|--------|--------|---------|
| 节点名称 | `结束-成功` | `结束-不支持` |
| 节点类型 | End | End |

**End 输出变量（两路字段名与类型必须一致）：**

| End 字段名 | Dify 类型 | 控制台选法 | 节点 9 来源 | 节点 10 来源 |
|------------|-----------|------------|-------------|--------------|
| `structured_output` | **Object** | 对象 | `校验并修正.structured_output` | `格式化错误输出.structured_output` |

**Dify 1.11.2 End 节点配置步骤：**

1. 输出模式选 **「结构化输出」**（不要选单一 `text` 字符串）。
2. 添加输出字段 `structured_output`，类型选 **Object**。
3. 值选择器分别指向上述来源节点。

后端 `CheckSimulationOutputMapper` 从 API `outputs.structured_output` 读取，与旧工作流键名一致。

---

## 6. 测试用例

| 编号 | 场景 | 输入 | 预期 |
|------|------|------|------|
| TC-01 | 血常规正常 | `techCode=XCG`, `isNormal=true` | 全部 status=normal；isNormal=true |
| TC-02 | 血常规异常-病毒 | `isNormal=false`, 疾病含「上呼吸道感染」 | ≥2 项 abnormal；LYMPH%↑ 等 |
| TC-03 | 血常规异常-细菌 | 疾病含「细菌性肺炎」 | WBC↑、NEUT%↑ |
| TC-04 | CRP 异常 | `techCode=CRP`, `isNormal=false` | CRP high；可有 inflammationLevel |
| TC-05 | 字段兼容 | 只传 `normal_status=false` | 走 6B 异常 LLM |
| TC-06 | 未配置项 | `checkName=未知` | resultItems=[]；友好 conclusion |
| TC-07 | HTTP 失败 | 关闭 API | 走节点 10，工作流不报错 |
| TC-08 | 医技联调 | 前端开关 + 提交 | structuredOutput 写入结果 JSON |

每种已配置检查：TC-01、TC-02 各跑 **5 次**，通过率 ≥ 95%。

---

## 7. 关联代码与实施清单

| 组件 | 路径 |
|------|------|
| 模拟入口 | `medtech-service/.../ai/CheckSimulationService.java` |
| Dify 客户端 | `medtech-service/.../ai/DifyWorkflowClient.java` |
| 输出解析 | `medtech-service/.../ai/CheckSimulationOutputMapper.java` |
| 疾病上下文 | `medtech-service/.../ai/CheckSimulationContextBuilder.java` |
| 前端 | `MedtechCheckStartPage.vue`、`MedtechInspectionStartPage.vue` |
| 类型定义 | `simulatedCheckResult.ts` |

### 7.1 后端待办

- [ ] `buildWorkflowInputs` 增加 `isNormal`、`techCode`
- [ ] 迁移 `035_simulation_config.sql` + 8 类初始 JSONB
- [ ] 实现 `GET /api/medtech/internal/simulation-config`
- [ ] 可选：`_validationWarning` 触发 retry

### 7.2 Dify 控制台待办

- [ ] 按第 5 节创建 11 个节点并连线
- [ ] Start 7 变量 **全部 String**；Code 输出变量类型与 §5 表格一致
- [ ] IF 条件：String **equals** `"true"` / `"false"`
- [ ] End：`structured_output` 类型 **Object**
- [ ] LLM temperature = **0.1**
- [ ] 发布工作流，更新 `DIFY_CHECK_SIMULATE_API_KEY`
- [ ] 跑 TC-01～TC-08

### 7.3 新增检查项（不改 Dify）

1. `medical_technology` 增加 `tech_code` / `tech_name`  
2. `simulation_config` 插入一条 JSONB（从 XCG 复制改）  
3. 调 API 验证 200  
4. 跑 TC-01 / TC-02  

---

## 8. 旧工作flow → v2.0 对照

| 旧问题 | v2.0 处理 |
|--------|-----------|
| 开关无效 | 节点 2 兼容 `normal_status` / `isNormal` |
| 要异常却正常 | 节点 6B + 配置里删冲突句 |
| 要正常却有异常 | 节点 2 清空 diseases + 节点 7 强制 normal |
| 8 个 LLM 难维护 | 2 个 LLM + DB 配置 |
| false 分支输出 workflow_id | 节点 10 输出标准 structured_output |

---

**实施顺序：** 后端表 + API（§3、§7.1）→ Dify 画布（§5）→ 切换 API Key → 下线旧工作流。
