# WF-FollowUp: 病例总结与医学建议工作流设计

## 1. 概述

随访系统「医患沟通」模块使用 Dify Workflow 生成 AI 病例总结，并在医生离线时提供医疗边界内的 AI 代答。

| 工作流 | App 用途 | 环境变量 |
|--------|----------|----------|
| W-CaseSummary | 病例总结 + 医学建议 | `DIFY_API_KEY_FOLLOW_UP_CASE_SUMMARY` |
| W-MedicalChat | 患者随访问答（可选独立 App） | `DIFY_API_KEY_FOLLOW_UP_MEDICAL_CHAT` |

调用方式：`POST {DIFY_BASE_URL}/v1/workflows/run`，`response_mode: blocking`。

未配置 API Key 时，medtech-service 使用 `CaseSummaryFallbackEngine` 规则模板兜底。

---

## 2. W-CaseSummary 输入（inputs）

后端 `CaseSummaryContextBuilder` 组装以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| registerId | number | 挂号 ID |
| patientName | string | 姓名 |
| caseNumber | string | 病历号 |
| gender / age | string/number | 基本信息 |
| diagnosis | string | 诊断 |
| chiefComplaint | string | 主诉 |
| presentIllness | string | 现病史 |
| allergy | string | 过敏史 |
| diseasesJson | string | 诊断列表 JSON |
| metricsJson | string | 近30天指标摘要 JSON |
| followUpRecordsJson | string | 随访反馈记录 JSON |
| observedToday | boolean | 今日是否已观察 |
| interviewScheduledToday | boolean | 今日是否排访谈 |

---

## 3. W-CaseSummary 输出（outputs）

工作流 end 节点应输出以下字段（支持 `structured_output` 包装）：

```json
{
  "caseSummary": "## 病例摘要\n...",
  "medicalAdvice": "随访与用药建议...",
  "riskAlerts": ["风险点1", "风险点2"],
  "followUpFocus": ["指标趋势", "症状变化"],
  "confidence": 0.85
}
```

---

## 4. W-CaseSummary Prompt 约束（建议）

```
你是医院随访系统的临床摘要助手。仅基于提供的结构化患者数据生成总结，不得编造未提供的检查结果。

要求：
1. caseSummary 使用 Markdown，包含：基本情况、诊断与主诉、近30天指标趋势、随访反馈、今日任务状态
2. medicalAdvice 给出可执行的随访建议，避免具体处方剂量
3. riskAlerts 列出需医生关注的风险点（数组）
4. followUpFocus 列出后续随访重点（数组）
5. 语气专业、简洁，面向医生阅读
```

---

## 5. W-MedicalChat 输入 / 输出

**输入：**

- 继承 W-CaseSummary 患者上下文
- `patientMessage`：患者最新留言
- `recentMessagesJson`：最近 20 条会话 JSON

**输出：**

```json
{
  "reply": "给患者的回复",
  "refused": false,
  "refusalReason": "",
  "confidence": 0.8
}
```

**Prompt 约束：**

- 仅回答与该患者随访、康复、用药依从、症状变化相关的问题
- 拒绝天气、娱乐、政治、开具处方等请求，`refused=true`
- 紧急情况提示就医，不做 definitive 诊断

---

## 6. 双版本病例总结（医生审核）

1. 工作流生成 → 写入 `follow_up_case_summary.ai_draft_content`（status=draft）
2. 医生在弹窗编辑 → `doctor_content`
3. 医生确认：
   - 仅内部：`approved`，`shared_to_patient=0`
   - 发布患者：`shared`，`shared_to_patient=1`，会话插入 `case_summary` 消息

---

## 7. 后续扩展

- Dify 知识库：接入科室随访指南
- HTTP 节点：调用离线训练的风险预测模型
- 微调模型：替换 LLM 节点 base model，无需改 API 契约
