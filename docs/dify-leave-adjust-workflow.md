# Dify 智能请假替班推荐工作流设计

> **用途**：本文档描述一个可直接在 Dify 云端复刻的 7 节点工作流，用于医生请假时智能推荐替班医生。
> **设计原则**：开始节点入参 100% 是 string（兼容所有 Dify 版本）；复合数据通过 HTTP 节点中段注入；输出 JSON-in-String 便于后端解析。
> **版本**：v1.0 · **日期**：2026-07-02

---

## 一、整体工作流图

```
┌─────────┐    ┌─────────┐    ┌──────────┐    ┌─────────┐
│ 1.Start │───▶│2.LLM    │───▶│3.HTTP    │───▶│4.Code   │
│ 入参    │    │理解请假 │    │拉上下文  │    │压缩数据 │
│ (全     │    │         │    │(回调后端 │    │         │
│  string)│    │         │    │ API)     │    │         │
└─────────┘    └─────────┘    └──────────┘    └─────────┘
                                                    │
                                                    ▼
               ┌─────────┐    ┌──────────┐    ┌─────────┐
               │7.End    │◀───│6.Code    │◀───│5.LLM    │
               │输出     │    │守门员校验│    │推荐替班 │
               │JSON str │    │(防幻觉)  │    │         │
               └─────────┘    └──────────┘    └─────────┘
```

---

## 二、节点 1：Start（开始节点）

### 2.1 输入变量定义

| 变量名 | 类型 | 必填 | 最大长度 | 说明 |
|--------|------|------|---------|------|
| `leave_id` | text-input | ✅ | 20 | 请假记录 ID（数字字符串） |
| `leave_summary` | paragraph | ✅ | 500 | 请假自然语言摘要 |
| `candidates_brief` | paragraph | ✅ | 2000 | 候选医生清单（管道分隔） |
| `affected_count` | text-input | ❌ | 10 | 影响患者数（数字字符串） |

> **为什么全用 string**：Dify 部分版本/模式的开始节点不支持 Object/Array 入参；即使支持，外部调用 Dify API 时序列化复杂类型容易出错。**全 string 是最稳妥的协议**。

### 2.2 调用示例（API 触发）

```bash
curl -X POST 'https://api.dify.ai/v1/workflows/run' \
  -H 'Authorization: Bearer app-xxxx' \
  -H 'Content-Type: application/json' \
  -d '{
    "inputs": {
      "leave_id": "123",
      "leave_summary": "2026-07-05 上午 心血管内科 医生456 因感冒发烧请病假",
      "candidates_brief": "医生456(456,本周3班,上午空)|医生457(457,本周0班,全天空)|医生458(458,本周2班,下午空)",
      "affected_count": "15"
    },
    "response_mode": "blocking",
    "user": "schedule-service"
  }'
```

### 2.3 字段填充规范

#### `leave_summary`（请假摘要）
格式：`{日期} {时段} {科室} 医生{ID} 因{原因} 请{类型}`

示例：
```
2026-07-05 上午 心血管内科 医生456 因感冒发烧请病假
2026-07-08 下午 神经外科 医生789 因家事请事假
```

#### `candidates_brief`（候选清单，关键格式）
**格式**：每个候选用 `|` 分隔，候选内部字段用 `,` 分隔，ID 用 `()` 包裹

```
{name}({id},{weeklyLoad字段},{available字段})|{name}({id},{weeklyLoad字段},{available字段})
```

**为什么用 `()` 包裹 ID**：节点 6 守门员用正则 `r'\((\d+),'` 提取合法 ID 集合，括号是关键标识。

示例：
```
医生456(456,本周3班,上午空)|医生457(457,本周0班,全天空)|医生458(458,本周2班,下午空)
```

> **这是防幻觉的源头**：后端 SQL 预筛过的候选 ID 才会出现在这里，LLM 只能从这个清单里选。

---

## 三、节点 2：LLM 理解请假语义（结构化输出）

### 3.1 节点配置

| 项 | 值 |
|----|------|
| 节点类型 | LLM |
| 模型 | gpt-4o-mini（或同等） |
| Temperature | 0.3 |
| Max Tokens | 200 |
| **结构化输出** | **✅ 开启** |

### 3.2 SYSTEM Prompt

```
你是医院排班 AI 助理。分析请假信息，输出 JSON 判断严重程度和紧急度。

只输出 JSON，不要任何其他文字，不要 markdown 代码块：
{"severity":"high|medium|low","urgency":"today|tomorrow|scheduled","impact_level":"critical|normal"}

判断标准：
- severity: leave_summary 含"病假"且 reason 含"急诊/手术/重症"=high；含"病假"=medium；其他=low
- urgency: leaveDate 是今天=today；是明天=tomorrow；其他=scheduled
- impact_level: affected_count > 10 = critical；否则 normal
```

### 3.3 USER Prompt

```
请假摘要：{{#start.leave_summary#}}
影响患者数：{{#start.affected_count#}}
```

### 3.4 结构化输出 Schema（开结构化输出后必填）

开启"结构化输出"开关后，把下面的 JSON Schema 粘到 Dify 的 schema 配置框：

```json
{
  "type": "object",
  "properties": {
    "severity": {
      "type": "string",
      "enum": ["high", "medium", "low"],
      "description": "严重程度：病假+急诊/手术/重症=high；普通病事假=medium；公假其他=low"
    },
    "urgency": {
      "type": "string",
      "enum": ["today", "tomorrow", "scheduled"],
      "description": "紧急度：请假日期是今天=today，明天=tomorrow，其他=scheduled"
    },
    "impact_level": {
      "type": "string",
      "enum": ["critical", "normal"],
      "description": "影响级别：affected_count>10=critical，否则 normal"
    }
  },
  "required": ["severity", "urgency", "impact_level"],
  "additionalProperties": false
}
```

**Schema 作用**：
- ✅ 强制 LLM 输出合法 JSON（不会输出多余文字、不会漏字段）
- ✅ 字段值限定在 enum 范围（LLM 不会乱写 severity=super-high 之类）
- ✅ `additionalProperties: false` 阻止 LLM 加私货字段

### 3.5 输出变量

**重要**：Dify 的 LLM 节点输出变量名**永远叫 `text`**，不管开不开结构化输出都一样。

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `llm_understand.text` | String | 内容是合法 JSON 字符串（结构化输出保证） |

**下游引用**：`{{#llm_understand.text#}}`

> ⚠️ **不要去找 `severity_json` 这个变量**——Dify 不支持给 LLM 节点自定义输出变量名。结构化输出只是**约束 LLM 的输出格式**，输出变量还是 `text`。

### 3.6 示例输出（`llm_understand.text` 的内容）

```json
{"severity":"medium","urgency":"tomorrow","impact_level":"critical"}
```

### 3.7 双重保险

| 层 | 保障 | 说明 |
|----|------|------|
| 第 1 层 | 结构化输出 Schema | Dify 调 OpenAI 时强制走 JSON Mode，LLM 输出合法 JSON |
| 第 2 层 | 节点 6 守门员 | 即使结构化输出偶尔失效，Code 节点正则 + 字段校验也能兜住 |

> **设计说明**：结构化输出是**前置约束**，节点 6 守门员是**后置兜底**，两层防护让 LLM 输出格式永远不会破坏业务。

---

## 四、节点 3：HTTP 拉详细上下文 ⭐

### 4.1 节点配置

| 项 | 值 |
|----|------|
| 节点类型 | HTTP Request |
| Method | GET |
| URL | `http://你的后端域名/api/schedule/adjust/context?leaveId={{#start.leave_id#}}` |
| Headers | `X-Dify-Token: 你的回调token` |
| Timeout | 10 秒 |
| Body Type | none |

### 4.2 Headers 详细配置

```json
[
  {"name": "X-Dify-Token", "value": "你的回调token"}
]
```

> **作用**：简单鉴权，防止外部恶意调用后端 API。

### 4.3 后端返回（参考结构）

HTTP 节点拿到的是 string（HTTP body 原文），后端返回的 JSON 会被 Dify 自动解析成 Object。响应结构：

```json
{
  "code": 200,
  "data": {
    "leaveInfo": {
      "id": 123,
      "physicianName": "张医生",
      "departmentName": "心血管内科",
      "leaveDate": "2026-07-05",
      "timeSlot": "上午",
      "reason": "感冒发烧"
    },
    "candidates": [
      {
        "physicianId": 457,
        "name": "王医生",
        "title": "主任医师",
        "weeklyLoad": 0,
        "availableSlots": "全天空闲"
      },
      {
        "physicianId": 458,
        "name": "李医生",
        "title": "副主任医师",
        "weeklyLoad": 2,
        "availableSlots": "下午"
      }
    ],
    "affectedPatientCount": 15,
    "departmentId": 5,
    "departmentRule": "心血管内科最少需 1 人在岗",
    "statistics": {
      "totalCandidates": 2,
      "scheduleId": 678
    }
  }
}
```

### 4.4 输出变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `http_result.body` | Object | HTTP 响应体（Dify 自动解析 JSON） |
| `http_result.status_code` | Number | HTTP 状态码 |

### 4.5 设计亮点

- **Dify 不存业务数据**：每次工作流执行都通过 HTTP 拉最新状态
- **职责分离**：多表 JOIN 的脏活全在后端，Dify 只调一次 API
- **实时性**：即使工作流跑了几十秒，期间数据库有变化也能拿到最新数据

---

## 五、节点 4：Code 压缩数据（Python）

### 5.1 节点配置

| 项 | 值 |
|----|------|
| 节点类型 | Code |
| 语言 | Python3 |

### 5.2 输入变量

| 变量名 | 来源 |
|--------|------|
| `http_result` | `http_context.body` |
| `leave_summary` | `start.leave_summary` |
| `affected_count` | `start.affected_count` |
| `candidates_brief` | `start.candidates_brief` |

### 5.3 代码

```python
def main(http_result: dict, leave_summary: str, affected_count: str, candidates_brief: str) -> dict:
    """把 HTTP 返回的大 JSON 压缩成 LLM 易读的短文本"""
    # http_result 可能是 dict 或 str（看 Dify 版本）
    if isinstance(http_result, str):
        import json
        try:
            http_result = json.loads(http_result)
        except Exception:
            http_result = {}
    
    data = http_result.get('data', {}) if isinstance(http_result, dict) else {}
    candidates = data.get('candidates', [])
    
    # 候选医生：一行一个，关键字段对齐
    if candidates:
        lines = []
        for c in candidates:
            lines.append(
                f"- {c.get('name', '未知')}(ID={c.get('physicianId')}) "
                f"{c.get('title', '普通号')}, "
                f"本周已排 {c.get('weeklyLoad', 0)} 班, "
                f"可替时段: {c.get('availableSlots', '未知')}"
            )
        candidates_text = "\n".join(lines)
    else:
        # HTTP 失败时降级用 candidates_brief（开始节点的预筛清单）
        candidates_text = candidates_brief
    
    context_text = f"""【请假信息】
{leave_summary}

【影响范围】
共 {affected_count} 位已挂号患者需要改约

【候选替班医生】（substitute_id 只能从以下 ID 中选）
{candidates_text}

【科室规则】
{data.get('departmentRule', '科室最少需 1 人在岗')}
"""
    return {"context_text": context_text}
```

### 5.4 输出变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `context_text` | String | 压缩后的上下文文本（约 300-500 字符） |

### 5.5 输出示例

```
【请假信息】
2026-07-05 上午 心血管内科 医生456 因感冒发烧请病假

【影响范围】
共 15 位已挂号患者需要改约

【候选替班医生】（substitute_id 只能从以下 ID 中选）
- 王医生(ID=457) 主任医师, 本周已排 0 班, 可替时段: 全天空闲
- 李医生(ID=458) 副主任医师, 本周已排 2 班, 可替时段: 下午

【科室规则】
心血管内科最少需 1 人在岗
```

> **设计说明**：HTTP 返回的 JSON 可能 5KB+，直接塞给 LLM 会"看不过来"。Code 节点压成 500 字符短文本，**LLM 推理质量直线提升**，token 消耗也大幅下降。

---

## 六、节点 5：LLM 推荐替班（核心推理）

### 6.1 节点配置

| 项 | 值 |
|----|------|
| 节点类型 | LLM |
| 模型 | gpt-4o-mini（或更强） |
| Temperature | 0.4 |
| Max Tokens | 400 |

### 6.2 SYSTEM Prompt

```
你是医院智能排班 AI。基于上下文，从候选医生中推荐最合适的替班人选。

【硬性规则】（必须遵守）
1. substitute_id 必须是候选清单中已存在的 ID，不能编造
2. 优先选择 weeklyLoad 最低的医生
3. 若所有候选本周都已排满，选择 title 最高的（经验丰富者）

【输出格式】（仅 JSON，无其他文字，无 markdown 代码块）
{
  "substitute_id": "候选医生 ID",
  "substitute_name": "医生姓名",
  "adjust_type": "临时替班",
  "reason": "30 字内说明选择理由",
  "patient_notification": "50 字内话术，含抱歉和改约信息"
}
```

### 6.3 USER Prompt

```
{{#code_compress.context_text#}}

可选参考：请假严重程度 = {{#llm_understand.text#}}
```

> **说明**：`{{#llm_understand.text#}}` 是节点 2 结构化输出的 JSON 字符串（内容如 `{"severity":"medium","urgency":"tomorrow","impact_level":"critical"}`），LLM 能读懂。

### 6.4 输出

节点变量名：`llm_recommend.text`（string 类型，Dify 自动产出）

### 6.5 输出示例

```json
{
  "substitute_id": "457",
  "substitute_name": "王医生",
  "adjust_type": "临时替班",
  "reason": "王医生本周无排班且全天空闲，建议接替",
  "patient_notification": "因医生请假，已为您改约至王医生 7 月 5 日上午门诊，如需调整请联系医院。"
}
```

---

## 七、节点 6：Code 守门员校验 ⭐（防幻觉核心）

### 7.1 节点配置

| 项 | 值 |
|----|------|
| 节点类型 | Code |
| 语言 | Python3 |

### 7.2 输入变量

| 变量名 | 来源 |
|--------|------|
| `llm_output` | `llm_recommend.text`（节点 5 的文本输出） |
| `candidates_brief` | `start.candidates_brief`（开始节点的候选清单） |
| `severity_text` | `llm_understand.text`（节点 2 结构化输出的 severity JSON） |

### 7.3 代码

```python
import json
import re


def main(llm_output: str, candidates_brief: str, severity_text: str) -> dict:
    """校验 LLM 推荐输出 + 透传严重程度"""

    # 0. 解析 severity（节点 2 结构化输出，已经是合法 JSON 字符串）
    severity_info = {}
    try:
        if severity_text:
            severity_info = json.loads(severity_text)
    except Exception as e:
        severity_info = {"parse_error": f"severity 解析失败: {e}"}

    # 1. 提取 JSON 块（防止 LLM 输出带 markdown ```json 包裹）
    m = re.search(r'\{[^{}]*\}', llm_output or "", re.DOTALL)
    if not m:
        return _fallback(candidates_brief, "LLM 输出无 JSON 块", severity_info)

    try:
        result = json.loads(m.group())
    except Exception as e:
        return _fallback(candidates_brief, f"JSON 解析失败: {e}", severity_info)

    # 2. 从 candidates_brief 提取合法 ID 集合
    #    格式：name(id,...)|name(id,...)
    valid_ids = set(re.findall(r'\((\d+),', candidates_brief or ""))
    sid = str(result.get("substitute_id", "")).strip()

    if sid not in valid_ids:
        return _fallback(candidates_brief, f"substitute_id={sid} 不在候选清单 {valid_ids}", severity_info)

    # 3. 必填字段校验
    required = ("substitute_id", "substitute_name", "reason")
    for k in required:
        if not result.get(k):
            return _fallback(candidates_brief, f"必填字段 {k} 缺失", severity_info)

    result["source"] = "ai"
    result["severity_info"] = severity_info  # 透传严重程度
    return {
        "result": json.dumps(result, ensure_ascii=False),
        "source": "ai"
    }


def _fallback(candidates_brief: str, error_msg: str, severity_info: dict) -> dict:
    """降级：候选清单第一个"""
    m = re.search(r'([^|(]+)\((\d+),', candidates_brief or "")
    if m:
        fb = {
            "substitute_id": m.group(2),
            "substitute_name": m.group(1).strip(),
            "adjust_type": "临时替班",
            "reason": f"AI 推理失败({error_msg})，默认候选首位",
            "patient_notification": "您的预约时间有调整，医院将尽快联系您",
            "source": "fallback",
            "severity_info": severity_info
        }
        return {
            "result": json.dumps(fb, ensure_ascii=False),
            "source": "fallback"
        }
    return {"result": "{}", "source": "error"}
```

### 7.4 输出变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `result` | String | JSON-in-String（最终结果） |
| `source` | String | `ai` / `fallback` / `error` |

### 7.5 防幻觉双重保险

1. **正则提取 `{...}`**：LLM 哪怕输出了 ` ```json ... ``` ` 也能提取出 JSON
2. **ID 集合校验**：从 `candidates_brief` 用正则 `r'\((\d+),'` 提取合法 ID，LLM 编的 ID 直接拒掉

> **这是整个工作流的"保险丝"**：LLM 再怎么乱编 substitute_id，这一步都会兜住——校验失败自动降级到候选列表第一个。业务永远不会因为 AI 幻觉而崩溃。

---

## 八、节点 7：End（结束节点）

### 8.1 输出变量定义

| 变量名 | 来源 |
|--------|------|
| `result` | `code_validate.result` |
| `source` | `code_validate.source` |
| `severity` | `llm_understand.text`（节点 2 结构化输出的 JSON 字符串） |

### 8.2 最终响应结构

工作流执行完成后，Dify 返回的 blocking 模式响应：

```json
{
  "task_id": "abc-123",
  "workflow_run_id": "run-456",
  "data": {
    "id": "run-456",
    "workflow_id": "wf-789",
    "status": "succeeded",
    "outputs": {
      "result": "{\"substitute_id\":\"457\",\"substitute_name\":\"王医生\",\"adjust_type\":\"临时替班\",\"reason\":\"本周无排班且全天空闲\",\"patient_notification\":\"...\",\"source\":\"ai\"}",
      "source": "ai",
      "severity": "{\"severity\":\"medium\",\"urgency\":\"tomorrow\",\"impact_level\":\"critical\"}"
    },
    "elapsed_time": 8.34,
    "total_tokens": 1234
  }
}
```

### 8.3 后端解析方式

后端拿到 `outputs.result`（string），用 Jackson 反序列化：

```java
String jsonStr = response.getData().getOutputs().get("result");
ObjectMapper mapper = new ObjectMapper();
Map<String, Object> result = mapper.readValue(jsonStr, Map.class);
Long substituteId = Long.valueOf(result.get("substitute_id").toString());
```

---

## 九、节点连线（Edges）

| From | To | 说明 |
|------|-----|------|
| `start` | `llm_understand` | 串行 |
| `llm_understand` | `http_context` | 串行（severity 可选输入） |
| `http_context` | `code_compress` | 串行（HTTP 结果交压缩） |
| `code_compress` | `llm_recommend` | 串行（压缩文本喂 LLM） |
| `llm_recommend` | `code_validate` | 串行（LLM 输出走守门员） |
| `code_validate` | `end` | 串行（校验结果输出） |

---

## 十、典型测试用例

### 10.1 正常场景

**输入**：
```json
{
  "leave_id": "123",
  "leave_summary": "2026-07-05 上午 心血管内科 医生456 因感冒发烧请病假",
  "candidates_brief": "医生457(457,本周0班,全天空)|医生458(458,本周2班,下午空)",
  "affected_count": "15"
}
```

**预期输出**：
```json
{
  "result": "{\"substitute_id\":\"457\",\"substitute_name\":\"王医生\",\"adjust_type\":\"临时替班\",\"reason\":\"本周无排班且全天空闲\",\"patient_notification\":\"...\",\"source\":\"ai\"}",
  "source": "ai"
}
```

### 10.2 异常场景（LLM 编造 ID）

**假设**：节点 5 LLM 输出了 `substitute_id: "999"`（不在候选清单）

**节点 6 行为**：
```
sid=999 not in {457, 458}
→ 降级：取候选首位 = 457
→ source = "fallback"
```

**输出**：
```json
{
  "result": "{\"substitute_id\":\"457\",\"substitute_name\":\"医生457\",\"reason\":\"AI 推理失败(substitute_id=999 不在候选清单 {'457', '458'})，默认候选首位\",\"source\":\"fallback\"}",
  "source": "fallback"
}
```

### 10.3 边界场景（无候选）

**输入**：
```json
{
  "leave_id": "124",
  "leave_summary": "2026-07-05 上午 心血管内科 医生456 因病假请假",
  "candidates_brief": "",
  "affected_count": "0"
}
```

**节点 6 行为**：
```
valid_ids = 空集合
→ _fallback 也找不到候选 → 返回 source="error", result="{}"
```

后端拿到 `source="error"` 时应该提示"无可替班医生，需人工介入"。

---

## 十一、性能与成本

| 指标 | 预估值 |
|------|--------|
| 总耗时 | 8-15 秒（blocking 模式） |
| Token 消耗 | 约 1000-1500 tokens |
| 单次调用成本 | 约 ¥0.02-0.05（gpt-4o-mini） |
| 主要耗时点 | 节点 3 HTTP（1-2s）+ 节点 5 LLM（3-6s） |

---

## 十二、设计亮点总结（答辩用）

| # | 亮点 | 实现位置 |
|---|------|---------|
| 1 | **多节点编排可视化** | 7 节点串联，Dify 画布一目了然 |
| 2 | **HTTP 回调机制** | 节点 3 拉后端最新数据，Dify 不缓存业务 |
| 3 | **Code 节点压缩** | 节点 4 把 5KB JSON 压成 500 字符短文本，提升 LLM 质量 |
| 4 | **结构化输出 Schema** | 节点 2 用 JSON Schema 强约束 LLM 输出，enum 限定枚举值 |
| 5 | **防幻觉正则校验** | 节点 6 用 `r'\((\d+),'` 从 candidates_brief 提取合法 ID |
| 6 | **降级容错** | 节点 6 校验失败自动降级到候选首位，业务不中断 |
| 7 | **JSON-in-String 协议** | 开始节点全 string，结束节点输出 string，规避 Dify 类型限制 |
| 8 | **双层防护** | 结构化输出（前置）+ 守门员 Code（后置），LLM 输出格式永不破坏业务 |
