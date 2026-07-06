# WF-Critical 危急值 AI 识别 · Dify 工作流接入实施规格（handoff）

> **用途**：指导 Dify「危急值 AI 识别」Workflow 编排与 `medtech-service` 联调。  
> **前提**：`migrate_036_critical_value.sql` 已执行；`critical_value_rule` 种子数据已入库；危急值闭环主流程（规则识别 → 复核上报 → SSE → 看板）可正常运行。  
> **设计文档**：本文档（编排细节见 **第 6 节**）  
> **适配平台**：Dify 1.11.2  
> **App 命名建议**：`WF-Critical 危急值AI识别`

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
2. 按 **第 6 节** 创建 **5 个节点**：开始 → 解析载荷 → LLM 研判 → 格式化输出 → 结束。
3. **无需 HTTP 节点**（不直连数据库；规则阈值已在 PostgreSQL `critical_value_rule` 表，由 Java 规则引擎处理）。
4. 发布工作流，复制 **API Key**（`app-xxx`）到 `.env`：

```bash
DIFY_WORKFLOW_CRITICAL_VALUE_DETECT=true
DIFY_API_KEY_CRITICAL_VALUE_DETECT=app-xxxxxxxx
```

5. 重启 `medtech-service`。
6. 用 **第 7 节** 测试用例在 Dify「运行」面板与医技执行页各测一遍。

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
| LLM 输出 | 推荐 String `text`（JSON 字符串），由 Code 解析 |
| End 出参 | `is_critical` 用 **String** `"true"`/`"false"`；`critical_items` 用 **Array[Object]** |
| 布尔判断 | IF 条件比较 String，不用 Boolean 节点类型 |

---

## 6. 节点详细设计（Dify 控制台逐步配置）

### 6.0 节点一览

| 序号 | 节点名称 | Dify 类型 | 作用 |
|------|----------|-----------|------|
| 1 | 开始 | Start | 接收 `tech_code`、`result_payload` |
| 2 | 解析载荷 | Code | 从 JSON 提取可读文本与上下文 |
| 3 | 危急值研判 | LLM | 医学判断是否存在危急值 |
| 4 | 格式化输出 | Code | 解析 LLM JSON，校验并输出 End 变量 |
| 5 | 结束 | End | 输出 `is_critical`、`critical_items`、`reason`、`summary` |

```text
1 开始 → 2 解析载荷 → 3 危急值研判 → 4 格式化输出 → 5 结束
```

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

### 6.3 节点 3：危急值研判（LLM）

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `危急值研判` |
| 节点类型 | LLM |
| 模型 | `deepseek-v4-pro` 或院内等价模型 |
| Temperature | **0.1** |
| 输出 | String `text`（JSON 字符串） |

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
1. 仅当存在明确或高度可疑的危急情况时判定为危急值；轻度异常、慢性改变、建议随访不算。
2. 若信息不足或仅为正常/阴性描述，判定为非危急值。
3. 输出必须是合法 JSON，不要 Markdown 代码块。

输出 JSON 格式：
{
  "is_critical": true或false,
  "severity": "CRITICAL"或"URGENT",
  "summary": "一句话概述",
  "reason": "判定理由",
  "critical_items": [
    {
      "item_name": "项目名称或发现",
      "value": "结果值或描述",
      "unit": "单位，无则空字符串",
      "reference_range": "参考范围或正常描述",
      "severity": "CRITICAL",
      "reason": "该项为何危急"
    }
  ]
}

当 is_critical=false 时，critical_items 必须为 []。
当 is_critical=true 时，critical_items 至少 1 条。
```

**User Prompt：**

```text
请判断以下医技结果是否存在危急值：

{{解析载荷.raw_text}}

若 payload 无效或内容为空，请返回 is_critical=false。
```

**输入变量绑定：**

| Prompt 占位 | 来源 |
|-------------|------|
| `{{解析载荷.raw_text}}` | 节点 2 `raw_text` |

---

### 6.4 节点 4：格式化输出

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `格式化输出` |
| 节点类型 | Code |
| 语言 | Python 3 |

**输入变量：**

| 变量名 | Dify 类型 | 来源 |
|--------|-----------|------|
| `llm_text` | String | `危急值研判.text` |
| `payload_valid` | String | `解析载荷.payload_valid` |
| `exam_name` | String | `解析载荷.exam_name` |

**输出变量：**

| 变量名 | Dify 类型 | 说明 |
|--------|-----------|------|
| `is_critical` | String | `"true"` / `"false"`（后端兼容） |
| `critical_items` | **Array[Object]** | 危急项数组 |
| `reason` | String | 总体理由 |
| `summary` | String | 一句话摘要 |

**节点代码（可直接粘贴）：**

```python
import json
import re

def clean(v):
    return "" if v is None else str(v).strip()

def parse_llm_json(text):
    raw = clean(text)
    if not raw:
        return {}
    raw = re.sub(r"^```(?:json)?\s*", "", raw)
    raw = re.sub(r"\s*```$", "", raw)
    try:
        return json.loads(raw)
    except Exception:
        return {}

def normalize_item(row, exam_name):
    if not isinstance(row, dict):
        return None
    name = clean(row.get("item_name") or row.get("itemName")) or exam_name or "AI识别危急值"
    return {
        "item_name": name,
        "value": clean(row.get("value")),
        "unit": clean(row.get("unit")),
        "reference_range": clean(row.get("reference_range") or row.get("referenceRange")),
        "severity": clean(row.get("severity")) or "CRITICAL",
        "reason": clean(row.get("reason")),
    }

def main(llm_text, payload_valid, exam_name):
    if clean(payload_valid) != "true":
        return {
            "is_critical": "false",
            "critical_items": [],
            "reason": "",
            "summary": "无有效结果内容",
        }

    data = parse_llm_json(llm_text)
    is_critical = bool(data.get("is_critical"))
    items = data.get("critical_items") or data.get("criticalItems") or []
    normalized = []
    if isinstance(items, list):
        for row in items:
            item = normalize_item(row, exam_name)
            if item:
                normalized.append(item)

    if is_critical and not normalized:
        normalized.append({
            "item_name": exam_name or "AI识别危急值",
            "value": "",
            "unit": "",
            "reference_range": "",
            "severity": clean(data.get("severity")) or "CRITICAL",
            "reason": clean(data.get("reason") or data.get("summary")),
        })

    if not is_critical:
        normalized = []

    return {
        "is_critical": "true" if is_critical else "false",
        "critical_items": normalized,
        "reason": clean(data.get("reason")),
        "summary": clean(data.get("summary")),
    }
```

---

### 6.5 节点 5：结束

| 配置项 | 值 |
|--------|-----|
| 节点名称 | `结束` |
| 节点类型 | End |

**输出变量（与 Java 解析对齐）：**

| 变量名 | Dify 类型 | 来源 | Java 读取字段 |
|--------|-----------|------|---------------|
| `is_critical` | String | `格式化输出.is_critical` | `is_critical` / `isCritical` |
| `critical_items` | Array[Object] | `格式化输出.critical_items` | `critical_items` / `criticalItems` |
| `reason` | String | `格式化输出.reason` | 兜底 `reason` |
| `summary` | String | `格式化输出.summary` | 兜底 `summary` |

**后端解析逻辑（`CriticalValueDetector.detectFromAi`）摘要：**

- `is_critical` 为 `true` / `"true"` / Boolean true 时视为命中。
- `critical_items` 每项读取 `item_name`/`itemName`、`value`、`unit`、`reference_range`/`referenceRange`、`reason`、`severity`。
- 若 `is_critical=true` 但 `critical_items` 为空，后端用 `reason`/`summary` 生成一条默认命中项。

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

**期望 End 输出：** `is_critical=true`，`critical_items` 至少 1 条，含「脑出血」相关描述。

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
| Dify 返回成功但后端无命中 | 检查 End 变量名是否为 `is_critical`、`critical_items`（下划线命名） |
| `critical_items` 后端解析为空 | End 必须输出 **Array[Object]**，不要只输出 JSON 字符串 |
| 工作流 succeeded 但结论离谱 | 调低 Temperature；在 System Prompt 补充本院危急值目录 |
| 与模拟检查共用 Key | **不要共用**；本 Workflow 需独立 API Key |

---

*文档版本：v1.0 · 与 `medtech-service` migrate_036 / `CriticalValueDetector` 对齐*
