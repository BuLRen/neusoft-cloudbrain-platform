# WF-FollowUp-Shift-01 随访工作排班 Dify 工作流

> 版本：v2.0（增强 7 节点版）  
> 状态：可部署  
> 后端对接：`FollowUpShiftDifyService` → `FollowUpShiftScheduleService.persistGeneratedPlan`

## 用途

管理员为某临床科室的随访护士生成**月度工作排班**：哪天值班、当天需联系哪些在管患者。

**边界**：不含门诊医生请假（WF-02）、不含待审批调班申请；节假日由 `holidays_json` 传入。

---

## 架构总览（7 节点，已确认）

```
开始 → Code①预处理 → Code②节假日 → Code③打分 → LLM排班 → Code④冲突修复 → Code⑤校验 → 结束
```

```mermaid
flowchart TD
    startNode[Start]
    prepNode[Code_prep]
    holidayNode[Code_holiday]
    scoreNode[Code_score]
    llmNode[LLM_schedule]
    conflictNode[Code_conflict]
    validateNode[Code_validate]
    endNode[End]

    startNode --> prepNode --> holidayNode --> scoreNode --> scoreNode --> llmNode
    llmNode --> conflictNode --> validateNode --> endNode
```





### 简化 4 节点版（功能等价）

```
开始 → Code_prep_all（合并①②③） → LLM → Code_fix_validate（合并④⑤） → 结束
```

合并说明见文末 [附录 A](#附录-a-4-节点简化版合并代码)。

---



## Dify 变量映射总表


| 节点                | 输入变量                                                                                                                                    | 输出变量                                                                                                                        | 类型            |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ------------- |
| **Start**         | （后端传入，见下表）                                                                                                                              | —                                                                                                                           | text-input ×7 |
| **Code_prep**     | `department_id`, `department_name`, `month`, `staff_json`, `patients_json`, `rules_json`, `holidays_json`                               | `planning_context_json`, `work_dates_json`, `staff_list_json`, `patients_list_json`, `rules_obj_json`, `holidays_list_json` | string        |
| **Code_holiday**  | `work_dates_json`, `rules_obj_json`, `holidays_list_json`                                                                               | `schedulable_dates_json`, `calendar_meta_json`                                                                              | string        |
| **Code_score**    | `patients_list_json`, `rules_obj_json`, `month`                                                                                         | `scored_patients_json`                                                                                                      | string        |
| **LLM**           | `planning_context_json`, `schedulable_dates_json`, `staff_list_json`, `scored_patients_json`, `rules_obj_json`                          | `draft_shifts_json`                                                                                                         | string        |
| **Code_conflict** | `draft_shifts_json`, `schedulable_dates_json`, `staff_list_json`, `scored_patients_json`, `rules_obj_json`, `conflict_report_json`（可选空） | `resolved_shifts_json`, `conflict_report_json`                                                                              | string        |
| **Code_validate** | `resolved_shifts_json`, `conflict_report_json`, `planning_context_json`                                                                 | `validated_shifts_json`                                                                                                     | string        |
| **End**           | `validated_shifts_json`                                                                                                                 | （工作流输出）                                                                                                                     | text          |


**End 节点对外输出变量名必须为** `validated_shifts_json`（与 `FollowUpShiftDifyService.parseDifyOutput` 一致）。

---



## Start 节点 — 后端入参

全部由 Java 后端 `FollowUpShiftDifyService.generateShifts` 传入，类型均为 **text-input**。


| 变量名               | 说明              | 示例        |
| ----------------- | --------------- | --------- |
| `department_id`   | 科室 ID           | `7`       |
| `department_name` | 科室名称            | `内分泌科`    |
| `month`           | 月份 yyyy-MM      | `2026-07` |
| `staff_json`      | 随访人员 JSON 数组字符串 | 见下        |
| `patients_json`   | 在管患者 JSON 数组字符串 | 见下        |
| `rules_json`      | 排班规则 JSON 对象字符串 | 见下        |
| `holidays_json`   | 节假日 JSON 数组字符串  | `[]`      |

### Dify「运行 / 调试」时 Start 各字段填什么（重要）

Start 节点 7 个变量类型均为 **text-input**。`staff_json` / `patients_json` / `rules_json` / `holidays_json` 必须填 **合法 JSON 字符串**（键和字符串值都用 **双引号**）。

**不要**填下面这种（会报 `JSONDecodeError: Expecting property name enclosed in double quotes`）：

```
[{'id': 123, 'name': '护士'}]     ❌ Python 单引号
[{id:123}]                        ❌ 键无引号
```

**请逐字段复制粘贴（内分泌科演示）：**

| 变量 | 直接粘贴的值 |
|------|----------------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-07` |
| `staff_json` | `[{"id":123,"name":"内分泌护士","max_patients_per_day":8}]` |
| `patients_json` | `[{"registerId":9001,"priority":"high","monitorEmployeeId":123,"lastContactDate":"2026-06-20","deadlineDate":"2026-12-01"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":8,"max_shift_imbalance":2}` |
| `holidays_json` | `[]` |

> 生产环境由 Java 后端 `FollowUpShiftDifyService` 自动传入，格式与上表一致。下方 `staff_json` 等代码块是**结构说明**，不要整段原样贴进 Dify 的 text 框（多行/缩进也可能导致解析失败，请用上表**单行**值）。

### staff_json

```json
[
  {"id": 123, "name": "内分泌护士", "max_patients_per_day": 8}
]
```



### patients_json

```json
[
  {
    "registerId": 9001,
    "priority": "high",
    "monitorEmployeeId": 123,
    "lastContactDate": "2026-06-20",
    "deadlineDate": "2026-12-01"
  }
]
```

> 字段名与 `FollowUpShiftMapper.selectPatientsForShiftPlanning` 一致；LLM 输出 task 时使用 snake_case `register_id`。



### rules_json（默认）

```json
{
  "workdays_per_week": 5,
  "min_contact_interval_days": 1,
  "deadline_days": 180,
  "max_patients_per_day": 8,
  "max_shift_imbalance": 2
}
```


| 字段                          | 含义                   |
| --------------------------- | -------------------- |
| `workdays_per_week`         | 5 = 仅周一至周五           |
| `min_contact_interval_days` | 同一患者两次联系最小间隔（冲突修复用）  |
| `deadline_days`             | 随访期限天数（默认 180）       |
| `max_patients_per_day`      | 护士每日最大联系患者数          |
| `max_shift_imbalance`       | 护士之间班次数最大允许差值（冲突修复用） |




### holidays_json

```json
[
  {"date": "2026-07-01", "name": "建党节", "type": "holiday"},
  {"date": "2026-07-05", "name": "调休上班", "type": "workday"}
]
```


| type      | 行为                   |
| --------- | -------------------- |
| `holiday` | 从可排班日剔除（即使是周一至周五）    |
| `workday` | 调休上班日，加入可排班日（即使是周六日） |


---



## Code 节点① — 数据预处理与日历展开

**Dify 节点名**：`prep_data`

### 输入

`department_id`, `department_name`, `month`, `staff_json`, `patients_json`, `rules_json`, `holidays_json`

### 输出

`planning_context_json`, `work_dates_json`, `staff_list_json`, `patients_list_json`, `rules_obj_json`, `holidays_list_json`

### Python（粘贴到 Dify Code 节点）

```python
import ast
import json
import calendar
from datetime import date


def _parse_json(value, default, field_name="json"):
    """兼容 Dify 调试：双引号 JSON / 已解析的 list·dict / 误用单引号的 Python 字面量。"""
    if value is None:
        return default
    if isinstance(value, (list, dict)):
        return value
    text = str(value).strip()
    if not text:
        return default
    if text.startswith("```"):
        lines = text.splitlines()
        text = "\n".join(lines[1:-1] if lines and lines[-1].strip() == "```" else lines[1:]).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        try:
            return ast.literal_eval(text)
        except (ValueError, SyntaxError) as exc:
            raise ValueError(
                f"{field_name} 不是合法 JSON。"
                f"请用双引号，例如 [{{\"id\":123,\"name\":\"护士\"}}]。"
                f"当前值前 120 字符: {text[:120]!r}"
            ) from exc


def _month_range(month):
    year, mon = map(int, month.split("-"))
    last_day = calendar.monthrange(year, mon)[1]
    return date(year, mon, 1), date(year, mon, last_day)


def main(
    department_id: str,
    department_name: str,
    month: str,
    staff_json: str,
    patients_json: str,
    rules_json: str,
    holidays_json: str,
) -> dict:
    if not month or len(month) != 7 or month[4] != "-":
        raise ValueError("month 必须为 yyyy-MM 格式")

    staff = _parse_json(staff_json, [], "staff_json")
    patients = _parse_json(patients_json, [], "patients_json")
    rules = _parse_json(
        rules_json,
        {
            "workdays_per_week": 5,
            "min_contact_interval_days": 1,
            "deadline_days": 180,
            "max_patients_per_day": 8,
            "max_shift_imbalance": 2,
        },
        "rules_json",
    )
    holidays = _parse_json(holidays_json, [], "holidays_json")

    if not staff:
        raise ValueError("staff_json 不能为空")
    if not isinstance(staff, list) or not isinstance(patients, list):
        raise ValueError("staff_json / patients_json 必须为 JSON 数组")

    start, end = _month_range(month)
    work_dates = []
    d = start
    weekday_names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    while d <= end:
        work_dates.append(
            {
                "date": d.isoformat(),
                "weekday": d.weekday(),  # 0=Mon
                "weekday_name": weekday_names[d.weekday()],
            }
        )
        d = date.fromordinal(d.toordinal() + 1)

    unassigned = sum(1 for p in patients if not p.get("monitorEmployeeId"))
    warnings = []
    if unassigned:
        warnings.append(f"{unassigned} 名患者未分配监视护士(monitorEmployeeId)")

    context = {
        "department_id": department_id,
        "department_name": department_name,
        "month": month,
        "staff_count": len(staff),
        "patient_count": len(patients),
        "unassigned_monitor_count": unassigned,
        "warnings": warnings,
    }

    return {
        "planning_context_json": json.dumps(context, ensure_ascii=False),
        "work_dates_json": json.dumps(work_dates, ensure_ascii=False),
        "staff_list_json": json.dumps(staff, ensure_ascii=False),
        "patients_list_json": json.dumps(patients, ensure_ascii=False),
        "rules_obj_json": json.dumps(rules, ensure_ascii=False),
        "holidays_list_json": json.dumps(holidays, ensure_ascii=False),
    }
```

---



## Code 节点② — 工作日与节假日过滤

**Dify 节点名**：`filter_schedulable_dates`

### 输入

`work_dates_json`, `rules_obj_json`, `holidays_list_json`

### 输出

`schedulable_dates_json`, `calendar_meta_json`

### Python

```python
import ast
import json


def _loads(value, default, field_name="json"):
    if value is None:
        return default
    if isinstance(value, (list, dict)):
        return value
    text = str(value).strip() or json.dumps(default)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return ast.literal_eval(text)


def main(work_dates_json: str, rules_obj_json: str, holidays_list_json: str) -> dict:
    work_dates = _loads(work_dates_json, [], "work_dates_json")
    rules = _loads(rules_obj_json, {}, "rules_obj_json")
    holidays = _loads(holidays_list_json, [], "holidays_list_json")

    workdays_per_week = int(rules.get("workdays_per_week", 5))
    holiday_set = set()
    extra_workdays = set()

    for h in holidays:
        d = h.get("date")
        if not d:
            continue
        if h.get("type") == "workday":
            extra_workdays.add(d)
        else:
            holiday_set.add(d)

    schedulable = []
    for item in work_dates:
        d = item["date"]
        wd = item["weekday"]  # 0=Mon .. 6=Sun
        is_weekday = wd < workdays_per_week
        if d in holiday_set:
            continue
        if d in extra_workdays or is_weekday:
            schedulable.append(d)

    schedulable = sorted(set(schedulable))
    meta = {
        "total_days_in_month": len(work_dates),
        "schedulable_day_count": len(schedulable),
        "holiday_count": len(holiday_set),
        "extra_workday_count": len(extra_workdays),
        "first_schedulable": schedulable[0] if schedulable else None,
        "last_schedulable": schedulable[-1] if schedulable else None,
    }

    return {
        "schedulable_dates_json": json.dumps(schedulable, ensure_ascii=False),
        "calendar_meta_json": json.dumps(meta, ensure_ascii=False),
    }
```

---



## Code 节点③ — 患者联系优先级打分

**Dify 节点名**：`score_patients`

### 输入

`patients_list_json`, `rules_obj_json`, `month`

### 输出

`scored_patients_json`

### 打分规则


| 因子                | 分值   |
| ----------------- | ---- |
| priority=critical | +100 |
| priority=high     | +60  |
| priority=normal   | +20  |
| 距 deadline ≤30 天  | +80  |
| 距 deadline ≤60 天  | +50  |
| 距 deadline ≤90 天  | +30  |
| 未联系过              | +40  |
| 距上次联系 ≥3 天        | +30  |
| 距上次联系 ≥1 天        | +10  |




### Python

```python
import ast
import json
from datetime import date, datetime


def _loads(value, default):
    if isinstance(value, list):
        return value
    text = str(value).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return ast.literal_eval(text)


def _parse_date(s):
    if not s:
        return None
    if isinstance(s, date):
        return s
    return datetime.strptime(str(s)[:10], "%Y-%m-%d").date()


def main(patients_list_json: str, rules_obj_json: str, month: str) -> dict:
    patients = _loads(patients_list_json, [])
    rules = _loads(rules_obj_json, {})
    year, mon = map(int, month.split("-"))
    ref = date(year, mon, 15)  # 以月中为参考日估算紧迫度

    priority_score = {"critical": 100, "high": 60, "normal": 20}
    scored = []

    for p in patients:
        rid = p.get("registerId")
        pr = str(p.get("priority") or "normal").lower()
        score = priority_score.get(pr, 20)

        deadline = _parse_date(p.get("deadlineDate"))
        days_to_deadline = None
        if deadline:
            days_to_deadline = (deadline - ref).days
            if days_to_deadline <= 30:
                score += 80
            elif days_to_deadline <= 60:
                score += 50
            elif days_to_deadline <= 90:
                score += 30

        last_contact = _parse_date(p.get("lastContactDate"))
        days_since = None
        if last_contact:
            days_since = (ref - last_contact).days
            if days_since >= 3:
                score += 30
            elif days_since >= 1:
                score += 10
        else:
            score += 40
            days_since = 999

        scored.append(
            {
                **p,
                "register_id": rid,
                "contact_score": score,
                "days_since_contact": days_since,
                "days_to_deadline": days_to_deadline,
                "monitor_employee_id": p.get("monitorEmployeeId"),
            }
        )

    scored.sort(key=lambda x: -x["contact_score"])
    return {"scored_patients_json": json.dumps(scored, ensure_ascii=False)}
```

---



## LLM 节点 — 生成月度排班草案

**Dify 节点名**：`llm_generate_shifts`  
**输出变量**：`draft_shifts_json`（string，纯 JSON，无 markdown）

### 系统提示词（System）

```
你是「医院随访科室月度排班助手」，专门为随访护士生成工作日值班与患者联系任务。

【重要】你的输出必须是排班结果，不是科室介绍。禁止只输出 department、month、科室名等元数据。

你的任务：
1. 阅读输入中的 schedulable_dates（可排班日期列表），为每个可排班日安排至少一名护士值班。
2. 在每个值班日，为该护士生成 contact_tasks（当日需电话联系的在管患者）。
3. 均衡各护士的值班日数量（班次数差不超过 rules.max_shift_imbalance）。
4. 高 contact_score 患者优先排入联系任务；critical/high 优先级患者本月至少安排 2 次联系（若可排班日足够）。
5. 患者尽量排在其 monitor_employee_id 对应护士的值班日；若无监视护士则按负载均衡分配。
6. 每位护士每个 work_date 的 contact_tasks 数量不得超过其 max_patients_per_day（或 rules.max_patients_per_day）。
7. 仅使用输入中的 employee id 与 register_id，禁止编造 ID。
8. 仅使用 schedulable_dates 中的日期，禁止排周末/节假日（除非该日在 schedulable_dates 中）。

输出要求：
- 只输出一个 JSON 对象，不要用 markdown 代码块，不要附加解释文字。
- 必须包含 shifts 数组与 summary 字符串；shifts 不能为空（每个可排班日至少安排一名护士值班）。
- 字段名必须使用 snake_case：employee_id、work_date、shift_type、contact_tasks、register_id、priority。
- 禁止使用 camelCase（employeeId、workDate、contactTasks、registerId）。
- shift_type 固定为 "full"。

输出示例（结构必须一致，日期与 ID 用输入中的真实值）：
{"shifts":[{"employee_id":123,"work_date":"2026-08-01","shift_type":"full","contact_tasks":[{"register_id":9001,"priority":"high"}]}],"summary":"2026-08草案"}
```



### 用户提示词（User）模板

在 Dify LLM 节点中引用上游变量：

```
科室：{{#planning_context_json#}}
可排班日期：{{#schedulable_dates_json#}}
随访护士：{{#staff_list_json#}}
患者（含紧迫度分数）：{{#scored_patients_json#}}
规则：{{#rules_obj_json#}}

请生成 {{month}} 的随访排班草案 JSON。
```

> 若 Dify 版本不支持在 User 中嵌 JSON 变量，可将 `planning_context_json` 等作为独立输入字段拼接。



### 输出 JSON Schema（LLM 必须遵守）

```json
{
  "shifts": [
    {
      "employee_id": 123,
      "work_date": "2026-07-02",
      "shift_type": "full",
      "contact_tasks": [
        {"register_id": 9001, "priority": "high"}
      ]
    }
  ],
  "summary": "草案说明文字"
}
```



### LLM 节点配置建议


| 项         | 建议值                |
| --------- | ------------------ |
| 温度        | 0.2 ~ 0.4          |
| 最大 tokens | 4096+（患者多时调高）      |
| 响应格式      | 若模型支持 JSON mode，开启 |


---



## Code 节点④ — 冲突检测与规则修复

**Dify 节点名**：`resolve_conflicts`

### 输入

`draft_shifts_json`, `schedulable_dates_json`, `staff_list_json`, `scored_patients_json`, `rules_obj_json`

### 输出

`resolved_shifts_json`, `conflict_report_json`

### 修复优先级（从高到低）

1. 剔除无效 `employee_id` / `register_id`
2. 剔除不在 `schedulable_dates` 的 `work_date`（或移至最近可排班日）
3. 同一 `work_date` + 同一 `register_id` 去重（保留 contact_score 更高归属）
4. 截断超 `max_patients_per_day` 的任务（保留高分患者）
5. 均衡护士班次数（将尾部班次从超载护士移到班次最少的护士）
6. **空班次回填**：若超过 30% 班次 `contact_tasks` 为空，按 `contact_score`、监视护士、`min_contact_interval_days` 自动填充（`sparse_contact_tasks` → `filled_by_score`）



### Python

> **Dify 说明**：LLM 节点若开启 JSON/结构化输出，`draft_shifts_json` 传到本节点时可能是 **dict 而非 str**。下列代码已兼容 dict / list / str，**禁止**对入参直接 `json.loads()`。

```python
import ast
import json
from collections import defaultdict


def _coerce_json(value, default):
    """兼容 str / dict / list；绝不把 dict 传给 json.loads。"""
    if value is None:
        return default
    if isinstance(value, dict):
        return value
    if isinstance(value, list):
        return value
    if isinstance(value, (bytes, bytearray)):
        value = value.decode("utf-8", errors="ignore")
    if not isinstance(value, str):
        return default
    text = value.strip()
    if not text:
        return default
    if text.startswith("```"):
        lines = text.splitlines()
        text = "\n".join(lines[1:-1] if lines and lines[-1].strip() == "```" else lines[1:]).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        try:
            return ast.literal_eval(text)
        except (ValueError, SyntaxError):
            return default


def _normalize_draft(value):
    """LLM 输出可能是 dict、JSON 字符串、Dify 包装字段、或 camelCase。"""
    if isinstance(value, list):
        return {"shifts": [_normalize_shift(s) for s in value], "summary": ""}
    if isinstance(value, dict):
        if "shifts" in value:
            return {
                "shifts": [_normalize_shift(s) for s in (value.get("shifts") or [])],
                "summary": value.get("summary") or "",
            }
        # LLM 常见误输出：只返回科室/月份元数据，没有 shifts
        meta_keys = {"department", "month", "department_name", "department_id", "科室", "月份"}
        if meta_keys.intersection(value.keys()) and "shifts" not in value:
            return {
                "shifts": [],
                "summary": "",
                "_wrong_schema": True,
                "_raw_keys": list(value.keys()),
            }
        for key in ("text", "answer", "output", "result", "draft_shifts_json", "structured_output"):
            if key in value:
                inner = _normalize_draft(value[key])
                if inner.get("shifts"):
                    return inner
    parsed = _coerce_json(value, None)
    if isinstance(parsed, dict) and "shifts" in parsed:
        return {
            "shifts": [_normalize_shift(s) for s in (parsed.get("shifts") or [])],
            "summary": parsed.get("summary") or "",
        }
    if isinstance(parsed, dict):
        meta_keys = {"department", "month", "department_name", "department_id", "科室", "月份"}
        if meta_keys.intersection(parsed.keys()):
            return {
                "shifts": [],
                "summary": "",
                "_wrong_schema": True,
                "_raw_keys": list(parsed.keys()),
            }
    if isinstance(parsed, list):
        return {"shifts": [_normalize_shift(s) for s in parsed], "summary": ""}
    return {"shifts": [], "summary": ""}


def _normalize_shift(s):
    if not isinstance(s, dict):
        return {}
    tasks_raw = s.get("contact_tasks") or s.get("contactTasks") or []
    tasks = []
    for t in tasks_raw or []:
        if not isinstance(t, dict):
            continue
        rid = t.get("register_id") or t.get("registerId")
        if rid is None:
            continue
        tasks.append(
            {
                "register_id": rid,
                "priority": t.get("priority") or "normal",
            }
        )
    return {
        "employee_id": s.get("employee_id") or s.get("employeeId"),
        "work_date": s.get("work_date") or s.get("workDate"),
        "shift_type": s.get("shift_type") or s.get("shiftType") or "full",
        "contact_tasks": tasks,
    }


def _days_between(d1, d2):
    from datetime import date

    a = date.fromisoformat(str(d1)[:10])
    b = date.fromisoformat(str(d2)[:10])
    return abs((b - a).days)


def _priority_interval(priority, rules):
    base = max(1, int(rules.get("min_contact_interval_days", 1)))
    if priority == "critical":
        return base
    if priority == "high":
        return max(base, 2)
    return max(base, 4)


def _distribute_contact_tasks(slots, patients, rules, staff):
    """按 contact_score、监视护士、最小联系间隔，向各班次分配 contact_tasks。"""
    if not slots:
        return []
    cap_default = int(rules.get("max_patients_per_day", 8))
    staff_cap = {
        _as_int(s.get("id")): int(s.get("max_patients_per_day", cap_default)) for s in staff
    }
    patients = list(patients) if isinstance(patients, list) else []
    patients.sort(key=lambda p: -int(p.get("contact_score") or 0))

    tasks_per_shift = [list(s.get("contact_tasks") or []) for s in slots]
    last_assigned = {}
    for i, slot in enumerate(slots):
        for t in tasks_per_shift[i]:
            rid = _as_int(t.get("register_id"))
            if rid is not None:
                last_assigned[rid] = slot["work_date"]

    by_employee = defaultdict(list)
    for i, slot in enumerate(slots):
        by_employee[_as_int(slot["employee_id"])].append(i)

    def _try_assign(slot_idx, rid, priority):
        wd = slots[slot_idx]["work_date"]
        eid = _as_int(slots[slot_idx]["employee_id"])
        cap = staff_cap.get(eid, cap_default)
        if len(tasks_per_shift[slot_idx]) >= cap:
            return False
        if any(_as_int(t.get("register_id")) == rid for t in tasks_per_shift[slot_idx]):
            return False
        last = last_assigned.get(rid)
        if last and _days_between(last, wd) < _priority_interval(priority, rules):
            return False
        tasks_per_shift[slot_idx].append({"register_id": rid, "priority": priority})
        last_assigned[rid] = wd
        return True

    for p in patients:
        rid = _as_int(p.get("register_id") or p.get("registerId"))
        if rid is None or rid in last_assigned:
            continue
        priority = p.get("priority") or "normal"
        monitor = p.get("monitor_employee_id") or p.get("monitorEmployeeId")
        monitor = _as_int(monitor) if monitor is not None else None
        candidates = by_employee.get(monitor, list(range(len(slots))))
        for slot_idx in sorted(candidates, key=lambda i: (len(tasks_per_shift[i]), slots[i]["work_date"])):
            if _try_assign(slot_idx, rid, priority):
                break

    changed = True
    while changed:
        changed = False
        for p in patients:
            rid = _as_int(p.get("register_id") or p.get("registerId"))
            if rid is None:
                continue
            priority = p.get("priority") or "normal"
            monitor = p.get("monitor_employee_id") or p.get("monitorEmployeeId")
            monitor = _as_int(monitor) if monitor is not None else None
            candidates = by_employee.get(monitor, list(range(len(slots))))
            for slot_idx in sorted(
                candidates, key=lambda i: (len(tasks_per_shift[i]), slots[i]["work_date"])
            ):
                if _try_assign(slot_idx, rid, priority):
                    changed = True
                    break

    return tasks_per_shift


def _build_fallback_shifts(schedulable, staff, scored_patients, rules):
    """LLM 未产出或全部被剔除时，按规则生成全月班次并分配联系任务。"""
    if not schedulable or not staff:
        return []
    slots = []
    for i, wd in enumerate(schedulable):
        member = staff[i % len(staff)]
        slots.append(
            {
                "employee_id": _as_int(member.get("id")),
                "work_date": str(wd)[:10],
                "shift_type": "full",
                "contact_tasks": [],
            }
        )
    tasks_per_shift = _distribute_contact_tasks(slots, scored_patients, rules, staff)
    shifts = []
    for i, slot in enumerate(slots):
        shifts.append({**slot, "contact_tasks": tasks_per_shift[i]})
    return shifts


def _as_int(value):
    try:
        return int(value)
    except (TypeError, ValueError):
        return value


def _score_map(scored_patients_json):
    patients = _coerce_json(scored_patients_json, [])
    if not isinstance(patients, list):
        patients = []
    out = {}
    for p in patients:
        rid = p.get("register_id") or p.get("registerId")
        if rid is not None:
            out[_as_int(rid)] = p.get("contact_score", 0)
            out[rid] = p.get("contact_score", 0)
    return out


def _nearest_schedulable(bad_date, schedulable):
    if not schedulable:
        return None
    if bad_date in schedulable:
        return bad_date
    best = schedulable[0]
    best_dist = abs(int(str(bad_date).replace("-", "")) - int(str(schedulable[0]).replace("-", "")))
    for d in schedulable[1:]:
        dist = abs(int(str(bad_date).replace("-", "")) - int(str(d).replace("-", "")))
        if dist < best_dist:
            best, best_dist = d, dist
    return best


def main(
    draft_shifts_json,
    schedulable_dates_json,
    staff_list_json,
    scored_patients_json,
    rules_obj_json,
) -> dict:
    draft = _normalize_draft(draft_shifts_json)
    report = []

    if draft.pop("_wrong_schema", False):
        report.append(
            {
                "type": "llm_wrong_schema",
                "message": "LLM 只返回了 department/month 等元数据，缺少 shifts 数组",
                "received_keys": draft.pop("_raw_keys", []),
                "action": "rule_based_fallback",
            }
        )

    schedulable = _coerce_json(schedulable_dates_json, [])
    if not isinstance(schedulable, list):
        schedulable = []
    sched_set = set(str(d) for d in schedulable)

    staff = _coerce_json(staff_list_json, [])
    if not isinstance(staff, list):
        staff = []
    staff_ids = {_as_int(s.get("id")) for s in staff}
    staff_cap = {_as_int(s.get("id")): s.get("max_patients_per_day", 8) for s in staff}

    scores = _score_map(scored_patients_json)
    valid_regs = set(scores.keys())
    patients_list = _coerce_json(scored_patients_json, [])
    if not isinstance(patients_list, list):
        patients_list = []

    rules = _coerce_json(rules_obj_json, {})
    if not isinstance(rules, dict):
        rules = {}
    default_cap = int(rules.get("max_patients_per_day", 8))
    max_imbalance = int(rules.get("max_shift_imbalance", 2))

    shifts = []
    for s in draft.get("shifts") or []:
        s = _normalize_shift(s)
        eid = _as_int(s.get("employee_id"))
        wd = str(s.get("work_date"))[:10] if s.get("work_date") else None
        if eid not in staff_ids:
            report.append({"type": "invalid_employee", "employee_id": eid, "action": "dropped_shift"})
            continue
        if wd not in sched_set:
            new_d = _nearest_schedulable(wd, [str(x) for x in schedulable])
            if new_d:
                report.append({"type": "bad_date", "from": wd, "to": new_d, "action": "moved"})
                wd = new_d
            else:
                report.append({"type": "bad_date", "from": wd, "action": "dropped_shift"})
                continue

        tasks = []
        for t in s.get("contact_tasks") or []:
            rid = _as_int(t.get("register_id") or t.get("registerId"))
            if rid not in valid_regs and t.get("register_id") not in valid_regs and t.get("registerId") not in valid_regs:
                report.append({"type": "invalid_register", "register_id": rid, "action": "dropped_task"})
                continue
            score = scores.get(rid, scores.get(t.get("register_id"), scores.get(t.get("registerId"), 0)))
            tasks.append(
                {
                    "register_id": rid,
                    "priority": t.get("priority") or "normal",
                    "_score": score,
                }
            )

        tasks.sort(key=lambda x: -x["_score"])
        cap = staff_cap.get(eid, default_cap)
        if len(tasks) > cap:
            report.append(
                {
                    "type": "over_cap",
                    "employee_id": eid,
                    "work_date": wd,
                    "from_count": len(tasks),
                    "cap": cap,
                    "action": "truncated",
                }
            )
            tasks = tasks[:cap]

        for t in tasks:
            t.pop("_score", None)

        shifts.append(
            {
                "employee_id": eid,
                "work_date": wd,
                "shift_type": s.get("shift_type") or "full",
                "contact_tasks": tasks,
            }
        )

    seen_day_patient = {}
    deduped = []
    for s in shifts:
        wd, eid = s["work_date"], s["employee_id"]
        kept = []
        for t in s["contact_tasks"]:
            key = (wd, t["register_id"])
            if key in seen_day_patient:
                report.append(
                    {
                        "type": "duplicate_same_day",
                        "register_id": t["register_id"],
                        "work_date": wd,
                        "action": "dropped_duplicate",
                    }
                )
                continue
            seen_day_patient[key] = eid
            kept.append(t)
        deduped.append({**s, "contact_tasks": kept})
    shifts = deduped

    if schedulable and staff and patients_list and shifts:
        empty_count = sum(1 for s in shifts if not s.get("contact_tasks"))
        if empty_count > len(shifts) * 0.3:
            report.append(
                {
                    "type": "sparse_contact_tasks",
                    "empty_shifts": empty_count,
                    "total_shifts": len(shifts),
                    "action": "filled_by_score",
                }
            )
            tasks_lists = _distribute_contact_tasks(shifts, patients_list, rules, staff)
            shifts = [{**s, "contact_tasks": tasks_lists[i]} for i, s in enumerate(shifts)]

    if schedulable and staff:
        need_count = len(schedulable)
        if len(shifts) < need_count:
            if shifts:
                report.append(
                    {
                        "type": "llm_incomplete_schedule",
                        "got": len(shifts),
                        "expected_min": need_count,
                        "action": "rule_based_fallback",
                    }
                )
            else:
                report.append({"type": "llm_empty_or_invalid", "action": "rule_based_fallback"})
            shifts = _build_fallback_shifts(schedulable, staff, patients_list, rules)

    if staff_ids and shifts:
        counts = defaultdict(int)
        for s in shifts:
            counts[s["employee_id"]] += 1
        while True:
            if not counts:
                break
            hi = max(counts, key=counts.get)
            lo = min(counts, key=counts.get)
            if counts[hi] - counts[lo] <= max_imbalance:
                break
            moved = False
            for i, s in enumerate(shifts):
                if s["employee_id"] == hi:
                    shifts[i] = {**s, "employee_id": lo}
                    counts[hi] -= 1
                    counts[lo] += 1
                    report.append(
                        {
                            "type": "rebalance",
                            "from_employee": hi,
                            "to_employee": lo,
                            "work_date": s["work_date"],
                            "action": "reassigned_shift",
                        }
                    )
                    moved = True
                    break
            if not moved:
                break

    resolved = {
        "shifts": sorted(shifts, key=lambda x: (x["work_date"], x["employee_id"])),
        "summary": draft.get("summary")
        or (f"规则降级排班，共 {len(shifts)} 个班次" if shifts else ""),
    }

    return {
        "resolved_shifts_json": json.dumps(resolved, ensure_ascii=False),
        "conflict_report_json": json.dumps(report, ensure_ascii=False),
    }
```

---



## Code 节点⑤ — 结构校验与补全

**Dify 节点名**：`validate_output`

### 输入

`resolved_shifts_json`, `conflict_report_json`, `planning_context_json`

### 输出

`validated_shifts_json`（**End 节点唯一对外输出**）

### Python

```python
import ast
import json


def _loads(value, default):
    if isinstance(value, (list, dict)):
        return value
    text = str(value).strip()
    if not text:
        return default
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return ast.literal_eval(text)


def main(
    resolved_shifts_json: str,
    conflict_report_json: str,
    planning_context_json: str,
) -> dict:
    data = _loads(resolved_shifts_json, {"shifts": []})
    report = _loads(conflict_report_json, [])
    ctx = _loads(planning_context_json, {})

    if "shifts" not in data or not isinstance(data["shifts"], list):
        raise ValueError("缺少 shifts 数组")

    normalized = []
    covered_patients = set()
    for s in data["shifts"]:
        eid = s.get("employee_id")
        wd = s.get("work_date")
        if eid is None or not wd:
            continue
        tasks = []
        for t in s.get("contact_tasks") or []:
            rid = t.get("register_id")
            if rid is None:
                continue
            covered_patients.add(rid)
            tasks.append(
                {
                    "register_id": rid,
                    "priority": t.get("priority") or "normal",
                }
            )
        normalized.append(
            {
                "employee_id": int(eid),
                "work_date": str(wd)[:10],
                "shift_type": s.get("shift_type") or "full",
                "contact_tasks": tasks,
            }
        )

    fix_count = len(report)
    dept = ctx.get("department_name") or ctx.get("department_id") or ""
    month = ctx.get("month") or ""
    summary = (
        f"{month} {dept}：{len(normalized)} 个班次，"
        f"覆盖 {len(covered_patients)} 名在管患者"
    )
    if fix_count:
        summary += f"，自动修复 {fix_count} 处冲突"

    out = {"shifts": normalized, "summary": summary}
    return {"validated_shifts_json": json.dumps(out, ensure_ascii=False)}
```

---



## End 节点

- 输出变量：`validated_shifts_json`（text）
- 后端读取：`outputs.validated_shifts_json` 或 fallback `outputs.text`



### 最终 JSON 示例

```json
{
  "shifts": [
    {
      "employee_id": 123,
      "work_date": "2026-07-02",
      "shift_type": "full",
      "contact_tasks": [
        {"register_id": 9001, "priority": "high"}
      ]
    }
  ],
  "summary": "2026-07 内分泌科：22 个班次，覆盖 1 名在管患者"
}
```

---



## 端到端样例（内分泌科 · 张糖友）



### 输入（Start）

在 Dify 中**不要**贴一整段 JSON 对象；按上表 **7 个字段分别填写**。合并视图仅作对照：

```json
{
  "department_id": "7",
  "department_name": "内分泌科",
  "month": "2026-07",
  "staff_json": "[{\"id\":123,\"name\":\"内分泌护士\",\"max_patients_per_day\":8}]",
  "patients_json": "[{\"registerId\":9001,\"priority\":\"high\",\"monitorEmployeeId\":123,\"lastContactDate\":\"2026-06-20\",\"deadlineDate\":\"2026-12-01\"}]",
  "rules_json": "{\"workdays_per_week\":5,\"min_contact_interval_days\":1,\"deadline_days\":180,\"max_patients_per_day\":8,\"max_shift_imbalance\":2}",
  "holidays_json": "[]"
}
```



### 中间产物摘要


| 阶段    | 关键结果                                                                    |
| ----- | ----------------------------------------------------------------------- |
| Code① | `patient_count=1`, `staff_count=1`, 7月共 31 天                            |
| Code② | `schedulable_day_count=23`（周一至周五，无额外节假日）                                |
| Code③ | 9001 `contact_score` ≈ 60+50+10=120（high + 距 deadline 约 5 个月 + 10 天前联系） |
| LLM   | 为 123 生成 23 个班次，每日 contact_tasks 含 9001（或分散多日）                          |
| Code④ | 若 LLM 某天排超 8 人则截断；去重                                                    |
| Code⑤ | 输出合法 `validated_shifts_json`                                            |




### 输出片段（validated_shifts_json 节选）

```json
{
  "shifts": [
    {
      "employee_id": 123,
      "work_date": "2026-07-01",
      "shift_type": "full",
      "contact_tasks": [{"register_id": 9001, "priority": "high"}]
    },
    {
      "employee_id": 123,
      "work_date": "2026-07-02",
      "shift_type": "full",
      "contact_tasks": [{"register_id": 9001, "priority": "high"}]
    }
  ],
  "summary": "2026-07 内分泌科：23 个班次，覆盖 1 名在管患者"
}
```



### 后端落库后

- `follow_up_shift_plan`：department_id=7, month=2026-07, status=draft
- `follow_up_staff_shift`：每条 shift 一行
- `follow_up_shift_contact_task`：每条 contact_task 关联 shift_id

管理员点「发布排班」→ 随访护士在工作台日历看到「班」标记。

---



## 样例 2 — 含节假日（7.1 建党节休）

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-07` |
| `staff_json` | `[{"id":123,"name":"内分泌护士","max_patients_per_day":8}]` |
| `patients_json` | `[{"registerId":9001,"priority":"high","monitorEmployeeId":123,"lastContactDate":"2026-06-20","deadlineDate":"2026-12-01"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":8,"max_shift_imbalance":2}` |
| `holidays_json` | `[{"date":"2026-07-01","name":"建党节","type":"holiday"}]` |

**验证点**：Code② 的 `schedulable_dates` 不含 `2026-07-01`；首个可排班日为 `2026-07-02`。

---

## 样例 3 — 双护士 + 双患者（监视人各不同）

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-07` |
| `staff_json` | `[{"id":123,"name":"内分泌护士A","max_patients_per_day":8},{"id":124,"name":"内分泌护士B","max_patients_per_day":8}]` |
| `patients_json` | `[{"registerId":9001,"priority":"high","monitorEmployeeId":123,"lastContactDate":"2026-06-28","deadlineDate":"2026-12-01"},{"registerId":9010,"priority":"normal","monitorEmployeeId":124,"lastContactDate":"2026-06-25","deadlineDate":"2026-11-15"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":8,"max_shift_imbalance":2}` |
| `holidays_json` | `[]` |

**验证点**：两护士班次数差 ≤2；9001 尽量落在 123 的班次，9010 落在 124。

---

## 样例 4 — 三护士 + 五患者（负载均衡）

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-08` |
| `staff_json` | `[{"id":123,"name":"护士甲","max_patients_per_day":6},{"id":124,"name":"护士乙","max_patients_per_day":6},{"id":125,"name":"护士丙","max_patients_per_day":6}]` |
| `patients_json` | `[{"registerId":9001,"priority":"high","monitorEmployeeId":123,"lastContactDate":"2026-07-10","deadlineDate":"2026-10-01"},{"registerId":9002,"priority":"critical","monitorEmployeeId":124,"lastContactDate":"2026-07-05","deadlineDate":"2026-09-01"},{"registerId":9003,"priority":"normal","monitorEmployeeId":125,"lastContactDate":"2026-07-20","deadlineDate":"2027-01-01"},{"registerId":9004,"priority":"high","monitorEmployeeId":123,"lastContactDate":null,"deadlineDate":"2026-11-01"},{"registerId":9005,"priority":"normal","monitorEmployeeId":null,"lastContactDate":"2026-07-01","deadlineDate":"2027-02-01"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":6,"max_shift_imbalance":2}` |
| `holidays_json` | `[]` |

**验证点**：8 月约 21 个工作日；三护士班次大致均分；9002（critical + deadline 近）contact_score 最高、全月联系次数最多；9005 无监视护士由规则均衡分配；**空 contact_tasks 班次应少于 30%**（Code④ 自动回填后）。

**样例 4 达标输出特征**（重跑 Code④ 新版后）：

- `shifts.length` = 21
- `summary` = `2026-08 内分泌科：21 个班次，覆盖 5 名在管患者，自动修复 N 处冲突`
- `conflict_report` 可能含 `sparse_contact_tasks` / `filled_by_score`
- 9002 出现次数 ≥ 9001 ≥ 9003/9005（按紧迫度）

---

## 样例 5 — 随访期限临近（高紧迫度）

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-07` |
| `staff_json` | `[{"id":123,"name":"内分泌护士","max_patients_per_day":8}]` |
| `patients_json` | `[{"registerId":9001,"priority":"high","monitorEmployeeId":123,"lastContactDate":"2026-06-01","deadlineDate":"2026-07-25"},{"registerId":9011,"priority":"normal","monitorEmployeeId":123,"lastContactDate":"2026-07-01","deadlineDate":"2027-06-01"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":8,"max_shift_imbalance":2}` |
| `holidays_json` | `[]` |

**验证点**：Code③ 中 9001 的 `contact_score` 明显高于 9011；LLM 应让 9001 本月联系次数更多。

---

## 样例 6 — 患者未分配监视护士

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-07` |
| `staff_json` | `[{"id":123,"name":"内分泌护士","max_patients_per_day":8}]` |
| `patients_json` | `[{"registerId":9001,"priority":"high","monitorEmployeeId":null,"lastContactDate":"2026-06-15","deadlineDate":"2026-12-01"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":8,"max_shift_imbalance":2}` |
| `holidays_json` | `[]` |

**验证点**：Code① `warnings` 含「未分配监视护士」；排班仍可生成，患者由唯一护士 123 承担。

---

## 样例 7 — 无在管患者（空患者列表）

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-07` |
| `staff_json` | `[{"id":123,"name":"内分泌护士","max_patients_per_day":8}]` |
| `patients_json` | `[]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":8,"max_shift_imbalance":2}` |
| `holidays_json` | `[]` |

**验证点**：`patient_count=0`；班次可生成但 `contact_tasks` 均为空数组；summary 覆盖 0 名患者。

---

## 样例 8 — 节假日 + 调休上班（周六补班）

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-07` |
| `staff_json` | `[{"id":123,"name":"内分泌护士","max_patients_per_day":8}]` |
| `patients_json` | `[{"registerId":9001,"priority":"high","monitorEmployeeId":123,"lastContactDate":"2026-06-20","deadlineDate":"2026-12-01"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":8,"max_shift_imbalance":2}` |
| `holidays_json` | `[{"date":"2026-07-01","name":"建党节","type":"holiday"},{"date":"2026-07-04","name":"调休上班","type":"workday"}]` |

**验证点**：`2026-07-01` 不可排班；`2026-07-04` 为周六但因 `type:workday` 应出现在 `schedulable_dates`。

---

## 样例 9 — 每日联系上限很低（测 Code④ 截断）

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-07` |
| `staff_json` | `[{"id":123,"name":"内分泌护士","max_patients_per_day":2}]` |
| `patients_json` | `[{"registerId":9001,"priority":"high","monitorEmployeeId":123,"lastContactDate":"2026-06-01","deadlineDate":"2026-12-01"},{"registerId":9010,"priority":"high","monitorEmployeeId":123,"lastContactDate":"2026-06-05","deadlineDate":"2026-11-01"},{"registerId":9011,"priority":"critical","monitorEmployeeId":123,"lastContactDate":"2026-06-10","deadlineDate":"2026-10-01"},{"registerId":9012,"priority":"normal","monitorEmployeeId":123,"lastContactDate":"2026-07-01","deadlineDate":"2027-01-01"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":2,"max_shift_imbalance":2}` |
| `holidays_json` | `[]` |

**验证点**：若 LLM 某日给 123 排了 4 个 contact_tasks，Code④ 应截断为 2 个并产生 `over_cap` 冲突记录。

---

## 样例 10 — 心血管科（演示数据 9002 / 李心糖）

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `3` |
| `department_name` | `心血管科` |
| `month` | `2026-07` |
| `staff_json` | `[{"id":126,"name":"心血管护士","max_patients_per_day":8}]` |
| `patients_json` | `[{"registerId":9002,"priority":"high","monitorEmployeeId":126,"lastContactDate":"2026-06-18","deadlineDate":"2026-12-15"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":8,"max_shift_imbalance":2}` |
| `holidays_json` | `[]` |

**验证点**：换科室 ID/名称后流程仍通；与内分泌科样例结构相同。

---

## 样例 11 — 短月 + 严格均衡（2 护士 2 患者）

| 变量 | 粘贴值 |
|------|--------|
| `department_id` | `7` |
| `department_name` | `内分泌科` |
| `month` | `2026-02` |
| `staff_json` | `[{"id":123,"name":"护士A","max_patients_per_day":5},{"id":124,"name":"护士B","max_patients_per_day":5}]` |
| `patients_json` | `[{"registerId":9001,"priority":"normal","monitorEmployeeId":123,"lastContactDate":"2026-01-20","deadlineDate":"2026-08-01"},{"registerId":9010,"priority":"normal","monitorEmployeeId":124,"lastContactDate":"2026-01-22","deadlineDate":"2026-08-01"}]` |
| `rules_json` | `{"workdays_per_week":5,"min_contact_interval_days":1,"deadline_days":180,"max_patients_per_day":5,"max_shift_imbalance":1}` |
| `holidays_json` | `[{"date":"2026-02-17","name":"春节","type":"holiday"},{"date":"2026-02-18","name":"春节","type":"holiday"},{"date":"2026-02-19","name":"春节","type":"holiday"},{"date":"2026-02-20","name":"春节","type":"holiday"},{"date":"2026-02-23","name":"春节","type":"holiday"}]` |

**验证点**：2 月工作日较少；`max_shift_imbalance:1` 要求两护士班次数几乎相等；春节日期被剔除。

---

## 测试顺序建议

| 顺序 | 样例 | 目的 |
|------|------|------|
| 1 | 样例 1 | 最小通路冒烟 |
| 2 | 样例 7 | 空患者边界 |
| 3 | 样例 2 / 8 | 节假日逻辑 |
| 4 | 样例 3 / 11 | 多护士均衡 |
| 5 | 样例 5 / 9 | 紧迫度与冲突修复 |
| 6 | 样例 4 | 综合压力 |

每次只改 Start 七个字段，无需改中间节点。

---

## 后端对接


| 项          | 说明                                                  |
| ---------- | --------------------------------------------------- |
| 服务         | `FollowUpShiftDifyService.generateShifts`           |
| 异步任务       | `FollowUpShiftAiTaskService.submit`                 |
| 持久化        | `FollowUpShiftScheduleService.persistGeneratedPlan` |
| API Key 配置 | `DIFY_API_KEY_FOLLOW_UP_SHIFT_SCHEDULE`             |
| 开关         | `DIFY_WORKFLOW_FOLLOW_UP_SHIFT_SCHEDULE=true`       |
| 降级         | Dify 失败时 `buildRuleBasedShifts` 规则排班                |




### 环境变量（`xikang-cloud-hospital/.env`）

在现有 `DIFY_BASE_URL` 下方追加（与 W2/W3 等同文件、同自托管地址）：

```bash
# 随访月度排班 AI（medtech-service · WF-FollowUp-Shift-01）
DIFY_WORKFLOW_FOLLOW_UP_SHIFT_SCHEDULE=true
DIFY_API_KEY_FOLLOW_UP_SHIFT_SCHEDULE=app-YGV08OYSTm2WcuL6sohEcsDV
```

| 变量 | 值 | 说明 |
|------|-----|------|
| `DIFY_BASE_URL` | `http://118.178.253.230` | 已有，勿带 `/v1` |
| `DIFY_WORKFLOW_FOLLOW_UP_SHIFT_SCHEDULE` | `true` | 开关 |
| `DIFY_API_KEY_FOLLOW_UP_SHIFT_SCHEDULE` | `app-YGV08OYSTm2WcuL6sohEcsDV` | 本工作流 App Key |

**生效服务**：`medtech-service`（端口 8098）。修改 `.env` 后需**重启 medtech-service**。

**未配置或 Dify 失败时**：自动走 `FollowUpShiftDifyService.buildRuleBasedShifts` 规则降级，页面仍可生成草稿。

---

### Dify 控制台配置步骤

1. **创建工作流 App**（类型：Workflow），名称建议 `WF-FollowUp-Shift-01`。
2. **Start 节点**：添加 7 个 text-input，变量名必须与下表完全一致（与 Java `FollowUpShiftDifyService` 对齐）：

| Start 变量 | 类型 | 说明 |
|------------|------|------|
| `department_id` | text | 科室 ID |
| `department_name` | text | 科室名称 |
| `month` | text | `yyyy-MM` |
| `staff_json` | text | 护士 JSON 数组字符串 |
| `patients_json` | text | 患者 JSON 数组字符串 |
| `rules_json` | text | 规则 JSON 对象字符串 |
| `holidays_json` | text | 节假日 JSON 数组字符串 |

3. **按本文 7 节点串联**：`prep` → `holiday` → `score` → `llm_generate_shifts` → `resolve_conflicts` → `validate_output` → **End**。
4. **LLM 节点**：输出变量名 `draft_shifts_json`；温度 0.2～0.3；max tokens ≥ 8192。
5. **Code④ `resolve_conflicts`**：从本文 **整段复制** Python（含 `_distribute_contact_tasks`、空班次回填逻辑）。
6. **End 节点**：仅暴露输出变量 `validated_shifts_json`（text）。
7. **发布工作流** → **访问 API** → 确认 API Key 为 `app-YGV08OYSTm2WcuL6sohEcsDV`。
8. **Dify 调试**：用样例 4 七个字段分别粘贴，运行后检查：
   - `shifts` 约 21 条（8 月）
   - 空 `contact_tasks` 班次应明显减少（Code④ 会 `filled_by_score`）
   - `summary` 形如 `2026-08 内分泌科：21 个班次，覆盖 5 名在管患者`

### 验证后端连通（可选）

```bash
curl -X POST "http://118.178.253.230/v1/workflows/run" \
  -H "Authorization: Bearer app-YGV08OYSTm2WcuL6sohEcsDV" \
  -H "Content-Type: application/json" \
  -d "{\"inputs\":{\"department_id\":\"7\",\"department_name\":\"内分泌科\",\"month\":\"2026-08\",\"staff_json\":\"[{\\\"id\\\":123,\\\"name\\\":\\\"护士甲\\\",\\\"max_patients_per_day\\\":6}]\",\"patients_json\":\"[]\",\"rules_json\":\"{}\",\"holidays_json\":\"[]\"},\"response_mode\":\"blocking\",\"user\":\"test\"}"
```

成功时 `data.outputs.validated_shifts_json` 为非空 JSON 字符串。

### 管理端验证

1. 重启 `medtech-service` 使 `.env` 生效。
2. 登录管理端 → **随访排班** → 选择科室与月份 → **AI 生成排班**。
3. 日志应出现 Dify 调用成功；失败则打印 `fallback to rule-based`。

---



## 排班结果为空（shifts: []）排查

### 典型案例：LLM 只回了科室/月份

若 Code④ 输入类似：

```json
"draft_shifts_json": {"department": "心内科", "month": "2025-03"}
```

这是 **LLM 错误输出**（复述了上下文，没有 `shifts`）。Code④ 无法从中解析班次，旧版会得到空结果。

**新版 Code④** 会：
1. 在 `conflict_report_json` 写入 `llm_wrong_schema`
2. 自动 **规则降级** 生成约 21 个班次（与 `schedulable_dates` 数量一致）

你贴的输入里 `schedulable_dates` 有 21 天、护士 3 人、患者 5 人，更新 Code④ 后预期输出 **21 条 shift**，且 9002 等患者会优先落在其 `monitorEmployeeId` 对应护士的班次。

若 `conflict_report_json` 仍为 `[]` 且 `shifts` 为空，说明 **Dify 里 Code④ 仍是旧代码**，请从 md 整段重新粘贴。

| 原因 | 现象 | 处理 |
|------|------|------|
| LLM 返回空数组 | `draft_shifts_json` 里 `shifts:[]` | 加强系统提示词；检查 LLM 是否因 token 不足截断 |
| 字段名用 camelCase | `employeeId` 导致 Code④ 全部 `invalid_employee` | 已在新版 Code④ 做兼容；请更新节点代码 |
| LLM 输出未接到变量 | Code④ 收到 null/空 | 确认 LLM 节点「输出」已映射到 `draft_shifts_json` |
| 上游 schedulable 为空 | 月份或节假日配置错误 | 检查 Code② 的 `schedulable_dates_json` 长度 |
| 仅有空患者样例 7 | 故意无患者 | 班次可有但 `contact_tasks` 为空；样例 4 不应为空 |

**新版 Code④** 在 LLM 无有效班次时会自动 **规则降级**（`rule_based_fallback`），样例 4 应得到约 21 个 8 月工作日班次。更新代码后 `conflict_report_json` 中若出现 `llm_empty_or_invalid` 表示走了降级。

**Dify LLM 节点检查：**

1. 输出变量名必须是 `draft_shifts_json`（或与 Code④ 入参一致）
2. 若用「结构化输出 / JSON Schema」，schema 根对象需含 `shifts` 数组
3. 建议温度 0.2～0.3，max tokens ≥ 8192（样例 4 患者多、月份长）

---

## Dify 部署检查清单

- [ ] Start 7 个 text-input 变量名与后端完全一致
- [ ] 各 Code 节点 `main` 入参/出参与本文映射表一致
- [ ] LLM 输出变量名为 `draft_shifts_json`
- [ ] End 仅暴露 `validated_shifts_json`
- [ ] 工作流发布后用 Postman / Dify 调试传入样例 1 验证
- [ ] 管理端「AI 生成排班」能成功并写入 draft 计划

---



## 附录 A — 4 节点简化版合并代码

将 Code①②③合并为 `prep_all`：

```python
# 在 prep 的 main 末尾调用 holiday + score 逻辑，一次性输出：
# planning_context_json, schedulable_dates_json, staff_list_json,
# scored_patients_json, rules_obj_json, calendar_meta_json
```

将 Code④⑤合并为 `fix_and_validate`：先执行 `resolve_conflicts` 的 main，再执行 `validate_output` 的 main，只输出 `validated_shifts_json`。

简化画布：

```
开始 → prep_all → LLM → fix_and_validate → 结束
```

---



## 附录 B — 未来扩展（未实现）


| 扩展     | 做法                                        |
| ------ | ----------------------------------------- |
| 护士请假   | 后端增加 `unavailable_dates_json`，Code② 按护士过滤 |
| 待审批调班  | 传入 `pending_changes_json`，Code④ 优先满足      |
| 节假日数据源 | 后端从国务院节假日 API 填充 `holidays_json`          |


