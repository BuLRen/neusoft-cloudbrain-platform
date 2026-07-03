# WF-FollowUp-Shift-01 随访工作排班 Dify 工作流

## 用途

管理员为某临床科室的随访医生生成月度工作排班：哪天值班、当天需联系哪些在管患者。

## 节点结构

```
开始 → LLM（排班分析） → Code（JSON 校验） → 结束
```

## 开始节点（全部为 text-input）

| 变量名 | 说明 | 示例 |
|--------|------|------|
| department_id | 科室 ID | `7` |
| department_name | 科室名称 | `内分泌科` |
| month | 月份 yyyy-MM | `2026-07` |
| staff_json | 随访人员 JSON 字符串 | 见下方 |
| patients_json | 在管患者 JSON 字符串 | 见下方 |
| rules_json | 规则 JSON 字符串 | 见下方 |
| holidays_json | 节假日 JSON 数组字符串 | `[]` |

### staff_json 格式

```json
[{"id": 12, "name": "张护士", "max_patients_per_day": 8}]
```

### patients_json 格式

```json
[
  {
    "registerId": 9001,
    "priority": "high",
    "monitorEmployeeId": 12,
    "lastContactDate": "2026-06-20",
    "deadlineDate": "2026-12-01"
  }
]
```

### rules_json 格式

```json
{"workdays_per_week": 5, "min_contact_interval_days": 1, "deadline_days": 180, "max_patients_per_day": 8}
```

## LLM 提示词要点

你是医院随访排班助手。根据输入的科室、月份、随访人员、在管患者与规则，生成合理的工作排班。

要求：
1. 仅安排周一至周五为工作日（除非 holidays_json 另有说明）
2. 均衡分配各随访人员的值班日
3. 高优先级（critical/high）患者优先排入工作日联系任务
4. 临近 deadlineDate（6 个月随访期限）的患者提高联系频率
5. 每人每日 contact_tasks 数量不超过 max_patients_per_day
6. 优先将患者分配给其 monitorEmployeeId 对应的医生值班日

只输出 JSON，不要 markdown 代码块。

## Code 节点（Python 校验示例）

```python
import json

def main(validated_shifts_json: str) -> dict:
    data = json.loads(validated_shifts_json)
    assert "shifts" in data
    for s in data["shifts"]:
        assert "employee_id" in s and "work_date" in s
        assert "contact_tasks" in s
    return {"validated_shifts_json": json.dumps(data, ensure_ascii=False)}
```

## 结束节点

输出变量：`validated_shifts_json`（text）

### 输出 JSON 结构

```json
{
  "shifts": [
    {
      "employee_id": 12,
      "work_date": "2026-07-01",
      "shift_type": "full",
      "contact_tasks": [
        {"register_id": 9001, "priority": "high"},
        {"register_id": 9002, "priority": "normal"}
      ]
    }
  ],
  "summary": "7月共安排 22 个班次，覆盖 45 名在管患者"
}
```

## 后端对接

- 服务：`FollowUpShiftDifyService`
- 配置：`xikang.ai.dify.api-key-follow-up-shift-schedule`
- 未配置 Dify 时自动降级为规则排班（工作日轮班）

## 环境变量

```bash
DIFY_API_KEY_FOLLOW_UP_SHIFT_SCHEDULE=app-xxx
DIFY_BASE_URL=http://your-dify-host
DIFY_WORKFLOW_FOLLOW_UP_SHIFT_SCHEDULE=true
```
