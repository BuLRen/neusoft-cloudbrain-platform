# AI 导诊 → AI 预问诊 上下文串联设计

> 状态：**设计定稿，待 review 后实现**
> 日期：2026-06-27
> 决策：预问诊读导诊原文自己判断 + 没提到就走完整 5 步 + FeignClient + 挂号后自动进入

---

## 1. 背景与目标

### 1.1 用户诉求（原话）

> "等我在预问诊的时候，如果我在导诊的情况下说了我有哪些病，以及说了我的一些既往病史，那你就不要再问了，你就确认一下就行了。如果我没说，你就继续去问。"

**核心**：导诊阶段患者自由文本里**已经提到的**既往史、过敏史、用药史等信息，预问诊**不要重复追问**，只需确认；**没提到的**，按现有流程正常问。

### 1.2 流程位置

患者流程：**导诊 → 选排班 → 支付 → 预问诊**（挂号后自动进入预问诊）。

串联发生在"导诊完成 → 预问诊开始"之间，通过 `register_id` 关联。

### 1.3 现状硬伤（已确认）

调研发现导诊服务**不结构化采集**既往史/过敏史：
- `triage-prompt.st` 只让 AI 输出症状/科室/风险/红旗征/可能病情/建议检查
- `ai_triage_record` 表无既往史/过敏史列，全靠 `symptom_description`（患者原文）和 `ai_analysis`（JSON）承载
- 后端钩子 `AiTriageService.getTriageSummary(registerId)`（`AiTriageService.java:426`）已写好，返回 `TriageSummary(symptomDescription, recommendDeptName, recommendDeptId, riskLevel, aiAnalysisJson)`，**但 consult-service 没调它**

### 1.4 选定方案

**预问诊读导诊原文自己判断**：
- 不改 triage-service
- 不改表结构
- consult-service 通过 Feign 拉导诊 `TriageSummary`，把**症状原文 + AI 分析 JSON** 注入预问诊 prompt
- 让预问诊模型自己判断"导诊原文里提到了哪些维度"，提到→确认，没提到→追问

---

## 2. 用户流程

### 2.1 主流程

```
[导诊] 患者输入："发烧咳嗽3天，有高血压史，青霉素过敏"
   ↓ AI 返回 推荐科室=呼吸内科, 风险=normal, 可能病情=[...]
   ↓ 落 ai_triage_record (symptom_description 含上述原文)
[选排班] → [支付] → 产生 registerId
   ↓ 自动跳 /patient/previsit?registerId=xxx
[预问诊·start] consult 调 Feign 拿 TriageSummary
   ↓ 把 symptomDescription + aiAnalysisJson 注入 prompt
[预问诊·开场白]
   "根据您导诊时描述：主要症状是发烧咳嗽3天，建议呼吸内科。
    您提到有高血压病史、青霉素过敏，这些信息准确吗？"
   ↓
[患者] "准确" / "不对，其实是..." / "补充：还伴随..."
   ↓
   ├─ 主诉+时长 已在导诊原文 → 跳过第1、2步
   ├─ 既往史（高血压）已提及 → 确认后跳过第4步
   ├─ 过敏史（青霉素）已提及 → 确认后跳过第5步
   └─ 仍需追问：伴随症状（导诊未要求采集，必问）
   ↓
[预问诊] 只问【伴随症状】这一项 → 结束 → 生成病历
```

### 2.2 边界情况

| 情况 | 处理 |
|------|------|
| **导诊原文里啥都没说**（只写"头疼"） | 预问诊走完整 5 步，和现在一样 |
| **该挂号无导诊记录**（直接挂号进来） | Feign 返回 null，预问诊走完整 5 步 |
| **Feign 调用失败/超时** | log.warn，降级为完整 5 步，不阻断 |
| **同一挂号二次进入预问诊** | 已有 session 续接（`getSession` 逻辑已存在） |
| **患者确认时纠正**（"不是青霉素，是头孢"） | 以患者本次回答为准，更新信息 |
| **导诊提到但患者不认**（"我没说过高血压"） | 以患者本次回答为准 |

---

## 3. 技术设计

### 3.1 服务间调用：FeignClient

**新增** `ai-consult-service/.../client/TriageClient.java`：

```java
@FeignClient(name = "ai-triage-service", path = "/api/ai/triage")
public interface TriageClient {
    @GetMapping("/summary/register/{registerId}")
    Result<TriageSummary> getTriageSummary(@PathVariable("registerId") Integer registerId);
}
```

**新增本地 DTO** `ai-consult-service/.../client/dto/TriageSummary.java`（不依赖 triage 包）：

```java
public record TriageSummary(
    String symptomDescription,   // 患者原始症状描述（核心信息源）
    String recommendDeptName,
    Long recommendDeptId,
    String riskLevel,            // normal/urgent/critical
    String aiAnalysisJson        // AI 分析 JSON（possibleConditions/suggestedExaminations 等）
) {}
```

**安全包装**（`PreConsultService` 新增）：

```java
private TriageSummary safeFetchTriage(Integer registerId) {
    if (registerId == null) return null;
    try {
        Result<TriageSummary> r = triageClient.getTriageSummary(registerId);
        return (r != null && r.getCode() == 200) ? r.getData() : null;
    } catch (Exception e) {
        log.warn("拉取导诊摘要失败，registerId={}，预问诊降级为完整流程", registerId, e);
        return null;
    }
}
```

**Maven**：确认 `ai-consult-service/pom.xml` 含 `spring-cloud-starter-openfeign`，启动类加 `@EnableFeignClients`。

### 3.2 Prompt 改造（核心）

**`previsit-prompt.st` 新增两段**（插在【采集顺序】之后、【患者档案·过敏史】之前）：

```
【导诊上下文 —— 重要背景】
{triageContext}

【导诊信息处理策略 —— 仅在【导诊上下文】非"无"时生效】
患者在导诊阶段可能已经提到了部分采集维度（主诉/症状时长/伴随症状/既往史/过敏史）。
你的任务是**逐项判断导诊原文里是否已提及**，按下列规则处理：

1. **开场白**：第一轮 AI 回复必须先**复述导诊中已提及的关键信息**（主要症状、推荐科室，
   以及原文里提到的既往史/过敏史），并询问"这些信息准确吗？如有补充请告诉我"。
   - 严禁透露【导诊 AI 分析】里的 possibleConditions / suggestedExaminations
     （那是给医生参考的，避免引发患者焦虑）

2. **患者确认后**（"准确""对""是的"等肯定回答）：
   - 导诊原文已提及的维度，视为已采集，**不再追问**
   - 导诊原文**未提及**的维度，按原采集顺序继续追问

3. **患者纠正或补充时**：
   - 以患者本次回答为准更新信息
   - 仍按原顺序继续追问未采集的维度

4. **如何判断"导诊原文是否提及某维度"**：
   - 主诉/症状时长：导诊原文几乎一定有（这是导诊必采的），通常可视为已提及
   - 伴随症状：看原文是否描述了多个症状
   - 既往史：看原文是否提到慢性病、手术史、长期用药（如"高血压""糖尿病""甲亢"）
   - 过敏史：看原文是否提到药物/食物过敏（如"青霉素过敏""对海鲜过敏"）
   - **不确定时按"未提及"处理**，宁可多问一句，不要漏采

5. 不要在预问诊里推荐科室（科室推荐已在导诊完成）
```

**占位符渲染逻辑**：

`triageContext` 为空时填 `"无"`；非空时格式化为：

```
· 患者主诉原文：{symptomDescription}
· 建议就诊科室：{recommendDeptName}
· 紧迫度等级：{riskLevel}
· 导诊 AI 分析（仅供你参考，不对患者复述）：{aiAnalysisJson}
```

### 3.3 PreConsultService 改造

**改动 1**：构造器注入 `TriageClient`

```java
private final TriageClient triageClient;  // 新增
```

**改动 2**：`start()` 拉 triage

```java
public Map<String, Object> start(Integer registerId, Integer patientId, Consumer<String> tokenConsumer) {
    String sessionUuid = UUID.randomUUID().toString();

    // 新增：拉导诊上下文（失败降级为 null）
    TriageSummary triage = safeFetchTriage(registerId);
    String triageContext = formatTriageContext(triage);

    String patientAllergy = safeQueryPatientAllergy(patientId);
    List<Message> messages = buildPromptMessages(sessionUuid, "", patientAllergy, triageContext);  // 多传一个参数
    // ...其余不变
}
```

**改动 3**：`buildPromptMessages` 加参数 `triageContext`

```java
private List<Message> buildPromptMessages(String sessionUuid, String newUserAnswer,
                                          String patientAllergy, String triageContext) {
    List<Message> historyMessages = chatMemoryRepository.findByConversationId(sessionUuid);
    String collectedHint = messagesToText(historyMessages);
    String allergyHint = (patientAllergy == null || patientAllergy.isBlank()) ? "无" : patientAllergy;
    String triageHint = (triageContext == null || triageContext.isBlank()) ? "无" : triageContext;  // 新增

    PromptTemplate pt = new PromptTemplate(previsitPromptResource);
    String systemPrompt = pt.render(Map.of(
            "collected", collectedHint,
            "patientAllergy", allergyHint,
            "triageContext", triageHint        // 新增
    ));
    // ...其余不变
}
```

**改动 4**：`reply()` 也要传 `triageContext`

由于 `reply()` 内部也调 `buildPromptMessages`，需要把 `triageContext` 在整个会话期间保持。两种做法：
- **做法 A（推荐）**：每次 reply 时重新通过 registerId 查 triage（多一次 Feign 调用，但逻辑简单、无状态）
- **做法 B**：start 时把 triageContext 存到 session 的某行记录里，reply 时读出

**选 A**，因为 Feign 调用很快，且 triage 记录不变，无需缓存。

**新增方法** `formatTriageContext`：

```java
private String formatTriageContext(TriageSummary t) {
    if (t == null) return null;
    StringBuilder sb = new StringBuilder();
    if (t.symptomDescription() != null && !t.symptomDescription().isBlank()) {
        sb.append("· 患者主诉原文：").append(t.symptomDescription()).append("\n");
    }
    if (t.recommendDeptName() != null && !t.recommendDeptName().isBlank()) {
        sb.append("· 建议就诊科室：").append(t.recommendDeptName()).append("\n");
    }
    if (t.riskLevel() != null && !t.riskLevel().isBlank()) {
        sb.append("· 紧迫度等级：").append(t.riskLevel()).append("\n");
    }
    if (t.aiAnalysisJson() != null && !t.aiAnalysisJson().isBlank()) {
        sb.append("· 导诊 AI 分析（仅供你参考，不对患者复述）：").append(t.aiAnalysisJson()).append("\n");
    }
    return sb.length() == 0 ? null : sb.toString();
}
```

### 3.4 数据库

**不改表结构**。理由：
- 导诊上下文是提示词背景，不是预问诊采集到的数据，不需要持久化
- 结构化病历（`finish()` 输出）从患者确认/回答后的内容生成
- 两表已通过 `register_id` 关联

### 3.5 前端串联

**改动 1：挂号（支付）成功后自动跳转预问诊**

定位挂号动作触发点（需确认：可能在 `PatientTriage.vue` 内联，或 registration 模块的支付成功页）：

```ts
// 支付成功后
router.push({
  path: '/patient/previsit',
  query: { registerId: String(newRegisterId) }
})
```

**改动 2：`PatientPrevisit.vue` 读 registerId**

页面加载时：
1. 读 `route.query.registerId`
2. 调 `GET /api/ai/consult/preconsult/session/{registerId}` 判断是否有历史会话
   - 有且未完成 → 续接（已有逻辑）
   - 有且已完成 → 显示已完成病历（已有逻辑）
   - 无 → 调 `POST /preconsult/start` 开新会话
3. （可选）顶部渲染导诊摘要卡片：推荐科室、风险等级，给患者连贯感

SSE 对话窗口、结束总结展示不变。

---

## 4. 改动清单

| # | 文件 | 类型 | 改动点 |
|---|------|------|--------|
| 1 | `ai-consult-service/pom.xml` | 配置 | 添加 `spring-cloud-starter-openfeign`（若缺） |
| 2 | `ai-consult-service/.../AiConsultApplication.java` | 启动类 | 加 `@EnableFeignClients` |
| 3 | `ai-consult-service/.../client/TriageClient.java` | **新增** | Feign 接口 |
| 4 | `ai-consult-service/.../client/dto/TriageSummary.java` | **新增** | 本地 DTO |
| 5 | `ai-consult-service/.../service/PreConsultService.java` | 修改 | 注入 TriageClient；`start()`/`reply()` 拉 triage；`buildPromptMessages` 加参数；新增 `safeFetchTriage` / `formatTriageContext` |
| 6 | `ai-consult-service/.../resources/prompts/previsit-prompt.st` | 修改 | 新增【导诊上下文】+【导诊信息处理策略】段；新增 `{triageContext}` 占位符 |
| 7 | 前端挂号触发处（待确认位置） | 修改 | 支付成功后 `router.push` 到 previsit 带 registerId |
| 8 | `xikang-hospital-frontend/.../PatientPrevisit.vue` | 修改 | 读 query.registerId 启动；可选渲染导诊摘要卡片 |

**triage-service 零改动。** 表结构零改动。

---

## 5. 风险与取舍

| 风险 | 说明 | 应对 |
|------|------|------|
| 模型对"是否提及某维度"判断不准 | LLM 行为不确定，可能漏判或误判 | prompt 加"不确定时按未提及处理，宁可多问"；上线后看日志，必要时加规则后处理 |
| Feign 增加首字延迟 | 多一次跨服务 RPC | 设短超时（1.5s），失败降级 |
| `aiAnalysisJson` 较长撑大 prompt | 含 possibleConditions/suggestedExaminations | 可在 `formatTriageContext` 里只取关键字段，或截断 |
| 同一 registerId 多条 triage 记录（重试） | 可能取到旧的 | 确认 `selectByRegisterId` 是否 `ORDER BY id DESC LIMIT 1`（实现时核对 mapper） |

---

## 6. 实现 TODO（review 通过后按序执行）

后端先行，能用 curl 验证后再接前端：

1. 确认 `ai-consult-service/pom.xml` 的 Feign 依赖和 `@EnableFeignClients`
2. 新增 `TriageClient` + `TriageSummary` 本地 DTO
3. 改 `previsit-prompt.st`（加两段 + 占位符）
4. 改 `PreConsultService`（注入 client、start/reply 拉 triage、buildPromptMessages 加参数、formatTriageContext）
5. **手工验证**：用 curl 调 `/api/ai/consult/preconsult/start`，确认开场白复述了导诊信息且只问未提及项
6. 前端挂号跳转 + 预问诊页读 registerId

---

## 7. 待确认的开放问题

1. **前端挂号触发点具体在哪？** 需要确认是 `PatientTriage.vue` 内联挂号，还是 registration 模块独立支付页。这决定改动 #7 的位置。
2. **`selectByRegisterId` 是否返回最新一条？** 实现时需核对 mapper XML。
3. **`aiAnalysisJson` 是否截断？** 如果太长撑爆 prompt，要不要在 `formatTriageContext` 里只取 `possibleConditions`。
