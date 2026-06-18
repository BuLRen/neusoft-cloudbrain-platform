# WF-01: AI生成月度排班 工作流设计文档

> 版本：v3.2
> 日期：2026-06-07
> 状态：双LLM节点+条件分支（初始生成+冲突修正+缺失检测）

---

## 工作流架构

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     WF-01 AI生成月度排班（双LLM节点+条件分支）                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────┐                                                               │
│  │ 1.开始节点  │ department_id, department_name, month, physicians,            │
│  │     ⏺      │            weekday_patterns, holidays                          │
│  └──────┬──────┘ │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────────────────┐                                                   │
│  │ 2.节假日处理            │ 自动计算周末和节假日，生成规则                     │
│  └──────┬──────────────────┘                                                   │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────────────────┐                                                   │
│  │ 3.LLM生成排班           │ AI 初始生成排班方案                             │
│  └──────┬──────────────────┘                                                   │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────────────────┐                                                   │
│  │ 4.冲突检测              │ 检测重复排班、工作过载                            │
│  └──────┬──────────────────┘                                                   │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      条件分支                                            │   │
│  │                         hasConflict?                                     │   │
│  │                      ┌─────┴─────┐                                       │   │
│  │                      ↓           ↓                                       │   │
│  │                 true          false                                      │   │
│  │                      ↓           ↓                                       │   │
│  │              ┌─────────────────────────┐                                 │   │
│  │              │ 3'.LLM修正排班         │                                 │   │
│  │              │ 接收上轮schedules      │                                 │   │
│  │              │ 和errors进行修正        │                                 │   │
│  │              └───────────┬─────────────┘                                 │   │
│  └──────────────────────────┼───────────────────────────────────────────────┘   │
│                             │                                                     │
│                             ▼                                                     │
│  ┌─────────────────────────┐                                                   │
│  │ 5.号源补充              │ 补充字段、格式校验                                │
│  └──────┬──────────────────┘                                                   │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────┐                                                               │
│  │ 6.结束节点  │ 输出最终排班结果                                              │
│  │     ⏹      │                                                               │
│ └─────────────┘                                                               │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 条件分支机制

### 条件分支逻辑

```
节点4输出 hasConflict = true ?
    │
    ├─ 是 → 节点3'（LLM修正排班）→ 继续节点5
    │
    └─ 否 → 继续节点5
```

### 节点3'的作用

当节点4检测到冲突时，节点3'会：
1. 接收节点4输出的 errors（冲突列表）
2. 接收节点3输出的 schedules（上轮生成的排班）
3. 基于错误信息修正排班
4. 输出修正后的 schedules（无论是否有冲突都输出）

---

## Coze 代码节点格式说明

Coze 代码节点必须使用以下格式：

```javascript
//only dayjs and lodash are allowed
import dayjs from 'dayjs';
import _ from 'lodash';

async function main({ params }: Args): Promise<Output> {
  // 获取参数：params.xxx
  // 返回结果：return { 变量名: 值 }

  // 你的代码...

  return {
    // 输出变量
  };
}
```

### 关键区别

| 项目 | 说明 |
|------|------|
| 参数获取 | `params.xxx` 而不是 `input.xxx` |
| 返回值 | `return { ... }` 而不是 `output.xxx = value` |
| 函数声明 | `async function` |
| 第三方库 | 可用 `dayjs` 和 `lodash` |

---

## 数据结构定义

### 医生对象 Physician

| 属性名 | 类型 | 说明 |
|--------|------|------|
| id | Integer | 医生ID |
| realname | String | 医生姓名 |
| position | String | 职称：主任医师号/专家号/普通号 |

### 排班对象 Schedule

| 属性名 | 类型 | 说明 |
|--------|------|------|
| physician_id | Integer | 医生ID |
| physician_name | String | 医生姓名 |
| work_date | String | 出诊日期（格式：YYYY-MM-DD） |
| time_slot | String | 时段：上午/下午 |
| total_quota | Integer | 总号源 |
| available_quota | Integer | 剩余号源 |
| used_quota | Integer | 已用号源 |
| price | Number | 挂号费 |
| regist_level_id | Integer | 挂号级别ID |
| ai_suggestion | String | AI建议 |
| status | String | 状态 |

### 历史规律对象 WeekdayPattern

| 属性名 | 类型 | 说明 |
|--------|------|------|
| avg_quota | Integer | 平均号源 |
| usage_rate | Number | 使用率 |

---

## 节点1：开始节点

### 类型设计

| 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|-----------|---------|------|------|------|
| department_id | Integer | 是 | 后端 | 科室ID |
| department_name | String | 是 | 后端 | 科室名称 |
| month | String | 是 | 后端 | 月份（格式：YYYY-MM） |
| physicians | Array\<Physician\> | 是 | 后端 | 医生列表 |
| weekday_patterns | Object | 否 | 后端 | 历史规律（有历史数据才传） |
| holidays | Array\<String\> | 否 | 后端 | 法定节假日列表 |

### physicians 输入示例

```json
{
  "physicians": [
    {"id": 5, "realname": "内科刘主任", "position": "主任医师号"},
    {"id": 4, "realname": "内科赵专家", "position": "专家号"},
    {"id": 1, "realname": "内科张医生", "position": "普通号"},
    {"id": 2, "realname": "内科李医生", "position": "普通号"},
    {"id": 3, "realname": "内科王医生", "position": "普通号"}
  ]
}
```

### 输入完整示例

```json
{
  "department_id": 1,
  "department_name": "内科",
  "month": "2026-07",
  "physicians": [
    {"id": 5, "realname": "内科刘主任", "position": "主任医师号"},
    {"id": 4, "realname": "内科赵专家", "position": "专家号"},
    {"id": 1, "realname": "内科张医生", "position": "普通号"},
    {"id": 2, "realname": "内科李医生", "position": "普通号"},
    {"id": 3, "realname": "内科王医生", "position": "普通号"}
  ],
  "weekday_patterns": {
    "周一": {"avg_quota": 100, "usage_rate": 0.85},
    "周二": {"avg_quota": 95, "usage_rate": 0.80}
  },
  "holidays": ["2026-07-04"]
}
```

---

## 节点2：节假日处理

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | month | String | 是 | 节点1 | 目标月份 |
| | holidays | Array\<String\> | 否 | 节点1 | 法定节假日列表 |
| **输出** | weekends | Array\<String\> | - | - | 周六列表 |
| | holidays | Array\<String\> | - | - | 法定节假日列表 |
| | reducedDays | Array\<String\> | - | - | 需要减少出诊的日期 |
| | summary | String | - | - | 节假日汇总信息 |

### 代码

```javascript
//only dayjs and lodash are allowed
import dayjs from 'dayjs';
import _ from 'lodash';

async function main({ params }: Args): Promise<Output> {
  const month = params.month || "2026-07";
  const holidays = params.holidays || [];

  const parts = month.split('-');
  const year = parseInt(parts[0], 10);
  const monthNum = parseInt(parts[1], 10);
  const daysInMonth = new Date(year, monthNum, 0).getDate();

  const weekends = [];
  const holidaysSet = {};
  const reducedDays = [];

  for (let i = 0; i < holidays.length; i++) {
    const h = holidays[i];
    if (h && typeof h === 'string') {
      holidaysSet[h.slice(0, 10)] = true;
    }
  }

  for (let day = 1; day <= daysInMonth; day++) {
    const dayStr = day < 10 ? '0' + day : String(day);
    const monthStr = monthNum < 10 ? '0' + monthNum : String(monthNum);
    const dateStr = year + '-' + monthStr + '-' + dayStr;
    const dayOfWeek = new Date(dateStr + 'T00:00:00').getDay();

    if (dayOfWeek === 6) {
      weekends.push(dateStr);
    }

    if (holidaysSet[dateStr]) {
      reducedDays.push(dateStr);
    }
  }

  return {
    weekends: weekends,
    holidays: Object.keys(holidaysSet),
    reducedDays: reducedDays,
    summary: '周末' + weekends.length + '天，节假日' + Object.keys(holidaysSet).length + '天'
  };
}
```

### 输出示例

```json
{
  "weekends": ["2026-07-11", "2026-07-18", "2026-07-25"],
  "holidays": ["2026-07-04"],
  "reducedDays": ["2026-07-04"],
  "summary": "周末3天，节假日1天"
}
```

---

## 节点3：LLM生成排班

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | department_name | String | 是 | 节点1 | 科室名称 |
| | month | String | 是 | 节点1 | 月份 |
| | physicians | Array\<Physician\> | 是 | 节点1 | 医生列表 |
| | weekday_patterns | Object | 否 | 节点1 | 历史规律 |
| | weekends | Array\<String\> | 是 | 节点2 | 周六列表 |
| | reducedDays | Array\<String\> | 是 | 节点2 | 法定节假日 |
| **输出** | schedules | Array\<Schedule\> | - | - | 排班明细列表 |
| | statistics | Object | - | - | 统计信息 |
| | warnings | Array\<String\> | - | - | 警告信息列表 |

### 系统提示词

```
# 角色定义
你是一个专业的医院排班专家。你的职责是为指定科室生成月度排班方案。

## 排班规则（必须遵守）
1. 每位医生每周出诊不超过4次
2. 每天上午/下午至少保证1名医生出诊
3. 同一医生同一时段不能重复排班
4. 专家号占比约33%

## 号源配置
- 主任医师号：30个/半天
- 专家号：25个/半天
- 普通号：50个/半天

## 时段
- 上午：8:00-12:00
- 下午：14:00-17:30

## 节假日规则
- 周末（周六）：只安排半天出诊（上午），每个时段安排1人
- 周日：**正常安排出诊**，每个时段至少1人（普通工作日规则）
- 法定节假日：减少出诊人数，每个时段安排1人，不能为0
- 普通工作日：正常安排，每个时段至少2人

## 必须为每个月所有日期生成排班
- 2026年7月有31天：1-31日
- 7月4日是**法定节假日**，也是**周六**，只安排上午1人
- 其他周六（7月11日、18日、25日）：只安排上午1人
- 周日（7月5日、12日、19日、26日）：**正常安排出诊**，每个时段至少1人
- 其他日期（周一至周五）：每天安排上午和下午，每时段至少2人

## 历史规律（如果有）
如果有历史数据，按照历史规律分配号源：
- 周一、周二：号源需求高，适当增加
- 周三、周四：号源需求较低
- 周五：号源需求中等

## 输出要求
必须严格按照以下JSON格式输出，**不要输出任何思考过程、解释说明或推理内容**，只输出JSON：
{
  "schedules": [
    {
      "physician_id": 整数,
      "physician_name": "字符串",
      "work_date": "YYYY-MM-DD",
      "time_slot": "上午"或"下午",
      "total_quota": 整数,
      "ai_suggestion": "20字内的AI理由"
    }
  ],
  "statistics": {
    "total_schedules": 整数,
    "total_quota": 整数,
    "expert_ratio": 小数,
    "avg_weekly_workload": 小数
  },
  "warnings": ["警告信息"]
}
```

### 用户提示词

```
请为{{department_name}}生成{{month}}月的排班方案。

医生列表：
{{physicians}}

历史规律：
{{weekday_patterns}}

周末（周六，只能安排上午）：
{{weekends}}

需要减少出诊的日期（法定节假日，每个时段安排1人）：
{{reducedDays}}

重要提醒：
- 必须为{{month}}月的所有日期（1日到31日）生成排班
- 每天都要有排班记录，不能遗漏任何一天
- 周六只安排上午，周日正常安排出诊（每个时段至少1人）
- 日期类型：周六→半天；周日→普通工作日；法定节假日→减半

请生成完整的排班方案。

重要：请直接输出JSON结果，不要输出任何思考过程或解释。只输出如下格式的JSON：
{"schedules":[...],"statistics":{...},"warnings":[...]}
```

### 输出示例

```json
{
  "schedules": [
    {"physician_id": 5, "physician_name": "张医生", "work_date": "2026-07-01", "time_slot": "上午", "total_quota": 30, "ai_suggestion": "周一上午需求高"},
    {"physician_id": 6, "physician_name": "李医生", "work_date": "2026-07-01", "time_slot": "下午", "total_quota": 25, "ai_suggestion": "副主任负责下午"}
  ],
  "statistics": {
    "total_schedules": 120,
    "total_quota": 3600,
    "expert_ratio": 0.33,
    "avg_weekly_workload": 3.5
  },
  "warnings": []
}
```

---

## 节点4：冲突检测

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | schedules | Array\<Schedule\> | 是 | 节点3 | AI生成的排班列表 |
| | month | String | 是 | 节点1 | 月份（用于检测缺失日期） |
| | weekends | Array\<String\> | 是 | 节点2 | 周六列表 |
| | reducedDays | Array\<String\> | 是 | 节点2 | 法定节假日 |
| **输出** | errors | Array\<String\> | - | - | 冲突错误列表（格式：DUPLICATE:医生ID:日期:时段:次数） |
| | missingDays | Array\<String\> | - | - | 缺失出诊的日期时段（格式：YYYY-MM-DD:上午/下午） |
| | warnings | Array\<String\> | - | - | 警告信息列表 |
| | hasConflict | Boolean | - | - | 是否有冲突 |
| | scheduleCount | Integer | - | - | 排班总数 |

### 分支输出

| hasConflict | 下一节点 |
|-------------|---------|
| true | 节点3'（LLM修正排班） |
| false | 节点5（号源补充） |

### 代码

```javascript
//only dayjs and lodash are allowed
import dayjs from 'dayjs';
import _ from 'lodash';

async function main({ params }: Args): Promise<Output> {
  const schedules = params.schedules || [];
  const month = params.month || "2026-07";
  const weekends = params.weekends || [];
  const reducedDays = params.reducedDays || [];

  const errors = [];
  const warnings = [];
  const missingDays = [];

  const weeklyCount = {};
  const dailyCount = {};
  const daySlotMap = {};  // 记录每天每个时段有哪些医生

  function getWeekNumber(dateStr) {
    const date = new Date(dateStr + 'T00:00:00');
    const firstDayOfYear = new Date(date.getFullYear(), 0, 1);
    const pastDaysOfYear = (date - firstDayOfYear) / 86400000;
    return Math.ceil((pastDaysOfYear + firstDayOfYear.getDay() + 1) / 7);
  }

  // 遍历所有排班记录，统计
  for (let i = 0; i < schedules.length; i++) {
    const s = schedules[i];
    const physicianId = s.physician_id;
    const workDate = s.work_date;
    const weekNum = getWeekNumber(workDate);

    // 每周出诊次数
    const weeklyKey = physicianId + '_' + weekNum;
    weeklyCount[weeklyKey] = (weeklyCount[weeklyKey] || 0) + 1;

    // 每天每时段出诊次数（用于检测重复）
    const dailyKey = physicianId + '_' + workDate + '_' + s.time_slot;
    dailyCount[dailyKey] = (dailyCount[dailyKey] || 0) + 1;

    // 记录每天每个时段有哪些医生
    const slotKey = workDate + '_' + s.time_slot;
    if (!daySlotMap[slotKey]) {
      daySlotMap[slotKey] = [];
    }
    daySlotMap[slotKey].push(physicianId);
  }

  // 检测每周超过4次
  for (const key in weeklyCount) {
    if (weeklyCount[key] > 4) {
      const parts = key.split('_');
      warnings.push('医生' + parts[0] + '在第' + parts[1] + '周被安排' + weeklyCount[key] + '次，超过4次限制');
    }
  }

  // 检测同一医生同一时段重复排班
  for (const key in dailyCount) {
    if (dailyCount[key] > 1) {
      const parts = key.split('_');
      const physicianId = parts[0];
      const date = parts[1];
      const timeSlot = parts[2];
      errors.push('DUPLICATE:' + physicianId + ':' + date + ':' + timeSlot + ':' + dailyCount[key]);
    }
  }

  // 计算需要检测的日期时段
  const parts = month.split('-');
  const year = parseInt(parts[0], 10);
  const monthNum = parseInt(parts[1], 10);
  const daysInMonth = new Date(year, monthNum, 0).getDate();

  const weekendsSet = {};
  for (let i = 0; i < weekends.length; i++) {
    weekendsSet[weekends[i]] = true;
  }

  const reducedDaysSet = {};
  for (let i = 0; i < reducedDays.length; i++) {
    reducedDaysSet[reducedDays[i]] = true;
  }

  // 遍历一个月的所有日期，检测缺失
  for (let day = 1; day <= daysInMonth; day++) {
    const dayStr = day < 10 ? '0' + day : String(day);
    const monthStr = monthNum < 10 ? '0' + monthNum : String(monthNum);
    const dateStr = year + '-' + monthStr + '-' + dayStr;
    const dayOfWeek = new Date(dateStr + 'T00:00:00').getDay();

    const isWeekend = dayOfWeek === 6;  // 周六
    const isHoliday = reducedDaysSet[dateStr];  // 法定节假日

    // 判断该天需要哪些时段
    let needMorning = false;
    let needAfternoon = false;

    if (isHoliday) {
      // 法定节假日：只有上午，1人
      needMorning = true;
      needAfternoon = false;
    } else if (isWeekend) {
      // 周六：只有上午，1人
      needMorning = true;
      needAfternoon = false;
    } else {
      // 普通日期：上午+下午，至少1人
      needMorning = true;
      needAfternoon = true;
    }

    // 检测上午
    if (needMorning) {
      const morningKey = dateStr + '_上午';
      if (!daySlotMap[morningKey] || daySlotMap[morningKey].length === 0) {
        missingDays.push(dateStr + ':上午');
      }
    }

    // 检测下午
    if (needAfternoon) {
      const afternoonKey = dateStr + '_下午';
      if (!daySlotMap[afternoonKey] || daySlotMap[afternoonKey].length === 0) {
        missingDays.push(dateStr + ':下午');
      }
    }
  }

  return {
    errors: errors,
    missingDays: missingDays,
    warnings: warnings,
    hasConflict: errors.length > 0 || missingDays.length > 0,
    scheduleCount: schedules.length
  };
}
```

### 输出示例

```json
{
  "errors": ["DUPLICATE:5:2026-07-01:上午:2"],
  "missingDays": ["2026-07-05:上午", "2026-07-05:下午"],
  "warnings": ["医生5在第2周被安排5次，超过4次限制"],
  "hasConflict": true,
  "scheduleCount": 120
}
```

---

## 节点3'：LLM修正排班

### 触发条件

当节点4输出 `hasConflict = true` 时执行此节点。

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | department_name | String | 是 | 节点1 | 科室名称 |
| | month | String | 是 | 节点1 | 月份 |
| | physicians | Array\<Physician\> | 是 | 节点1 | 医生列表 |
| | weekday_patterns | Object | 否 | 节点1 | 历史规律 |
| | weekends | Array\<String\> | 是 | 节点2 | 周六列表 |
| | reducedDays | Array\<String\> | 是 | 节点2 | 法定节假日 |
| | original_schedules | Array\<Schedule\> | 是 | 节点3 | 上轮生成的排班 |
| | errors | Array\<String\> | 是 | 节点4 | 上轮冲突错误（DUPLICATE:医生ID:日期:时段:次数） |
| | missingDays | Array\<String\> | 是 | 节点4 | 缺失出诊的日期时段（YYYY-MM-DD:上午/下午） |
| **输出** | schedules | Array\<Schedule\> | - | - | 修正后的排班明细列表 |
| | statistics | Object | - | - | 统计信息 |
| | warnings | Array\<String\> | - | - | 警告信息列表 |

### 系统提示词

```
# 角色定义
你是一个专业的医院排班修正专家。你的职责是根据上轮错误信息修正排班方案。

## 上轮冲突错误
{{errors}}
格式说明：DUPLICATE:医生ID:日期:时段:次数，表示同一医生同一时段被安排了多次

## 上轮缺失出诊的日期时段
{{missingDays}}
格式说明：YYYY-MM-DD:上午/下午，表示该天该时段没有医生出诊，必须补充

## 上轮生成的排班（需要修正的部分）
{{original_schedules}}

## 修正要求
1. 只修正有错误的排班记录，保留正确的部分
2. 针对 DUPLICATE 错误：删除重复的排班记录
3. 针对 missingDays：为缺失的日期时段补充医生排班
4. 确保修正后的排班符合所有规则：
   - 每位医生每周出诊不超过4次
   - 每天上午/下午至少保证1名医生出诊
   - 同一医生同一时段不能重复排班
   - 专家号占比约33%
5. 不要完全重新生成，只在原有基础上修改错误的部分
6. 输出完整的排班列表（包含修正的和正确的）

## 排班规则
- 周六：只安排上午，每个时段1人
- 周日：正常安排，每个时段至少1人
- 法定节假日：每个时段1人
- 普通工作日：每个时段至少2人

## 号源配置
- 主任医师号：30个/半天
- 专家号：25个/半天
- 普通号：50个/半天

## 输出要求
必须严格按照以下JSON格式输出，**不要输出任何思考过程、解释说明或推理内容**，只输出JSON：
{
  "schedules": [
    {
      "physician_id": 整数,
      "physician_name": "字符串",
      "work_date": "YYYY-MM-DD",
      "time_slot": "上午"或"下午",
      "total_quota": 整数,
      "ai_suggestion": "20字内的AI理由"
    }
  ],
  "statistics": {
    "total_schedules": 整数,
    "total_quota": 整数,
    "expert_ratio": 小数,
    "avg_weekly_workload": 小数
  },
  "warnings": ["警告信息"]
}
```

### 用户提示词

```
请根据上轮错误信息修正排班方案。

科室：{{department_name}}
月份：{{month}}

医生列表：
{{physicians}}

上轮冲突错误（DUPLICATE格式，需删除重复）：
{{errors}}

上轮缺失出诊的日期时段（需补充医生）：
{{missingDays}}

上轮生成的排班：
{{original_schedules}}

重要：
- 针对 DUPLICATE 错误：删除同一医生同一时段的重复记录
- 针对 missingDays：为缺失的日期时段补充安排医生
- 只修正错误的部分，保留正确的排班记录
- 输出完整的排班列表

重要：请直接输出JSON结果，不要输出任何思考过程或解释。只输出如下格式的JSON：
{"schedules":[...],"statistics":{...},"warnings":[...]}
```

### 输出示例

```json
{
  "schedules": [
    {"physician_id": 5, "physician_name": "内科刘主任", "work_date": "2026-07-01", "time_slot": "上午", "total_quota": 30, "ai_suggestion": "修正后的出诊安排"}
  ],
  "statistics": {
    "total_schedules": 120,
    "total_quota": 3600,
    "expert_ratio": 0.33,
    "avg_weekly_workload": 3.5
  },
  "warnings": []
}
```

---

## 节点5：号源补充

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | schedules | Array\<Schedule\> | 是 | 节点3 或 节点3' | AI生成的排班列表 |
| | physicians | Array\<Physician\> | 是 | 节点1 | 医生列表 |
| **输出** | valid | Boolean | - | - | 校验是否通过 |
| | errors | Array\<String\> | - | - | 错误列表 |
| | validated_schedules | Array\<Schedule\> | - | - | 校验通过的排班列表 |
| | totalCount | Integer | - | - | 排班总数 |

### 代码

```javascript
//only dayjs and lodash are allowed
import dayjs from 'dayjs';
import _ from 'lodash';

async function main({ params }: Args): Promise<Output> {
  const schedules = params.schedules || [];
  const physicians = params.physicians || [];

  const quotaMap = {
    "主任医师号": 30,
    "专家号": 25,
    "普通号": 50
  };

  const priceMap = {
    "主任医师号": 30.00,
    "专家号": 20.00,
    "普通号": 15.00
  };

  const levelIdMap = {
    "主任医师号": 3,
    "专家号": 2,
    "普通号": 1
  };

  const physicianMap = {};
  for (let i = 0; i < physicians.length; i++) {
    const p = physicians[i];
    physicianMap[p.id] = p;
  }

  const errors = [];
  const validated = [];
  const seen = {};

  for (let i = 0; i < schedules.length; i++) {
    const s = schedules[i];
    const errs = [];

    if (!/^\d{4}-\d{2}-\d{2}$/.test(s.work_date)) {
      errs.push('第' + (i + 1) + '条：日期格式错误');
    }
    if (!['上午', '下午'].includes(s.time_slot)) {
      errs.push('第' + (i + 1) + '条：时段错误');
    }
    const key = s.work_date + '_' + s.physician_id + '_' + s.time_slot;
    if (seen[key]) {
      errs.push('第' + (i + 1) + '条：重复排班');
    }
    seen[key] = true;
    if (!physicianMap[s.physician_id]) {
      errs.push('第' + (i + 1) + '条：医生ID ' + s.physician_id + ' 不存在');
    }

    if (errs.length > 0) {
      for (let j = 0; j < errs.length; j++) {
        errors.push(errs[j]);
      }
    } else {
      const physician = physicianMap[s.physician_id];
      const position = physician ? physician.position : '普通号';
      validated.push({
        physician_id: s.physician_id,
        physician_name: s.physician_name,
        work_date: s.work_date,
        time_slot: s.time_slot,
        total_quota: s.total_quota || quotaMap[position] || 30,
        available_quota: s.total_quota || quotaMap[position] || 30,
        used_quota: 0,
        price: priceMap[position] || 15.00,
        regist_level_id: levelIdMap[position] || 1,
        ai_suggestion: s.ai_suggestion || '',
        status: '草稿'
      });
    }
  }

  return {
    valid: errors.length === 0,
    errors: errors,
    validated_schedules: validated,
    totalCount: validated.length
  };
}
```

### 输出示例

```json
{
  "valid": true,
  "errors": [],
  "validated_schedules": [
    {
      "physician_id": 5,
      "physician_name": "张医生",
      "work_date": "2026-07-01",
      "time_slot": "上午",
      "total_quota": 30,
      "available_quota": 30,
      "used_quota": 0,
      "price": 30.00,
      "regist_level_id": 3,
      "ai_suggestion": "周一上午需求高",
      "status": "草稿"
    }
  ],
  "totalCount": 120
}
```

---

## 节点6：结束节点

### 类型设计

| 输出变量名 | 变量类型 | 来源 | 说明 |
|-----------|---------|------|------|
| validated_schedules | Array\<Schedule\> | 节点5 | 校验通过的排班列表 |
| statistics | Object | 节点3 | 统计信息 |
| errors | Array\<String\> | 节点4 | 冲突错误 |
| warnings | Array\<String\> | 节点4 | 警告信息 |
| message | String | 固定值 | 提示信息（**不能为空**，填写：排班方案已生成） |

### message 固定值

```
排班方案已生成
```

### 输出示例

```json
{
  "validated_schedules": [...],
  "statistics": {
    "total_schedules": 120,
    "total_quota": 3600,
    "expert_ratio": 0.33,
    "avg_weekly_workload": 3.5
  },
  "errors": [],
  "warnings": [],
  "message": "排班方案已生成，共120条"
}
```

---

## 变量类型汇总表

|节点 | 变量名 | 类型 |
|------|--------|------|
| **节点1-开始** | department_id | Integer |
| | department_name | String |
| | month | String |
| | physicians | Array\<Physician\> |
| | weekday_patterns | Object |
| | holidays | Array\<String\> |
| **节点2-节假日处理** | month（输入） | String |
| | holidays（输入） | Array\<String\> |
| | weekends（输出） | Array\<String\> |
| | holidays（输出） | Array\<String\> |
| | reducedDays（输出） | Array\<String\> |
| | summary（输出） | String |
| **节点3-LLM生成** | department_name（输入） | String |
| | month（输入） | String |
| | physicians（输入） | Array\<Physician\> |
| | weekday_patterns（输入） | Object |
| | weekends（输入） | Array\<String\> |
| | reducedDays（输入） | Array\<String\> |
| | schedules（输出） | Array\<Schedule\> |
| | statistics（输出） | Object |
| | warnings（输出） | Array\<String\> |
| **节点4-冲突检测** | schedules（输入） | Array\<Schedule\> |
| | errors（输出） | Array\<String\> |
| | warnings（输出） | Array\<String\> |
| | hasConflict（输出） | Boolean |
| | scheduleCount（输出） | Integer |
| **节点5-号源补充** | schedules（输入） | Array\<Schedule\> |
| | physicians（输入） | Array\<Physician\> |
| | valid（输出） | Boolean |
| | errors（输出） | Array\<String\> |
| | validated_schedules（输出） | Array\<Schedule\> |
| | totalCount（输出） | Integer |
| **节点6-结束** | validated_schedules（输出） | Array\<Schedule\> |
| | statistics（输出） | Object |
| | errors（输出） | Array\<String\> |
| | warnings（输出） | Array\<String\> |
| | message（输出） | String |

---

## Coze 搭建步骤

```
1. 创建工作流：wf_schedule_generate

2. 节点1（开始节点）配置：
   - department_id (Integer)
   - department_name (String)
   - month (String)
   - physicians (Array)
   - weekday_patterns (Object) ← 可选
   - holidays (Array) ← 可选

3. 节点2（代码节点）：节假日处理
   输入：month, holidays
   代码：复制上面的代码
   输出：weekends (Array), holidays (Array), reducedDays (Array), summary (String)

4. 节点3（LLM节点）：生成排班
   模型：通义千问2.5，Temperature：0.3
   输入：department_name, month, physicians, weekday_patterns, weekends, reducedDays
   输出：schedules (Array), statistics (Object), warnings (Array)

5. 节点4（代码节点）：冲突检测
   输入：schedules, month, weekends, reducedDays
   代码：复制上面的代码
   输出：errors (Array), missingDays (Array), warnings (Array), hasConflict (Boolean), scheduleCount (Integer)

6. 节点3'（LLM节点）：修正排班
   模型：通义千问2.5，Temperature：0.3
   输入：department_name, month, physicians, weekday_patterns, weekends, reducedDays,
         original_schedules, errors, missingDays
   输出：schedules (Array), statistics (Object), warnings (Array)

7. 节点5（代码节点）：号源补充
   输入：schedules, physicians
   代码：复制上面的代码
   输出：valid (Boolean), errors (Array), validated_schedules (Array), totalCount (Integer)

8. 节点6（结束节点）：
   输出：validated_schedules (Array), statistics (Object), errors (Array), warnings (Array), message (String)

9. 配置条件分支：
   - 在节点4后添加条件判断
   - 条件：hasConflict == true
   - true 分支 → 节点3'
   - false 分支 → 节点5

10. 连接节点：
   开始 → 节点2 → 节点3 → 节点4
                                    │
                          hasConflict?
                             ↓     ↓
                        true      false
                             ↓     ↓
                        节点3'     ↓
                             ↓     ↓
                            都继续    ↓
                             ↓     ↓
                           节点5 → 结束
```

---

## Coze 条件分支配置详细说明

### 节点连接顺序

```
开始节点 → 节点1 → 节点2 → 节点3 → 节点4
                                         │
                               hasConflict?
                                  ↓     ↓
                             true      false
                                  ↓     ↓
                            节点3'      ↓
                                  ↓     ↓
                                 都继续
                                  ↓     ↓
                                节点5 → 结束节点
```

### 条件分支配置

| 配置项 | 值 |
|--------|-----|
| 条件变量 | hasConflict (Boolean) |
| true 分支 | 节点3'（LLM修正排班） |
| false 分支 | 节点5（号源补充） |

### 节点3（LLM初始生成）输入输出

| 类型 | 参数名 | 来源 | 说明 |
|------|--------|------|------|
| **输入** | department_name | 节点1 | 科室名称 |
| | month | 节点1 | 月份 |
| | physicians | 节点1 | 医生列表 |
| | weekday_patterns | 节点1 | 历史规律（可选） |
| | weekends | 节点2 | 周六列表 |
| | reducedDays | 节点2 | 法定节假日 |
| **输出** | schedules | - | 排班列表 |
| | statistics | - | 统计信息 |
| | warnings | - | 警告列表 |

### 节点4（冲突检测）输入输出

| 类型 | 参数名 | 来源 | 说明 |
|------|--------|------|------|
| **输入** | schedules | 节点3 | 排班列表 |
| | month | 节点1 | 月份（用于检测缺失日期） |
| | weekends | 节点2 | 周六列表 |
| | reducedDays | 节点2 | 法定节假日 |
| **输出** | errors | - | 冲突错误列表（DUPLICATE:医生ID:日期:时段:次数） |
| | missingDays | - | 缺失出诊的日期时段（YYYY-MM-DD:上午/下午） |
| | warnings | - | 警告列表 |
| | hasConflict | - | 是否有冲突 |
| | scheduleCount | - | 排班总数 |

### 节点3'（LLM修正排班）输入输出

| 类型 | 参数名 | 来源 | 说明 |
|------|--------|------|------|
| **输入** | department_name | 节点1 | 科室名称 |
| | month | 节点1 | 月份 |
| | physicians | 节点1 | 医生列表 |
| | weekday_patterns | 节点1 | 历史规律（可选） |
| | weekends | 节点2 | 周六列表 |
| | reducedDays | 节点2 | 法定节假日 |
| | original_schedules | 节点3 | 上轮生成的排班 |
| | errors | 节点4 | 上轮冲突错误 |
| | missingDays | 节点4 | 缺失出诊的日期时段 |
| **输出** | schedules | - | 修正后的排班列表 |
| | statistics | - | 统计信息 |
| | warnings | - | 警告列表 |

### 节点5（号源补充）输入输出

| 类型 | 参数名 | 来源 | 说明 |
|------|--------|------|------|
| **输入** | schedules | 节点3 或 节点3' | 排班列表 |
| | physicians | 节点1 | 医生列表 |
| **输出** | valid | - | 校验是否通过 |
| | errors | - | 错误列表 |
| | validated_schedules | - | 校验通过的排班列表 |
| | totalCount | - | 排班总数 |

---

## 后端需要新增的代码

```java
@PostMapping("/coze/generate")
public Map<String, Object> generateScheduleByCoze(@RequestBody Map<String, Object> request) {
    Long departmentId = ((Number) request.get("departmentId")).longValue();
    String month = (String) request.get("month");

    // 1. 查询科室下的医生
    List<Employee> physicians = employeeFeignClient.getByDepartment(departmentId);

    // 2. 查询历史排班，计算周规律（如果有）
    Map<String, Object> weekdayPatterns = null;
    List<DoctorSchedule> history = doctorScheduleService.getByMonth(departmentId, getLastMonth(month));
    if (history != null && !history.isEmpty()) {
        weekdayPatterns = calculateWeekdayPatterns(history);
    }

    // 3. 计算法定节假日
    List<String> holidays = calculateHolidays(month);

    // 4. 调用 Coze 工作流 API
    Map<String, Object> cozeRequest = new HashMap<>();
    cozeRequest.put("department_id", departmentId);
    cozeRequest.put("department_name", departmentService.getNameById(departmentId));
    cozeRequest.put("month", month);
    cozeRequest.put("physicians", physicians);
    cozeRequest.put("weekday_patterns", weekdayPatterns);
    cozeRequest.put("holidays", holidays);

    Map<String, Object> cozeResult = cozeService.runWorkflow(cozeRequest);

    // 5. 保存到数据库
    List<Map<String, Object>> schedules = (List<Map<String, Object>>) cozeResult.get("validated_schedules");
    SchedulePlan plan = schedulePlanService.createPlan(departmentId, month);
    schedulePlanService.saveSchedules(plan.getId(), schedules);

    return success(Map.of(
        "plan_id", plan.getId(),
        "schedule_count", schedules.size(),
        "status", "草稿"
    ));
}
```

---

> 文档版本：v3.1
> 最后更新：2026-06-07
> 维护人：Claude
> 变更说明：v3.2 节点4新增missingDays检测（某天没医生的日期时段）；节点3'新增missingDays输入用于修正