# WF-Critical 危急值 AI 识别 · Dify 工作流接入实施规格（handoff）

> **用途**：指导 Dify「危急值 AI 识别」Workflow 编排与 `medtech-service` 联调。  
> **前提**：`migrate_036_critical_value.sql` 已执行；`critical_value_rule` 种子数据已入库；危急值闭环主流程（规则识别 → 复核上报 → SSE → 看板）可正常运行。  
> **设计文档**：本文档（编排细节见 **第 6 节**）  
> **适配平台**：Dify 1.11.2  
> **App 命名建议**：`WF-Critical 危急值AI识别`  
> **文档版本**：v1.1（LLM 改为**结构化输出**推荐方案，与 WF-05/WF-06 对齐）

---

## 0. 任务一句话

将 Dify Workflow 接入 `medtech-service` 的 `CriticalValueDetector`，在**规则引擎未命中**时，对影像报告、文字描述、非结构化结果做 AI 危急值兜底研判，输出 `is_critical` + `critical_items`，供医技端弹出「危急值复核确认」对话框。

---

## 1. 已完成（代码侧）

| 类别 | 内容 | 关键路径 |
|------|------|----------|
| DB | `critical_value_alert` / `critical_value_rule` | `docker/init-db/migrate_036_critical_value.sql` |
| 规则引擎 | 血钾、血糖、肌钙蛋白等阈值比对 | `CriticalValueDetector.java`、`CriticalValueRuleMapper` |
| Dify 客户端 | `runCriticalValueDetectBlocking` | `DifyWorkflowClient.java` |
| 开关配置 | `workflow-critical-value-detect` / `api-key-critical-value-detect` | `DifyAiProperties.java`、`application.yml` |
| 调用时机 | 仅规则未命中时调 AI；失败静默降级 | `CriticalValueDetector.detectFromAi()` |
| 结果挂载 | 检查/检验/处置 submit 返回 `criticalDetect` | `MedtechService.attachCriticalDetect()` |
| 前端复核 | 医技弹窗确认后 `POST /critical-value/report` | `CriticalValueConfirmDialog.vue` |
| 配置占位 | `DIFY_WORKFLOW_CRITICAL_VALUE_DETECT`、`DIFY_API_KEY_CRITICAL_VALUE_DETECT` | `.env`、`application.yml` |

**未配置 API Key 或开关未开时**：自动跳过 AI，仅走规则引擎，**不影响**结果提交与闭环流程。

---

## 1.1 上线前剩余操作（运维 / 你本地填 Key）

| 步骤 | 说明 | 状态 |
|------|------|------|
| 1 | Dify 控制台按 **第 6 节** 新建 Workflow App | ⬜ 待做 |
| 2 | 发布工作流，复制 API Key 到 `.env` | ⬜ 待填 |
| 3 | `.env` 设置 `DIFY_WORKFLOW_CRITICAL_VALUE_DETECT=true` | ⬜ 待填 |
| 4 | 确认 `DIFY_ENABLED=true`、`DIFY_BASE_URL` 指向自托管 Dify | 确认 |
| 5 | 重启 `medtech-service`，日志无 `危急值 AI 识别失败` 即可 | ⬜ Key 填完后 |
| 6 | 医技提交**非数值型**结果（如影像结论含「脑出血」）→ 弹复核框 → `detectSource: ai` | 可测 |

---

## 2. Dify 控制台编排步骤（速览）

1. 新建 **Workflow App**，命名：`WF-Critical 危急值AI识别`。
2. 按 **第 6 节** 创建节点（推荐 7 个：含 IF 与空结果分支；可简化为 5 个）。
3. **LLM 节点必须开启结构化输出**（§6.4.2 JSON Schema），不要用 `text` 模式。
4. **无需 HTTP 节点**（不直连数据库；规则阈值已在 PostgreSQL `critical_value_rule` 表，由 Java 规则引擎处理）。
5. 发布工作流，复制 **API Key**（`app-xxx`）到 `.env`：

```bash
DIFY_WORKFLOW_CRITICAL_VALUE_DETECT=true
DIFY_API_KEY_CRITICAL_VALUE_DETECT=app-xxxxxxxx
```

6. 重启 `medtech-service`。
7. 用 **第 7 节** 测试用例在 Dify「运行」面板与医技执行页各测一遍。

---

## 3. 环境变量清单

| 变量 | 说明 |
|------|------|
| `DIFY_ENABLED` | `true` |
| `DIFY_BASE_URL` | 自托管 Dify 根地址（**无** `/v1` 后缀） |
| `DIFY_WORKFLOW_CRITICAL_VALUE_DETECT` | `true` / `1` / `yes` / `on` 表示启用 |
| `DIFY_API_KEY_CRITICAL_VALUE_DETECT` | 本 Workflow App 专用 API Key |
| `DIFY_CONNECT_TIMEOUT_MS` | 可选，默认 30000 |
| `DIFY_READ_TIMEOUT_MS` | 可选，默认 300000 |

与模拟检查/检验共用 `DIFY_BASE_URL`，**API Key 独立**（一个 Workflow 一个 Key）。

---

## 4. 联调检查清单

- [ ] TC-001：影像报告含「急性脑出血」→ `is_critical=true`，`critical_items` 非空
- [ ] TC-002：正常胸片描述「未见明显异常」→ `is_critical=false`
- [ ] TC-003：检验数值已在规则表命中（如血钾 2.1）→ **不调 Dify**，`detectSource=rule`
- [ ] TC-004：检验数值未命中规则，但 `resultText` 描述危急 → `detectSource=ai`
- [ ] TC-005：空 `result_payload` / `{}` → `is_critical=false`，工作流不报错
- [ ] TC-006：Dify 超时或 Key 错误 → 后端静默降级，医技仍可正常提交结果
- [ ] TC-007：医技确认上报后，医生端收到强制弹窗，看板有记录
- [ ] `.env` 已填 Key 且 `medtech-service` 重启

---

## 5. 工作流定位与原则

### 5.1 本工作流负责

- 阅读医技结果 JSON（含影像描述、结论、自由文本、结构化明细等）。
- 按**临床危急值**常识判断是否需立即通知临床医生。
- 输出结构化 `is_critical` 与 `critical_items` 列表。

### 5.2 本工作流不负责

- 数值型检验指标的阈值比对（已由 `critical_value_rule` + Java 规则引擎完成）。
- 自动上报危急值（必须医技人工复核确认）。
- 推送医生、签收、处置、看板（由 Java 业务层完成）。
- 直连 PostgreSQL 或调用内部 HTTP API。

### 5.3 与规则引擎的关系

```text
医技 submit 结果
    ↓
Java 规则引擎（critical_value_rule）
    ├─ 命中 → 返回 criticalDetect（detectSource=rule）→ 结束，不调 Dify
    └─ 未命中 → 若 Dify 已配置 → 调用本 Workflow
                    ├─ is_critical=true → criticalDetect（detectSource=ai）
                    └─ false / 失败 → 无危急值
```

### 5.4 Dify 1.11.2 类型约定

| 约定 | 说明 |
|------|------|
| Start 入参 | **全部 String**（含 JSON 载荷） |
| LLM 输出 | **推荐结构化输出（JSON Schema）**，字段直接为 Boolean / String / Array[Object] |
| Code 节点 | 读取 LLM 结构化字段做校验归一化；**不再** `json.loads(text)` |
| End 出参 | `is_critical` 用 **Boolean**；`critical_items` 用 **Array[Object]** |
| IF 条件 | 比较 String（如 `payload_valid` 等于 `"true"`） |
| 后端兼容 | `CriticalValueDetector` 已支持 Boolean `is_critical` 与 List `critical_items` |

### 5.5 为什么推荐 LLM 结构化输出（而非 text）

本工作流输出包含 **嵌套数组 `critical_items[]`**，与 WF-05（门诊确诊）、WF-06（智能荐药）同类，**应使用结构化输出**：

| 对比项 | text + Code 解析（v1.0） | 结构化输出（v1.1 推荐） |
|--------|--------------------------|-------------------------|
| 稳定性 | 易出现 Markdown 代码块、多余说明文字 | Schema 约束，字段类型明确 |
| 下游引用 | 必须经 Code `json.loads` | 直接引用 `危急值研判.is_critical`、`危急值研判.critical_items` |
| 数组类型 | 需手动解析为 Array[Object] | Dify 直接输出 **Array[Object]** |
| 项目惯例 | 模拟检查因需 Code 强制改 `isNormal` 才用 text | 确诊/荐药/排班均已用结构化输出 |

> **说明**：WF-MedTech「模拟检查检验」工作流使用 `text`，是因为节点 7 要**强制覆盖** `isNormal` 与各指标 `status`，与 LLM 原始输出不同。本工作流**不需要**覆盖 LLM 布尔判断，因此更适合结构化输出。

**End 节点注意**：后端 `CriticalValueDetector` 从 API `outputs` **顶层**读取 `is_critical`、`critical_items`（不是嵌套在 `structured_output` 对象里）。因此 End 请映射**平铺 4 个字段**，不要只输出一个 `structured_output` 包装对象（除非后续改 Java 解包逻辑）。

---

## 6. 节点详细设计（Dify 控制台逐步配置）

### 6.0 节点一览

| 序号 | 节点名称 | Dify 类型 | 作用 |
|------|----------|-----------|------|
| 1 | 开始 | Start | 接收 `tech_code`、`result_payload` |
| 2 | 解析载荷 | Code | 从 JSON 提取可读文本与上下文 |
| 3 | 是否有效载荷 | IF / 条件分支 | `payload_valid` 为 `"true"` 才调 LLM（可选但推荐） |
| 4 | 危急值研判 | LLM | **结构化输出**医学判断结果 |
| 5 | 校验归一化 | Code | 字段归一化、空项兜底、清空非危急项 |
| 6 | 空结果输出 | Code | 无效载荷时直接返回非危急（接 IF false 分支） |
| 7 | 结束 | End | 平铺输出 `is_critical`、`critical_items`、`reason`、`summary` |

**推荐拓扑（含 IF，省 Token）：**

```text
1 开始 → 2 解析载荷 → 3 是否有效载荷
                          ├─ true  → 4 危急值研判 → 5 校验归一化 → 7 结束
                          └─ false → 6 空结果输出 ────────────────→ 7 结束
```

**简化拓扑（不用 IF，由节点 5 内部短路）：**

```text
1 开始 → 2 解析载荷 → 4 危急值研判 → 5 校验归一化 → 7 结束
```

### 6.0.1 全工作流变量类型速查

| 节点 | 变量名 | Dify 类型 | 方向 |
|------|--------|-----------|------|
| 开始 | `tech_code`、`result_payload` | String | 入 → 出 |
| 解析载荷 | `raw_text`、`payload_valid` 等 | String | 出 |
| 危急值研判 | `is_critical` | **Boolean** | 出 |
| 危急值研判 | `severity`、`summary`、`reason` | String | 出 |
| 危急值研判 | `critical_items` | **Array[Object]** | 出 |
| 校验归一化 | `is_critical` | **Boolean** | 出 |
| 校验归一化 | `critical_items` | **Array[Object]** | 出 |
| 结束 | 同上 4 字段 | 与校验归一化一致 | 工作流最终输出 |

---

### 6.1 节点 1：开始

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `开始` |
| 节点类型 | Start |

**输入变量（全部 String，与后端一致）：**

| 变量名 | 必填 | 控制台选法 | 说明 | 示例 |
|--------|------|------------|------|------|
| `tech_code` | 否 | 短文本 | 医技项目编码 | `XCG` |
| `result_payload` | 是 | 段落 | 完整结果 JSON 字符串 | 见 §6.1.1 |

**后端组装（`CriticalValueDetector.detectFromAi`）：**

```java
inputs.put("tech_code", techCode != null ? techCode : "");
inputs.put("result_payload", objectMapper.writeValueAsString(resultData));
```

#### 6.1.1 `result_payload` 常见结构

后端传入的是医技 `submit` 请求体 + 业务字段的 JSON，可能包含：

| 字段 | 场景 | 说明 |
|------|------|------|
| `values` | 动态表单 | 键值对，规则引擎已扫过 |
| `structuredOutput` | 模拟检验/检查 | 含 `resultItems[]`、`conclusion` |
| `resultText` | 文本结论 | 影像/检查报告原文 |
| `checkResult` / `inspectionResult` | 兼容字段 | 结果文本 |
| `disposalResult` | 处置 | 处置结果描述 |

**示例（影像型）：**

```json
{
  "values": {},
  "structuredOutput": {
    "checkName": "头颅CT",
    "conclusion": "右侧基底节区高密度影，考虑急性脑出血，建议立即临床处理。",
    "resultItems": []
  },
  "resultText": "右侧基底节区高密度影，考虑急性脑出血。"
}
```

**示例（检验型，规则未命中时的兜底）：**

```json
{
  "values": { "customField": "阳性" },
  "structuredOutput": {
    "checkName": "尿常规",
    "conclusion": "尿蛋白++++，镜检大量红细胞，提示肾小球源性出血可能。",
    "resultItems": [
      { "itemName": "尿蛋白", "value": "++++", "unit": "", "referenceRange": "阴性" }
    ]
  }
}
```

---

### 6.2 节点 2：解析载荷

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `解析载荷` |
| 节点类型 | Code |
| 语言 | Python 3 |

**输入变量：**

| 变量名 | Dify 类型 | 来源 |
|--------|-----------|------|
| `tech_code` | String | `开始.tech_code` |
| `result_payload` | String | `开始.result_payload` |

**输出变量（须在 Code 面板声明类型）：**

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `tech_code` | String | trim 后 |
| `exam_name` | String | 从 structuredOutput.checkName 等提取 |
| `conclusion` | String | 结论/报告摘要 |
| `result_items_text` | String | 结构化指标格式化文本 |
| `raw_text` | String | 合并后的完整可读文本，供 LLM 阅读 |
| `payload_valid` | String | `"true"` / `"false"` |

**节点代码（可直接粘贴）：**

```python
import json

def clean(v):
    return "" if v is None else str(v).strip()

def main(tech_code, result_payload):
    tech_code = clean(tech_code)
    raw = clean(result_payload)
    exam_name = ""
    conclusion = ""
    items_lines = []

    data = {}
    if raw:
        try:
            data = json.loads(raw)
        except Exception:
            data = {}

    if isinstance(data, dict):
        so = data.get("structuredOutput") or data.get("structured_output") or {}
        if isinstance(so, dict):
            exam_name = clean(so.get("checkName") or so.get("techName"))
            conclusion = clean(so.get("conclusion"))
            items = so.get("resultItems") or so.get("result_items") or []
            if isinstance(items, list):
                for row in items:
                    if not isinstance(row, dict):
                        continue
                    name = clean(row.get("itemName") or row.get("item_name"))
                    val = clean(row.get("value"))
                    unit = clean(row.get("unit"))
                    ref = clean(row.get("referenceRange") or row.get("reference_range"))
                    if name:
                        items_lines.append(f"- {name}: {val} {unit}（参考: {ref}）".strip())

        for key in ("resultText", "checkResult", "inspectionResult", "disposalResult", "result"):
            text = clean(data.get(key))
            if text and text not in conclusion:
                conclusion = (conclusion + "\n" + text).strip() if conclusion else text

        values = data.get("values")
        if isinstance(values, dict) and values:
            for k, v in values.items():
                items_lines.append(f"- {k}: {v}")

    raw_text_parts = []
    if exam_name:
        raw_text_parts.append(f"检查/项目：{exam_name}")
    if tech_code:
        raw_text_parts.append(f"医技编码：{tech_code}")
    if conclusion:
        raw_text_parts.append(f"结论/报告：\n{conclusion}")
    if items_lines:
        raw_text_parts.append("指标明细：\n" + "\n".join(items_lines))

    raw_text = "\n\n".join(raw_text_parts).strip()
    payload_valid = "true" if raw_text else "false"

    return {
        "tech_code": tech_code,
        "exam_name": exam_name,
        "conclusion": conclusion,
        "result_items_text": "\n".join(items_lines),
        "raw_text": raw_text,
        "payload_valid": payload_valid,
    }
```

---

### 6.3 节点 3：是否有效载荷（IF，可选）

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `是否有效载荷` |
| 节点类型 | IF / 条件分支 |

| 条件 | 分支 |
|------|------|
| `解析载荷.payload_valid` **等于** `"true"` | → 节点 4 危急值研判 |
| ELSE | → 节点 6 空结果输出 |

> 若希望画布更简单，可删除本节点，由节点 5 在 `payload_valid != "true"` 时直接返回非危急。

---

### 6.4 节点 4：危急值研判（LLM · 结构化输出）

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `危急值研判` |
| 节点类型 | LLM |
| 模型 | `deepseek-v4-pro` 或院内等价模型 |
| Temperature | **0.1** |
| 输出方式 | **结构化输出（Structured Output）** |

#### 6.4.1 Dify 配置步骤

1. 打开 LLM 节点 → **输出** → 选择 **结构化输出**。
2. 切换到 **JSON Schema 源码模式**，粘贴 §6.4.2 Schema。
3. **不要**在 Schema 中写 `"additionalProperties": false`（参考 WF-01/WF-05 踩坑说明）。
4. 保存后，Dify 将自动暴露 `is_critical`、`critical_items` 等字段供下游引用。
5. System / User Prompt 中**删除**「输出必须是合法 JSON」等字样——由 Schema 约束即可。

#### 6.4.2 结构化输出 JSON Schema

```json
{
  "type": "object",
  "properties": {
    "is_critical": {
      "type": "boolean",
      "description": "是否存在需要立即通知临床医生的危急值"
    },
    "severity": {
      "type": "string",
      "description": "CRITICAL 或 URGENT"
    },
    "summary": {
      "type": "string",
      "description": "一句话概述"
    },
    "reason": {
      "type": "string",
      "description": "总体判定理由"
    },
    "critical_items": {
      "type": "array",
      "description": "is_critical=true 时至少 1 条；false 时必须为空数组",
      "items": {
        "type": "object",
        "properties": {
          "item_name": {
            "type": "string",
            "description": "项目名称或影像发现"
          },
          "value": {
            "type": "string",
            "description": "结果值或描述，无则空字符串"
          },
          "unit": {
            "type": "string",
            "description": "单位，无则空字符串"
          },
          "reference_range": {
            "type": "string",
            "description": "参考范围或正常描述"
          },
          "severity": {
            "type": "string",
            "description": "CRITICAL 或 URGENT"
          },
          "reason": {
            "type": "string",
            "description": "该项为何危急"
          }
        }
      }
    }
  },
  "required": [
    "is_critical",
    "severity",
    "summary",
    "reason",
    "critical_items"
  ]
}
```

#### 6.4.3 LLM 结构化输出字段（下游可引用）

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `is_critical` | **Boolean** | 是否危急值 |
| `severity` | String | `CRITICAL` / `URGENT` |
| `summary` | String | 一句话概述 |
| `reason` | String | 总体理由 |
| `critical_items` | **Array[Object]** | 危急项列表 |

**System Prompt：**

```text
你是医院信息系统中的「危急值识别」助手，服务于医技科室与临床危急值闭环。

任务：根据医技结果文本，判断是否属于需要立即通知开单医生的临床危急值。

危急值示例（非穷举）：
- 检验：血钾<2.5或>6.5 mmol/L、血糖<2.2或>22.2、肌钙蛋白显著升高、血小板<20×10^9/L
- 影像：急性脑出血、主动脉夹层、肺栓塞、张力性气胸、消化道穿孔
- 心电/监护：室颤、无脉室速、显著长 QT 伴室性心律失常
- 微生物：血培养阳性（需结合上下文）

注意：
1. 仅当存在明确或高度可疑的危急情况时判定 is_critical=true；轻度异常、慢性改变、建议随访不算。
2. 若信息不足或仅为正常/阴性描述，is_critical=false 且 critical_items=[]。
3. is_critical=true 时 critical_items 至少 1 条；false 时 critical_items 必须为空数组。
4. 字段名使用 snake_case：item_name、reference_range。
```

**User Prompt：**

```text
请判断以下医技结果是否存在危急值：

{{解析载荷.raw_text}}
```

**输入变量绑定：**

| Prompt 占位 | 来源 |
|-------------|------|
| `{{解析载荷.raw_text}}` | 节点 2 `raw_text` |

---

### 6.5 节点 5：校验归一化（Code）

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `校验归一化` |
| 节点类型 | Code |
| 语言 | Python 3 |

**作用**：在 LLM 结构化输出基础上做确定性兜底（空载荷短路、字段名兼容、危急项至少 1 条），**不再解析 `text` JSON**。

**输入变量：**

| 变量名 | Dify 类型 | 来源 |
|--------|-----------|------|
| `payload_valid` | String | `解析载荷.payload_valid` |
| `exam_name` | String | `解析载荷.exam_name` |
| `is_critical` | **Boolean** | `危急值研判.is_critical` |
| `severity` | String | `危急值研判.severity` |
| `summary` | String | `危急值研判.summary` |
| `reason` | String | `危急值研判.reason` |
| `critical_items` | **Array[Object]** | `危急值研判.critical_items` |

**输出变量：**

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `is_critical` | **Boolean** | 最终是否危急 |
| `critical_items` | **Array[Object]** | snake_case 归一化后的数组 |
| `reason` | String | 总体理由 |
| `summary` | String | 一句话摘要 |

**节点代码（可直接粘贴）：**

```python
def clean(v):
    return "" if v is None else str(v).strip()

def as_bool(v):
    if isinstance(v, bool):
        return v
    if v is None:
        return False
    return clean(v).lower() in ("true", "1", "yes")

def normalize_item(row, exam_name, default_severity):
    if not isinstance(row, dict):
        return None
    name = clean(row.get("item_name") or row.get("itemName")) or exam_name or "AI识别危急值"
    return {
        "item_name": name,
        "value": clean(row.get("value")),
        "unit": clean(row.get("unit")),
        "reference_range": clean(row.get("reference_range") or row.get("referenceRange")),
        "severity": clean(row.get("severity")) or default_severity or "CRITICAL",
        "reason": clean(row.get("reason")),
    }

def main(payload_valid, exam_name, is_critical, severity, summary, reason, critical_items):
    if clean(payload_valid).lower() != "true":
        return {
            "is_critical": False,
            "critical_items": [],
            "reason": "",
            "summary": "无有效结果内容",
        }

    critical = as_bool(is_critical)
    default_severity = clean(severity) or "CRITICAL"
    normalized = []

    items = critical_items if isinstance(critical_items, list) else []
    for row in items:
        item = normalize_item(row, exam_name, default_severity)
        if item:
            normalized.append(item)

    if critical and not normalized:
        normalized.append({
            "item_name": exam_name or "AI识别危急值",
            "value": "",
            "unit": "",
            "reference_range": "",
            "severity": default_severity,
            "reason": clean(reason) or clean(summary),
        })

    if not critical:
        normalized = []

    return {
        "is_critical": critical,
        "critical_items": normalized,
        "reason": clean(reason),
        "summary": clean(summary),
    }
```

---

### 6.6 节点 6：空结果输出（Code，接 IF false 分支）

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `空结果输出` |
| 节点类型 | Code |

**输入变量：** 无（或占位 `exam_name` 来自解析载荷）

**输出变量：** 与节点 5 相同（`is_critical` Boolean、`critical_items` Array 等）

```python
def main():
    return {
        "is_critical": False,
        "critical_items": [],
        "reason": "",
        "summary": "无有效结果内容",
    }
```

---

### 6.7 节点 7：结束

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `结束` |
| 节点类型 | End |

**输出变量（平铺映射，与 Java 顶层字段对齐）：**

| 变量名 | Dify 类型 | 来源 | Java 读取 |
|--------|-----------|------|-----------|
| `is_critical` | **Boolean** | `校验归一化.is_critical` 或 `空结果输出.is_critical` | `is_critical` / `isCritical` |
| `critical_items` | **Array[Object]** | 同上 `.critical_items` | `critical_items` / `criticalItems` |
| `reason` | String | 同上 `.reason` | 兜底 `reason` |
| `summary` | String | 同上 `.summary` | 兜底 `summary` |

**后端解析逻辑（`CriticalValueDetector.detectFromAi`）摘要：**

- `is_critical` 支持 **Boolean `true`** 或字符串 `"true"`。
- `critical_items` 必须是 **JSON 数组**（List），不能是 JSON 字符串。
- 若 `is_critical=true` 但 `critical_items` 为空，后端用 `reason`/`summary` 生成默认命中项。

---

## 7. 测试用例（Dify 运行面板）

在 Dify「运行」中直接填写 Start 变量进行测试。

### TC-001 急性脑出血（应命中）

`tech_code`:

```text
CT_HEAD
```

`result_payload`:

```json
{
  "structuredOutput": {
    "checkName": "头颅CT",
    "conclusion": "右侧基底节区高密度影，考虑急性脑出血，建议立即通知临床医生。"
  },
  "resultText": "考虑急性脑出血"
}
```

**期望 End 输出：** `is_critical` 为 `true`（Boolean），`critical_items` 至少 1 条，含「脑出血」相关描述。

---

### TC-002 正常胸片（不应命中）

`result_payload`:

```json
{
  "structuredOutput": {
    "checkName": "胸部X线",
    "conclusion": "双肺纹理清晰，心影大小形态正常，未见明显实质性病变。"
  }
}
```

**期望：** `is_critical=false`，`critical_items=[]`。

---

### TC-003 空载荷（不应命中）

`result_payload`:

```json
{}
```

**期望：** `is_critical=false`，工作流 `succeeded`。

---

### TC-004 定性检验异常（应命中）

`result_payload`:

```json
{
  "structuredOutput": {
    "checkName": "血培养",
    "conclusion": "血培养阳性，检出金黄色葡萄球菌，提示血流感染可能。"
  }
}
```

**期望：** `is_critical=true`（微生物阳性按院内制度可报危急值）。

---

## 8. 与规则引擎差异速查

| 项 | 规则引擎 | 本 Dify Workflow |
|----|----------|------------------|
| 触发时机 | submit 后首先执行 | 仅规则未命中时 |
| 数据源 | `critical_value_rule` 表 | LLM 阅读 `result_payload` |
| 适用场景 | 结构化数值（血钾、血糖等） | 影像/文字/定性描述 |
| 输出字段 | `CriticalItemHit` | 同结构，经 `detectSource=ai` 返回 |
| 配置 | SQL 种子 + 表维护 | Dify App + `.env` Key |
| 失败行为 | 无命中 | 静默降级，不影响提交 |

---

## 9. 常见问题

| 问题 | 处理 |
|------|------|
| 医技提交后从不出现 AI 命中 | 检查 Key/开关；多数数值异常已被规则引擎拦截，应看 `detectSource=rule` |
| Dify 返回成功但后端无命中 | 检查 End 是否**平铺**输出 `is_critical`、`critical_items`（勿只包在 `structured_output` 内） |
| `critical_items` 后端解析为空 | End / LLM 结构化输出必须是 **Array[Object]**，不能是 JSON 字符串 |
| LLM 结构化输出校验失败 | 检查 Schema 是否误加 `additionalProperties: false`；放宽 `required` 或重试 |
| Dify 把 `critical_items` 落成 String | 检查 LLM 是否误用 text 模式；应改结构化输出 |
| 工作流 succeeded 但结论离谱 | 调低 Temperature；在 System Prompt 补充本院危急值目录 |
| 与模拟检查共用 Key | **不要共用**；本 Workflow 需独立 API Key |

---

## 10. 附录：text 模式（v1.0 遗留，不推荐）

若 Dify 版本不支持结构化输出，可回退为 LLM 输出 `text` + Code `json.loads`。该方案易遇 Markdown 包裹、字段缺失等问题，**仅作兜底**。v1.1 起请以 §6.4 结构化输出为准。

---

*文档版本：v1.1 · LLM 结构化输出 · 与 `medtech-service` migrate_036 / `CriticalValueDetector` 对齐*
