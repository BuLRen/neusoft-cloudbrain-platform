# Dify 智能请假替班推荐工作流

> **版本**：v2.1 · **日期**：2026-07-04
> **架构**：5 节点版（Start → HTTP → Code → LLM → End）
> **设计原则**：Dify 只做"LLM 推理"，所有候选校验都在后端 Java 侧完成。
> **v2.1 变更**：HTTP 节点返回的 `body` 在 Dify 里是 string 类型，无法点选 `data.candidates` 等嵌套字段。新增 Code 节点（Python）解析 body string，输出可被 LLM 直接引用的强类型变量。

---

## 一、整体工作流图

```
┌─────┐   ┌──────┐   ┌──────┐   ┌─────┐   ┌─────┐
│Start│──▶│ HTTP │──▶│ Code │──▶│ LLM │──▶│ End │
│入参 │   │拉body│   │解析  │   │推理 │   │输出 │
│     │   │string│   │JSON  │   │     │   │     │
└─────┘   └──────┘   └──────┘   └─────┘   └─────┘
```

**5 个节点**：
- Start：入参（leave_id / leave_summary / leaving_physician_name / candidates_brief / affected_count）
- HTTP：调后端 `getAdjustContext` 拿完整上下文，但 Dify 把 `body` 当成 string 返回
- Code：解析 body string → 拆出 candidates / departmentRule / affectedPatientCount 三个强类型变量
- LLM：基于 Code 输出做推理，结构化输出 JSON
- End：透传 LLM `.text` 给后端

**为什么需要 Code 节点**：Dify 平台对 HTTP 响应体的自动 JSON 解析有局限 —— 如果响应 Content-Type 不是 `application/json` 或响应体里有非 ASCII 字符，`body` 会被识别为 string 而不是 Object，下游 LLM 就无法用变量选择器点选 `body.data.candidates`。Code 节点用 `json.loads` 显式解析，绕过这个限制。

---

## 二、节点 1：Start（开始节点）

### 2.1 输入变量定义

| 变量名 | 类型 | 必填 | 最大长度 | 说明 |
|--------|------|------|---------|------|
| `leave_id` | text-input | ✅ | 20 | 请假记录 ID（数字字符串） |
| `leave_summary` | paragraph | ✅ | 500 | 请假自然语言摘要（含真实姓名） |
| `leaving_physician_name` | text-input | ✅ | 50 | 请假医生真实姓名（防 LLM 编"医生1"） |
| `candidates_brief` | paragraph | ✅ | 2000 | 候选医生清单，格式见 2.3 |
| `affected_count` | text-input | ❌ | 10 | 影响患者数（数字字符串） |

> **为什么全用 string**：Dify 开始节点不支持 Object/Array 入参；外部调用 Dify API 时序列化复杂类型容易出错。**全 string 是最稳妥的协议**。

### 2.2 调用示例（后端 schedule-service 自动调用，无需手测）

```bash
curl -X POST 'https://api.dify.ai/v1/workflows/run' \
  -H 'Authorization: Bearer app-xxxx' \
  -H 'Content-Type: application/json' \
  -d '{
    "inputs": {
      "leave_id": "123",
      "leave_summary": "2026-07-05 上午 心血管内科 张医生(ID=456) 因感冒发烧请病假",
      "leaving_physician_name": "张医生",
      "candidates_brief": "(457,王医生,主任医师,本周0班,上午空闲,余号8)|(458,李医生,副主任医师,本周2班,下午空闲,余号3)",
      "affected_count": "15"
    },
    "response_mode": "blocking",
    "user": "schedule-service"
  }'
```

### 2.3 `candidates_brief` 格式（关键，后端解析依赖此格式）

**格式**：每个候选用 `|` 分隔，候选内部字段用 `,` 分隔，整体用 `()` 包裹

```
({id},{姓名},{级别},{weeklyLoad},{available},余号{N})|({id},{姓名},{级别},{weeklyLoad},{available},余号{N})
```

**为什么用 `()` 包裹且 ID 紧跟开头**：后端解析时用正则 `r'\((\d+),'` 提取合法 ID 集合，**左括号后必须是 数字+逗号**，姓名不能放在 ID 前。

**后端预筛规则**（`findLeaveSubstitutes` 已实现，无需在 Dify 重复实现）：
- ✅ 同科室（`department_id` 等值）
- ✅ 同挂号级别（`regist_level_id` 等值，避免专家号降级到普通号）
- ✅ 同日期同时段无班次冲突（避免唯一约束 `idx_ds_unique` 撞车）
- ✅ 状态正常 + 有余号
- ✅ 排除请假医生本人

---

## 三、节点 2：HTTP 拉详细上下文

### 3.1 节点配置

| 项 | 值 |
|----|------|
| 节点类型 | HTTP Request |
| Method | GET |
| URL | `http://182.92.193.45:8095/api/schedule/adjust/context?leaveId={{#start.leave_id#}}` |
| Headers | `X-Dify-Token: schedule-internal-2026` |
| Timeout | 10 秒 |
| Body Type | none |

> ⚠️ URL 里的 `{{#start.leave_id#}}` 必须用 Dify prompt 输入框右侧的"**插入变量**"按钮选，不要手打。

> ⚠️ `X-Dify-Token` 的值必须和后端 `schedule-service` 配置文件里的 `dify.callback-token` 一致（默认 `schedule-internal-2026`），否则后端返回"鉴权失败"。

### 3.2 后端返回结构

```json
{
  "code": 200,
  "data": {
    "leaveInfo": {
      "id": 123,
      "physicianId": 456,
      "leaveDate": "2026-07-05",
      "timeSlot": "上午",
      "leaveType": "病假",
      "reason": "感冒发烧"
    },
    "candidates": [
      {
        "physicianId": 457,
        "name": "王医生",
        "title": "主任医师",
        "weeklyLoad": 0,
        "availableSlots": "上午空闲",
        "availableQuota": 8
      },
      {
        "physicianId": 458,
        "name": "李医生",
        "title": "副主任医师",
        "weeklyLoad": 2,
        "availableSlots": "下午空闲",
        "availableQuota": 3
      }
    ],
    "affectedPatientCount": 15,
    "departmentId": 5,
    "departmentRule": "该科室最少需 1 人在岗",
    "statistics": {
      "totalCandidates": 2,
      "scheduleId": 789
    }
  }
}
```

> **关键**：HTTP 节点的 `candidates` 数组 和开始节点 `candidates_brief` 用的是**同一个后端查询**（`findLeaveSubstitutesPublic`）。LLM 看到的候选和后端校验的候选永远一致，不会出现"LLM 推 5 但候选只有 2"的矛盾。

### 3.3 输出变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `http.body` | **String** | HTTP 响应体（**注意：Dify 默认当 string 处理，不能直接点选 data.candidates**） |
| `http.status_code` | Number | HTTP 状态码 |
| `http.headers` | Object | 响应头列表 JSON |
| `http.files` | Array[File] | 文件列表 |

> ⚠️ **关键限制**：Dify 把 `body` 识别成 string，所以下游 LLM 节点用变量选择器点到 `body` 就到头了，**没法再展开 `body.data.candidates`**。这就是为什么需要下一个节点（Code）来显式解析。

---

## 四、节点 3：Code 解析 body string（必填，绕过 Dify 限制）

### 4.1 节点配置

| 项 | 值 |
|----|------|
| 节点类型 | Code |
| 语言 | Python 3 |

### 4.2 输入变量（Code 节点的"输入变量"区域）

| 变量名 | 值（用变量选择器选） |
|---|---|
| `raw_body` | HTTP 节点的 `body` 字段（**String** 类型） |

> ⚠️ 输入变量类型必须显式选 **String**，不要选 Object/Any。

### 4.3 代码（完整复制粘贴）

```python
import json

def main(raw_body: str) -> dict:
    """解析 HTTP 节点返回的 body string，提取候选和科室规则。"""
    try:
        parsed = json.loads(raw_body)
    except Exception as e:
        # 解析失败兜底：返回空候选 + 写明错误
        return {
            "candidates_json": "[]",
            "department_rule": "该科室最少需 1 人在岗",
            "affected_patient_count": 0,
            "parse_error": str(e)
        }

    # 后端结构：{ code: 200, data: { candidates: [...], departmentRule: ... } }
    data = parsed.get("data", {}) if isinstance(parsed, dict) else {}

    candidates = data.get("candidates", [])
    department_rule = data.get("departmentRule", "该科室最少需 1 人在岗")
    affected = data.get("affectedPatientCount", 0)

    # 把候选重新序列化成紧凑 JSON string 给 LLM 看
    candidates_json = json.dumps(candidates, ensure_ascii=False)

    return {
        "candidates_json": candidates_json,
        "department_rule": department_rule,
        "affected_patient_count": affected
    }
```

### 4.4 输出变量声明（Code 节点的"输出变量"区域手动添加）

| 变量名 | 类型 | 说明 |
|---|---|---|
| `candidates_json` | String | 候选医生数组 JSON 化后的字符串 |
| `department_rule` | String | 科室规则文本（默认"该科室最少需 1 人在岗"） |
| `affected_patient_count` | Number | 受影响患者数（HTTP 返回的 affectedPatientCount） |

> ⚠️ **必须手动声明**这 3 个输出变量，Dify 才知道有这些字段可被下游 LLM 节点引用。
>
> ⚠️ **不要声明 `parse_error`**：Code 代码里只在解析失败时才返回这个字段，正常路径不返回，会导致 Dify 报 "missing output variable" 错误。

---

## 五、节点 4：LLM 推荐替班（核心推理节点）

### 5.1 节点配置

| 项 | 值 |
|----|------|
| 节点类型 | LLM |
| 模型 | DeepSeek-V4（或同等） |
| Temperature | **0** |
| Max Tokens | **400** |
| **结构化输出** | **✅ 开启** |

### 5.2 结构化输出 Schema（必填）

```json
{
  "type": "object",
  "properties": {
    "substitute_id": {
      "type": "string",
      "description": "候选医生 ID（必须是 HTTP 返回的候选列表中已存在的 physicianId）"
    },
    "substitute_name": {
      "type": "string",
      "description": "替班医生姓名"
    },
    "adjust_type": {
      "type": "string",
      "enum": ["临时替班", "长期替班", "科室轮换"],
      "description": "调整类型，默认选临时替班"
    },
    "reason": {
      "type": "string",
      "description": "选择理由，30 字以内中文"
    },
    "patient_notification": {
      "type": "string",
      "description": "发给患者的中文通知，50 字以内，必须使用 leaving_physician_name 字段提供的真实姓名"
    }
  },
  "required": ["substitute_id", "substitute_name", "adjust_type", "reason", "patient_notification"],
  "additionalProperties": false
}
```

### 5.3 SYSTEM Prompt

```
你是医院智能排班 AI。基于 Code 节点返回的候选医生列表，推荐最合适的替班人选。

【硬性规则】
1. substitute_id 必须从 Code 节点 candidates_json 列表的 physicianId 字段中选取，严禁编造或使用列表外的 ID
2. 优先 weeklyLoad 最低的医生（本周排班最少的）
3. 若并列，优先 availableQuota 最高的（号源最充足的）
4. 若仍并列，优先 title 包含"主任"的（经验最丰富）
5. patient_notification 必须使用 {{#start.leaving_physician_name#}} 字段提供的真实姓名，禁止写"医生1""医生X""某医生"等占位词

【输出要求】
严格按结构化输出 Schema 输出 JSON，不要输出任何思考过程、不要 markdown 代码块、不要前后缀说明文字。
```

> ⚠️ SYSTEM prompt 里的 `{{#start.leaving_physician_name#}}` 必须用"插入变量"按钮选。

### 5.4 USER Prompt

```
【请假信息】
{{#start.leave_summary#}}

【候选替班医生】（substitute_id 只能从以下列表的 physicianId 中选）
{{#code.candidates_json#}}

【影响范围】
共 {{#start.affected_count#}} 位已挂号患者需要改约

【科室规则】
{{#code.department_rule#}}
```

> ⚠️ 所有 `{{#xxx#}}` 都必须用"插入变量"按钮选，不要手打。变量来源：
> - `start.leave_summary` / `start.affected_count` → 开始节点
> - `code.candidates_json` / `code.department_rule` → **Code 节点**（节点名要选你实际 Code 节点的名字，不一定是 `code`）

### 5.5 输出变量

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `llm_recommend.text` | String | LLM 文本输出（可能带 `<think>` 前缀，后端会处理） |
| `llm_recommend.structured_output` | Object | Dify 解析后的对象（不用，后端只用 text） |

> End 节点**输出 `.text` 字段**，不要输出 `.structured_output`。后端会自己剥 `<think>` 和解析，更稳。

---

## 六、节点 5：End（结束节点）

### 6.1 输出变量定义

| 变量名 | 来源 |
|--------|------|
| `llm_result` | LLM 节点的 `text` 输出（**原样透传**） |

> **关键**：
> 1. End 节点不做任何加工，就是把 LLM 的 `.text` 字段透传给后端
> 2. **变量名必须是 `llm_result`**，后端 `parseLeaveAdjustResult` 只认这个名字
> 3. 删掉所有其他输出变量（如 `result`、`formatted`、`gatekeeper_decision` 等），避免干扰

### 6.2 最终响应结构

```json
{
  "task_id": "abc-123",
  "workflow_run_id": "run-456",
  "data": {
    "id": "run-456",
    "workflow_id": "wf-789",
    "status": "succeeded",
    "outputs": {
      "llm_result": "{\"substitute_id\":\"457\",\"substitute_name\":\"王医生\",\"adjust_type\":\"临时替班\",\"reason\":\"本周无排班且全天空闲\",\"patient_notification\":\"...\"}"
    },
    "elapsed_time": 5.34,
    "total_tokens": 800
  }
}
```

---

## 七、节点连线（Edges）

| From | To | 说明 |
|------|-----|------|
| `start` | `http` | 串行（HTTP 需要 leave_id） |
| `http` | `code` | 串行（Code 解析 HTTP body string） |
| `code` | `llm` | 串行（LLM 用 Code 输出当输入） |
| `llm` | `end` | 串行（End 透传 LLM 输出） |

**4 条主线，没有跨节点旁路**，部署调试极简。

---

## 八、后端解析与校验逻辑（无需手动改，已就绪）

### 8.1 解析链路

```
Dify outputs.llm_result (string, 可能含 <think> 标签)
  ↓ extractOutermostJson() —— 括号配平算法，正确处理嵌套对象
干净 JSON 字符串
  ↓ objectMapper.readTree()
JsonNode payload（含 substitute_id 等字段）
  ↓ 实时 DB 候选校验
DifyLeaveAdjustResult（source = ai / fallback / error）
```

### 8.2 候选 ID 实时校验（关键，替代 Dify 守门员节点）

拿到 LLM 推荐的 `substitute_id` 后，后端在最新候选清单里二次校验：

```java
Optional<SubstituteCandidate> matched = candidates.stream()
    .filter(c -> String.valueOf(c.getPhysicianId()).equals(substituteId))
    .findFirst();

if (matched.isPresent()) {
    // ✅ 校验通过：用 LLM 推荐的医生，source = "ai"
} else if (!candidates.isEmpty()) {
    // ⚠️ 校验失败：LLM 推了不合法 ID → 降级到候选首位，source = "fallback"
    // reason 写明降级原因（"AI 推荐 ID=X 不在严格候选清单，降级到首位"）
} else {
    // ❌ 候选清单为空：彻底失败，source = "error"
}
```

> **后端校验比 Dify 节点更可靠**：
> - 后端用 `findLeaveSubstitutes` 重新查最新候选（DB 实时数据）
> - 后端用括号配平算法解析（不是贪婪正则）
> - 后端有完整日志，可追踪每一步决策

---

## 九、典型测试用例

### 9.1 正常场景（LLM 推荐合法 ID）

**输入**：
```json
{
  "leave_id": "123",
  "leave_summary": "2026-07-05 上午 心血管内科 张医生(ID=456) 因感冒发烧请病假",
  "leaving_physician_name": "张医生",
  "candidates_brief": "(457,王医生,主任医师,本周0班,上午空闲,余号8)|(458,李医生,副主任医师,本周2班,下午空闲,余号3)",
  "affected_count": "15"
}
```

**预期**：
- 后端日志：`Dify 替班推荐校验通过：substituteId=457, name=王医生`
- 数据库：`schedule_adjust_request` 表插入一条 `source=ai` 的记录

### 9.2 异常场景（LLM 推荐了不在候选清单的 ID）

**假设**：HTTP 节点返回候选 = `[457, 458]`，但 LLM 输出 `substitute_id: "999"`（编造的）

**后端行为**：
```
matched = ❌（999 不在 [457, 458]）
→ 降级到候选首位 = 457
→ source = "fallback"
→ 日志：Dify 替班推荐 ID=999 不在严格候选清单 [457, 458]，降级到首位=457
```

> 这就是 v2.0 的兜底机制 —— 即使 LLM 推错了，业务也不会崩，会自动降级到合法候选。

### 9.3 异常场景（LLM 输出带 `<think>` 标签）

**假设**：LLM 输出
```
<think>分析请假信息，王医生本周无排班...</think>{"substitute_id":"457",...}
```

**后端 `extractOutermostJson` 行为**：
- 整体不是合法 JSON（happy path 失败）
- 括号配平从 `{"substitute_id"` 的 `{` 开始匹配
- 跳过字符串内的 `{` `}`
- 正确返回最外层 JSON 对象

---

## 十、从零搭建工作流清单（如果你要新建一个）

按下面顺序在 Dify 创建节点：

1. **新建 5 个节点**：Start / HTTP Request / Code / LLM / End
2. **配置 Start**：按第 2.1 节加 5 个输入变量
3. **配置 HTTP**：按第 3.1 节填 URL / Headers / Timeout
4. **配置 Code**：
   - 语言选 Python 3
   - 输入变量加 `raw_body`（String 类型，值选 HTTP 节点的 `body`）
   - 代码按第 4.3 节完整粘贴
   - 输出变量手动声明 4 个：`candidates_json` / `department_rule` / `affected_patient_count` / `parse_error`
5. **配置 LLM**：
   - 模型选 DeepSeek-V4，Temperature = 0，Max Tokens = 400
   - 开启"结构化输出"，Schema 按第 5.2 节
   - SYSTEM prompt 按第 5.3 节（用插入变量按钮选 `leaving_physician_name`）
   - USER prompt 按第 5.4 节（用插入变量按钮选所有变量，**注意现在引用的是 Code 节点的 `candidates_json` / `department_rule`，不是 HTTP 节点**）
6. **配置 End**：按第 6.1 节加唯一输出变量 `llm_result`
7. **连线**：按第七节连 4 条线
8. **发布**：右上角"发布"按钮 → 确认（**这步必做，不发布调的还是老版本**）

---

## 十一、部署后验证清单

部署后端到测试环境，触发一次请假流程，逐项确认：

- [ ] schedule-service 日志出现 `Dify 上下文聚合：leaveId=X, candidates=N, affectedPatients=M`
- [ ] schedule-service 日志出现 `Dify 替班推荐校验通过：substituteId=Z`（**source=ai 是关键标志**）
- [ ] 数据库 `schedule_adjust_request` 表有新记录，`source` 字段 = `ai`
- [ ] 前端"待确认调整"列表能看到新记录，"调整后医生"列显示正常姓名（不是空、不是"医生X"）
- [ ] 管理员确认调整后，`doctor_schedule` 表对应排班的 `physician_id` 已更新
- [ ] `registration` 表对应挂号记录的 `physician_id` 已更新，`remark` 字段含 `[医生变更]` 标记

**如果出现 `source=fallback`**：把日志里 `Dify 替班推荐 ID=X 不在严格候选清单 [Y]` 那行截下来，通常是 LLM prompt 没改对 或 HTTP 节点变量引用错了。
