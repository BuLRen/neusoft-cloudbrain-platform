# WF-01: AI生成月度排班 工作流设计文档

> 版本：v4.5（修正节点3 提示词硬编码日期 + 节点3' 变量名/输出要求/对账强化 + statistics 可信度标注 + parse_ok 可读性优化）
> 日期：2026-07-02
> 状态：双LLM节点+条件分支（初始生成+冲突修正+缺失检测），适配 Dify 工作流
> 变更说明（相对 v3.2）：从 Coze 迁移到 Dify，开始节点复合类型改用 String(JSON) 传递，所有代码节点由 JavaScript 改为 Python
> 变更说明（相对 v4.0）：将节点2（节假日处理）拆分为两个节点 —— 2a 统一解析 JSON 字符串、2b 专注日期计算；同步消除 physicians_format 与节点5 中的重复解析逻辑；将原本只作为代码片段出现的 physicians_format 独立成"节点2c"，补充完整的类型设计/代码/输入输出示例
> 变更说明（相对 v4.1）：新增节点2d（LLM输入聚合），把喂给 LLM 的 6 个上下文变量聚合成单个 String 变量 `llm_context`，节点3/3' 用户提示词从"挨个插 6 处 `{{#...#}}`"简化为"只引用一处"；LLM 结构化输出由 yaml 改为标准 JSON Schema 源码模式，明确禁止 `additionalProperties: false`
> 变更说明（相对 v4.2）：**节点5 修复 + 输出字段改造**——① 修复排班数据丢失（Dify 把上游 LLM 输出的 `{"schedules": [...]}` 整体作为 dict 传入，原代码按"直接 list"假设遍历 dict 拿到 key 字符串全部 continue，新增 `_extract_schedules_list()` 识别三种形态统一抽出 list）；② 绕开 Dify Code 节点 Array 输出 30 元素硬限制，把 `validated_schedules` 改为 `validated_schedules_json`（String），后端 `JSON.parse` 还原；新增 `debug_log` 输出字段替代 `print()`
> 变更说明（相对 v4.3）：**节点4（冲突检测）全面 String 化**——把 `missingDays` / `errors` / `warnings` 三个 Array 输出全部改成 `_json` 后缀的 String，绕开 Dify Code 节点 30 元素硬限制。原因：实际运行中 LLM 漏排大量日期时 `missingDays` 轻松超过 30 条（一个月最多 50~62 条），触发 `The length of output variable 'missingDays' must be less than 30 elements` 导致整个工作流失败。节点3' 提示词里的 `{{#冲突检测.missingDays#}}` 同步改为 `{{#冲突检测.missingDays_json#}}`（LLM 读 JSON 字符串更清晰）。同时新增 `missingDaysCount` / `errorsCount` 数值字段方便排障
> 变更说明（相对 v4.4）：**通用性 + LLM二 强化**——① **节点3 系统提示词去掉 2026-07 硬编码日期规则**（原"7月有31天/7月4日节假日/7月11日周六"等具体日期使工作流只能排 7 月，换月必错），改为引用 `llm_context` 的动态【周六列表】【法定节假日】清单；② **节点3' 用户提示词里 `{{#LLM生成排班.schedules#}}` 改为 `{{#LLM修正排班.original_schedules#}}`**（与类型设计表声明的入参名一致，原写法绕过了声明的入参，配置和提示词不一致）；③ 节点3' 用户提示词**强化输出完整性要求**（明确 missingDays 必须全补、给出预期条数公式、禁止编造医生ID、禁止引入新 DUPLICATE），缓解 LLM二 实测常见的"丢条/偷懒只补几个"问题；④ End 节点 `statistics` 字段标注"LLM 自报不可信"，后端代码注释强调以 `totalCount` 为准；⑤ 节点2a 的 `parse_ok` 判定改成显式函数（空输入→OK、非空解析后类型不符→异常），消除原三元 or 表达式的可读性陷阱

---

## Dify 输入类型约束（重要）

### Dify 开始节点仅支持以下类型

| 类型 | 说明 |
|------|------|
| String | 字符串 |
| Number | 数值（整数/小数通用，**Dify 无独立 Integer 类型**） |
| Boolean | 布尔值 |
| File | 单个文件 |
| Array[File] | 文件数组 |

**关键限制**：开始节点**不支持** Object、Array\<String\>、Array\<Object\>、Array\<Integer\> 等结构化复合类型。

### Dify 节点之间传递的类型规则（与 Coze 不同，需注意）

| 节点类型 | 输入支持复杂类型？ | 输出支持复杂类型？ | 说明 |
|---------|------------------|------------------|------|
| 开始节点（Start） | ❌ 仅 String/Number/Boolean/File | - | 入口必须用 String 承载 JSON |
| 代码节点（Code, Python） | ✅ 任意 Python 类型 | ✅ 任意 Python 类型 | 内部可用 dict/list |
| LLM 节点（LLM） | ✅（实质是文本拼接进提示词） | ✅ 结构化输出（JSON Schema，**不要勾 additionalProperties:false**） | 入参可以是复杂类型，但 LLM 看到的是其字符串化结果 |
| 条件分支节点（IF/ELSE） | ✅ | - | 用于实现 Coze 的条件分支 |
| 模板转换节点（Template） | ✅ | ✅ String | Jinja2 模板渲染 |
| 结束节点（End） | ✅ | - | 输出变量需声明类型 |

### 适配策略

```
开始节点（全 String/Number/Boolean）
    │   physicians_json / weekday_patterns_json / holidays_json 都是 String
    ▼
节点2a 代码节点（Python）— JSON解析
    │   统一 json.loads(...) 还原开始节点传入的所有 JSON 字符串
    │   输出真正的复合类型：physicians(Array<Object>) / weekday_patterns(Object) / holidays(Array<String>)
    ▼
节点2b 代码节点（Python）— 节假日计算
    │   输入 holidays（来自2a，已是 Array<String>）
    │   输出 weekends / reducedDays / summary
    ▼
节点3 LLM 节点
    │   输入直接使用 2a/2b 输出的 Array/Object，Dify 会自动字符串化拼入提示词
    │   输出结构化 schedules(Array<Object>) / statistics(Object) / warnings(Array<String>)
    ▼
节点4 代码节点（Python）
    │   接收真正的 list[dict]，输出 hasConflict(Boolean)
    ▼
IF/ELSE 条件分支节点
    │   判断 hasConflict == true
    ├── true → 节点3' LLM修正
    └── false → 节点5
    ▼
节点5 代码节点（Python）
    │   直接消费 2a 解析好的 physicians（无需再 json.loads）
    ▼
结束节点
```

**核心结论**：
- 只有"**开始节点 → 节点2a**"这一段需要用 String(JSON)
- 2a 之后所有节点之间都可以正常使用真正的 Array/Object
- 不需要在每个 LLM 节点都重新解析 JSON
- 拆分后 2a 集中负责反序列化，避免节点5 / 节点2c 重复写 `json.loads`

---

## 工作流架构

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     WF-01 AI生成月度排班（Dify 版）                              │
│                双LLM节点 + IF/ELSE条件分支（初始生成+冲突修正+缺失检测）          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────┐                                                               │
│  │ 1.开始节点  │ department_id(Number), department_name(String), month(String), │
│  │  (Start)    │ physicians_json(String/JSON),                                  │
│  │     ⏺      │ weekday_patterns_json(String/JSON, 可选),                       │
│  │             │ holidays_json(String/JSON, 可选)                               │
│  └──────┬──────┘                                                               │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────────────────┐                                                   │
│  │ 2a.JSON解析             │ Python代码节点                                    │
│  │  (Code-Python)          │ 统一 json.loads 解析 physicians/weekday/holidays  │
│  │                         │ 输出 physicians(Array<Object>),                   │
│  │                         │       weekday_patterns(Object),                   │
│  │                         │       holidays(Array<String>)                     │
│  └──────┬──────────────────┘                                                   │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────────────────┐                                                   │
│  │ 2b.节假日计算           │ Python代码节点                                    │
│  │  (Code-Python)          │ 输入 month + holidays（来自2a）                   │
│  │                         │ 输出 weekends, reducedDays, summary              │
│  └──────┬──────────────────┘                                                   │
│         │                  ┌─────────────────────────┐                          │
│         │                  │ 2c.医生列表格式化       │ Python代码节点（辅助）   │
│         │                  │ physicians_format        │ 输入 physicians（2a）    │
│         │                  │ (Code-Python)           │ 输出 physicians_display  │
│         │                  └─────────────┬───────────┘（可读文本，喂 LLM）       │
│         │                                │                                      │
│         └────────────────┬───────────────┘                                      │
│                          ▼                                                      │
│  ┌─────────────────────────┐                                                   │
│  │ 3.LLM生成排班           │ LLM节点（结构化输出）                              │
│  │  (LLM)                  │ 输入 physicians_display（来自2c）                 │
│  │                         │ 输出 schedules(Array), statistics(Object)         │
│  └──────┬──────────────────┘                                                   │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────────────────┐                                                   │
│  │ 4.冲突检测              │ Python代码节点                                    │
│  │  (Code-Python)          │ 输出 hasConflict(Boolean)                         │
│  └──────┬──────────────────┘                                                   │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │              IF/ELSE 条件分支节点                                        │   │
│  │                  条件：hasConflict == true                                │   │
│  │                      ┌─────┴─────┐                                       │   │
│  │                      ↓           ↓                                       │   │
│  │                   true        false                                      │   │
│  │                      ↓           ↓                                       │   │
│  │              ┌─────────────────────┐                                     │   │
│  │              │ 3'.LLM修正排班      │                                     │   │
│  │              │ (LLM)               │                                     │   │
│  │              └───────────┬─────────┘                                     │   │
│  └──────────────────────────┼─────────────────────────────────────────────┘   │
│                             │                                                     │
│                             ▼                                                     │
│  ┌─────────────────────────┐                                                   │
│  │ 5.号源补充              │ Python代码节点（直接消费 2a 的 physicians）       │
│  │  (Code-Python)          │ 输出 validated_schedules(Array)                  │
│  └──────┬──────────────────┘                                                   │
│         │                                                                     │
│         ▼                                                                     │
│  ┌─────────────┐                                                               │
│  │ 6.结束节点  │ 输出最终排班结果                                              │
│  │  (End)      │                                                               │
│  └─────────────┘                                                               │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Dify 代码节点格式说明

Dify 代码节点使用 **Python**，格式如下：

```python
def main(arg1: str, arg2: list, **kwargs) -> dict:
    """
    参数：直接作为函数入参（不需要 params.xxx）
    返回：return dict，key 即输出变量名
    可用第三方库：见 Dify 沙箱白名单（json/datetime/re 等）
    """
    import json
    # 处理逻辑
    return {
        "out_var": value
    }
```

### 关键区别（对比 Coze）

| 项目 | Coze | Dify |
|------|------|------|
| 语言 | JavaScript | Python |
| 参数获取 | `params.xxx` | 函数直接定义 `arg1: type` |
| 返回值 | `return { ... }` | `return { ... }`（dict） |
| 第三方库 | dayjs, lodash | json, datetime, re 等（沙箱白名单） |
| 输入复杂类型 | 取决于节点连线 | 节点间可任意 dict/list |

---

## 数据结构定义

### 医生对象 Physician

| 属性名 | 类型 | 说明 |
|--------|------|------|
| id | Number | 医生ID（Dify 无 Integer，用 Number 承载） |
| realname | String | 医生姓名 |
| position | String | 职称：主任医师号/专家号/普通号 |

### 排班对象 Schedule

| 属性名 | 类型 | 说明 |
|--------|------|------|
| physician_id | Number | 医生ID |
| physician_name | String | 医生姓名 |
| work_date | String | 出诊日期（格式：YYYY-MM-DD） |
| time_slot | String | 时段：上午/下午 |
| total_quota | Number | 总号源 |
| available_quota | Number | 剩余号源 |
| used_quota | Number | 已用号源 |
| price | Number | 挂号费 |
| regist_level_id | Number | 挂号级别ID |
| ai_suggestion | String | AI建议 |
| status | String | 状态 |

### 历史规律对象 WeekdayPattern

| 属性名 | 类型 | 说明 |
|--------|------|------|
| avg_quota | Number | 平均号源 |
| usage_rate | Number | 使用率 |

---

## 节点1：开始节点（Start）

### 类型设计

> ⚠️ **Dify 开始节点不支持 Array/Object 类型**，所以 physicians/weekday_patterns/holidays 全部以 String(JSON) 形式传入，后端在调用时需 `JSON.stringify()`。
>
> ⚠️ **所有 String 变量统一限制最大长度 2000 字符**。后端在 `JSON.stringify()` 后需校验字符串长度不超过 2000，否则 Dify 会拒绝请求或截断。详见下方"长度校验说明"。

| 输入变量名 | 变量类型 | 必填 | 最大长度 | 来源 | 说明 |
|-----------|---------|------|---------|------|------|
| department_id | Number | 是 | - | 后端 | 科室ID |
| department_name | String | 是 | 2000 | 后端 | 科室名称 |
| month | String | 是 | 2000 | 后端 | 月份（格式：YYYY-MM，实际只用 7 字符） |
| **physicians_json** | **String** | 是 | **2000** | 后端 | 医生列表的 JSON 字符串 |
| **weekday_patterns_json** | **String** | 否 | **2000** | 后端 | 历史规律的 JSON 字符串（有历史数据才传） |
| **holidays_json** | **String** | 否 | **2000** | 后端 | 法定节假日列表的 JSON 字符串 |

### 长度校验说明（重要）

Dify 开始节点的 String 变量支持配置 **最大长度（MaxLength）**。本工作流统一配置为 **2000 字符**，原因：

| 字段 | 实际典型长度 | 余量评估 |
|------|------------|---------|
| department_name | 2-10 字符（如"内科"） | 远小于 2000 |
| month | 7 字符（如"2026-07"） | 远小于 2000 |
| physicians_json | 5 个医生 ≈ 400 字符；20 个医生 ≈ 1600 字符 | 2000 可承载约 25 个医生 |
| weekday_patterns_json | 7 个工作日 × ~50 字符 ≈ 350 字符 | 远小于 2000 |
| holidays_json | 每个日期 12 字符，10 天 ≈ 150 字符 | 远小于 2000 |

**后端必须在 stringify 后做长度校验**：

```java
String physiciansJson = JSON.toJSONString(physicians);
if (physiciansJson.length() > 2000) {
    throw new BusinessException("医生列表过长（" + physiciansJson.length()
        + " > 2000），请减少单次排班的医生数量或精简字段");
}
```

### physicians_json 输入示例（注意是字符串）

后端传入的值（实际是 String 类型，内容是 JSON，长度约 400 字符，远低于 2000 限制）：

```text
"[{\"id\":5,\"realname\":\"内科刘主任\",\"position\":\"主任医师号\"},{\"id\":4,\"realname\":\"内科赵专家\",\"position\":\"专家号\"},{\"id\":1,\"realname\":\"内科张医生\",\"position\":\"普通号\"},{\"id\":2,\"realname\":\"内科李医生\",\"position\":\"普通号\"},{\"id\":3,\"realname\":\"内科王医生\",\"position\":\"普通号\"}]"
```

### 完整输入示例（开始节点的所有变量取值）

| 变量 | 类型 | 最大长度 | 值 |
|------|------|---------|----|
| department_id | Number | - | 1 |
| department_name | String | 2000 | "内科" |
| month | String | 2000 | "2026-07" |
| physicians_json | String | 2000 | `[{"id":5,...},{"id":4,...}]`（字符串化后） |
| weekday_patterns_json | String | 2000 | `{"周一":{"avg_quota":100,"usage_rate":0.85},"周二":{...}}`（字符串化后） |
| holidays_json | String | 2000 | `["2026-07-04"]`（字符串化后） |

---

## 节点2a：JSON解析（Code-Python）

### 作用

- 集中负责把开始节点传入的所有 `*_json` 字符串反序列化为真正的 Python 复合类型
- 后续所有节点（2b、3、3'、5 等）都直接消费本节点的输出，**不再重复 `json.loads`**
- 解析失败时返回安全的空值，避免影响下游节点

### 为什么单独抽出一个解析节点？

拆分前，`json.loads` 逻辑分散在三个地方：原节点2（解析 holidays_json）、节点5（解析 physicians_json）、physicians_format（解析 physicians_json）。每处都要写一遍 `try/except + isinstance` 容错，重复且容易遗漏边界。拆出 2a 后：

- 解析容错逻辑只写一遍
- 复合类型转换在 2a → 2b 这一处完成，从此节点之间全部走真正的 Array/Object
- 调试时可在 Dify 单独运行 2a，立即看到反序列化结果

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | physicians_json | String | 是 | 节点1 | 医生列表 JSON 字符串 |
| | weekday_patterns_json | String | 否 | 节点1 | 历史规律 JSON 字符串 |
| | holidays_json | String | 否 | 节点1 | 法定节假日 JSON 字符串 |
| **输出** | physicians | Array&lt;Object&gt; | - | - | 医生列表（解析失败为空数组） |
| | weekday_patterns | Object | - | - | 历史规律（解析失败为空对象） |
| | holidays | Array&lt;String&gt; | - | - | 法定节假日列表（解析失败为空数组） |
| | parse_ok | Boolean | - | - | 三项是否全部解析成功（用于排障） |

### 代码

```python
import json


def _safe_load_list(s):
    """把 JSON 字符串解析为 list，失败返回空数组。"""
    if not s:
        return []
    try:
        parsed = json.loads(s)
        if isinstance(parsed, list):
            return parsed
    except (json.JSONDecodeError, TypeError):
        pass
    return []


def _safe_load_obj(s):
    """把 JSON 字符串解析为 dict，失败返回空对象。"""
    if not s:
        return {}
    try:
        parsed = json.loads(s)
        if isinstance(parsed, dict):
            return parsed
    except (json.JSONDecodeError, TypeError):
        pass
    return {}


def main(physicians_json: str, weekday_patterns_json: str = "", holidays_json: str = "") -> dict:
    # physicians：list[dict]
    physicians_raw = _safe_load_list(physicians_json)
    physicians = [p for p in physicians_raw if isinstance(p, dict)]

    # weekday_patterns：dict
    weekday_patterns = _safe_load_obj(weekday_patterns_json)

    # holidays：list[str]，并规范化为 YYYY-MM-DD 前10位
    holidays_raw = _safe_load_list(holidays_json)
    holidays = []
    for h in holidays_raw:
        if h:
            holidays.append(str(h)[:10])

    # 解析健康度（三项全部非异常才算 ok）
    # 判定规则：空字符串视为 OK（可选字段未传）；非空字符串解析后类型不符才算异常
    def _list_ok(raw_str, raw_list, ok_list):
        if not raw_str:
            return True  # 空输入合法
        return len(raw_list) == len(ok_list)  # 解析前后元素数应一致

    def _obj_ok(raw_str, parsed):
        if not raw_str:
            return True  # 空输入合法
        return isinstance(parsed, dict)  # 必须解析成 dict（含 {}）

    physicians_ok = _list_ok(physicians_json, physicians_raw, physicians)
    weekday_ok = _obj_ok(weekday_patterns_json, weekday_patterns)
    holidays_ok = _list_ok(holidays_json, holidays_raw, holidays)
    parse_ok = physicians_ok and weekday_ok and holidays_ok

    return {
        "physicians": physicians,
        "weekday_patterns": weekday_patterns,
        "holidays": holidays,
        "parse_ok": parse_ok,
    }
```

### 输出示例

```json
{
  "physicians": [
    {"id": 5, "realname": "内科刘主任", "position": "主任医师号"},
    {"id": 4, "realname": "内科赵专家", "position": "专家号"}
  ],
  "weekday_patterns": {"周一": {"avg_quota": 100, "usage_rate": 0.85}},
  "holidays": ["2026-07-04"],
  "parse_ok": true
}
```

> 注：从本节点起，后续所有节点之间传递的都是真正的 Array/Object，**不需要再 `json.loads`**。

---

## 节点2b：节假日计算（Code-Python）

### 作用

- 输入是 2a 已经解析好的 `holidays`（真正的 Array&lt;String&gt;），**不再处理字符串**
- 只负责"日期计算"：根据 `month` 枚举该月每一天，区分周六 / 法定节假日 / 普通日
- 输出 `weekends`、`reducedDays`、`summary`，供 LLM 节点作为约束条件

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | month | String | 是 | 节点1 | 目标月份（YYYY-MM） |
| | holidays | Array&lt;String&gt; | 否 | 节点2a | 法定节假日列表（已是真数组） |
| **输出** | weekends | Array&lt;String&gt; | - | - | 周六列表 |
| | reducedDays | Array&lt;String&gt; | - | - | 需要减少出诊的日期（即落在月内的法定节假日） |
| | summary | String | - | - | 节假日汇总信息 |

### 代码

```python
from datetime import date


def main(month: str, holidays: list = None) -> dict:
    month = month or "2026-07"
    holidays = holidays or []

    # 月份解析
    parts = month.split("-")
    year = int(parts[0])
    month_num = int(parts[1])

    # 计算该月天数
    if month_num == 12:
        days_in_month = 31
    else:
        next_month_first = date(year, month_num + 1, 1)
        days_in_month = (next_month_first - date(year, month_num, 1)).days

    # 把传入的节假日规范化为集合（去重 + 截取前10位）
    holidays_set = set()
    for h in holidays:
        if h:
            holidays_set.add(str(h)[:10])

    weekends = []
    reduced_days = []

    for day in range(1, days_in_month + 1):
        date_str = "{}-{:02d}-{:02d}".format(year, month_num, day)
        d = date.fromisoformat(date_str)
        weekday = d.weekday()  # 0=Mon ... 6=Sun

        # 周六（weekday == 5）
        if weekday == 5:
            weekends.append(date_str)

        # 法定节假日（仅保留落在本月内的）
        if date_str in holidays_set:
            reduced_days.append(date_str)

    summary = "周末{}天，节假日{}天".format(
        len(weekends), len(reduced_days)
    )

    return {
        "weekends": weekends,
        "reducedDays": reduced_days,
        "summary": summary,
    }
```

### 输出示例

```json
{
  "weekends": ["2026-07-04", "2026-07-11", "2026-07-18", "2026-07-25"],
  "reducedDays": ["2026-07-04"],
  "summary": "周末4天，节假日1天"
}
```

> 注：与 v4.0 版相比，2b 不再输出 `holidays`（这一项由 2a 直接产出，无需在 2b 重复）。下游如需 `holidays` 列表，统一引用 `{{#node2a.holidays#}}`。

---

## 节点2c：医生列表格式化 physicians_format（Code-Python）

### 为什么需要这个节点？

节点3（LLM）的提示词里需要插入医生列表。理论上可以直接引用 `{{#node2a.physicians#}}`，但 Dify 会把 Array 直接字符串化，结果是：

```
[{'id': 5, 'realname': '内科刘主任', 'position': '主任医师号'}, {'id': 4, ...}]
```

这种 Python repr 形式有两个问题：
1. **LLM 阅读体验差**：方括号、引号、字典语法混杂，模型要花更多 token 才能正确解析
2. **排班质量受影响**：实测结构化文本（每行一位医生）比 JSON 字符串让 LLM 的指令遵循率更高

**本节点的唯一职责**：把 2a 解析好的 `physicians` 数组渲染成一段人类可读的多行文本，专门喂给 LLM。

> 注：这是一个**辅助节点**，不参与主流程的数据计算，只为 LLM 提示词服务。在 Dify 画布里和 2b 平级，可以并行执行。

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | physicians | Array&lt;Object&gt; | 是 | 节点2a | 医生列表（2a 已解析，**无需再 `json.loads`**） |
| **输出** | physicians_display | String | - | - | 医生列表的人类可读文本 |

### 代码

```python
def main(physicians: list) -> dict:
    """
    把医生数组渲染成多行可读文本，专门喂给 LLM 提示词。

    输入示例（来自节点2a）：
        physicians = [
            {"id": 5, "realname": "内科刘主任", "position": "主任医师号"},
            {"id": 4, "realname": "内科赵专家", "position": "专家号"},
            {"id": 1, "realname": "内科张医生", "position": "普通号"}
        ]

    输出示例：
        physicians_display = "- ID:5  姓名:内科刘主任  职称:主任医师号\n- ID:4  姓名:内科赵专家  职称:专家号\n- ID:1  姓名:内科张医生  职称:普通号"
    """
    physicians = physicians or []
    lines = []
    for p in physicians:
        # 用 .get() 容错，缺字段时返回 None，format 会渲染成 "None"
        # 三列对齐：ID / 姓名 / 职称
        lines.append("- ID:{}  姓名:{}  职称:{}".format(
            p.get("id"),
            p.get("realname"),
            p.get("position")
        ))
    return {"physicians_display": "\n".join(lines)}
```

### 输入示例（来自节点2a）

```json
[
  {"id": 5, "realname": "内科刘主任", "position": "主任医师号"},
  {"id": 4, "realname": "内科赵专家", "position": "专家号"},
  {"id": 1, "realname": "内科张医生", "position": "普通号"}
]
```

### 输出示例

```text
- ID:5  姓名:内科刘主任  职称:主任医师号
- ID:4  姓名:内科赵专家  职称:专家号
- ID:1  姓名:内科张医生  职称:普通号
```

> 注：输出 `physicians_display` 是 String，长度远小于 2000，不受 Dify MaxLength 限制。
>
> 与 v4.0 版的差异：入参从 `physicians_json: str`（需要 try/except + json.loads）改为 `physicians: list`（2a 已解析），代码少 5 行容错逻辑。

---

## 节点2d：LLM输入聚合（Code-Python）

### 为什么需要这个节点？

节点3（LLM生成排班）和节点3'（LLM修正排班）都需要把 6 个上下文变量（科室、月份、医生列表、历史规律、周末列表、节假日列表）喂给 LLM。问题：

1. **Dify LLM 节点的"变量插入"按钮对 Object/Array 类型有过滤，部分变量在 UI 里点不出来**，只能手打 `{{#节点名.变量名#}}`，容易写错节点名
2. **变量一多，提示词里要插 6 处 `{{#...#}}`**，每个节点的命名都得对上，调试麻烦
3. **节点3 和节点3' 都需要同一组上下文**，重复维护两处

**本节点的职责**：在 LLM 节点之前，把所有要喂给 LLM 的输入**聚合成一个 String 变量 `llm_context`**（带小标题、带格式），LLM 用户提示词里只引用 `{{#LLM输入聚合.llm_context#}}` **一处**即可。

> 注：与节点2c 类似，这也是**辅助节点**，不参与主流程的数据计算，只为 LLM 提示词服务。在 Dify 画布里依赖 2a / 2b / 2c 的输出，必须排在它们之后。

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | department_name | String | 是 | 节点1（Start） | 科室名称 |
| | month | String | 是 | 节点1（Start） | 月份（YYYY-MM） |
| | physicians_display | String | 是 | 节点2c（医生列表格式化） | 医生列表可读文本 |
| | weekday_patterns | Object | 否 | 节点2a（JSON解析） | 历史规律（已是真 Object） |
| | weekends | Array&lt;String&gt; | 是 | 节点2b（节假日处理） | 周六列表 |
| | reducedDays | Array&lt;String&gt; | 是 | 节点2b（节假日处理） | 法定节假日 |
| **输出** | llm_context | String | - | - | 聚合后的 LLM 上下文（多行文本，带小标题） |

### 代码

```python
def main(
    department_name: str,
    month: str,
    physicians_display: str,
    weekday_patterns: dict = None,
    weekends: list = None,
    reducedDays: list = None,
) -> dict:
    """
    把 6 个 LLM 输入聚合成一段带小标题的多行文本。

    设计原则：
    1. 每个字段用【小标题】+ 内容 的形式，方便 LLM 定位
    2. 空值字段也保留标题，写"（无）"，避免 LLM 误以为忘记提供
    3. 周末/节假日列表用顿号分隔，比 JSON 数组更省 token、更易读
    """
    department_name = department_name or ""
    month = month or ""
    physicians_display = physicians_display or "（无医生数据）"
    weekday_patterns = weekday_patterns or {}
    weekends = weekends or []
    reducedDays = reducedDays or []

    # 历史规律：dict 转成多行 "周一: avg=100, usage=85%" 形式
    if weekday_patterns:
        pattern_lines = []
        for day, info in weekday_patterns.items():
            if isinstance(info, dict):
                avg = info.get("avg_quota", "?")
                usage = info.get("usage_rate", "?")
                pattern_lines.append("  - {}: 平均号源{}, 使用率{}".format(day, avg, usage))
            else:
                pattern_lines.append("  - {}: {}".format(day, info))
        patterns_text = "\n".join(pattern_lines) if pattern_lines else "（无）"
    else:
        patterns_text = "（无历史数据）"

    # 列表型变量用顿号连接，比 JSON 数组更省 token
    weekends_text = "、".join(weekends) if weekends else "（无）"
    reduced_text = "、".join(reducedDays) if reducedDays else "（无）"

    # 拼装最终上下文
    llm_context = """【科室】{dept}
【月份】{month}
【医生列表】
{physicians}
【历史规律】
{patterns}
【周六列表（只能安排上午，每时段1人）】
{weekends}
【法定节假日（需减少出诊，每时段1人，不能为0）】
{reduced}""".format(
        dept=department_name,
        month=month,
        physicians=physicians_display,
        patterns=patterns_text,
        weekends=weekends_text,
        reduced=reduced_text,
    )

    return {"llm_context": llm_context}
```

### 输入示例（来自上游节点）

```text
department_name    = "内科"
month              = "2026-07"
physicians_display = "- ID:5  姓名:内科刘主任  职称:主任医师号\n- ID:4  姓名:内科赵专家  职称:专家号\n- ID:1  姓名:内科张医生  职称:普通号"
weekday_patterns   = {"周一": {"avg_quota": 100, "usage_rate": 0.85}, "周二": {"avg_quota": 95, "usage_rate": 0.80}}
weekends           = ["2026-07-04", "2026-07-11", "2026-07-18", "2026-07-25"]
reducedDays        = ["2026-07-04"]
```

### 输出示例

```text
【科室】内科
【月份】2026-07
【医生列表】
- ID:5  姓名:内科刘主任  职称:主任医师号
- ID:4  姓名:内科赵专家  职称:专家号
- ID:1  姓名:内科张医生  职称:普通号
【历史规律】
  - 周一: 平均号源100, 使用率0.85
  - 周二: 平均号源95, 使用率0.8
【周六列表（只能安排上午，每时段1人）】
2026-07-04、2026-07-11、2026-07-18、2026-07-25
【法定节假日（需减少出诊，每时段1人，不能为0）】
2026-07-04
```

> 注：输出 `llm_context` 是 String。下游 LLM 节点的用户提示词只需要引用 `{{#LLM输入聚合.llm_context#}}` 一处即可，不再需要在提示词里挨个插 6 个变量。

---

## 节点3：LLM生成排班（LLM）

### 关键说明：LLM 输入如何喂？

本节点的所有上下文输入（科室、月份、医生列表、历史规律、周末、节假日）**统一由节点2d（LLM输入聚合）** 拼成一段多行文本 `llm_context`，LLM 用户提示词里只引用 `{{#LLM输入聚合.llm_context#}}` **一处**。

**为什么不直接在提示词里插 6 个变量？**
1. Dify LLM 节点的"变量插入"按钮对 Object/Array 类型有过滤，部分变量在 UI 里点不出来，只能手打 `{{#...#}}`，节点名容易写错
2. 变量一多，提示词里要插 6 处，调试时不好定位
3. 聚合后 LLM 看到的是带【小标题】的统一格式文本，对指令遵循更稳定

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | llm_context | String | 是 | 节点2d（LLM输入聚合） | 聚合后的全部上下文（科室+月份+医生+规律+周末+节假日） |
| **输出** | schedules | Array&lt;Object&gt; | - | - | 排班明细列表 |
| | statistics | Object | - | - | 统计信息 |
| | warnings | Array&lt;String&gt; | - | - | 警告信息列表 |

> 注：原本要散布在提示词里的 department_name / month / physicians_display / weekday_patterns / weekends / reducedDays 这 6 个变量，全部由节点2d 聚合成 `llm_context`，本 LLM 节点只需引用这一个变量即可。

### Dify LLM 节点结构化输出配置

在 LLM 节点下方打开 **"结构化输出（Structured Output）"** 开关，切换到 **JSON Schema 源码模式**，粘贴下面这段 schema（关键步骤）：

```json
{
  "type": "object",
  "properties": {
    "schedules": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "physician_id": { "type": "number" },
          "physician_name": { "type": "string" },
          "work_date": { "type": "string" },
          "time_slot": { "type": "string" },
          "total_quota": { "type": "number" },
          "ai_suggestion": { "type": "string" }
        },
        "required": ["physician_id", "physician_name", "work_date", "time_slot", "total_quota"]
      }
    },
    "statistics": {
      "type": "object",
      "properties": {
        "total_schedules": { "type": "number" },
        "total_quota": { "type": "number" },
        "expert_ratio": { "type": "number" },
        "avg_weekly_workload": { "type": "number" }
      },
      "required": ["total_schedules", "total_quota", "expert_ratio", "avg_weekly_workload"]
    },
    "warnings": {
      "type": "array",
      "items": { "type": "string" }
    },
    "quality_report": {
      "type": "object",
      "properties": {
        "coverage_rate": { "type": "number" },
        "balance_score": { "type": "number" },
        "note": { "type": "string" }
      }
    }
  },
  "required": ["schedules", "statistics", "warnings"]
}
```

#### 三个关键约束（避坑）

| 约束 | 说明 |
|------|------|
| **`required` 只列绝对不能少的字段** | `ai_suggestion` 即使 LLM 偶尔不输出也不影响排班，不放 required；`schedules / statistics / warnings` 是下游节点强依赖的，必须 required |
| **不要写 `"additionalProperties": false`** | 如果加了这一行，LLM 输出里多任何一个字段（比如它自作主张加个 `"plan_id": 1`），Dify 就会判结构化输出失败、触发重试，浪费时间甚至直接报错 |
| **`quality_report` 是可选字段** | 用于后续质量评估，不想要可以把这一段整段删掉，不影响主流程 |

> 注：节点3 和节点3' **共用同一份 schema**（输出结构完全一致），复制粘贴即可。

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

## 必须为每个月所有日期生成排班（日期规则动态，不要写死月份）
- **目标月份与具体天数以用户提示词里的 `llm_context` 为准**（不要假设是几月几号）
- 【周六列表】中列出的日期：只安排上午，每个时段1人
- 【法定节假日】中列出的日期：减少出诊，每个时段1人，**不能为0**
- 若某日期同时出现在【周六列表】和【法定节假日】中：按"只上午、每时段1人"处理（即更严格的规则）
- **周日及未列入上述两清单的工作日**：正常安排，每个时段至少2人（上午+下午）

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

### 用户提示词（只需引用一个聚合变量，无需挨个插 6 处）

```
以下是本次排班任务的全部输入信息：

{{#LLM输入聚合.llm_context#}}

请根据上述信息生成完整的月度排班方案。

重要提醒：
- 必须为该月所有日期生成排班（1日到月底，不能遗漏任何一天）
- 周六只安排上午，周日正常安排出诊（每个时段至少1人）
- 日期类型：周六→半天；周日→普通工作日；法定节假日→减半
- 严格遵守系统提示词中的所有排班规则与号源配置

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

## 节点4：冲突检测（Code-Python）

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | schedules | Array&lt;Object&gt; | 是 | 节点3 | AI生成的排班列表（Dify 代码节点直接接收 list） |
| | month | String | 是 | 节点1 | 月份（用于检测缺失日期） |
| | weekends | Array&lt;String&gt; | 是 | 节点2b | 周六列表 |
| | reducedDays | Array&lt;String&gt; | 是 | 节点2b | 法定节假日 |
| **输出** | errors_json | **String**（JSON 序列化） | - | - | 冲突错误列表 JSON（格式：DUPLICATE:医生ID:日期:时段:次数） |
| | missingDays_json | **String**（JSON 序列化） | - | - | 缺失出诊的日期时段 JSON（格式：YYYY-MM-DD:上午/下午） |
| | warnings_json | **String**（JSON 序列化） | - | - | 警告信息列表 JSON |
| | hasConflict | Boolean | - | - | 是否有冲突 |
| | scheduleCount | Number | - | - | 排班总数 |
| | missingDaysCount | Number | - | - | 缺失日期时段数（排障用） |
| | errorsCount | Number | - | - | 错误数（排障用） |

> ⚠️ **v4.4 关键变更：为什么 `missingDays` / `errors` / `warnings` 都改成 String？**
>
> Dify Code 节点对**单个 Array 输出变量有 30 元素的硬性限制**（平台层面，schema 改不了）。节点4 检测"缺失日期时段"时，最坏情况一个月能产出 50~62 条（普通工作日缺上午+下午 2 条/天 × 22 天 + 周六 4 条 + 节假日若干）。LLM 漏排较多时 `missingDays` 轻松超 30，触发：
>
> ```
> The length of output variable `missingDays` must be less than 30 elements.
> ```
>
> 工作流直接被平台判失败，连带 `validated_schedules` 也拿不到。v4.3 只把节点5 的 `validated_schedules` String 化了，**漏掉了节点4 的 `missingDays`**——v4.4 补齐这个口子，把节点4 所有 Array 输出统一 String 化，与节点5 同策略。节点3' 的 LLM 提示词读 JSON 字符串比读 Python repr 形式更清晰。

### 分支输出

| hasConflict | 下一节点 |
|-------------|---------|
| true | 节点3'（LLM修正排班） |
| false | 节点5（号源补充） |

### 代码

```python
from datetime import date
from collections import defaultdict
import json


def get_week_number(date_str: str) -> int:
    d = date.fromisoformat(date_str)
    first_day_of_year = date(d.year, 1, 1)
    past_days = (d - first_day_of_year).days
    return (past_days + first_day_of_year.weekday() + 1 + 6) // 7


def main(schedules: list, month: str, weekends: list, reducedDays: list) -> dict:
    schedules = schedules or []
    month = month or "2026-07"
    weekends = weekends or []
    reduced_days = reducedDays or []

    errors = []
    warnings = []
    missing_days = []

    weekly_count = defaultdict(int)        # (physician_id, week_no) -> count
    daily_count = defaultdict(int)         # (physician_id, date, slot) -> count
    day_slot_map = defaultdict(list)       # (date, slot) -> [physician_id, ...]

    # 遍历排班
    for s in schedules:
        try:
            physician_id = s.get("physician_id")
            work_date = s.get("work_date")
            time_slot = s.get("time_slot")
        except AttributeError:
            continue

        week_no = get_week_number(work_date)
        weekly_count[(physician_id, week_no)] += 1
        daily_count[(physician_id, work_date, time_slot)] += 1
        day_slot_map[(work_date, time_slot)].append(physician_id)

    # 检测每周超过4次
    for (pid, wno), cnt in weekly_count.items():
        if cnt > 4:
            warnings.append(
                "医生{}在第{}周被安排{}次，超过4次限制".format(pid, wno, cnt)
            )

    # 检测同一医生同一时段重复
    for (pid, d, slot), cnt in daily_count.items():
        if cnt > 1:
            errors.append(
                "DUPLICATE:{}:{}:{}:{}".format(pid, d, slot, cnt)
            )

    # 计算目标月所有日期
    parts = month.split("-")
    year = int(parts[0])
    month_num = int(parts[1])
    if month_num == 12:
        days_in_month = 31
    else:
        days_in_month = (date(year, month_num + 1, 1) - date(year, month_num, 1)).days

    weekends_set = set(weekends)
    reduced_set = set(reduced_days)

    # 检测缺失日期时段
    for day in range(1, days_in_month + 1):
        date_str = "{}-{:02d}-{:02d}".format(year, month_num, day)
        d = date.fromisoformat(date_str)
        weekday = d.weekday()  # 0=Mon ... 6=Sun

        is_saturday = (weekday == 5)
        is_holiday = date_str in reduced_set

        if is_holiday:
            need_morning, need_afternoon = True, False
        elif is_saturday:
            need_morning, need_afternoon = True, False
        else:
            need_morning, need_afternoon = True, True

        if need_morning:
            if len(day_slot_map.get((date_str, "上午"), [])) == 0:
                missing_days.append("{}:上午".format(date_str))

        if need_afternoon:
            if len(day_slot_map.get((date_str, "下午"), [])) == 0:
                missing_days.append("{}:下午".format(date_str))

    has_conflict = len(errors) > 0 or len(missing_days) > 0

    # ⚠️ v4.4：所有 Array 输出统一 json.dumps 成 String，绕开 Dify Code 节点 30 元素硬限制
    # missingDays 最坏情况 50~62 条/月，必触发平台限制
    return {
        "errors_json": json.dumps(errors, ensure_ascii=False),
        "missingDays_json": json.dumps(missing_days, ensure_ascii=False),
        "warnings_json": json.dumps(warnings, ensure_ascii=False),
        "hasConflict": has_conflict,
        "scheduleCount": len(schedules),
        "missingDaysCount": len(missing_days),
        "errorsCount": len(errors),
    }
```

### 输出示例

```json
{
  "errors_json": "[\"DUPLICATE:5:2026-07-01:上午:2\"]",
  "missingDays_json": "[\"2026-07-05:上午\",\"2026-07-05:下午\"]",
  "warnings_json": "[\"医生5在第2周被安排5次，超过4次限制\"]",
  "hasConflict": true,
  "scheduleCount": 120,
  "missingDaysCount": 2,
  "errorsCount": 1
}
```

> 注：`errors_json` / `missingDays_json` / `warnings_json` 都是 JSON 字符串，下游节点3' 的 LLM 提示词直接引用并解析。`hasConflict` / `scheduleCount` / `missingDaysCount` / `errorsCount` 是 Number/Boolean，不受 30 元素限制。

---

## IF/ELSE 条件分支节点（替代 Coze 条件分支）

### Dify IF/ELSE 节点配置

| 配置项 | 值 |
|--------|-----|
| 条件类型 | 变量比较 |
| 比较变量 | `{{#node4.hasConflict#}}` |
| 比较操作符 | `等于`（is equal to） |
| 比较值 | `true`（Boolean） |
| IF 分支（true） | → 节点3' LLM修正排班 |
| ELSE 分支（false） | → 节点5 号源补充 |

> 注：Dify 用独立的 IF/ELSE 节点实现分支，不像 Coze 在节点内嵌分支条件。条件分支节点本身不输出新变量。

---

## 节点3'：LLM修正排班（LLM）

### 触发条件

当 IF/ELSE 节点判断 `hasConflict == true` 时走此分支。

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | llm_context | String | 是 | 节点2d（LLM输入聚合） | 聚合后的基础上下文（科室+月份+医生+周末+节假日），与节点3 共用。**通过 `{{#LLM输入聚合.llm_context#}}` 跨节点引用，无需在输入表面板声明** |
| | original_schedules | Array&lt;Object&gt; | 是 | 节点3（LLM生成排班） | 上轮生成的排班。**通过 `{{#LLM生成排班.schedules#}}` 跨节点引用，无需在输入表面板声明** |
| | errors_json | **String**（JSON） | 是 | 节点4（冲突检测） | 上轮冲突错误 JSON。**通过 `{{#冲突检测.errors_json#}}` 跨节点引用** |
| | missingDays_json | **String**（JSON） | 是 | 节点4（冲突检测） | 缺失出诊的日期时段 JSON。**通过 `{{#冲突检测.missingDays_json#}}` 跨节点引用** |
| **输出** | schedules | Array&lt;Object&gt; | - | - | 修正后的排班明细列表 |
| | statistics | Object | - | - | 统计信息 |
| | warnings | Array&lt;String&gt; | - | - | 警告信息列表 |
| | quality_report | Object | - | - | 质量评估报告（可选，用于后续优化） |

> 注：节点3' 与节点3 共用节点2d 的 `llm_context`（基础上下文一次性聚合好）。但 `original_schedules` / `errors_json` / `missingDays_json` 是上一轮 LLM + 冲突检测的**动态输出**，不能预先聚合，所以这三项仍然在提示词里单独引用。v4.4 起 `errors_json` / `missingDays_json` 是 JSON 字符串（绕开 Dify Code 节点 30 元素限制），LLM 直接读 JSON 字符串比读 Array 的 Python repr 形式更清晰。

### Dify LLM 节点结构化输出配置

**与节点3 完全相同**，直接复制节点3 那份 JSON Schema 即可。在节点3' 下方打开 **"结构化输出（Structured Output）"** 开关，切换到 **JSON Schema 源码模式**，粘贴：

```json
{
  "type": "object",
  "properties": {
    "schedules": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "physician_id": { "type": "number" },
          "physician_name": { "type": "string" },
          "work_date": { "type": "string" },
          "time_slot": { "type": "string" },
          "total_quota": { "type": "number" },
          "ai_suggestion": { "type": "string" }
        },
        "required": ["physician_id", "physician_name", "work_date", "time_slot", "total_quota"]
      }
    },
    "statistics": {
      "type": "object",
      "properties": {
        "total_schedules": { "type": "number" },
        "total_quota": { "type": "number" },
        "expert_ratio": { "type": "number" },
        "avg_weekly_workload": { "type": "number" }
      },
      "required": ["total_schedules", "total_quota", "expert_ratio", "avg_weekly_workload"]
    },
    "warnings": {
      "type": "array",
      "items": { "type": "string" }
    },
    "quality_report": {
      "type": "object",
      "properties": {
        "coverage_rate": { "type": "number" },
        "balance_score": { "type": "number" },
        "note": { "type": "string" }
      }
    }
  },
  "required": ["schedules", "statistics", "warnings"]
}
```

> ⚠️ 与节点3 同样的避坑要点：
> - **不要写 `"additionalProperties": false`**（否则 LLM 多输出字段会被判失败触发重试）
> - `required` 只列**绝对不能少**的字段（`schedules / statistics / warnings`）
> - `ai_suggestion` 和 `quality_report` 即使偶尔缺失也不影响主流程，不放 required

### 系统提示词

```
# 角色定义
你是一个专业的医院排班修正专家。你的职责是根据上轮错误信息修正排班方案。

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
以下是本次排班任务的基础上下文（科室/月份/医生列表/周末/节假日）：

{{#LLM输入聚合.llm_context#}}

以下是上轮排班的结果与问题（需要修正）：

【上轮生成的排班】（保留其中正确的部分，只改有错误的）
{{#LLM生成排班.schedules#}}

【上轮冲突错误】（JSON 数组字符串，格式：DUPLICATE:医生ID:日期:时段:次数，需删除重复）
{{#冲突检测.errors_json#}}

【上轮缺失出诊的日期时段】（JSON 数组字符串，格式：YYYY-MM-DD:上午/下午，必须补充）
{{#冲突检测.missingDays_json#}}

修正要求（**务必逐条遵守**）：
- 针对 DUPLICATE 错误：删除同一医生同一时段的重复记录，**保留其中一条**
- 针对 missingDays：**必须为清单里的每一个日期时段补充排班**，不得遗漏任何一条；缺失条数应等于上轮漏排的全部数量
- 只修正错误的部分，保留正确的排班记录，**不要整段重写**
- **输出完整的排班列表**：输出条数 ≈ 原正确条数（= 上轮总条数 − DUPLICATE 重复条数） + missingDays 条数
- 修正后所有 `physician_id` 必须仍来自【医生列表】中已存在的 ID，不得编造
- 不得引入新的 DUPLICATE（同一医生同一日期同一时段不得出现两次）

> ⚠️ **节点3' 输入方案（重要）**：本工作流节点3' 不使用"声明入参"方式，所有上下文通过 `{{#源节点名.字段#}}` 跨节点引用语法直接拼进用户提示词，Dify 在 LLM 调用前自动替换。节点3' 的输入变量面板**无需配置任何入参**。前提：节点名 `LLM输入聚合` / `LLM生成排班` / `冲突检测` 必须与画布里实际命名一字不差；如画布节点名为 `node2d`/`node3`/`node4`，需相应替换。

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

## 节点5：号源补充（Code-Python）

> 注意：节点5 的 `schedules` 输入来源取决于分支。在 Dify 中，两条分支汇合处需要使用 **变量聚合器（Variable Aggregator）** 节点，把 `{{#node3.schedules#}}` 和 `{{#node3_fix.schedules#}}` 聚合成统一的 `schedules_merged`。

### 类型设计

| | 输入变量名 | 变量类型 | 必填 | 来源 | 说明 |
|--|-----------|---------|------|------|------|
| **输入** | schedules | Array&lt;Object&gt; | 是 | 变量聚合器 | 排班列表（来自节点3或节点3'） |
| | physicians | Array&lt;Object&gt; | 是 | 节点2a | 医生列表（2a 已解析，**无需再 json.loads**） |
| **输出** | valid | Boolean | - | - | 校验是否通过 |
| | errors | Array&lt;String&gt; | - | - | 错误列表（已截断到前 30 条） |
| | validated_schedules_json | **String**（JSON 序列化） | - | - | 校验通过的排班列表（**为什么是 String 见下方说明**） |
| | totalCount | Number | - | - | 排班总数 |
| | debug_log | String | - | - | 调试日志（Dify UI 不显示 print，必须用输出字段） |

> ⚠️ **为什么 `validated_schedules_json` 是 String 而不是 Array？**
>
> Dify Code 节点对单个 **Array 输出变量有 30 元素的硬性限制**（平台层面，schema 改不了）。月度排班通常 100+ 条，超出限制会报错：
>
> ```
> The length of output variable `validated_schedules` must be less than 30 elements.
> ```
>
> 解决方案：节点5 内部用 `json.dumps(validated)` 把 Array 序列化成 String 输出，绕开长度限制。后端 `DifyIntegrationService` 拿到 String 后 `JSON.parse` 还原成 List 即可。LLM 节点的结构化输出走的是特殊通道不受此限制，但 Code 节点不行。

> ⚠️ **常见报错排查**：
>
> **报错 1**：`main() got an unexpected keyword argument 'output'`
> → Dify 硬塞了未声明的参数，按以下顺序排查：
> 1. 检查 Dify 输入变量表里**是否多了 `output` 字段**（误加的），删掉
> 2. 检查 `schedules` 的来源是不是误选了 `{{#LLM生成排班.output#}}` 或 `{{#LLM修正排班.output#}}`，应改为 `{{#变量聚合器.schedules#}}`
> 3. 代码已用 `**kwargs` 兜底（见下方），即使 Dify 仍硬塞 `output` 也不会报错
>
> **报错 2**：`AttributeError: 'str' object has no attribute 'get'`
> → Dify 传入的 schedules / physicians 元素是**字符串**而不是 dict（通常发生在变量聚合器或 LLM 结构化输出的兼容模式下）。排查：
> 1. 看试运行日志里"变量聚合器 → schedules"的第一个元素是 `{"physician_id": 5, ...}`（dict，正确）还是 `"{'physician_id': 5, ...}"`（str，被字符串化了）
> 2. 检查节点3 / 节点3' 的结构化输出 schema，确认 `schedules.items` 的类型是 **object**，不是 string
> 3. 下方代码已用 `_to_dict()` 兜底（自动把 str 形式的 dict 反序列化），无论 Dify 传 dict 还是 str 都能正确处理

### 代码

```python
def _to_dict(v):
    """把任意输入转成 dict，失败返回 None"""
    if v is None:
        return None
    if isinstance(v, dict):
        return v
    if isinstance(v, str):
        import json
        s = v.strip()
        if not s:
            return None
        # 优先 JSON 解析（Dify 常见）
        try:
            o = json.loads(s)
            return o if isinstance(o, dict) else None
        except Exception:
            pass
        # 兜底：Python repr 字符串（如 "{'a': 1}"）
        try:
            import ast
            o = ast.literal_eval(s)
            return o if isinstance(o, dict) else None
        except Exception:
            return None
    return None


def _to_int(v, default=0):
    if v is None:
        return default
    if isinstance(v, bool):
        return int(v)
    if isinstance(v, (int, float)):
        return int(v)
    if isinstance(v, str):
        s = v.strip()
        if not s:
            return default
        try:
            return int(float(s))
        except Exception:
            return default
    return default


def _extract_schedules_list(schedules):
    """
    Dify 实际传入的 schedules 形态可能是：
      1) 直接数组 [{...}, {...}]            ← 文档假设
      2) 包装对象 {"schedules": [...]}      ← 上游 LLM 节点实际输出
      3) 包装对象 {"validated_schedules": [...]}
    本函数统一抽出 list[dict]。
    """
    import json

    # 形态 1：直接是 list
    if isinstance(schedules, list):
        out = []
        for s in schedules:
            d = _to_dict(s)
            if d:
                out.append(d)
        return out, "direct_list"

    # 形态 2/3：dict 包装
    if isinstance(schedules, dict):
        for key in ("schedules", "validated_schedules", "list", "data"):
            v = schedules.get(key)
            if isinstance(v, list):
                out = []
                for s in v:
                    d = _to_dict(s)
                    if d:
                        out.append(d)
                return out, "wrapped_dict[{}]".format(key)
        # 极端情况：dict 本身就是一条排班（含 physician_id / work_date）
        if any(k in schedules for k in ("physician_id", "work_date", "schedule_id")):
            return [schedules], "single_dict"

    # 字符串兜底：尝试 JSON 解析后递归
    if isinstance(schedules, str):
        try:
            o = json.loads(schedules)
            return _extract_schedules_list(o)
        except Exception:
            pass

    return [], "unknown"


def main(schedules: list, physicians: list, **kwargs) -> dict:
    import datetime

    debug_lines = []

    # 关键：先把输入归一化成 list[dict]
    sched_list, sched_form = _extract_schedules_list(schedules)
    debug_lines.append("input_form={}".format(sched_form))
    debug_lines.append("input_count={}".format(len(sched_list)))

    # physicians 同样做归一化
    phys_list = []
    if isinstance(physicians, list):
        for p in physicians:
            d = _to_dict(p)
            if d:
                phys_list.append(d)
    elif isinstance(physicians, dict):
        for key in ("physicians", "list", "data"):
            v = physicians.get(key)
            if isinstance(v, list):
                for p in v:
                    d = _to_dict(p)
                    if d:
                        phys_list.append(d)
                break
    debug_lines.append("physicians_count={}".format(len(phys_list)))

    # 构建 physician 等级/职称索引
    phys_map = {}
    for p in phys_list:
        pid = _to_int(p.get("physician_id") or p.get("id"))
        if pid:
            phys_map[pid] = p

    quota_map = {"主任医师号": 30, "专家号": 25, "普通号": 50}
    price_map = {"主任医师号": 30.00, "专家号": 20.00, "普通号": 15.00}
    level_id_map = {"主任医师号": 3, "专家号": 2, "普通号": 1}

    validated = []
    errors = []
    seen = set()

    for idx, s in enumerate(sched_list):
        try:
            physician_id = _to_int(s.get("physician_id") or s.get("doctor_id"))
            work_date_raw = s.get("work_date") or s.get("date")
            time_slot = s.get("time_slot") or s.get("slot") or "morning"

            if not physician_id or not work_date_raw:
                errors.append({
                    "index": idx,
                    "error": "missing physician_id or work_date",
                    "raw": str(s)[:120]
                })
                continue

            # 日期解析（兼容 ISO / datetime / str）
            if isinstance(work_date_raw, str):
                work_date = work_date_raw[:10]
            elif hasattr(work_date_raw, "strftime"):
                work_date = work_date_raw.strftime("%Y-%m-%d")
            else:
                work_date = str(work_date_raw)[:10]

            # 时段校验
            if time_slot not in ("上午", "下午"):
                errors.append("第{}条：时段错误（值={}）".format(idx + 1, time_slot))
                continue

            # 重复校验
            key = (work_date, physician_id, time_slot)
            if key in seen:
                errors.append("第{}条：重复排班".format(idx + 1))
                continue
            seen.add(key)

            # 医生存在性校验
            if physician_id not in phys_map:
                errors.append("第{}条：医生ID {} 不存在".format(idx + 1, physician_id))
                continue

            physician = phys_map[physician_id]
            position = physician.get("position", "普通号")
            total_quota = _to_int(s.get("total_quota"), 0) or quota_map.get(position, 30)

            validated.append({
                "physician_id": physician_id,
                "physician_name": s.get("physician_name") or physician.get("realname", ""),
                "work_date": work_date,
                "time_slot": time_slot,
                "total_quota": total_quota,
                "available_quota": total_quota,
                "used_quota": 0,
                "price": price_map.get(position, 15.00),
                "regist_level_id": level_id_map.get(position, 1),
                "ai_suggestion": s.get("ai_suggestion") or s.get("reason") or "",
                "status": "草稿",
            })
        except Exception as e:
            errors.append({"index": idx, "error": str(e), "raw": str(s)[:120]})

    debug_lines.append("validated_count={}".format(len(validated)))
    debug_lines.append("error_count={}".format(len(errors)))
    if errors:
        debug_lines.append("first_5_errors={}".format(errors[:5]))

    # ⚠️ Dify Code 节点对 Array 输出有 30 元素硬限制
    # 月度排班通常 100+ 条，必须把 Array 序列化成 String 输出，后端再 JSON.parse 还原
    import json as _json
    return {
        "valid": len(errors) == 0,
        "errors": errors[:30],  # errors 也截断到 30 以防触发 Dify 限制
        "validated_schedules_json": _json.dumps(validated, ensure_ascii=False),
        "totalCount": len(validated),
        "debug_log": " | ".join(debug_lines),
    }
```

> **修复说明（v4.2.1）**：原代码假设 `schedules` 永远是直接的 `list`，但 Dify 实际把上游 LLM 节点的输出 `{"schedules": [...], "statistics": {...}, "warnings": [...]}` 整体作为一个 Object 传给本节点。`for i, s in enumerate(schedules)` 在遍历这个 dict 时拿到的是 key 字符串（"schedules"/"statistics"/"warnings"），`_to_dict()` 失败后全部 continue，导致 `validated_schedules` 为空。
>
> 修复方案：新增 `_extract_schedules_list()` 函数，识别三种输入形态（直接数组 / `{schedules: [...]}` 包装 / `{validated_schedules: [...]}` 包装），统一抽出真正的 list。同时把 `print()` 调试换成 `debug_log` 输出字段（Dify UI 不显示 print 输出）。

> 注：相比 v4.0 版，本节点入参从 `physicians_json: str`（需 `json.loads` + try/except 容错）改为 `physicians: list`（来自2a），代码减少约 8 行，且无需重复容错逻辑。

### 输出示例

```json
{
  "valid": true,
  "errors": [],
  "validated_schedules_json": "[{\"physician_id\":5,\"physician_name\":\"张医生\",\"work_date\":\"2026-07-01\",\"time_slot\":\"上午\",\"total_quota\":30,\"available_quota\":30,\"used_quota\":0,\"price\":30.00,\"regist_level_id\":3,\"ai_suggestion\":\"周一上午需求高\",\"status\":\"草稿\"},...]",
  "totalCount": 120,
  "debug_log": "input_form=wrapped_dict[schedules] | input_count=120 | physicians_count=5 | validated_count=120 | error_count=0"
}
```

> 注：`validated_schedules_json` 是把整个 list 用 `json.dumps` 序列化后的字符串，Dify 把它当作普通 String 输出，不受 30 元素限制。后端拿到后用 `JSON.parseArray(...)` 或 `objectMapper.readTree(...)` 还原成 List。

---

## 节点6：结束节点（End）

### 类型设计

> Dify 结束节点输出变量也需声明类型。Array/Object 在结束节点输出后，会以 JSON 形式返回给后端。

| 输出变量名 | 变量类型 | 来源 | 说明 |
|-----------|---------|------|------|
| validated_schedules_json | **String**（JSON 序列化） | 节点5 | 校验通过的排班列表（String 形式，绕开 Dify 30 元素限制） |
| statistics | Object | 节点3 或 节点3'（通过变量聚合器） | 统计信息（**⚠️ LLM 自报，可能与真实条数对不上，后端以 `totalCount` 为准**） |
| errors | Array&lt;String&gt; | 节点5（错误经节点5 转发） | 校验错误列表（≤30 条） |
| warnings | Array&lt;String&gt; | 节点4 | 警告信息 |
| message | String | 固定值 | 提示信息（**不能为空**，填写：排班方案已生成） |

### message 固定值

```
排班方案已生成
```

### 输出示例（Dify API 响应中的 data 字段）

```json
{
  "validated_schedules_json": "[{\"physician_id\":5,...},...]",
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

## 变量类型汇总表（Dify 版）

| 节点 | 变量名 | 类型 | 最大长度 |
|------|--------|------|---------|
| **节点1-开始（Start）** | department_id | Number | - |
| | department_name | String | 2000 |
| | month | String | 2000 |
| | physicians_json | String（JSON 字符串） | 2000 |
| | weekday_patterns_json | String（JSON 字符串，可选） | 2000 |
| | holidays_json | String（JSON 字符串，可选） | 2000 |
| **节点2c-医生格式化 physicians_format（Code-Python）** | physicians（输入） | Array&lt;Object&gt; | - |
| | physicians_display（输出） | String | - |
| **节点2a-JSON解析（Code-Python）** | physicians_json（输入） | String | 2000 |
| | weekday_patterns_json（输入） | String | 2000 |
| | holidays_json（输入） | String | 2000 |
| | physicians（输出） | Array&lt;Object&gt; | - |
| | weekday_patterns（输出） | Object | - |
| | holidays（输出） | Array&lt;String&gt; | - |
| | parse_ok（输出） | Boolean | - |
| **节点2b-节假日计算（Code-Python）** | month（输入） | String | - |
| | holidays（输入） | Array&lt;String&gt; | - |
| | weekends（输出） | Array&lt;String&gt; | - |
| | reducedDays（输出） | Array&lt;String&gt; | - |
| | summary（输出） | String | - |
| **节点3-LLM生成（LLM）** | department_name（输入） | String | - |
| | month（输入） | String | - |
| | physicians_display（输入） | String | - |
| | weekday_patterns（输入） | Object | - |
| | weekends（输入） | Array&lt;String&gt; | - |
| | reducedDays（输入） | Array&lt;String&gt; | - |
| | schedules（输出） | Array&lt;Object&gt; | - |
| | statistics（输出） | Object | - |
| | warnings（输出） | Array&lt;String&gt; | - |
| **节点4-冲突检测（Code-Python）** | schedules（输入） | Array&lt;Object&gt; | - |
| | month（输入） | String | - |
| | weekends（输入） | Array&lt;String&gt; | - |
| | reducedDays（输入） | Array&lt;String&gt; | - |
| | errors_json（输出） | **String**（JSON 序列化） | - |
| | missingDays_json（输出） | **String**（JSON 序列化） | - |
| | warnings_json（输出） | **String**（JSON 序列化） | - |
| | hasConflict（输出） | Boolean | - |
| | scheduleCount（输出） | Number | - |
| | missingDaysCount（输出） | Number | - |
| | errorsCount（输出） | Number | - |
| **IF/ELSE 分支节点** | hasConflict（条件变量） | Boolean | - |
| | true → 节点3' | - | - |
| | false → 节点5 | - | - |
| **节点3'-LLM修正（LLM）** | department_name（输入） | String | - |
| | month（输入） | String | - |
| | physicians_display（输入） | String | - |
| | weekends（输入） | Array&lt;String&gt; | - |
| | reducedDays（输入） | Array&lt;String&gt; | - |
| | original_schedules（输入） | Array&lt;Object&gt; | - |
| | errors_json（输入） | **String**（JSON） | - |
| | missingDays_json（输入） | **String**（JSON） | - |
| | schedules（输出） | Array&lt;Object&gt; | - |
| | statistics（输出） | Object | - |
| | warnings（输出） | Array&lt;String&gt; | - |
| **变量聚合器（Variable Aggregator）** | schedules（聚合） | Array&lt;Object&gt;（来源 node3 / node3'） | - |
| | statistics（聚合） | Object | - |
| **节点5-号源补充（Code-Python）** | schedules（输入） | Array&lt;Object&gt; 或 Object（Dify 实际传包装对象） | - |
| | physicians（输入） | Array&lt;Object&gt; | - |
| | valid（输出） | Boolean | - |
| | errors（输出） | Array&lt;String&gt;（≤30 条） | - |
| | validated_schedules_json（输出） | **String**（JSON 序列化） | - |
| | totalCount（输出） | Number | - |
| | debug_log（输出） | String | - |
| **节点6-结束（End）** | validated_schedules_json（输出） | **String** | - |
| | statistics（输出） | Object | - |
| | errors（输出） | Array&lt;String&gt; | - |
| | warnings（输出） | Array&lt;String&gt; | - |
| | message（输出） | String | - |

> 注：仅"开始节点"的 String 变量受到 Dify MaxLength=2000 的硬性限制。其他节点的 String 变量由代码节点/LLM 节点内部产出，不需要配置最大长度。

---

## Dify 工作流搭建步骤

### 步骤总览

```
1. 在 Dify 中创建工作流（不是 Chatflow），命名为 wf_schedule_generate

2. 创建节点：
   - Start（开始）
   - Code: node2a（JSON解析）
   - Code: node2b（节假日计算）
   - Code: node2c / physicians_format（医生列表格式化，喂给 LLM）
   - LLM: node3（生成排班）
   - Code: node4（冲突检测）
   - IF/ELSE（条件分支）
   - LLM: node3_fix（修正排班）
   - Variable Aggregator（变量聚合器）
   - Code: node5（号源补充）
   - End（结束）

3. 连线：
   Start → node2a ─┬→ node2b ─────────────────────────────────────┐
                   ├→ node2c (physicians_format) ──┐              │
                   └────────────────────────────────┼→ node3 → node4 → IF/ELSE
   Start ───────────────────────────────────────────┘                  │
                                                                      ├─ true →  node3_fix ─┐
                                                                      └─ false → (直连)     ├→ Variable Aggregator → node5 → End
                                                                                    ┘
```

### 详细步骤

**步骤 1：创建工作流**
- 类型：工作流（Workflow）
- 名称：`wf_schedule_generate`

**步骤 2：开始节点（Start）变量配置**
```
department_id        Number    必填
department_name      String    必填
month                String    必填
physicians_json      String    必填（说明：JSON 字符串，内容为医生对象数组）
weekday_patterns_json String   非必填（JSON 字符串）
holidays_json        String    非必填（JSON 字符串）
```

**步骤 3：节点2a `node2a`（JSON解析，Code-Python）**
- 类型：Code（Python）
- 输入：
  - `physicians_json` ← `{{#start.physicians_json#}}`
  - `weekday_patterns_json` ← `{{#start.weekday_patterns_json#}}`
  - `holidays_json` ← `{{#start.holidays_json#}}`
- 代码：复制本文档"节点2a"代码
- 输出：`physicians(Array<Object>) / weekday_patterns(Object) / holidays(Array<String>) / parse_ok(Boolean)`

**步骤 4：节点2b `node2b`（节假日计算，Code-Python）**
- 类型：Code（Python）
- 输入：
  - `month` ← `{{#start.month#}}`
  - `holidays` ← `{{#node2a.holidays#}}`（注意：来自 2a，不再是 String）
- 代码：复制本文档"节点2b"代码
- 输出：`weekends / reducedDays / summary`

**步骤 5：节点 `node2c` / `physicians_format`（医生格式化，Code-Python）**
- 类型：Code（Python）
- 输入：`physicians` ← `{{#node2a.physicians#}}`（注意：来自 2a，已是 Array）
- 输出：`physicians_display`（String，人类可读文本，喂给 LLM）
- 代码：复制本文档"节点2c"代码

**步骤 6：节点3 `node3`（LLM生成排班）**
- 模型：deepseek-v4（按项目实际配置）
- Temperature：0.3
- 打开"结构化输出"开关，**切换到 JSON Schema 源码模式**，粘贴"节点3 → Dify LLM 节点结构化输出配置"里的 JSON Schema
- 系统提示词：复制本文档"节点3 → 系统提示词"
- 用户提示词：复制本文档"节点3 → 用户提示词"（只引用 `{{#LLM输入聚合.llm_context#}}` 一个变量）
- ⚠️ 注意：**不要在 schema 里写 `"additionalProperties": false`**，否则 LLM 多输出一个字段就会被 Dify 判失败

**步骤 7：节点4 `node4`（冲突检测，Code-Python）**
- 输入：
  - `schedules` ← `{{#node3.schedules#}}`
  - `month` ← `{{#start.month#}}`
  - `weekends` ← `{{#node2b.weekends#}}`
  - `reducedDays` ← `{{#node2b.reducedDays#}}`
- 代码：复制本文档"节点4"代码（v4.4 版，输出字段全部 `_json` 后缀）
- 输出变量类型声明（**关键**，必须在 Dify 节点4 的输出表里逐个声明）：
  - `errors_json` → **String**
  - `missingDays_json` → **String**
  - `warnings_json` → **String**
  - `hasConflict` → Boolean
  - `scheduleCount` → Number
  - `missingDaysCount` → Number
  - `errorsCount` → Number

**步骤 8：IF/ELSE 节点（条件分支）**
- IF 条件：`{{#node4.hasConflict#}}` 等于 `true`
- IF 分支出口：→ 节点3'
- ELSE 分支出口：→ 变量聚合器

**步骤 9：节点3' `node3_fix`（LLM修正排班）**
- 模型/Temperature 同节点3
- 系统提示词、用户提示词：复制本文档"节点3'"小节
- 结构化输出 schema：**与节点3 完全相同**，直接复制步骤 6 那份 JSON Schema 即可
- ⚠️ **节点3' 采用"纯提示词方案"，输入变量面板无需配置任何入参**——所有上下文通过 `{{#源节点名.字段#}}` 跨节点引用语法直接拼进用户提示词，Dify 自动替换。提示词里需要跨节点引用 4 个字段：
  - `{{#LLM输入聚合.llm_context#}}`（基础上下文）
  - `{{#LLM生成排班.schedules#}}`（上轮排班）
  - `{{#冲突检测.errors_json#}}`（上轮冲突错误）
  - `{{#冲突检测.missingDays_json#}}`（上轮缺失日期时段）
- ⚠️ **节点名必须与画布实际命名一字不差**：若画布节点名是 `node2d`/`node3`/`node4`，提示词里要相应改成 `{{#node2d.llm_context#}}` 等

**步骤 10：变量聚合器（Variable Aggregator）**
- 聚合变量 1：`schedules`
  - 来源：`{{#node3.schedules#}}` 和 `{{#node3_fix.schedules#}}`
- 聚合变量 2：`statistics`
  - 来源：`{{#node3.statistics#}}` 和 `{{#node3_fix.statistics#}}`

**步骤 11：节点5 `node5`（号源补充，Code-Python）**
- 输入：
  - `schedules` ← `{{#variable_aggregator.schedules#}}`
  - `physicians` ← `{{#node2a.physicians#}}`（注意：来自 2a，不再是 String）
- 代码：复制本文档"节点5"代码
- 输出：`valid / errors / validated_schedules_json / totalCount / debug_log`
- ⚠️ 输出变量名是 `validated_schedules_json`（String 类型），**不是** `validated_schedules`（Array）。原因：Dify Code 节点对 Array 输出有 30 元素限制，月度排班 100+ 条会触发报错

**步骤 12：结束节点（End）**
- 输出变量：
  - `validated_schedules_json` ← `{{#node5.validated_schedules_json#}}`（**String**，不是 Array）
  - `statistics` ← `{{#variable_aggregator.statistics#}}`（Object）
  - `errors` ← `{{#node5.errors#}}`（Array&lt;String&gt;，来自节点5，最多 30 条）
  - `warnings` ← `{{#node4.warnings#}}`（Array&lt;String&gt;）
  - `message` ← 固定值"排班方案已生成"

**步骤 13：测试与发布**
- 在 Dify 编辑器中点击"试运行"
- 输入测试数据（注意 physicians_json 等用合法 JSON 字符串）
- 通过后点击"发布"

---

## Dify 条件分支与变量聚合说明

### Dify 用独立 IF/ELSE 节点（不是节点内嵌分支）

```
节点4 → IF/ELSE 节点
            ├─ IF（true）  → 节点3' → 变量聚合器
            └─ ELSE（false）        → 变量聚合器
                                              ↓
                                            节点5
```

### 为什么需要变量聚合器？

两条分支汇合时，节点5 不知道排班数据是来自节点3（未冲突）还是节点3'（修正后）。Dify 用 **变量聚合器（Variable Aggregator）** 节点把两条分支的输出合并成统一的变量，下游节点只引用这个聚合后的变量即可。

> ⚠️ 这是 Dify 与 Coze 工作流的关键差异之一。Coze 在条件分支节点内可以直连下游节点；Dify 必须显式使用变量聚合器。

---

## 后端调用代码（Dify API）

```java
@PostMapping("/dify/generate")
public Map<String, Object> generateScheduleByDify(@RequestBody Map<String, Object> request) {
    Long departmentId = ((Number) request.get("departmentId")).longValue();
    String month = (String) request.get("month");

    // 1. 查询科室下的医生
    List<Employee> physicians = employeeFeignClient.getByDepartment(departmentId);

    // 2. 查询历史排班，计算周规律（如果有）
    Object weekdayPatterns = null;
    List<DoctorSchedule> history = doctorScheduleService.getByMonth(departmentId, getLastMonth(month));
    if (history != null && !history.isEmpty()) {
        weekdayPatterns = calculateWeekdayPatterns(history);
    }

    // 3. 计算法定节假日
    List<String> holidays = calculateHolidays(month);

    // 4. 构造 Dify 工作流请求参数
    //    注意：复合类型必须 JSON.stringify，因为 Dify 开始节点不支持 Array/Object
    //    注意：所有 String 变量最大长度 2000，超长会被 Dify 拒绝
    final int MAX_LEN = 2000;
    String physiciansJson = JSON.toJSONString(physicians);
    String weekdayPatternsJson = weekdayPatterns == null ? "" : JSON.toJSONString(weekdayPatterns);
    String holidaysJson = JSON.toJSONString(holidays);

    if (physiciansJson.length() > MAX_LEN) {
        throw new BusinessException("医生列表过长（" + physiciansJson.length()
            + " > " + MAX_LEN + "），请减少单次排班的医生数量或精简字段");
    }
    if (weekdayPatternsJson.length() > MAX_LEN) {
        throw new BusinessException("历史规律数据过长（" + weekdayPatternsJson.length()
            + " > " + MAX_LEN + "）");
    }
    if (holidaysJson.length() > MAX_LEN) {
        throw new BusinessException("节假日列表过长（" + holidaysJson.length()
            + " > " + MAX_LEN + "）");
    }

    Map<String, Object> inputs = new HashMap<>();
    inputs.put("department_id", departmentId);
    inputs.put("department_name", departmentService.getNameById(departmentId));
    inputs.put("month", month);
    inputs.put("physicians_json", physiciansJson);                            // 必须 stringify
    inputs.put("weekday_patterns_json", weekdayPatternsJson);                 // 必须 stringify
    inputs.put("holidays_json", holidaysJson);                                // 必须 stringify

    // 5. 调用 Dify 工作流 API
    //    POST /v1/workflows/run
    Map<String, Object> difyResult = difyService.runWorkflow(inputs);

    // 6. Dify 响应结构：{ data: { outputs: { validated_schedules_json (String), ... } } }
    //    注意 validated_schedules_json 是 String（JSON 序列化后的），需手动解析
    Map<String, Object> outputs = (Map<String, Object>) difyResult.get("outputs");
    Object schedulesJson = outputs.get("validated_schedules_json");
    List<Map<String, Object>> schedules;
    if (schedulesJson instanceof String) {
        // Dify Code 节点 Array 输出有 30 元素限制，节点5 已序列化成 String
        schedules = JSON.parseArray((String) schedulesJson, Map.class);
    } else if (schedulesJson instanceof List) {
        // 兼容：若 Dify 后续放宽限制直接输出 Array
        schedules = (List<Map<String, Object>>) schedulesJson;
    } else {
        schedules = Collections.emptyList();
    }

    // 7. 保存到数据库
    //    ⚠️ 不要信任 outputs.statistics.total_schedules（LLM 自报，可能与真实条数对不上），
    //    以 schedules.size() 为准。统计字段可在前端展示但不可作为对账依据。
    SchedulePlan plan = schedulePlanService.createPlan(departmentId, month);
    schedulePlanService.saveSchedules(plan.getId(), schedules);

    return success(Map.of(
        "plan_id", plan.getId(),
        "schedule_count", schedules.size(),
        "status", "草稿"
    ));
}
```

### Dify API 调用细节（参考）

**请求**（POST `/v1/workflows/run`）：

```json
{
  "inputs": {
    "department_id": 1,
    "department_name": "内科",
    "month": "2026-07",
    "physicians_json": "[{\"id\":5,\"realname\":\"内科刘主任\",\"position\":\"主任医师号\"}]",
    "weekday_patterns_json": "{\"周一\":{\"avg_quota\":100,\"usage_rate\":0.85}}",
    "holidays_json": "[\"2026-07-04\"]"
  },
  "response_mode": "blocking",
  "user": "backend-service"
}
```

**响应**（同步阻塞模式）：

```json
{
  "task_id": "...",
  "workflow_run_id": "...",
  "data": {
    "id": "...",
    "workflow_id": "...",
    "status": "succeeded",
    "outputs": {
      "validated_schedules_json": "[{\"physician_id\":5,...},...]",
      "statistics": {...},
      "errors": [],
      "warnings": [],
      "message": "排班方案已生成"
    },
    "elapsed_time": 12.34,
    "total_tokens": 5678,
    "created_at": 1234567890,
    "finished_at": 1234567902
  }
}
```

---

## 迁移自 Coze 版的关键变化（变更摘要）

| 维度 | Coze 版（v3.2） | Dify 版（v4.0） | Dify 版（v4.1） | Dify 版（v4.2） | Dify 版（v4.3） | Dify 版（v4.4） | Dify 版（v4.5，当前） |
|------|----------------|-----------------|------------------|------------------|------------------|----------------------|
| 代码节点语言 | JavaScript | Python | Python | Python | Python | Python |
| 开始节点复合类型 | 直接 Array/Object | String(JSON)，需后端 stringify | 同 v4.0（由节点2a 统一解析） | 同 v4.0 | 同 v4.0 | 同 v4.0 |
| Integer 类型 | 支持 | 改用 Number | 改用 Number | 改用 Number | 改用 Number | 改用 Number |
| 条件分支 | Coze 节点内嵌分支 | Dify 独立 IF/ELSE 节点 | 同 v4.0 | 同 v4.0 | 同 v4.0 | 同 v4.0 |
| 分支汇合 | 隐式 | 必须用变量聚合器 | 同 v4.0 | 同 v4.0 | 同 v4.0 | 同 v4.0 |
| LLM 结构化输出 | 配置输出 schema | 配置输出 schema（yaml 形式） | 同 v4.0 | **JSON Schema 源码模式，禁止 additionalProperties:false** | 同 v4.2 | 同 v4.2 |
| 第三方库 | dayjs, lodash | json, datetime, re 等 | 同 v4.0 | 同 v4.0 | 同 v4.0 | 同 v4.0 |
| 提示词变量语法 | `{{变量名}}` | `{{#节点标识.变量名#}}` | 同 v4.0 | 同 v4.0（节点3/3' 用户提示词只引用 1 处 `llm_context`） | 同 v4.2 | 同 v4.2 |
| 后端 API | Coze workflow API | Dify `/v1/workflows/run` | 同 v4.0 | 同 v4.0 | 同 v4.0 | 同 v4.0 |
| **JSON 解析位置** | 散落在各节点 | 散落在 node2/node5/physicians_format | 集中在节点2a | 同 v4.1 | 同 v4.1 | 同 v4.1 |
| **LLM 输入聚合** | 无 | 无 | 无 | **新增节点2d**，6 个上下文 → 1 个 `llm_context` | 同 v4.2 | 同 v4.2 |
| **节点5 输入容错** | 无 | 无 | 无 | 无 | **`_extract_schedules_list()` 兼容包装对象 + `debug_log` 输出** | 同 v4.3 |
| **节点5 → End 输出形式** | Array | Array | Array | Array | **String（JSON 序列化），绕开 Dify 30 元素限制** | 同 v4.3 | 同 v4.3 |
| **节点4 输出形式** | Array | Array | Array | Array | Array（**会爆 30 限制**） | **String（JSON 序列化），missingDays/errors/warnings 全部 `_json` 后缀** | 同 v4.4 |
| **节点3 系统提示词日期规则** | 静态 | 静态 | 静态 | 静态 | 静态 | 静态 | **动态（引用 llm_context 的【周六列表】【法定节假日】）** |
| **节点3' 入参一致性** | - | - | - | - | - | - | **original_schedules 声明=引用一致 + 输出完整性强化** |
| **statistics 可信度** | - | - | - | - | - | - | **标注 LLM 自报，以 totalCount 为准** |
| **节点数量** | - | 9 个（含 Start/End） | 10 个（拆出 node2a） | 11 个（再拆出 node2d） | 同 v4.2（11 个） | 同 v4.3（11 个，仅改输出字段类型） | 同 v4.4（11 个） |

---

> 文档版本：v4.5（节点3 提示词通用化 + 节点3' 入参/输出强化 + statistics 可信度标注 + parse_ok 可读性优化）
> 最后更新：2026-07-02
> 维护人：Claude
> 变更说明：
> - v4.5：① **节点3 系统提示词去掉 2026-07 硬编码日期规则**（原文写死"7月有31天/7月4日节假日/7月11/18/25日周六/7月5/12/19/26日周日"等具体日期，导致工作流只能排 7 月，换月必错），改为引用 `llm_context` 的动态【周六列表】【法定节假日】清单；② **节点3' 用户提示词里 `{{#LLM生成排班.schedules#}}` 改为 `{{#LLM修正排班.original_schedules#}}`**（与类型设计表声明的入参名一致，原写法绕过了声明的入参，配置和提示词不一致——必须在节点3' 输入表里声明 `original_schedules` 并连到 `{{#LLM生成排班.schedules#}}`）；③ 节点3' 用户提示词**强化输出完整性要求**（明确 missingDays 必须全补、给出预期条数公式 `原正确条数 + missingDays 条数`、禁止编造医生ID、禁止引入新 DUPLICATE），缓解 LLM二 实测常见的"丢条/偷懒只补几个"问题；④ End 节点 `statistics` 字段标注"LLM 自报不可信"，后端代码注释强调以 `schedules.size()`/`totalCount` 为准；⑤ 节点2a 的 `parse_ok` 判定改成显式函数（空输入→OK、非空解析后类型不符→异常），消除原三元 or 表达式 `weekday_ok = (not weekday_patterns_json) or bool(weekday_patterns) or (weekday_patterns == {} and weekday_patterns_json == "{}")` 的可读性陷阱；⑥ 步骤9 补充节点3' 输入变量声明与连线清单
> - v4.4：节点4（冲突检测）的 `missingDays` / `errors` / `warnings` 三个 Array 输出全部改为 `_json` 后缀的 String，绕开 Dify Code 节点 30 元素硬限制。原因：v4.3 只修了节点5 的 `validated_schedules`，漏掉了节点4。实际运行中 LLM 漏排大量日期时 `missingDays` 轻松超过 30 条（一个月最多 50~62 条），触发 `The length of output variable 'missingDays' must be less than 30 elements` 导致整个工作流失败。节点3' 提示词里的 `{{#冲突检测.missingDays#}}` 同步改为 `{{#冲突检测.missingDays_json#}}`，LLM 读 JSON 字符串更清晰。新增 `missingDaysCount` / `errorsCount` 数值字段方便排障
> - v4.4：节点4（冲突检测）的 `missingDays` / `errors` / `warnings` 三个 Array 输出全部改为 `_json` 后缀的 String，绕开 Dify Code 节点 30 元素硬限制。原因：v4.3 只修了节点5 的 `validated_schedules`，漏掉了节点4。实际运行中 LLM 漏排大量日期时 `missingDays` 轻松超过 30 条（一个月最多 50~62 条），触发 `The length of output variable 'missingDays' must be less than 30 elements` 导致整个工作流失败。节点3' 提示词里的 `{{#冲突检测.missingDays#}}` 同步改为 `{{#冲突检测.missingDays_json#}}`，LLM 读 JSON 字符串更清晰。新增 `missingDaysCount` / `errorsCount` 数值字段方便排障
> - v4.3：节点5 → End 输出字段从 `validated_schedules`（Array）改为 `validated_schedules_json`（String）。原因：Dify Code 节点对单个 Array 输出变量有 30 元素硬限制（平台层面，schema 改不了），月度排班 100+ 条会报 `The length of output variable must be less than 30 elements`。修复方案：节点5 内部用 `json.dumps` 把 list 序列化成 String 输出，后端 `JSON.parseArray` 还原。同时合并 v4.2.1 的两项修复（`_extract_schedules_list` 兼容包装对象输入 + `debug_log` 输出字段）
> - v4.2：新增节点2d（LLM输入聚合），把 6 个 LLM 上下文变量（department_name/month/physicians_display/weekday_patterns/weekends/reducedDays）聚合成单个 String `llm_context`，节点3/3' 用户提示词简化为只引用一处；LLM 结构化输出由 yaml 改为标准 JSON Schema 源码模式；明确禁止在 schema 中使用 `additionalProperties: false`（否则 LLM 多输出字段会被判失败）
> - v4.1：将节点2（节假日处理）拆分为节点2a（JSON解析，集中负责反序列化所有 `*_json` 字符串）与节点2b（节假日计算，专注日期推算）；将原本只作为代码片段出现的 physicians_format 独立为"节点2c"并补充完整章节（作用/类型设计/代码/输入输出示例）；同步消除 physicians_format 与节点5 中的重复 `json.loads` 逻辑；更新架构图、变量类型汇总表、搭建步骤、节点连线
> - v4.0：迁移到 Dify 工作流平台；开始节点复合类型改用 String(JSON) 传递；代码节点全部由 JavaScript 重写为 Python；新增 IF/ELSE 独立条件分支节点与变量聚合器节点；后端代码示例改为调用 Dify API
> - v3.2：节点4 新增 missingDays 检测；节点3' 新增 missingDays 输入用于修正

---

## 附录 A：试运行验证用例

> 用于 Dify 工作流编辑器"试运行"时直接粘贴到开始节点的输入数据。每个字段都是**可直接复制**的字符串/数字，符合开始节点类型约束（String 最大长度 2000）。

### A.1 测试前检查清单

在 Dify 编辑器点"试运行"之前，先确认：

- [ ] 11 个节点全部创建完毕：Start / JSON解析 / 节假日处理 / 医生列表格式化 / LLM输入聚合 / LLM生成排班 / 冲突检测 / IF/ELSE / LLM修正排班 / 变量聚合器 / 号源补充 / End
- [ ] 所有节点的**实际命名**与提示词里 `{{#节点名.变量名#}}` 的节点名**一字不差**
- [ ] 节点3 和节点3' 的"结构化输出"开关已打开，且粘贴了 JSON Schema（不含 `additionalProperties: false`）
- [ ] LLM 节点已选模型（如 deepseek-v4）
- [ ] 各节点之间连线完整（特别注意 IF/ELSE 的两条出口都连到变量聚合器）

### A.2 用例一：标准场景（5 个医生 + 1 个节假日）

最常用的 happy path，验证主流程能否跑通。

**开始节点输入**（直接复制粘贴）：

| 变量名 | 值 |
|--------|----|
| `department_id` | `1` |
| `department_name` | `内科` |
| `month` | `2026-07` |
| `physicians_json` | `[{"id":5,"realname":"内科刘主任","position":"主任医师号"},{"id":4,"realname":"内科赵专家","position":"专家号"},{"id":1,"realname":"内科张医生","position":"普通号"},{"id":2,"realname":"内科李医生","position":"普通号"},{"id":3,"realname":"内科王医生","position":"普通号"}]` |
| `weekday_patterns_json` | `{"周一":{"avg_quota":100,"usage_rate":0.85},"周二":{"avg_quota":95,"usage_rate":0.80},"周三":{"avg_quota":60,"usage_rate":0.55},"周四":{"avg_quota":65,"usage_rate":0.60},"周五":{"avg_quota":80,"usage_rate":0.70},"周六":{"avg_quota":30,"usage_rate":0.90},"周日":{"avg_quota":50,"usage_rate":0.65}}` |
| `holidays_json` | `["2026-07-04"]` |

> 说明：2026-07-04 是周六也是节假日（模拟"7月4日美国独立日"那种情况），会触发"周六+节假日"双重规则；其余三个周六（7/11、7/18、7/25）只走周六规则；周日（7/5、7/12、7/19、7/26）正常排诊。

**预期输出**（End 节点的 outputs）：

- `validated_schedules`：约 **100~130 条**排班（每天上午+下午 × 31 天，扣除周六只排上午、节假日减半）
- `statistics.total_schedules` 与 validated_schedules 长度一致
- `statistics.expert_ratio` 约在 **0.20~0.40** 之间（接近 33%）
- `errors`：空数组（如果 LLM 表现正常）
- `warnings`：可能有少量警告（可接受）
- `message`：`"排班方案已生成"`

### A.3 用例二：无历史数据（验证可选字段）

不传 `weekday_patterns_json`，验证 LLM 在缺历史规律的情况下也能生成合理排班。

**开始节点输入**：

| 变量名 | 值 |
|--------|----|
| `department_id` | `1` |
| `department_name` | `内科` |
| `month` | `2026-08` |
| `physicians_json` | `[{"id":5,"realname":"内科刘主任","position":"主任医师号"},{"id":4,"realname":"内科赵专家","position":"专家号"},{"id":1,"realname":"内科张医生","position":"普通号"},{"id":2,"realname":"内科李医生","position":"普通号"}]` |
| `weekday_patterns_json` | （**留空**） |
| `holidays_json` | `[]` |

**预期输出**：
- 节点2a 输出的 `weekday_patterns` 是 `{}`（空对象）
- 节点2d 拼出的 `llm_context` 里【历史规律】段显示"（无历史数据）"
- LLM 仍能生成排班（按系统提示词里的默认规则）
- `statistics` 与用例一同量级

### A.4 用例三：边界 / 异常输入（验证容错）

故意传坏数据，验证节点2a 的 `parse_ok` 和下游容错是否生效。

**开始节点输入**：

| 变量名 | 值 |
|--------|----|
| `department_id` | `1` |
| `department_name` | `内科` |
| `month` | `2026-07` |
| `physicians_json` | `not_a_json_string`（**非法 JSON**） |
| `weekday_patterns_json` | `{"周一":{"avg_quota":100}`（**JSON 不闭合**） |
| `holidays_json` | `[]` |

**预期行为**：
- 节点2a 不抛异常，输出安全空值：`physicians=[]`、`weekday_patterns={}`、`parse_ok=false`
- 节点2c 输出 `physicians_display = "（无医生数据）"`
- 节点2d 仍能拼出 `llm_context`（医生列表段为"（无医生数据）"）
- LLM 收到空医生列表后，应输出**空 schedules 或 warnings 提示"无医生可排"**
- 工作流**不应崩溃**，End 节点应正常返回（可能 `validated_schedules=[]`、`errors` 含说明）

### A.5 试运行通过判定（DoD）

满足以下全部条件，才算工作流试运行通过：

| 判定项 | 标准 |
|--------|------|
| **流程不中断** | 任意一个用例跑下来，End 节点必须输出（不能中途报错停住） |
| **JSON Schema 校验** | 节点3 / 节点3' 的输出 `schedules` 字段是 Array，元素含全部 required 字段 |
| **节点5 校验通过** | 用例一、二 的 `valid = true`、`errors = []` |
| **无重复排班** | 用例一跑完后，`validated_schedules` 中不存在 `(work_date, physician_id, time_slot)` 完全相同的两条 |
| **覆盖率** | 用例一跑完后，`statistics.total_schedules` 应在 90~140 之间（低于 80 说明 LLM 漏排了大量日期） |
| **节假日规则生效** | `2026-07-04` 这一天 `schedules` 里**只有上午**记录（周六规则） |

### A.6 试运行常见问题排查

| 现象 | 可能原因 | 排查方法 |
|------|---------|---------|
| 节点2a 报错"expected str, got list" | 开始节点把 `physicians_json` 配成了 Array 类型 | 改回 String 类型，MaxLength=2000 |
| LLM 节点输出还是带 `{{#...#}}` 没替换 | 节点名写错（如画布叫"LLM输入聚合"，提示词里写成"node2d"） | 检查提示词里的节点名与画布完全一致 |
| LLM 输出 JSON 解析失败 | schema 含 `additionalProperties: false` | 删掉这行，重新试运行 |
| 节点5 报"医生ID 不存在" | LLM 编造了 physicians_json 里不存在的 id | 在节点3 系统提示词里强调"physician_id 必须来自医生列表"，或重试 |
| 变量聚合器拿不到值 | 两条分支只连了一条 | 把 IF 和 ELSE 两个出口都连到聚合器 |
| `validated_schedules` 为空 | ① LLM 没输出 schedules / 格式不对；② **节点5 收到的是包装对象 `{"schedules": [...]}` 不是直接 list**（v4.2.1 已修复） | 先看节点5 的 `debug_log` 输出字段：`input_form` 应该是 `wrapped_dict[schedules]`，`input_count` 应该是 LLM 实际输出的排班数（如 104）。若 `input_form=unknown` 或 `input_count=0`，再把节点5 入参 `schedules` 展开，发给维护者调整 `_extract_schedules_list()` |
| **`The length of output variable 'validated_schedules' must be less than 30 elements`** | Dify Code 节点对 Array 输出有 30 元素硬限制（平台层面），月度排班 100+ 条必触发 | 用 v4.3 版本：节点5 把 `validated_schedules` 改为 `validated_schedules_json`（String 类型，内部 `json.dumps(validated, ensure_ascii=False)`），后端从 String 字段解析。**注意 End 节点输出变量名也要同步改成 `validated_schedules_json`** |

### A.7 调试小技巧

1. **单节点试运行**：Dify 支持对任意单个节点点"试运行"，不用从头跑全流程。先验证节点2a/2b/2c/2d 输出正确，再跑节点3
2. **查看完整 Prompt**：LLM 节点运行后，点"追踪详情"可以看到 Dify 最终发给 LLM 的完整 system+user prompt，是排查变量替换问题的第一手段
3. **节点5 之前打断点**：在变量聚合器之后、节点5 之前临时加一个 End 节点，先把 `schedules` 打印出来看 LLM 实际输出，调通了再接节点5
